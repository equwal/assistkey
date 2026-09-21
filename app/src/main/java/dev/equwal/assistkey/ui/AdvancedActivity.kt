package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
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
        settingsFile(col)
        shell(col)
    }

    /** The hardware view: a key at a time, the Power button, combinations. */
    private fun fullControl(col: LinearLayout) {
        col.header("Full control")
        col.row(
            "Keys",
            Summary.keys(Device.keys(this).size, boundOnKeys())
        ) { startActivity(Intent(this, KeysActivity::class.java)) }
        col.row(
            "Power button",
            Summary.power(Store.bindings(this).all().keys.count { HwKey.POWER in it.keys })
        ) { startActivity(Intent(this, PowerActivity::class.java)) }
        col.row(
            "Two-key combinations",
            "Hold one key and press another"
        ) { startActivity(Intent(this, ChordActivity::class.java)) }
    }

    private fun tools(col: LinearLayout) {
        col.header("Tools")
        col.row(
            "Key tester",
            "See exactly which keys reach the filter on this device"
        ) { startActivity(Intent(this, KeyTesterActivity::class.java)) }

        val t = Store.timing(this)
        col.row(
            "Gesture timing",
            "Multi-tap " + t.multiTapMs + " ms · hold " + t.holdMs + " ms · chord " + t.chordMs + " ms"
        ) { startActivity(Intent(this, TimingActivity::class.java)) }

        if (Device.hasFirmwareKeyHooks) {
            val hidden = ViwoodsBridge.keys().filter { ViwoodsBridge.hidesFromFilter(this, it) }
            col.row(
                "Firmware key hooks",
                if (hidden.isEmpty()) "What the firmware itself does with each key"
                else "Hiding " + hidden.joinToString(" and ") { it.label.lowercase() } +
                    " from this app - tap for the fix",
                state = if (hidden.isEmpty()) null else "Hiding a key"
            ) { startActivity(Intent(this, ViwoodsActivity::class.java)) }
        }

        col.row(
            "Device report",
            "Help get this device fully supported - you see everything before it is sent"
        ) { startActivity(Intent(this, ReportActivity::class.java)) }
    }

    private fun settingsFile(col: LinearLayout) {
        col.header("Settings file")
        col.row(
            "Export and import",
            "Save your settings to a file, or share them"
        ) { startActivity(Intent(this, BackupActivity::class.java)) }
    }

    private fun shell(col: LinearLayout) {
        if (!Shell.SUPPORTED) return
        col.header("For devices with Shizuku or root")
        col.row(
            "Shell access",
            "Optional. More Power button gestures, and system switches",
            state = Shell.describe(this)
        ) { startActivity(Intent(this, ShellActivity::class.java)) }
        if (Shell.ready) {
            col.row(
                "Extra-dim light",
                "Below the lowest the system slider allows"
            ) { startActivity(Intent(this, DisplayActivity::class.java)) }
        }
    }

    private fun boundOnKeys(): Int {
        val keys = Device.keys(this).toSet()
        return Store.bindings(this).all().keys.count { t ->
            HwKey.POWER !in t.keys && t.keys.any { it in keys }
        }
    }
}
