package dev.equwal.assistkey.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Shell access, by way of Shizuku.
 *
 * Shizuku is a separate, free app that a user starts from the device itself
 * through Android's wireless debugging - no computer, no root. Once it is
 * running and has said yes to us, this object can start processes under the
 * shell uid, and everything Android keeps from ordinary apps is within reach:
 * the kernel input nodes (so the Power key), `settings`, `cmd overlay`.
 *
 * It uses Shizuku's plain remote-process call and nothing grander. Shizuku's
 * bound "user service" would be the tidier design, but its starter dies inside
 * LoadedApk.makeApplication on Android 16 before our code is ever loaded, and a
 * pipe from `getevent` needs none of it.
 *
 * All of this is optional. Without shell access the app falls back on what an
 * installed app can do alone, and says what is missing.
 */
object Shell {

    private const val TAG = "AssistKey"

    /** This build can use shell access. The `play` flavour has a stub with false here. */
    const val SUPPORTED = true
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 7301

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = ArrayList<() -> Unit>()
    private var hooked = false
    private var granted = false

    val ready: Boolean get() = alive() && permitted()

    fun state(c: Context): State = when {
        ready -> State.READY
        !installed(c) -> State.NOT_INSTALLED
        !alive() -> State.NOT_RUNNING
        else -> State.NO_PERMISSION
    }

    fun describe(c: Context): String = when (state(c)) {
        State.READY -> "On"
        State.NO_PERMISSION -> "Shizuku is running - permission needed"
        State.NOT_RUNNING -> "Shizuku is installed but not started"
        State.NOT_INSTALLED -> "Off - needs the free Shizuku app"
    }

    /** Called whenever the state may have changed; always on the main thread. */
    fun onChange(l: () -> Unit) { if (l !in listeners) listeners.add(l) }
    fun removeOnChange(l: () -> Unit) { listeners.remove(l) }
    private fun changed() = main.post { ArrayList(listeners).forEach { it() } }

    private fun installed(c: Context): Boolean =
        runCatching { c.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true }.getOrDefault(false)

    private fun alive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun permitted(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Safe to call often. Registers for Shizuku's comings and goings, once. */
    fun connect(c: Context) {
        val app = c.applicationContext
        if (!hooked) {
            hooked = true
            runCatching {
                Shizuku.addBinderReceivedListenerSticky { onUp(app) }
                Shizuku.addBinderDeadListener {
                    stopWatching()
                    changed()
                }
                Shizuku.addRequestPermissionResultListener { _, _ -> onUp(app) }
            }
        }
        onUp(app)
    }

    private fun onUp(app: Context) {
        if (ready && !granted) {
            granted = true
            // With this the app can put the Power button back to firmware
            // defaults by itself, even when Shizuku is gone after a restart.
            run("pm grant " + app.packageName + " android.permission.WRITE_SECURE_SETTINGS")
            Log.i(TAG, "shell access ready")
        }
        changed()
    }

    fun requestPermission() {
        runCatching { if (alive() && !permitted()) Shizuku.requestPermission(REQUEST_CODE) }
    }

    // ---- processes ---------------------------------------------------------------------------

    /**
     * Shizuku.newProcess is private in the v13 API but is the supported wire
     * call underneath, and the one every shell-style client uses.
     */
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    private fun start(vararg argv: String): Process? = runCatching {
        newProcess.invoke(null, argv, null, null) as Process
    }.onFailure { Log.w(TAG, "shell process failed: " + argv.joinToString(" "), it) }.getOrNull()

    data class Result(val exit: Int, val output: String) {
        val ok: Boolean get() = exit == 0
    }

    private fun execNow(command: String): Result {
        val p = start("sh", "-c", "$command 2>&1") ?: return Result(-1, "no shell access")
        return runCatching {
            val out = p.inputStream.bufferedReader().readText()
            Result(p.waitFor(), out)
        }.getOrElse { Result(-1, it.toString()) }
    }

    /** Runs off the main thread; [done] comes back on it. */
    fun run(command: String, done: ((Result) -> Unit)? = null) {
        worker.execute {
            val r = if (ready) execNow(command) else Result(-1, "no shell access")
            if (!r.ok) Log.i(TAG, "shell: " + command + " -> " + r.exit + " " + r.output.take(200))
            done?.let { main.post { it(r) } }
        }
    }

    /** Commands in order, stopping at nothing; [done] gets true if every one succeeded. */
    fun runAll(commands: List<String>, done: ((Boolean) -> Unit)? = null) {
        worker.execute {
            var allOk = true
            for (c in commands) if (!ready || !execNow(c).ok) allOk = false
            done?.let { main.post { it(allOk) } }
        }
    }

    // ---- keys ----------------------------------------------------------------------------------

    private val watchers = ArrayList<Process>()
    @Volatile private var generation = 0

    /**
     * Reports presses and releases of the given Linux key codes, read from the
     * kernel input nodes by `getevent` running as the shell user. Only the
     * nodes that declare one of the keys are opened, which keeps the
     * touchscreen's firehose out of it.
     */
    fun watchKeys(scanCodes: Set<Int>, onKey: (code: Int, down: Boolean, at: Long) -> Unit, started: (Boolean) -> Unit) {
        stopWatching()
        val mine = ++generation
        worker.execute {
            val nodes = nodesDeclaring(scanCodes)
            val procs = nodes.mapNotNull { node -> start("getevent", "-q", node)?.also { pump(it, scanCodes, mine, onKey) } }
            synchronized(watchers) {
                if (generation == mine) watchers.addAll(procs) else procs.forEach { it.destroy() }
            }
            Log.i(TAG, "watching " + nodes.joinToString(" "))
            main.post { started(procs.isNotEmpty()) }
        }
    }

    fun stopWatching() {
        generation++
        synchronized(watchers) {
            watchers.forEach { p -> runCatching { p.destroy() } }
            watchers.clear()
        }
    }

    /** Lines look like `0001 0074 00000001`: type, code, value, in hex. */
    private fun pump(p: Process, wanted: Set<Int>, mine: Int, onKey: (Int, Boolean, Long) -> Unit) {
        Thread({
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    if (generation != mine) return@forEachLine
                    val f = line.trim().split(' ').filter { it.isNotEmpty() }
                    if (f.size != 3 || f[0] != "0001") return@forEachLine
                    val code = f[1].toIntOrNull(16) ?: return@forEachLine
                    val value = f[2].toLongOrNull(16) ?: return@forEachLine
                    // value 2 is auto-repeat, which carries no news
                    if (code !in wanted || value > 1L) return@forEachLine
                    val at = SystemClock.uptimeMillis()
                    main.post { if (generation == mine) onKey(code, value == 1L, at) }
                }
            }
            // The pipe closed: Shizuku went away, or we were told to stop.
            if (generation == mine) main.post { changed() }
        }, "keys").apply { isDaemon = true }.start()
    }

    /** `getevent -p` prints, per device, the hex key codes it declares. */
    private fun nodesDeclaring(codes: Set<Int>): List<String> {
        val hex = codes.map { "%04x".format(it) }.toSet()
        val found = LinkedHashSet<String>()
        var node: String? = null
        var inKeys = false
        execNow("getevent -p").output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("add device") -> {
                    node = line.substringAfter(": ").trim()
                    inKeys = false
                }
                line.startsWith("KEY (0001):") -> {
                    inKeys = true
                    if (line.substringAfter(":").split(' ').any { it in hex }) node?.let(found::add)
                }
                inKeys && line.contains("(00") -> inKeys = false // next event class
                inKeys -> if (line.split(' ').any { it in hex }) node?.let(found::add)
            }
        }
        return found.toList()
    }
}
