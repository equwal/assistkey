package dev.equwal.assistkey.shell

import android.content.Context
import android.provider.Settings
import android.util.Log
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.store.Store

/**
 * Taking the Power button over, and giving it back.
 *
 * No app is ever shown the Power key: the window manager acts on it before
 * input reaches anybody. But the shell user can read the kernel input nodes,
 * so with shell access the button can be watched from outside the framework.
 * To make that useful the firmware has to stop reacting first - short press,
 * long press and the double-tap camera gesture are all switched to "nothing" -
 * and from then on every tap, double tap and hold is ours to time and to bind,
 * through the same gesture engine that serves the other keys.
 *
 * The firmware's own values are saved before they are touched and restored the
 * moment any of this stops being true: shell access lost, the app locked, the
 * user switching it off. Restoring does not need shell access; the app grants
 * itself WRITE_SECURE_SETTINGS through the shell the first time it connects,
 * for exactly this reason. A restart that leaves Shizuku stopped therefore
 * leaves a Power button that works normally, not one that does nothing.
 *
 * Power + Volume up is always left opening the power menu.
 */
object PowerControl {

    private const val TAG = "AssistKey"
    private const val PREFS = "assistkey_power"
    private const val K_WANTED = "wanted"
    private const val K_TAKEN = "taken"
    private const val K_SAVED = "saved_"

    const val KEY_POWER = 116

    private class Knob(val scope: String, val name: String, val managed: String)

    private val knobs = listOf(
        Knob("global", "power_button_short_press", "0"),
        Knob("global", "power_button_long_press", "0"),
        Knob("secure", "camera_double_tap_power_gesture_disabled", "1"),
        Knob("secure", "double_tap_power_button_gesture_enabled", "0"),
        // The way out, whatever else happens.
        Knob("global", "key_chord_power_volume_up", "2")
    )

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** True while raw Power presses are reaching the gesture engine. */
    @Volatile var active = false
        private set

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The user's switch. On by default: it only takes effect with shell access. */
    fun wanted(c: Context): Boolean = prefs(c).getBoolean(K_WANTED, true)

    fun setWanted(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(K_WANTED, on).apply()
    }

    private fun anyPowerBinding(c: Context): Boolean =
        Store.bindings(c).all().any { (t, _) -> dev.equwal.assistkey.model.HwKey.POWER in t.keys }

    /**
     * Brings reality into line with what should be true right now. Cheap and
     * idempotent; call it whenever shell access, the licence, the bindings or
     * the service may have changed. [onKey] is where presses are delivered.
     */
    fun sync(c: Context, serviceRunning: Boolean, onKey: (down: Boolean, at: Long) -> Unit) {
        val should = serviceRunning && Shell.ready && wanted(c) &&
            License.active(c) && anyPowerBinding(c)
        when {
            should && !active -> take(c, onKey)
            !should && (active || prefs(c).getBoolean(K_TAKEN, false)) -> release(c)
        }
    }

    private var taking = false

    private fun take(c: Context, onKey: (Boolean, Long) -> Unit) {
        if (taking) return
        taking = true
        main.postDelayed({ taking = false }, 8000L)
        val p = prefs(c)
        val read = knobs.joinToString("; ") { "settings get " + it.scope + " " + it.name }
        Shell.run(read) { r ->
            if (!r.ok) return@run
            // Save once. A second take() after a crash must not record our own
            // values as if they were the firmware's.
            if (!p.getBoolean(K_TAKEN, false)) {
                val lines = r.output.trim().lines()
                val e = p.edit()
                knobs.forEachIndexed { i, k -> e.putString(K_SAVED + k.name, lines.getOrNull(i)?.trim() ?: "null") }
                e.putBoolean(K_TAKEN, true).apply()
            }
            Shell.runAll(knobs.map { "settings put " + it.scope + " " + it.name + " " + it.managed }) {
                Shell.watchKeys(setOf(KEY_POWER), { _, down, at -> onKey(down, at) }) { ok ->
                    active = ok
                    taking = false
                    Log.i(TAG, "power button: " + if (ok) "managed" else "watch failed")
                    if (!ok) release(c)
                }
            }
        }
    }

    private fun release(c: Context) {
        active = false
        Shell.stopWatching()
        val p = prefs(c)
        if (!p.getBoolean(K_TAKEN, false)) return

        val saved = knobs.associateWith { p.getString(K_SAVED + it.name, "null") ?: "null" }
        if (Shell.ready) {
            Shell.runAll(saved.map { (k, v) ->
                if (v == "null") "settings delete " + k.scope + " " + k.name
                else "settings put " + k.scope + " " + k.name + " " + v
            }) { ok -> if (ok) p.edit().putBoolean(K_TAKEN, false).apply() }
            return
        }
        // No shell: fall back on the permission the shell granted us earlier.
        val ok = saved.all { (k, v) ->
            runCatching {
                val value = if (v == "null") null else v
                if (k.scope == "global") Settings.Global.putString(c.contentResolver, k.name, value)
                else Settings.Secure.putString(c.contentResolver, k.name, value)
            }.getOrDefault(false)
        }
        if (ok) p.edit().putBoolean(K_TAKEN, false).apply()
        Log.i(TAG, "power button: handed back to the firmware" + if (ok) "" else " (incomplete)")
    }
}
