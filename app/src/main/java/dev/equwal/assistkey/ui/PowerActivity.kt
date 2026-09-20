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

    private var readable = true

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        readable = PowerNative.canRead(this)
        val col = Ui.page(this)
        col.title("Power button")
        col.note(
            "The system eats the power key before any app can filter it, so " +
                "these three slots are everything that is reachable."
        )

        if (!PowerNative.canWriteSecure(this)) {
            col.note(
                "The firmware switches below cannot be changed without a " +
                    "permission that only adb can grant. Run this once with the " +
                    "device plugged in:"
            )
            col.code(PowerNative.GRANT_COMMAND)
        } else if (!readable) {
            col.note(
                "Android blocks apps from reading these particular settings, so " +
                    "the current values show as unknown. Writing them still " +
                    "works - pick a value and it is applied."
            )
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
        col.row(
            "Currently: " + PowerNative.describe(PowerNative.shortPress, cur),
            "Handled entirely by the firmware - this rewrites its setting"
        ) {
            pickNative("Short press", PowerNative.shortPress, cur) { v ->
                apply(
                    PowerNative.setShortPress(this, v),
                    "global", PowerNative.SHORT_PRESS, v
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

        onOffRow(
            col,
            "Firmware double-press gesture",
            PowerNative.doubleTapGestureEnabled(this),
            "Must be on for double press to do anything at all"
        ) { on ->
            apply(
                PowerNative.setDoubleTapGestureEnabled(this, on),
                "secure", PowerNative.DOUBLE_TAP, if (on) 1 else 0
            )
        }

        onOffRow(
            col,
            "Camera double-press gesture",
            PowerNative.cameraDoubleTapEnabled(this),
            "The older camera-specific route, still live on this firmware"
        ) { on ->
            apply(
                PowerNative.setCameraDoubleTapEnabled(this, on),
                "secure", PowerNative.CAMERA_DOUBLE_TAP, if (on) 0 else 1
            )
        }
    }

    // ---- long press: assistant impersonation -------------------------------

    private fun longPress(col: LinearLayout) {
        col.header("Press and hold")
        val cur = PowerNative.longPressValue(this)
        val held = Channels.isSatisfied(this, Channel.ASSISTANT)

        col.note(
            when {
                cur == 5 && held ->
                    "Routed here: the firmware sends hold to the assistant, and that is us."
                cur == 5 ->
                    "The firmware sends hold to the assistant, but this app does not " +
                        "hold that role yet."
                cur == null && held ->
                    "This app holds the assistant role. Whether the firmware sends " +
                        "hold to the assistant cannot be read, so try it and see."
                cur == null ->
                    "Neither the firmware setting nor the role is in place yet."
                else ->
                    "The firmware does not send hold to the assistant, so nothing " +
                        "reaches this app."
            }
        )

        val spec = Store.bindings(this)[Channel.POWER_HOLD]
        col.row("Action", spec?.describe() ?: "Default behaviour") {
            startActivity(ActionPickerActivity.intent(this, Channel.POWER_HOLD))
        }

        col.row(
            "Firmware behaviour: " + PowerNative.describe(PowerNative.longPress, cur),
            "Set this to Digital assistant"
        ) {
            pickNative("Press and hold", PowerNative.longPress, cur) { v ->
                apply(PowerNative.setLongPress(this, v), "global", PowerNative.LONG_PRESS, v)
            }
        }

        val ms = PowerNative.longPressMs(this)
        val opts = listOf(250, 350, 500, 650, 800, 1000).map {
            PowerNative.Option(it, it.toString() + " ms")
        }
        col.row(
            "Hold time: " + PowerNative.describe(opts, ms),
            "How long the button must be down before it counts as a hold"
        ) {
            pickNative("Hold time", opts, ms) { v ->
                apply(
                    PowerNative.setLongPressMs(this, v),
                    "global", PowerNative.LONG_PRESS_MS, v
                )
            }
        }
    }

    private fun escapeHatch(col: LinearLayout) {
        col.header("Escape hatch")
        val cur = PowerNative.chordVolumeUpValue(this)
        col.row(
            "Power + Volume up: " + PowerNative.describe(PowerNative.chordVolumeUp, cur),
            "Keep this on the power menu so you can always shut down"
        ) {
            pickNative("Power + Volume up", PowerNative.chordVolumeUp, cur) { v ->
                apply(
                    PowerNative.setChordVolumeUp(this, v),
                    "global", PowerNative.CHORD_VOL_UP, v
                )
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * An explicit On/Off choice rather than a toggle, because when the current
     * value cannot be read there is nothing to toggle away from.
     */
    private fun onOffRow(
        col: LinearLayout,
        title: String,
        current: Boolean?,
        blurb: String,
        onPick: (Boolean) -> Unit
    ) {
        val shown = when (current) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
        col.row(title + ": " + shown, blurb) {
            pickNative(title, PowerNative.onOff, current?.let { if (it) 1 else 0 }) { v ->
                onPick(v == 1)
            }
        }
    }

    private fun pickNative(
        title: String,
        options: List<PowerNative.Option>,
        current: Int?,
        onPick: (Int) -> Unit
    ) {
        val labels = options.map { o ->
            val mark = if (current != null && o.value == current) "* " else "   "
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
                "Refused - run: " + PowerNative.adbFallback(scope, key, value),
                Toast.LENGTH_LONG
            ).show()
        }
        build()
    }
}
