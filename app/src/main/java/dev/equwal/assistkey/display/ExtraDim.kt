package dev.equwal.assistkey.display

import android.content.Context
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.shell.Shell

/**
 * Dimmer than the dimmest the system allows.
 *
 * Measured on the Viwoods reader: the frontlight is an ordinary backlight LED
 * whose driver accepts 1..255 (and has 2047 real steps behind that), but the
 * framework will not go below 5. Ask for 4 through any official route - the
 * brightness setting, even the shell's `cmd display set-brightness` - and it
 * snaps to zero. Writing the LED node directly gets under the floor, and the
 * value then sticks until the system slider is next moved.
 *
 * The node belongs to `system`, so this needs more than the shell user: it
 * works when Shizuku itself runs as root, or on firmware whose shell may `su`
 * (userdebug builds, which is how these readers ship). Otherwise the write is
 * refused and the screen says so.
 */
object ExtraDim {

    private const val PREFS = "assistkey_display"
    private const val K_LEVEL = "extra_dim"

    private val nodes = listOf(
        "/sys/class/leds/lcd-backlight/brightness",
        "/sys/class/backlight/panel0-backlight/brightness"
    )

    /** 0 is off; otherwise the raw value to hold the light at. */
    fun level(c: Context): Int =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_LEVEL, 0)

    /** The raw values on offer: everything under the system's floor. */
    fun levels(): List<Int> = (1 until (Device.brightnessFloor ?: 5)).toList().reversed()

    fun set(c: Context, level: Int, done: (Boolean) -> Unit) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(K_LEVEL, level).apply()
        if (level == 0) restore(done) else write(level, done)
    }

    /** Re-asserts the chosen level; called when the screen comes on. */
    fun reapply(c: Context) {
        val l = level(c)
        if (l > 0 && Shell.ready) write(l) {}
    }

    private fun write(value: Int, done: (Boolean) -> Unit) {
        // First node that exists; plain write, then through su if refused.
        val script = nodes.joinToString(" ") { "'$it'" }.let { list ->
            "for n in $list; do [ -e \$n ] || continue; " +
                "echo $value > \$n 2>/dev/null || su 0 sh -c \"echo $value > \$n\"; " +
                "[ \"\$(cat \$n)\" = \"$value\" ] && exit 0; exit 1; done; exit 2"
        }
        Shell.run(script) { done(it.ok) }
    }

    /** Hands the light back by nudging the system's own brightness setting. */
    private fun restore(done: (Boolean) -> Unit) {
        Shell.run(
            "v=\$(settings get system screen_brightness); " +
                "settings put system screen_brightness \$((v+1)); settings put system screen_brightness \$v"
        ) { done(it.ok) }
    }
}
