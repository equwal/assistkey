package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import dev.equwal.assistkey.home.HomeActivity
import dev.equwal.assistkey.home.HomeSettingsActivity
import dev.equwal.assistkey.home.RecentsActivity
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
            "A clock, your apps, and a search line",
            state = if (isDefault) "In use" else if (offered) "Ready" else "Off"
        ) { startActivity(Intent(this, HomeSettingsActivity::class.java)) }

        col.row(
            "Recent apps",
            "Cards you can bind to a button"
        ) { startActivity(Intent(this, RecentsActivity::class.java)) }

    }
}
