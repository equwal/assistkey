package dev.equwal.assistkey.device

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyCharacterMap
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.display.ExtraDim
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What this device has, found by asking, not by making the user press anything.
 *
 * AssistKey is a key remap tool. It must never say "press the button you want
 * to find". So every source here is passive: Android's own key tables, the
 * input devices the framework lists, and - with shell access - the key codes
 * each kernel input node declares. No press is recorded, and no sequence of
 * presses is ever part of detection.
 *
 * The result is held in a preference file of its own. It describes one device
 * and one build, so it is not a setting and never enters an export - see
 * [dev.equwal.assistkey.store.SettingsFile].
 *
 * The parsing and the storage format are pure functions over text, so a test
 * runs them with no device.
 */
object Detect {

    /** The answer for one capability. UNKNOWN means the app may not look. */
    enum class State(val label: String) {
        YES("Yes"),
        NO("No"),
        UNKNOWN("Unknown")
    }

    /** What one detection found. [capabilities] is a capability name to its state. */
    data class Result(
        val fingerprint: String,
        val appVersion: String,
        val name: String,
        val profile: String,
        val keys: List<HwKey>,
        val capabilities: Map<String, State>
    ) {
        fun capability(name: String): State = capabilities[name] ?: State.UNKNOWN
    }

    // ---- capabilities -------------------------------------------------------

    const val KEY_FILTER = "Key filter"
    const val ASSISTANT_ROLE = "Assistant role"
    const val CAMERA_ROUTE = "Double press Power for camera"
    const val SECURE_SETTINGS = "Write secure settings"
    const val SHELL_ACCESS = "Shell access"
    const val EMERGENCY_SOS = "Emergency SOS switch"
    const val FRONTLIGHT = "Frontlight control"
    const val NAV_OVERLAYS = "Navigation overlays"

    /** Every capability this build knows, in the order the screens show them. */
    val capabilityNames: List<String> = listOf(
        KEY_FILTER, ASSISTANT_ROLE, CAMERA_ROUTE, SECURE_SETTINGS,
        SHELL_ACCESS, EMERGENCY_SOS, FRONTLIGHT, NAV_OVERLAYS
    )

    // ---- scan codes ---------------------------------------------------------

    /**
     * The Linux input code for each key this app can bind. `getevent` reports
     * these, not Android key codes, so a detection through the shell has to
     * translate. The values are from the kernel's input-event-codes.h and match
     * Android's own Generic.kl.
     */
    val scanCodes: Map<HwKey, Int> = mapOf(
        HwKey.AI to 59, // KEY_F1: the AI key on the Viwoods reader
        HwKey.F2 to 60,
        HwKey.F3 to 61,
        HwKey.F4 to 62,
        HwKey.PAGE_UP to 104,
        HwKey.PAGE_DOWN to 109,
        HwKey.MUTE to 113,
        HwKey.VOL_DOWN to 114,
        HwKey.VOL_UP to 115,
        HwKey.POWER to PowerControl.KEY_POWER,
        HwKey.CAMERA to 212,
        HwKey.HEADSET to 226,
        HwKey.FOCUS to 528,
        HwKey.ASSIST to 583
    )

    private val byScanCode: Map<Int, HwKey> = scanCodes.entries.associate { (k, v) -> v to k }

    // ---- pure core ----------------------------------------------------------

    /** Preference file for the result. Keep it out of the settings file. */
    const val PREFS = "assistkey_detect"
    private const val K_RESULT = "result"
    private const val FORMAT = 1

    /** Marks a section in the shell probe output. */
    const val MARK = "@@"

    /** Each key once, in the order [HwKey] declares them. */
    fun ordered(keys: Collection<HwKey>): List<HwKey> =
        keys.distinct().sortedBy { it.ordinal }

    /**
     * The Linux key codes the input nodes declare, from `getevent -p`. A node
     * prints its codes in hex after `KEY (0001):` and continues on indented
     * lines until the next event class. Junk lines add nothing.
     */
    fun scanCodesFromGetevent(text: String): Set<Int> {
        val out = LinkedHashSet<Int>()
        var inKeys = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("KEY (0001):") -> {
                    inKeys = true
                    hexInto(line.substringAfter(':'), out)
                }
                line.isEmpty() || line.startsWith("add device") ||
                    line.startsWith("name:") || line.contains("(00") -> inKeys = false
                inKeys -> hexInto(line, out)
            }
        }
        return out
    }

    /** The keys of [scanCodesFromGetevent] this app has a name for. */
    fun keysFromGetevent(text: String): List<HwKey> =
        ordered(scanCodesFromGetevent(text).mapNotNull { byScanCode[it] })

    /** Adds every short hex word in [part]. Anything else is ignored. */
    private fun hexInto(part: String, out: MutableCollection<Int>) {
        part.split(' ', '\t').forEach { word ->
            if (word.isNotEmpty() && word.length <= 4 && word.all(::isHex)) {
                word.toIntOrNull(16)?.let(out::add)
            }
        }
    }

    private fun isHex(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /**
     * The text under [marker] in the shell probe output, up to the next marker.
     * Empty when the marker is not there.
     */
    fun section(text: String, marker: String): String {
        val at = text.indexOf(MARK + marker)
        if (at < 0) return ""
        val from = at + MARK.length + marker.length
        val end = text.indexOf(MARK, from)
        return if (end < 0) text.substring(from) else text.substring(from, end)
    }

    fun encode(r: Result): String = JSONObject()
        .put("format", FORMAT)
        .put("fingerprint", r.fingerprint)
        .put("app", r.appVersion)
        .put("name", r.name)
        .put("profile", r.profile)
        .put("keys", JSONArray(r.keys.map { it.token }))
        .put("capabilities", JSONObject().also { o ->
            r.capabilities.forEach { (name, state) -> o.put(name, state.name) }
        })
        .toString()

    /**
     * The opposite of [encode]. Null for text this build cannot read. A key or
     * a capability this build does not know is dropped, so an older build can
     * still read a newer file.
     */
    fun decode(text: String): Result? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optInt("format", -1) != FORMAT) return null
        val keys = root.optJSONArray("keys") ?: JSONArray()
        val caps = root.optJSONObject("capabilities") ?: JSONObject()
        val states = LinkedHashMap<String, State>()
        capabilityNames.forEach { name ->
            if (!caps.has(name)) return@forEach
            val value = runCatching { State.valueOf(caps.optString(name)) }.getOrNull()
            if (value != null) states[name] = value
        }
        return Result(
            fingerprint = root.optString("fingerprint"),
            appVersion = root.optString("app"),
            name = root.optString("name"),
            profile = root.optString("profile"),
            keys = ordered((0 until keys.length()).mapNotNull { HwKey.fromToken(keys.optString(it)) }),
            capabilities = states
        )
    }

    // ---- storage ------------------------------------------------------------

    private var cachedText: String? = null
    private var cached: Result? = null

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The last detection, or null when none has run on this device yet. */
    fun stored(c: Context): Result? {
        val text = prefs(c).getString(K_RESULT, null) ?: return null
        if (text != cachedText) {
            cached = decode(text)
            cachedText = text
        }
        return cached
    }

    fun save(c: Context, r: Result) {
        val text = encode(r)
        cachedText = text
        cached = r
        prefs(c).edit().putString(K_RESULT, text).apply()
    }

    /**
     * Detects once at first start, and again after a firmware or app change.
     * A stored result that still matches costs one preference read.
     *
     * A result that was found without shell access is also stale as soon as
     * there is shell access, because the shell sees input nodes the framework
     * does not report. The other way round it stands: detection only adds.
     */
    fun refreshIfStale(c: Context) {
        val have = stored(c)
        if (have != null &&
            have.fingerprint == Build.FINGERPRINT &&
            have.appVersion == BuildConfig.VERSION_NAME &&
            !(have.capability(SHELL_ACCESS) != State.YES && Shell.ready)
        ) {
            return
        }
        val app = c.applicationContext
        run(app) { save(app, it) }
    }

    // ---- the probe ----------------------------------------------------------

    /**
     * Asks the device what it has. [done] comes back on the main thread. The
     * framework part is a handful of table lookups; only the shell part, which
     * runs off the main thread, takes any time.
     */
    fun run(c: Context, done: (Result) -> Unit) {
        val app = c.applicationContext
        val caps = LinkedHashMap<String, State>()
        caps[KEY_FILTER] = yesNo(Channels.isAccessibilityOn(app))
        caps[ASSISTANT_ROLE] = yesNo(Channels.isRoleAvailable(app, RoleManager.ROLE_ASSISTANT))
        caps[CAMERA_ROUTE] = maybe(PowerNative.cameraDoubleTapEnabled(app))
        caps[SECURE_SETTINGS] = yesNo(PowerNative.canWriteSecure(app))
        caps[SHELL_ACCESS] = yesNo(Shell.ready)
        caps[EMERGENCY_SOS] = yesNo(PowerNative.canWriteSecure(app) || Shell.ready)

        val framework = frameworkKeys()
        if (!Shell.ready) {
            // Without the shell the light node is usually unreadable, and the
            // overlays cannot be listed at all. Say so rather than guess.
            caps[FRONTLIGHT] = if (anyNode()) State.YES else State.UNKNOWN
            caps[NAV_OVERLAYS] = State.UNKNOWN
            return done(result(framework, caps))
        }
        Shell.run(probeScript()) { r ->
            val keys = if (r.ok) keysFromGetevent(r.output.substringBefore(MARK)) else emptyList()
            caps[FRONTLIGHT] = when {
                !r.ok -> State.UNKNOWN
                section(r.output, "light").contains("present") -> State.YES
                else -> State.NO
            }
            val navbars = section(r.output, "nav").trim().toIntOrNull()
            caps[NAV_OVERLAYS] = when {
                !r.ok || navbars == null -> State.UNKNOWN
                navbars > 0 -> State.YES
                else -> State.NO
            }
            done(result(ordered(framework + keys), caps))
        }
    }

    private fun result(keys: List<HwKey>, caps: Map<String, State>): Result = Result(
        fingerprint = Build.FINGERPRINT,
        appVersion = BuildConfig.VERSION_NAME,
        name = Device.name,
        profile = Device.profile.name,
        keys = keys,
        capabilities = caps
    )

    private fun yesNo(on: Boolean): State = if (on) State.YES else State.NO

    private fun maybe(on: Boolean?): State = if (on == null) State.UNKNOWN else yesNo(on)

    /**
     * What the framework says this device has. [KeyCharacterMap.deviceHasKey]
     * asks every input device; the second pass asks each device by itself and
     * leaves out full keyboards, because a typing keyboard is not a button on
     * this device.
     */
    private fun frameworkKeys(): List<HwKey> {
        val found = LinkedHashSet<HwKey>()
        HwKey.entries.forEach { key ->
            if (runCatching { KeyCharacterMap.deviceHasKey(key.code) }.getOrDefault(false)) {
                found += key
            }
        }
        val codes = HwKey.entries.map { it.code }.toIntArray()
        runCatching { InputDevice.getDeviceIds() }.getOrDefault(IntArray(0)).forEach { id ->
            val device = runCatching { InputDevice.getDevice(id) }.getOrNull() ?: return@forEach
            if (device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) return@forEach
            val has = runCatching { device.hasKeys(*codes) }.getOrNull() ?: return@forEach
            HwKey.entries.forEachIndexed { i, key -> if (has.getOrNull(i) == true) found += key }
        }
        return ordered(found)
    }

    private fun anyNode(): Boolean =
        ExtraDim.nodes.any { runCatching { File(it).exists() }.getOrDefault(false) }

    /**
     * One shell command for the three things only the shell can see: the key
     * codes of every input node, the navigation overlays, and the light node.
     */
    private fun probeScript(): String {
        val nodes = ExtraDim.nodes.joinToString(" ") { "'" + it + "'" }
        return listOf(
            "getevent -p 2>/dev/null",
            "echo '" + MARK + "nav'; cmd overlay list 2>/dev/null | grep -ci navbar",
            "echo '" + MARK + "light'; for n in " + nodes +
                "; do [ -e \$n ] && echo present; done"
        ).joinToString("; ")
    }
}
