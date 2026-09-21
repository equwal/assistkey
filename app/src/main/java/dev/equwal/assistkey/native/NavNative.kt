package dev.equwal.assistkey.native

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.WindowInsets

/**
 * How the reader is navigated, as far as the system is concerned: whether the
 * three-button bar is showing, and whether swipe gestures are live.
 *
 * Both can be read here and neither can be changed here. Measured on firmware
 * 1.5.6:
 *
 *  - The bar is Android's navigation-mode overlay. `threebutton` shows it;
 *    `gestural` removes it outright (no pill, no inset) and brings Android's
 *    edge-swipe Back with it. Switching overlays is a shell-only operation.
 *  - The swipe up from the bottom edge to Home is Viwoods' own, present in both
 *    modes, and is switched by Settings.System `disable_gesture_bottom`. That
 *    is an OEM key outside SettingsProvider's public list, so no ordinary app
 *    may write it, whatever permissions it holds.
 *  - In gestural mode the edge-swipe Back can only be removed by shrinking its
 *    inset to nothing: Settings.Secure back_gesture_inset_scale_left/right = 0.
 *
 * Order matters when applying. Changing the overlay makes SystemUI forget
 * `disable_gesture_bottom`, so the overlay goes first and the setting after.
 */
object NavNative {

    private const val GESTURE_BOTTOM = "disable_gesture_bottom"
    private const val OVERLAY = "com.android.internal.systemui.navbar."

    /** True if the three-button bar is on screen, null if it cannot be told yet. */
    fun buttonsShowing(a: Activity): Boolean? {
        val insets = a.window.decorView.rootWindowInsets ?: return null
        val bar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
        val dp = a.resources.displayMetrics.density
        // The button bar is about 48dp tall. Gestural mode leaves nothing here
        // on this firmware, and a thin pill strip on stock Android.
        return maxOf(bar.bottom, bar.left, bar.right) >= 40 * dp
    }

    /** True if the swipe-up gesture is live, null if the setting cannot be read. */
    fun gesturesOn(c: Context): Boolean? {
        val name = dev.equwal.assistkey.device.Device.bottomGestureSetting
        if (name == null) {
            // Stock Android: gestures exist exactly when the bar does not.
            return null
        }
        val v = runCatching { Settings.System.getString(c.contentResolver, name) }
            .getOrElse { return null }
        return v?.trim() != "1"
    }

    /**
     * What a shell has to run to produce the wanted state, in the order it must
     * run. The pause and the double write are not decoration: SystemUI rebuilds
     * itself when the overlay changes and only notices the gesture setting if it
     * changes afterwards.
     */
    fun shellCommands(buttons: Boolean, gestures: Boolean): List<String> {
        val off = if (gestures) "0" else "1"
        val nudge = if (gestures) "1" else "0"
        // Only meaningful without the bar, but harmless with it, and setting it
        // every time means a later switch to gestural mode cannot surprise.
        val scale = if (gestures) "0.6" else "0"
        val out = ArrayList<String>()
        out += "cmd overlay enable-exclusive --category " + OVERLAY + (if (buttons) "threebutton" else "gestural")
        if (dev.equwal.assistkey.device.Device.bottomGestureSetting != null) {
            out += "sleep 4"
            out += "settings put system $GESTURE_BOTTOM $nudge"
            out += "sleep 1"
            out += "settings put system $GESTURE_BOTTOM $off"
        }
        out += "settings put secure back_gesture_inset_scale_left $scale"
        out += "settings put secure back_gesture_inset_scale_right $scale"
        return out
    }

    /** The same, for someone typing them at a computer. */
    fun commands(buttons: Boolean, gestures: Boolean): List<String> =
        shellCommands(buttons, gestures).filterNot { it.startsWith("sleep") }.distinct().map { "adb shell $it" }
}
