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

    /** The light nodes to try, in order. Detection looks for the same ones. */
    val nodes = listOf(
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

    // ---- from a key ---------------------------------------------------------------------

    /**
     * The order of brightness, darkest last: off (the system level), then the
     * levels under the floor from high to low. [darker] is one step down that
     * order and [brighter] one step up; both stop at the ends.
     */
    fun darker(level: Int, levels: List<Int>): Int = when {
        levels.isEmpty() -> 0
        level == 0 -> levels.first()
        else -> levels.getOrElse(levels.indexOf(level) + 1) { levels.last() }
    }

    fun brighter(level: Int, levels: List<Int>): Int {
        val i = levels.indexOf(level)
        return if (i <= 0) 0 else levels[i - 1]
    }

    /**
     * On and off. On is the lowest level there is. Off gives the light back to
     * the brightness the user set in the system.
     */
    fun toggled(level: Int, levels: List<Int>): Int = if (level > 0) 0 else levels.lastOrNull() ?: 0

    /**
     * One step of the system brightness, 0..255. A step is a quarter of the
     * present value and at least 5, so steps are fine where the light is low and
     * coarse where it is high. The result stays between [floor] and 255.
     */
    fun systemStep(value: Int, up: Boolean, floor: Int): Int {
        val step = maxOf(5, value / 4)
        return (if (up) value + step else value - step).coerceIn(floor, 255)
    }

    private fun systemBrightness(c: Context, up: Boolean, done: (Boolean) -> Unit) {
        // Brighter from an extra-dim level means: leave extra-dim first.
        if (up && level(c) > 0) return set(c, 0, done)
        Shell.run("settings get system screen_brightness") { r ->
            val now = r.output.trim().toIntOrNull() ?: return@run done(false)
            val next = systemStep(now, up, Device.brightnessFloor ?: 1)
            Shell.run("settings put system screen_brightness_mode 0; settings put system screen_brightness $next") {
                done(it.ok)
            }
        }
    }

    /** Runs a key action: "toggle", "darker", "brighter", "system_up" or "system_down". */
    fun act(c: Context, what: String, done: (Boolean) -> Unit = {}) {
        if (what == "system_up") return systemBrightness(c, true, done)
        if (what == "system_down") return systemBrightness(c, false, done)
        val now = level(c)
        val next = when (what) {
            "darker" -> darker(now, levels())
            "brighter" -> brighter(now, levels())
            else -> toggled(now, levels())
        }
        if (next == now) return done(true)
        set(c, next, done)
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
