package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import dev.equwal.assistkey.device.Detect
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.model.HwKey
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

    private fun boundOnKeys(): Int {
        val keys = Device.keys(this).toSet()
        return Store.bindings(this).all().keys.count { t ->
            HwKey.POWER !in t.keys && t.keys.any { it in keys }
        }
    }
}
