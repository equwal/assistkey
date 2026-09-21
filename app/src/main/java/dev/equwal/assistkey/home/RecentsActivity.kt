package dev.equwal.assistkey.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.ShellActivity
import dev.equwal.assistkey.ui.Ui
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * Recent apps, as a list.
 *
 * The stock switcher is a carousel of screenshots - the worst thing an e-ink
 * panel can be asked to draw. This is the same information as text: most recent
 * first, one tap to go back to it.
 *
 * Android does not tell apps what is running. With shell access the list is the
 * real task list of the system, and an app can be closed from it. Without, it
 * is rebuilt from the usage log, which needs the usage-access grant.
 *
 * Bind it to a key from any action list: Navigation > Recent apps (list).
 */
class RecentsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        Apps.recentFromShell(this) { fromShell ->
            if (isFinishing) return@recentFromShell
            when {
                fromShell != null -> show(fromShell, closable = true)
                Apps.hasUsageAccess(this) -> show(Apps.recentFromUsage(this), closable = false)
                else -> askForAccess()
            }
        }
    }

    private fun show(apps: List<Apps.App>, closable: Boolean) {
        val col = Ui.page(this)
        col.title("Recent apps")
        if (apps.isEmpty()) col.note("Nothing recent.")
        apps.forEach { app -> entry(col, app, closable) }
        if (closable && apps.isNotEmpty()) {
            col.header("All of them")
            col.button("Close all") {
                Shell.runAll(apps.map { "am force-stop " + it.pkg }) { load() }
            }
            col.note("Press and hold an app to close just that one.")
        }
        if (!closable) col.note("With shell access this list is exact, and apps can be closed from it.")
    }

    private fun entry(col: LinearLayout, app: Apps.App, closable: Boolean) {
        val row = col.row(app.label, null) {
            Apps.launch(this, app)
            finish()
        }
        if (!closable) return
        row.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle(app.label)
                .setItems(arrayOf("Close", "App info")) { _, which ->
                    if (which == 0) {
                        Shell.run("am force-stop " + app.pkg) { load() }
                    } else {
                        runCatching {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))
                            )
                        }
                    }
                }.show()
            true
        }
    }

    private fun askForAccess() {
        val col = Ui.page(this)
        col.title("Recent apps")
        col.note(
            "Android does not tell apps what has been used recently. Either give " +
                "AssistKey usage access, or turn on shell access, which gives the " +
                "exact list the system keeps."
        )
        col.button("Give usage access") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        col.button("Shell access") { startActivity(Intent(this, ShellActivity::class.java)) }
    }
}
