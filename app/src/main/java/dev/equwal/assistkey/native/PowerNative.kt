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
 * event.
 *
 * Two separate restrictions apply, and they are not symmetric. Writing needs
 * WRITE_SECURE_SETTINGS, which is not grantable from a settings screen - it has
 * to come over adb. Reading is blocked outright: since Android 12 a settings
 * key annotated @hide throws SecurityException for any non-system caller, and
 * holding WRITE_SECURE_SETTINGS does not exempt you. So every read here returns
 * null on refusal and the UI says "unknown" rather than inventing a value.
 */
object PowerNative {

    const val GRANT_COMMAND =
        "adb shell pm grant dev.equwal.assistkey android.permission.WRITE_SECURE_SETTINGS"

    const val SHORT_PRESS = "power_button_short_press"
    const val LONG_PRESS = "power_button_long_press"
    const val LONG_PRESS_MS = "power_button_long_press_duration_ms"
    const val CHORD_VOL_UP = "key_chord_power_volume_up"
    const val CAMERA_DOUBLE_TAP = "camera_double_tap_power_gesture_disabled"
    const val DOUBLE_TAP = "double_tap_power_button_gesture_enabled"

    /** A firmware behaviour with its raw value, for a plain radio list. */
    data class Option(val value: Int, val label: String, val note: String = "")

    /**
     * SHORT_PRESS_POWER_* in PhoneWindowManager. 6 and above only exist on
     * newer builds; an unsupported value is ignored by the framework, so they
     * are offered with a warning rather than hidden.
     */
    val shortPress = listOf(
        Option(1, "Sleep", "Stock behaviour"),
        Option(0, "Nothing", "Power becomes a free button - pair with a hold binding"),
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

    val onOff = listOf(Option(1, "On"), Option(0, "Off"))

    fun canWriteSecure(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    // ---- reads: null means unset or refused --------------------------------

    private fun cr(c: Context): ContentResolver = c.contentResolver

    private fun globalInt(c: Context, key: String): Int? =
        runCatching { Settings.Global.getString(cr(c), key)?.trim()?.toIntOrNull() }.getOrNull()

    private fun secureInt(c: Context, key: String): Int? =
        runCatching { Settings.Secure.getString(cr(c), key)?.trim()?.toIntOrNull() }.getOrNull()

    fun shortPressValue(c: Context): Int? = globalInt(c, SHORT_PRESS)
    fun longPressValue(c: Context): Int? = globalInt(c, LONG_PRESS)
    fun longPressMs(c: Context): Int? = globalInt(c, LONG_PRESS_MS)
    fun chordVolumeUpValue(c: Context): Int? = globalInt(c, CHORD_VOL_UP)

    /** Inverted in the framework: the setting records "disabled". */
    fun cameraDoubleTapEnabled(c: Context): Boolean? =
        secureInt(c, CAMERA_DOUBLE_TAP)?.let { it == 0 }

    fun doubleTapGestureEnabled(c: Context): Boolean? =
        secureInt(c, DOUBLE_TAP)?.let { it == 1 }

    /**
     * Whether this build lets us read these at all. Used to explain an
     * "unknown" rather than leaving the user wondering.
     */
    fun canRead(c: Context): Boolean =
        runCatching { Settings.Global.getString(cr(c), LONG_PRESS); true }.getOrDefault(false)

    /** Renders a value against a list, coping with unknown and with junk. */
    fun describe(options: List<Option>, value: Int?): String = when {
        value == null -> "Unknown"
        else -> options.firstOrNull { it.value == value }?.label ?: ("Unknown (" + value + ")")
    }

    // ---- writes: need the adb grant ----------------------------------------

    fun setShortPress(c: Context, v: Int) = putGlobal(c, SHORT_PRESS, v)
    fun setLongPress(c: Context, v: Int) = putGlobal(c, LONG_PRESS, v)
    fun setLongPressMs(c: Context, v: Int) = putGlobal(c, LONG_PRESS_MS, v)
    fun setChordVolumeUp(c: Context, v: Int) = putGlobal(c, CHORD_VOL_UP, v)

    /** Takes the user-facing sense; the stored value is inverted. */
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
}
