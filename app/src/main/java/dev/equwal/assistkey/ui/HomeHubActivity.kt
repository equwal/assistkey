package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import dev.equwal.assistkey.bundle.Bundled
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.home.RecentsActivity
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.ui.Ui.row

/** The home screen and the recent-apps screen. Both are apps of their own, which this screen offers. */
class HomeHubActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Home and recents")

        if (runCatching { packageManager.getPackageInfo(RecentsActivity.INK_RECENTS, 0) }.isSuccess) {
            col.check(
                "Recents button opens Ink Recents",
                "In place of the recent apps of the system",
                Device.inkRecentsForSystem(this)
            ) { Device.setInkRecentsForSystem(this, it) }
        }
        BundledRows.add(this, col, Bundled.HOME)
    }
}
