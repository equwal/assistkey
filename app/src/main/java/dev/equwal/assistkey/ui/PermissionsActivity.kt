package dev.equwal.assistkey.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import dev.equwal.assistkey.bundle.Bundled
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row
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
        val granted: Boolean,
        val ask: () -> Unit
    )

    private var walking = false
    private var asked = HashSet<String>()

    private fun items(): List<Item> {
        val state = granted(this)
        val list = ArrayList<Item>()
        list += Item(
            "Button remapping",
            "Everything else depends on it",
            state[0]
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
            "For holding the Power button",
            state[1]
        ) {
            Channels.setEnabled(this, Channel.ASSISTANT, true)
            start(Channels.claimIntent(this, Channel.ASSISTANT))
        }
        list += Item(
            "Microphone",
            "For Voice typing",
            state[2]
        ) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        return list + extensions()
    }

    /**
     * What the apps that go with Rebind need. Each one is a switch in the
     * settings of Android that only the user can set. An item shows only when
     * it can apply: the install switch in the build that carries apps, the
     * others when their app is installed.
     */
    private fun extensions(): List<Item> {
        val list = ArrayList<Item>()
        if (Bundled.SUPPORTED) {
            list += Item(
                "Install apps from Rebind",
                "For the apps that Rebind carries",
                packageManager.canRequestPackageInstalls()
            ) {
                start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + packageName)))
            }
        }
        if (installed(INK_RECENTS)) {
            list += Item(
                "Ink Recents: app usage data",
                "Puts the recent apps in order",
                usageAccess(INK_RECENTS)
            ) {
                val page = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                // Straight to the switch of that app, where the settings app can. Else the list.
                val direct = Intent(page).setData(Uri.parse("package:" + INK_RECENTS))
                if (!Channels.safeStart(this, direct)) start(page)
            }
        }
        val homes = Bundled.items.filter { it.group == Bundled.HOME && it.pkg != INK_RECENTS }.map { it.pkg }
        if (homes.any(::installed)) {
            val home = packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
            list += Item(
                "Home screen",
                "Choose inkOS or ThinkLauncher",
                home in homes
            ) { start(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }
        return list
    }

    private fun installed(pkg: String): Boolean =
        runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess

    /** Whether another app has usage access. False where Android does not tell. */
    private fun usageAccess(pkg: String): Boolean = runCatching {
        val uid = packageManager.getApplicationInfo(pkg, 0).uid
        getSystemService(android.app.AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg) ==
            android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

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
        val item = items().firstOrNull { !it.granted && it.title !in asked }
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
        val missing = all.count { !it.granted }
        val col = Ui.page(this, "Permissions")
        col.note("Nothing here is required.")
        if (missing > 0) {
            col.primaryButton("Set up what is missing (" + missing + ")") {
                walking = true
                asked = HashSet()
                next()
            }
        }

        all.forEach { item -> entry(col, item) }

        col.note("If a switch turns itself off, allow restricted settings in App info.")
        col.button("Open App info") { Channels.safeStart(this, Channels.appInfoIntent(this)) }

        if (!PowerNative.canWriteSecure(this)) {
            col.header("From a computer, once")
            col.note("It lets the app change the Power button and Emergency SOS settings.")
            col.code(PowerNative.GRANT_COMMAND)
        }
    }

    private fun entry(col: LinearLayout, item: Item) {
        col.row(item.title, item.why, state = if (item.granted) "Granted" else "Missing") {
            walking = false
            item.ask()
        }
    }

    companion object {
        private const val PREFS = "assistkey_setup"
        private const val K_SHOWN = "permissions_shown"
        private const val INK_RECENTS = "dev.equwal.inkrecents"

        /**
         * Whether each grant is in place, in the order this screen lists them.
         * The hub and the Setup screen count these, so there is one list.
         */
        fun granted(c: Context): List<Boolean> = listOf(
            Channels.isSatisfied(c, Channel.ACCESSIBILITY),
            Channels.isSatisfied(c, Channel.ASSISTANT),
            Dictation.hasMicrophone(c)
        )

        /** Opens this screen the first time the app runs, and never again by itself. */
        fun showOnce(a: Activity) {
            val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getBoolean(K_SHOWN, false)) return
            p.edit().putBoolean(K_SHOWN, true).apply()
            a.startActivity(Intent(a, PermissionsActivity::class.java))
        }
    }
}
