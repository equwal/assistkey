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
        val col = Ui.page(this, "Shell access")
        col.row("Status", null, enabled = false, state = Shell.describe(this))
        col.note("Shizuku, a free app, sets this up with no computer.")

        if (state == Shell.State.READY) {
            col.header("What this unlocks")
            col.note("Every Power button gesture, the button bar and system gestures.")
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
            "Tap Build number seven times in Settings > About. Then switch on " +
                "Wireless debugging. It needs Wi-Fi."
        )
        col.button("Open Developer options") {
            if (!tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
                tryStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }

        col.header("3. Start Shizuku")
        col.note("In Shizuku choose Pairing, pair with the code, then press Start.")
        if (state != Shell.State.NOT_INSTALLED) {
            col.button("Open Shizuku") {
                packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let { tryStart(it) }
            }
        }

        col.header("4. Allow Rebind")
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
