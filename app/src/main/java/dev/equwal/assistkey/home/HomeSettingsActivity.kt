package dev.equwal.assistkey.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import dev.equwal.assistkey.ui.Ui
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row

/**
 * Switching the AssistKey home screen on, and tuning it.
 *
 * The home activity is disabled in the manifest. Until the user asks for it
 * here the app is not a launcher and never shows up as one.
 */
class HomeSettingsActivity : Activity() {

    private val home by lazy { ComponentName(this, HomeActivity::class.java) }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun enabled(): Boolean =
        packageManager.getComponentEnabledSetting(home) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun isDefault(): Boolean {
        val res = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        return res?.activityInfo?.packageName == packageName
    }

    private fun build() {
        val col = Ui.page(this, "Home screen")
        col.note("A clock, the few apps you choose, and a line to type in.")

        col.check("Offer AssistKey as a home screen", null, enabled()) { on ->
            packageManager.setComponentEnabledSetting(
                home,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            build()
        }
        if (!enabled()) return

        col.row(
            "Home screen",
            if (isDefault()) "This is your home screen" else "Not your home screen yet",
            enabled = false,
            state = if (isDefault()) "In use" else "Ready"
        )
        if (!isDefault()) {
            col.primaryButton("Make it the home screen") {
                val rm = getSystemService(RoleManager::class.java)
                val i = if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
                } else Intent(Settings.ACTION_HOME_SETTINGS)
                runCatching { startActivityForResult(i, 1) }
                    .onFailure { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
            }
        } else {
            col.button("Choose a different home screen") {
                runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            }
        }
        col.button("Open it") { startActivity(Intent(this, HomeActivity::class.java)) }

        col.header("Options")
        col.check("Clock and date", null, Apps.showClock(this)) { Apps.setShowClock(this, it) }
        col.check("Open the app when one match is left", "After at least two letters", Apps.autoOpen(this)) {
            Apps.setAutoOpen(this, it)
        }

        col.header("On the home screen")
        val byPkg = Apps.all(this).associateBy { it.pkg }
        val favs = Apps.favourites(this).mapNotNull { byPkg[it] }
        if (favs.isEmpty()) col.note("Nothing yet.")
        favs.forEach { app ->
            col.row(app.label, "Tap to remove") { Apps.toggleFavourite(this, app.pkg); build() }
        }
        col.button("Add an app") {
            val all = Apps.all(this).filter { it.pkg !in Apps.favourites(this) }
            Ui.pick(this, "Keep on home", all.map { it.label }) { i ->
                Apps.toggleFavourite(this, all[i].pkg); build()
            }
        }

        val hidden = Apps.hidden(this).mapNotNull { byPkg[it] }
        if (hidden.isNotEmpty()) {
            col.header("Hidden from search")
            hidden.forEach { app ->
                col.row(app.label, "Tap to show again") { Apps.toggleHidden(this, app.pkg); build() }
            }
        }
        col.note(
            "On the home screen itself, press and hold an app for its options, or " +
                "empty space to come back here."
        )
    }
}
