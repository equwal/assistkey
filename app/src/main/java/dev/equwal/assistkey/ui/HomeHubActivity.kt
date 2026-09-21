package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import dev.equwal.assistkey.home.HomeActivity
import dev.equwal.assistkey.home.HomeSettingsActivity
import dev.equwal.assistkey.home.RecentsActivity
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.row

/** The two screens AssistKey draws for itself: the home screen, and recents. */
class HomeHubActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Home and recents")

        val offered = packageManager.getComponentEnabledSetting(
            ComponentName(this, HomeActivity::class.java)
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val isDefault = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == packageName

        col.row(
            "Home screen",
            "A clock, the apps you choose, and a line to type in",
            state = if (isDefault) "In use" else if (offered) "Ready" else "Off"
        ) { startActivity(Intent(this, HomeSettingsActivity::class.java)) }

        col.row(
            "Recent apps",
            "Card switcher made for e-ink, bindable to any key"
        ) { startActivity(Intent(this, RecentsActivity::class.java)) }

        col.more("Home and recents", ABOUT)
    }

    private companion object {
        const val ABOUT =
            "The home screen keeps the app list out of sight until you type. " +
                "When one match is left it opens itself. There are no icons and " +
                "no grid, so there is nothing for an e-ink panel to redraw.\n\n" +
                "AssistKey is not a home screen until you switch it on and the " +
                "system asks you to choose.\n\n" +
                "Recent apps is a row of cards like the Android switcher, drawn " +
                "with outlines and no animation. Bind it to a key from the action " +
                "list, under AssistKey."
    }
}
