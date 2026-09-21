package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * The Power button.
 *
 * It has two lives. With shell access the app reads the button straight from
 * the kernel and it becomes a key like any other: any number of taps, hold, and
 * combinations. Without shell access no app is ever shown the key, and what is
 * left is two side doors - a hold arrives as an assistant request, a double
 * press as a camera launch - plus whatever the firmware's own switches offer.
 *
 * The bindings are the same in both lives. "Hold" is one binding whichever door
 * it comes through, so gaining or losing shell access changes what can fire,
 * never what is configured.
 */
class PowerActivity : Activity() {

    private val redraw: () -> Unit = { if (!isFinishing) build() }

    override fun onResume() {
        super.onResume()
        Shell.onChange(redraw)
        Shell.connect(this)
        build()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeOnChange(redraw)
    }

    private fun build() {
        val col = Ui.page(this, "Power button")
        val direct = Shell.ready && PowerControl.wanted(this)
        if (direct) direct(col) else sideDoors(col)
        emergencySos(col)
    }

    /**
     * Android starts Emergency SOS on five quick Power presses. That collides
     * with a 5-tap binding, and with fast tapping in general. It is a safety
     * feature, so the app never turns it off by itself: this is the user's switch.
     */
    private fun emergencySos(col: LinearLayout) {
        if (!Shell.ready && !PowerNative.canWriteSecure(this)) return
        col.header("Emergency SOS")
        col.check(
            "Start it on five presses",
            "Untick to use 5 taps for an action",
            PowerNative.emergencySos(this)
        ) { on ->
            PowerNative.setEmergencySos(this, on) { ok ->
                if (!ok) Toast.makeText(this, "The system refused", Toast.LENGTH_LONG).show()
                build()
            }
        }
    }

    private fun bindRow(col: LinearLayout, label: String, t: Trigger, enabled: Boolean = true) {
        val spec = Store.bindings(this).raw(t)
        col.row(label, spec.describe(), enabled) {
            startActivity(ActionPickerActivity.intent(this, t))
        }
    }

    private val power = setOf(HwKey.POWER)

    // ---- with shell access ----------------------------------------------------------------

    private fun direct(col: LinearLayout) {
        bindRow(col, "Tap", Trigger(power, GestureType.TAP, 1))
        bindRow(col, "Double tap", Trigger(power, GestureType.TAP, 2))
        bindRow(col, "Triple tap", Trigger(power, GestureType.TAP, 3))
        bindRow(col, "4 taps", Trigger(power, GestureType.TAP, 4))
        bindRow(col, "5 taps", Trigger(power, GestureType.TAP, 5))
        bindRow(col, "Hold", Trigger(power, GestureType.HOLD))

        col.header("Power held, then another button")
        dev.equwal.assistkey.device.Device.keys(this).forEach { key ->
            bindRow(col, "Power + " + key.label, Trigger.powerThen(key))
        }

        col.header("Safety")
        col.note("Power + Volume up always opens the power menu.")
        col.check("Let Rebind handle the Power button", null, true) { on ->
            PowerControl.setWanted(this, on)
            (ServiceHolder.service as? KeyFilterService)?.syncPower()
            build()
        }

        if (ServiceHolder.service == null) {
            col.note("Button remapping is off.")
        }
    }

    // ---- without it --------------------------------------------------------------------------

    private fun sideDoors(col: LinearLayout) {
        col.header("Hold")
        door(col, Channel.ASSISTANT)
        bindRow(col, "Hold", Channel.POWER_HOLD)
        dev.equwal.assistkey.device.Device.keys(this).forEach { key ->
            bindRow(col, "Power + " + key.label, Trigger.powerThen(key))
        }

        col.header("Double press")
        door(col, Channel.CAMERA)
        bindRow(col, "Double press", Channel.POWER_DOUBLE)

        col.header("Tap")
        col.note("A tap cannot reach any app.")
        firmware(col)

        col.header("Wallet")
        door(col, Channel.WALLET)

        if (!Shell.SUPPORTED) {
            // No shell access in this build: the side doors are everything.
        } else if (Shell.ready) {
            col.check("Let Rebind handle the Power button", "Tap, double tap, hold and more", false) {
                PowerControl.setWanted(this, it)
                (ServiceHolder.service as? KeyFilterService)?.syncPower()
                build()
            }
        } else {
            col.row("Shell access", "Unlocks every Power gesture", state = Shell.describe(this)) {
                startActivity(Intent(this, ShellActivity::class.java))
            }
        }

    }

    /** One side door: its switch, what state it is in, and how to open it. */
    private fun door(col: LinearLayout, ch: Channel) {
        val on = Channels.isEnabled(this, ch)
        col.check(ch.title, ch.summary, on) { checked ->
            Channels.setEnabled(this, ch, checked)
            if (checked && !Channels.isSatisfied(this, ch)) claim(ch)
            build()
        }
        if (on) {
            col.row("Status", null, enabled = false, state = Channels.status(this, ch))
            if (!Channels.isSatisfied(this, ch)) col.button("Set up") { claim(ch) }
        }
    }

    private fun claim(ch: Channel) {
        if (ch == Channel.CAMERA) {
            Toast.makeText(this, "Choose Camera app, then pick Rebind", Toast.LENGTH_LONG).show()
        }
        if (!Channels.safeStart(this, Channels.claimIntent(this, ch))) {
            Toast.makeText(this, "This device has no screen for that", Toast.LENGTH_LONG).show()
        }
    }

    // ---- the firmware's own switches -------------------------------------------------------

    private fun firmware(col: LinearLayout) {
        if (!PowerNative.canWriteSecure(this)) {
            col.note("Needs shell access, or this once from a computer:")
            col.code(PowerNative.GRANT_COMMAND)
            return
        }
        val short = PowerNative.shortPressValue(this)
        col.row("Tap: " + PowerNative.describe(PowerNative.shortPress, short), "Handled by the system") {
            pick("Tap", PowerNative.shortPress, short) { v ->
                applied(PowerNative.setShortPress(this, v))
            }
        }
        val long = PowerNative.longPressValue(this)
        col.row(
            "Hold: " + PowerNative.describe(PowerNative.longPress, long),
            "Must be Digital assistant"
        ) {
            pick("Hold", PowerNative.longPress, long) { v -> applied(PowerNative.setLongPress(this, v)) }
        }
        val ms = PowerNative.longPressMs(this)
        val times = listOf(250, 350, 500, 650, 800, 1000).map { PowerNative.Option(it, it.toString() + " ms") }
        col.row("Hold time: " + PowerNative.describe(times, ms), null) {
            pick("Hold time", times, ms) { v -> applied(PowerNative.setLongPressMs(this, v)) }
        }
    }

    private fun pick(title: String, options: List<PowerNative.Option>, current: Int?, onPick: (Int) -> Unit) {
        val labels = options.map { o ->
            (if (current != null && o.value == current) "* " else "   ") + o.label +
                if (o.note.isBlank()) "" else "  -  " + o.note
        }
        Ui.pick(this, title, labels) { i -> onPick(options[i].value) }
    }

    private fun applied(ok: Boolean) {
        Toast.makeText(this, if (ok) "Applied" else "The system refused", Toast.LENGTH_SHORT).show()
        build()
    }
}
