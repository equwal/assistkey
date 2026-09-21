package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import dev.equwal.assistkey.bundle.Bundled
import dev.equwal.assistkey.ui.Ui.row

/** The home screen and the recent-apps screen. Both are apps of their own, which this screen offers. */
class HomeHubActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Home and recents")

        BundledRows.add(this, col, Bundled.HOME)
    }
}
