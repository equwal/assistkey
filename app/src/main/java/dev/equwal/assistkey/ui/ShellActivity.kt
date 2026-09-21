package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * Getting shell access, from the device itself.
 *
 * Shizuku is the piece that makes it possible without a computer: it pairs with
 * Android's own wireless debugging over the loopback interface and then runs a
 * small server as the shell user. This screen walks through that and then asks
 * Shizuku for permission.
 */
class ShellActivity : Activity() {

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
        val state = Shell.state(this)
        val col = Ui.page(this)
        col.title("Shell access")
        col.row("Status: " + Shell.describe(this), null, enabled = false)

        col.note(
            "Android keeps a few things from every installed app: the Power " +
                "button, the navigation bar, the system gestures, a device maker's " +
                "own key settings. The shell user - the one a computer gets over " +
                "USB debugging - can reach all of them. Shizuku, a free app, gives " +
                "AssistKey that same access from the device itself, with no " +
                "computer and no root."
        )

        if (state == Shell.State.READY) {
            col.header("What this unlocks")
            col.note(
                "Power button: tap, double tap, more taps, hold, and combinations " +
                    "with other keys.\n" +
                    "Navigation: show or hide the button bar and switch system " +
                    "gestures, from inside the app.\n" +
                    "Device key settings that would otherwise need a computer."
            )
            col.note(
                "Shizuku started over wireless debugging stops when the device " +
                    "restarts. Until it is started again AssistKey gives the Power " +
                    "button back to the system, so it always works."
            )
            return
        }

        steps(col, state)
    }

    private fun steps(col: LinearLayout, state: Shell.State) {
        col.header("1. Install Shizuku")
        if (state == Shell.State.NOT_INSTALLED) {
            col.button("Get Shizuku") {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Shell.SHIZUKU_PACKAGE))
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                if (!tryStart(market)) tryStart(web)
            }
        } else {
            col.note("Done.")
        }

        col.header("2. Turn on wireless debugging")
        col.note(
            "Settings > About > tap Build number seven times to unlock Developer " +
                "options. In Developer options, switch on Wireless debugging. It " +
                "needs Wi-Fi to be connected, but nothing leaves the device."
        )
        col.button("Open Developer options") {
            if (!tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
                tryStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }

        col.header("3. Start Shizuku")
        col.note(
            "In Shizuku choose Pairing, then in Wireless debugging choose Pair " +
                "device with pairing code and type the code into Shizuku's " +
                "notification. Back in Shizuku, press Start."
        )
        if (state != Shell.State.NOT_INSTALLED) {
            col.button("Open Shizuku") {
                packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let { tryStart(it) }
            }
        }

        col.header("4. Allow AssistKey")
        when (state) {
            Shell.State.NO_PERMISSION -> col.button("Ask Shizuku for permission") {
                Shell.requestPermission()
            }
            else -> col.note("Once Shizuku is running, come back here.")
        }
    }

    private fun tryStart(i: Intent): Boolean =
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
}
