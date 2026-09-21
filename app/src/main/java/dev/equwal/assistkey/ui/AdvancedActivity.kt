package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Detect
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.row

/**
 * Everything that is not part of setting up one button: the key-at-a-time way
 * of working, the tools, and the settings file.
 *
 * Shell access lives at the bottom, on purpose. Most people never need it and
 * the app is complete without it. It is not in the `play` build at all.
 */
class AdvancedActivity : Activity() {

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
        val col = Ui.page(this, "Advanced")
        fullControl(col)
        tools(col)
        hacks(col)
        settingsFile(col)
    }

    /** The hardware view: a key at a time, the Power button, combinations. */
    private fun fullControl(col: LinearLayout) {
        col.header("Full control")
        col.row(
            "Buttons",
            Summary.keys(Device.keys(this).size, boundOnKeys())
        ) { startActivity(Intent(this, KeysActivity::class.java)) }
        col.row(
            "Power button",
            Summary.power(Store.bindings(this).all().keys.count { HwKey.POWER in it.keys })
        ) { startActivity(Intent(this, PowerActivity::class.java)) }
        col.row(
            "Two-button combinations",
            "Hold one, press another"
        ) { startActivity(Intent(this, ChordActivity::class.java)) }
    }

    private fun tools(col: LinearLayout) {
        col.header("Tools")
        col.row(
            "Detect this device",
            Summary.detected(Detect.stored(this)?.keys?.size)
        ) { startActivity(Intent(this, DetectActivity::class.java)) }
        col.row(
            "Button tester",
            "See which presses reach the app"
        ) { startActivity(Intent(this, KeyTesterActivity::class.java)) }

        val t = Store.timing(this)
        col.row(
            "Gesture timing",
            "Multi-tap " + t.multiTapMs + " ms · hold " + t.holdMs + " ms · chord " + t.chordMs + " ms"
        ) { startActivity(Intent(this, TimingActivity::class.java)) }

        col.row(
            "Device report",
            "Help get this device supported"
        ) { startActivity(Intent(this, ReportActivity::class.java)) }
    }

    private fun settingsFile(col: LinearLayout) {
        col.header("Settings file")
        col.row("Export and import") { startActivity(Intent(this, BackupActivity::class.java)) }
    }

    /**
     * The tricks that get more out of the hardware than Android offers an app.
     * They are in one place, so that a user who wants none of them can pass by.
     */
    private fun hacks(col: LinearLayout) {
        col.header("Hardware hacks")
        fun door(title: String, hint: String, ch: Channel) = col.row(
            title, hint, state = if (Channels.isSatisfied(this, ch)) "On" else "Off"
        ) { startActivity(Intent(this, PowerActivity::class.java)) }
        door("Hold Power", "Rebind stands in for the assistant app", Channel.ASSISTANT)
        door("Double press Power", "Rebind stands in for the camera app", Channel.CAMERA)
        door("Wallet button", "Rebind stands in for the wallet app", Channel.WALLET)
        if (Shell.SUPPORTED) {
            col.row(
                "Full Power button",
                "Every Power press, through shell access",
                state = Shell.describe(this)
            ) { startActivity(Intent(this, ShellActivity::class.java)) }
            // No light node, no extra-dim. Detection says so; do not offer it.
            if (Shell.ready && Detect.stored(this)?.capability(Detect.FRONTLIGHT) != Detect.State.NO) {
                col.row(
                    "Extra-dim light",
                    "Below the lowest the system slider allows"
                ) { startActivity(Intent(this, DisplayActivity::class.java)) }
            }
        }
        if (Device.hasFirmwareKeyHooks) {
            val hidden = ViwoodsBridge.keys().filter { ViwoodsBridge.hidesFromFilter(this, it) }
            col.row(
                "Device button settings",
                if (hidden.isEmpty()) "What this device does with each button"
                else "Hiding " + hidden.joinToString(" and ") { it.label.lowercase() } +
                    " from this app",
                state = if (hidden.isEmpty()) null else "Hiding a button"
            ) { startActivity(Intent(this, ViwoodsActivity::class.java)) }
        }

    }

    private fun boundOnKeys(): Int {
        val keys = Device.keys(this).toSet()
        return Store.bindings(this).all().keys.count { t ->
            HwKey.POWER !in t.keys && t.keys.any { it in keys }
        }
    }
}
