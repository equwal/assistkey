package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import dev.equwal.assistkey.bundle.Bundled
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Detect
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.row

/**
 * The tricks that get more out of the hardware than Android offers an app:
 * the app stands in for the assistant, the camera and the wallet to get the
 * Power button, reads Power through shell access, sets the light below its
 * floor, and shows what the device itself does with each button.
 *
 * They are in one place, so that a user who wants none of them can pass by.
 */
class HacksActivity : Activity() {

    private val redraw: () -> Unit = { if (!isFinishing) build() }

    override fun onResume() {
        super.onResume()
        Shell.onChange(redraw)
        build()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeOnChange(redraw)
    }

    private fun build() {
        val col = Ui.page(this, "Hardware hacks")
        fun door(title: String, hint: String, vararg ways: Channel) = col.row(
            title, hint, state = if (ways.any { Channels.isSatisfied(this, it) }) "On" else "Off"
        ) { startActivity(Intent(this, PowerActivity::class.java)) }
        door("Hold Power", "Through the assistant role", Channel.ASSISTANT)
        // One gesture, two ways in. A device sends the double tap to the camera or to the wallet.
        door("Double tap Power", "Through the camera or the wallet intent", Channel.CAMERA, Channel.WALLET)
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
        BundledRows.add(this, col, Bundled.HACKS)
    }
}
