package dev.equwal.assistkey.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.equwal.assistkey.ui.HomeHubActivity

/**
 * The "Recent apps" action. The recent-apps screen is a program of its own,
 * Ink Recents. This activity has no screen: it opens Ink Recents and ends.
 * Where Ink Recents is not installed, it opens the screen that offers it.
 *
 * The class keeps its old name, because bindings made before hold the name.
 */
class RecentsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = Intent(ACTION_OPEN).setPackage(INK_RECENTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(open) }.isFailure) {
            Toast.makeText(this, "Install Ink Recents", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, HomeHubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        const val INK_RECENTS = "dev.equwal.inkrecents"
        const val ACTION_OPEN = "dev.equwal.inkrecents.OPEN"
    }
}
