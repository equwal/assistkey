package dev.equwal.assistkey.shell

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Shell access, in the build that has none.
 *
 * The `play` flavour carries no Shizuku code and no Shizuku permission. This
 * object has the same surface as the `full` one, and always answers "not
 * available", so the rest of the app needs no flavour checks beyond
 * [SUPPORTED]. Everything that depends on shell access falls back by itself.
 */
object Shell {

    const val SUPPORTED = false
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    data class Result(val exit: Int, val output: String) {
        val ok: Boolean get() = exit == 0
    }

    private val main = Handler(Looper.getMainLooper())

    val ready: Boolean get() = false

    fun state(c: Context): State = State.NOT_INSTALLED
    fun describe(c: Context): String = "Not in this build"

    fun onChange(l: () -> Unit) = Unit
    fun removeOnChange(l: () -> Unit) = Unit
    fun connect(c: Context) = Unit
    fun requestPermission() = Unit

    fun run(command: String, done: ((Result) -> Unit)? = null) {
        done?.let { main.post { it(Result(-1, "no shell access")) } }
    }

    fun runAll(commands: List<String>, done: ((Boolean) -> Unit)? = null) {
        done?.let { main.post { it(commands.isEmpty()) } }
    }

    fun watchKeys(scanCodes: Set<Int>, onKey: (code: Int, down: Boolean, at: Long) -> Unit, started: (Boolean) -> Unit) {
        main.post { started(false) }
    }

    fun stopWatching() = Unit
}
