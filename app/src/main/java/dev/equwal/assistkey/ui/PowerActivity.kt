package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * Power is its own screen because it is not like the other keys.
 *
 * PhoneWindowManager consumes KEYCODE_POWER before the input dispatcher, so no
 * app - accessibility service or otherwise - ever sees it. That leaves exactly
 * three slots, each reached a different way: short press is firmware-only,
 * double press arrives as a camera or wallet launch, and long press arrives as
 * an assistant request. No multi-tap beyond two, and no chords.
 */
class PowerActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("Power button")
        col.note(
            "The system eats the power key before any app can filter it, so " +
                "these three slots are everything that is reachable."
        )
        if (!PowerNative.canWriteSecure(this)) {
            col.note(
                "Short press and the firmware gesture switches below need a " +
                    "permission that only adb can grant. Run this once with the " +
                    "device plugged in:"
            )
            col.code(PowerNative.GRANT_COMMAND)
        }

        shortPress(col)
        doublePress(col)
        longPress(col)
        escapeHatch(col)
    }

    // ---- short press: firmware only ---------------------------------------

    private fun shortPress(col: LinearLayout) {
        col.header("Short press")
        val cur = PowerNative.shortPressValue(this)
        val label = PowerNative.shortPress.firstOrNull { it.value == cur }?.label
            ?: ("Unknown (" + cur + ")")
        col.row(
            "Currently: " + label,
            "Handled entirely by the firmware - this rewrites its setting"
        ) {
            pickNative("Short press", PowerNative.shortPress, cur) { v ->
                apply(
                    PowerNative.setShortPress(this, v),
                    "global", "power_button_short_press", v
                )
            }
        }
    }

    // ---- double press: camera / wallet impersonation -----------------------

    private fun doublePress(col: LinearLayout) {
        col.header("Double press")
        val camera = Channels.isSatisfied(this, Channel.CAMERA)
        val wallet = Channels.isSatisfied(this, Channel.WALLET)

        col.note(
            when {
                camera && wallet -> "Routed here through both the camera and wallet channels."
                camera -> "Routed here through the camera channel."
                wallet -> "Routed here through the wallet channel."
                else ->
                    "Nothing is routing double press here yet. Turn on the camera " +
                        "or wallet channel on the main screen, then pick this app " +
                        "as the double-press target in system settings."
            }
        )

        val spec = Store.bindings(this)[Channel.POWER_DOUBLE]
        col.row("Action", spec?.describe() ?: "Default behaviour") {
            startActivity(ActionPickerActivity.intent(this, Channel.POWER_DOUBLE))
        }

        col.row(
            "Firmware double-press gesture: " +
                if (PowerNative.doubleTapGestureEnabled(this)) "on" else "off",
            "Must be on for double press to do anything at all"
        ) {
            val on = !PowerNative.doubleTapGestureEnabled(this)
            apply(
                PowerNative.setDoubleTapGestureEnabled(this, on),
                "secure", "double_tap_power_button_gesture_enabled", if (on) 1 else 0
            )
        }

        col.row(
            "Camera double-press gesture: " +
                if (PowerNative.cameraDoubleTapEnabled(this)) "on" else "off",
            "The older camera-specific route, still live on this firmware"
        ) {
            val on = !PowerNative.cameraDoubleTapEnabled(this)
            apply(
                PowerNative.setCameraDoubleTapEnabled(this, on),
                "secure", "camera_double_tap_power_gesture_disabled", if (on) 0 else 1
            )
        }
    }

    // ---- long press: assistant impersonation -------------------------------

    private fun longPress(col: LinearLayout) {
        col.header("Press and hold")
        val cur = PowerNative.longPressValue(this)
        val assistant = cur == 5
        val held = Channels.isSatisfied(this, Channel.ASSISTANT)

        col.note(
            when {
                assistant && held ->
                    "Routed here: the firmware sends hold to the assistant, and that is us."
                assistant ->
                    "The firmware sends hold to the assistant, but this app does not " +
                        "hold that role yet."
                else ->
                    "The firmware does not send hold to the assistant, so nothing " +
                        "reaches this app."
            }
        )

        val spec = Store.bindings(this)[Channel.POWER_HOLD]
        col.row("Action", spec?.describe() ?: "Default behaviour") {
            startActivity(ActionPickerActivity.intent(this, Channel.POWER_HOLD))
        }

        val label = PowerNative.longPress.firstOrNull { it.value == cur }?.label
            ?: ("Unknown (" + cur + ")")
        col.row("Firmware behaviour: " + label, "Set this to Digital assistant") {
            pickNative("Press and hold", PowerNative.longPress, cur) { v ->
                apply(
                    PowerNative.setLongPress(this, v),
                    "global", "power_button_long_press", v
                )
            }
        }

        col.row(
            "Hold time: " + PowerNative.longPressMs(this) + " ms",
            "How long the button must be down before it counts as a hold"
        ) {
            val opts = listOf(250, 350, 500, 650, 800, 1000).map {
                PowerNative.Option(it, it.toString() + " ms")
            }
            pickNative("Hold time", opts, PowerNative.longPressMs(this)) { v ->
                apply(
                    PowerNative.setLongPressMs(this, v),
                    "global", "power_button_long_press_duration_ms", v
                )
            }
        }
    }

    private fun escapeHatch(col: LinearLayout) {
        col.header("Escape hatch")
        val cur = PowerNative.chordVolumeUpValue(this)
        val label = PowerNative.chordVolumeUp.firstOrNull { it.value == cur }?.label
            ?: ("Unknown (" + cur + ")")
        col.row(
            "Power + Volume up: " + label,
            "Keep this on the power menu so you can always shut down"
        ) {
            pickNative("Power + Volume up", PowerNative.chordVolumeUp, cur) { v ->
                apply(
                    PowerNative.setChordVolumeUp(this, v),
                    "global", "key_chord_power_volume_up", v
                )
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private fun pickNative(
        title: String,
        options: List<PowerNative.Option>,
        current: Int,
        onPick: (Int) -> Unit
    ) {
        val labels = options.map { o ->
            val mark = if (o.value == current) "* " else "   "
            mark + o.label + if (o.note.isBlank()) "" else "  -  " + o.note
        }
        Ui.pick(this, title, labels) { i -> onPick(options[i].value) }
    }

    /** A refused write is the normal case without the adb grant; say so. */
    private fun apply(ok: Boolean, scope: String, key: String, value: Int) {
        if (ok) {
            Toast.makeText(this, "Applied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Refused - run: adb shell settings put " + scope + " " + key + " " + value,
                Toast.LENGTH_LONG
            ).show()
        }
        build()
    }
}
