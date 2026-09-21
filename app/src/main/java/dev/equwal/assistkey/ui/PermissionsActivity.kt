package dev.equwal.assistkey.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.home.Apps
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title
import dev.equwal.assistkey.voice.Dictation

/**
 * Every permission the app can use, in one place: what it is for, whether it is
 * granted, and a way to grant it.
 *
 * "Set up what is missing" walks through the missing ones in order. Android
 * grants each of these on a screen of its own, so the walk opens one screen,
 * waits for the user to come back, and opens the next. It can be run again at
 * any time, which is the point: a reinstall or a system update takes grants
 * away without saying so.
 *
 * Nothing here is required. The app works with whatever has been granted.
 */
class PermissionsActivity : Activity() {

    private class Item(
        val title: String,
        val why: String,
        val granted: () -> Boolean,
        val ask: () -> Unit
    )

    private var walking = false
    private var asked = HashSet<String>()

    private fun items(): List<Item> {
        val list = ArrayList<Item>()
        list += Item(
            "Key remapping",
            "The accessibility key filter. It sees the keys and carries out the actions. Everything else depends on it.",
            { Channels.isSatisfied(this, Channel.ACCESSIBILITY) }
        ) {
            Channels.setEnabled(this, Channel.ACCESSIBILITY, true)
            AccessibilityDisclosure.show(
                this,
                onAgree = { start(Channels.claimIntent(this, Channel.ACCESSIBILITY)) },
                onDecline = { next() }
            )
        }
        list += Item(
            "Digital assistant",
            "So that holding the Power button reaches this app.",
            { Channels.isSatisfied(this, Channel.ASSISTANT) }
        ) {
            Channels.setEnabled(this, Channel.ASSISTANT, true)
            start(Channels.claimIntent(this, Channel.ASSISTANT))
        }
        list += Item(
            "Microphone",
            "For Voice typing only. A speech app on this device does the listening.",
            { Dictation.hasMicrophone(this) }
        ) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        list += Item(
            "App usage data",
            "For the recent-apps cards, to put apps in order of last use. Not needed with shell access.",
            { Apps.hasUsageAccess(this) || Shell.ready }
        ) { start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        return list
    }

    override fun onResume() {
        super.onResume()
        Shell.connect(this)
        build()
        if (walking) next()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        build()
        if (walking) next()
    }

    private var lastStep = 0L

    /**
     * Opens the first missing grant that this walk has not asked for yet. Coming
     * back from a permission dialog reports twice, as a result and as a resume,
     * so a second call close behind the first is dropped.
     */
    private fun next() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastStep < 600) return
        lastStep = now
        val item = items().firstOrNull { !it.granted() && it.title !in asked }
        if (item == null) {
            walking = false
            build()
            return
        }
        asked.add(item.title)
        item.ask()
    }

    private fun start(i: Intent) {
        if (!Channels.safeStart(this, i)) next()
    }

    private fun build() {
        val all = items()
        val missing = all.count { !it.granted() }
        val col = Ui.page(this)
        col.title("Permissions")
        col.note(
            "What the app can be allowed to do, and why. Nothing here is required: " +
                "the app works with whatever you grant."
        )
        if (missing == 0) {
            col.note("Everything is granted.")
        } else {
            col.button("Set up what is missing (" + missing + ")") {
                walking = true
                asked = HashSet()
                next()
            }
            col.note(
                "Android grants each one on a screen of its own. Come back here " +
                    "after each, and the next one opens."
            )
        }

        col.header("One at a time")
        all.forEach { item -> entry(col, item) }

        if (Shell.SUPPORTED) {
            col.header("For devices with Shizuku or root")
            col.row(
                "Shell access: " + Shell.describe(this),
                "Optional. More Power button gestures, and system switches"
            ) { startActivity(Intent(this, ShellActivity::class.java)) }
        }

        col.header("If a switch turns itself back off")
        col.note(
            "Android blocks some switches for apps that were installed from a file. " +
                "Open App info, tap the three-dot menu, choose Allow restricted " +
                "settings, then try again."
        )
        col.button("Open App info") { Channels.safeStart(this, Channels.appInfoIntent(this)) }

        if (!PowerNative.canWriteSecure(this)) {
            col.header("From a computer, once")
            col.note(
                "One permission cannot be granted on the device" +
                    (if (Shell.SUPPORTED) " without shell access" else "") +
                    ". It lets the app change the system's own Power button and Emergency SOS settings."
            )
            col.code(PowerNative.GRANT_COMMAND)
        }
    }

    private fun entry(col: LinearLayout, item: Item) {
        val ok = item.granted()
        col.row((if (ok) "Granted: " else "Missing: ") + item.title, item.why) {
            walking = false
            item.ask()
        }
    }

    companion object {
        private const val PREFS = "assistkey_setup"
        private const val K_SHOWN = "permissions_shown"

        /** Opens this screen the first time the app runs, and never again by itself. */
        fun showOnce(a: Activity) {
            val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getBoolean(K_SHOWN, false)) return
            p.edit().putBoolean(K_SHOWN, true).apply()
            a.startActivity(Intent(a, PermissionsActivity::class.java))
        }
    }
}
