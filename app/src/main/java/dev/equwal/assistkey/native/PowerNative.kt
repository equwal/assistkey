package dev.equwal.assistkey.native

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * The firmware's own power-button behaviour, which lives in Settings.Global /
 * Settings.Secure rather than in anything an app can intercept.
 *
 * This is the only way to touch short-press Power at all: no app ever sees that
 * event. Writing here needs WRITE_SECURE_SETTINGS, which is not grantable from
 * a settings screen - it has to come over adb - so every write is attempted and
 * reported rather than assumed.
 */
object PowerNative {

    const val GRANT_COMMAND =
        "adb shell pm grant dev.equwal.assistkey android.permission.WRITE_SECURE_SETTINGS"

    private const val SHORT_PRESS = "power_button_short_press"
    private const val LONG_PRESS = "power_button_long_press"
    private const val LONG_PRESS_MS = "power_button_long_press_duration_ms"
    private const val CHORD_VOL_UP = "key_chord_power_volume_up"
    private const val CAMERA_DOUBLE_TAP = "camera_double_tap_power_gesture_disabled"
    private const val DOUBLE_TAP = "double_tap_power_button_gesture_enabled"

    /** A firmware behaviour with its raw value, for a plain radio list. */
    data class Option(val value: Int, val label: String, val note: String = "")

    /**
     * SHORT_PRESS_POWER_* in PhoneWindowManager. 6 and above only exist on
     * newer builds; an unsupported value is simply ignored by the framework,
     * so they are offered with a warning rather than hidden.
     */
    val shortPress = listOf(
        Option(1, "Sleep", "Stock behaviour"),
        Option(0, "Nothing", "Power becomes a free button - use with a long-press binding"),
        Option(4, "Home"),
        Option(5, "Close keyboard, else Home"),
        Option(2, "Sleep immediately"),
        Option(3, "Sleep immediately and go Home"),
        Option(6, "Lock, else sleep", "Newer builds only"),
        Option(7, "Screensaver, else sleep", "Newer builds only")
    )

    /** LONG_PRESS_POWER_* in PhoneWindowManager. */
    val longPress = listOf(
        Option(5, "Digital assistant", "Required for the Assistant channel"),
        Option(1, "Power menu", "Stock behaviour on most devices"),
        Option(0, "Nothing"),
        Option(4, "Voice assist"),
        Option(2, "Power off (with confirmation)"),
        Option(3, "Power off immediately", "No confirmation - be careful")
    )

    /** KEY_CHORD_POWER_VOLUME_UP. Keep an escape hatch to the power menu. */
    val chordVolumeUp = listOf(
        Option(2, "Power menu", "Recommended escape hatch"),
        Option(1, "Mute"),
        Option(0, "Nothing")
    )

    fun canWriteSecure(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    // ---- reads (always allowed) -------------------------------------------

    private fun cr(c: Context): ContentResolver = c.contentResolver

    fun shortPressValue(c: Context): Int = Settings.Global.getInt(cr(c), SHORT_PRESS, 1)
    fun longPressValue(c: Context): Int = Settings.Global.getInt(cr(c), LONG_PRESS, 1)
    fun longPressMs(c: Context): Int = Settings.Global.getInt(cr(c), LONG_PRESS_MS, 500)
    fun chordVolumeUpValue(c: Context): Int = Settings.Global.getInt(cr(c), CHORD_VOL_UP, 2)

    /** Inverted in the framework: the setting records "disabled". */
    fun cameraDoubleTapEnabled(c: Context): Boolean =
        Settings.Secure.getInt(cr(c), CAMERA_DOUBLE_TAP, 0) == 0

    fun doubleTapGestureEnabled(c: Context): Boolean =
        Settings.Secure.getInt(cr(c), DOUBLE_TAP, 0) == 1

    // ---- writes (need the adb grant) --------------------------------------

    fun setShortPress(c: Context, v: Int) = putGlobal(c, SHORT_PRESS, v)
    fun setLongPress(c: Context, v: Int) = putGlobal(c, LONG_PRESS, v)
    fun setLongPressMs(c: Context, v: Int) = putGlobal(c, LONG_PRESS_MS, v)
    fun setChordVolumeUp(c: Context, v: Int) = putGlobal(c, CHORD_VOL_UP, v)
    fun setCameraDoubleTapEnabled(c: Context, on: Boolean) =
        putSecure(c, CAMERA_DOUBLE_TAP, if (on) 0 else 1)
    fun setDoubleTapGestureEnabled(c: Context, on: Boolean) =
        putSecure(c, DOUBLE_TAP, if (on) 1 else 0)

    private fun putGlobal(c: Context, k: String, v: Int): Boolean =
        runCatching { Settings.Global.putInt(cr(c), k, v) }.getOrDefault(false)

    private fun putSecure(c: Context, k: String, v: Int): Boolean =
        runCatching { Settings.Secure.putInt(cr(c), k, v) }.getOrDefault(false)

    /** So the user can paste the exact command when a write is refused. */
    fun adbFallback(scope: String, key: String, value: Int): String =
        "adb shell settings put " + scope + " " + key + " " + value

    fun shortPressAdb(v: Int) = adbFallback("global", SHORT_PRESS, v)
    fun longPressAdb(v: Int) = adbFallback("global", LONG_PRESS, v)
}
