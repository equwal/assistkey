package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import dev.equwal.assistkey.bundle.Bundled
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * The master switch, what the app has been allowed to do, and the licence.
 *
 * Button remapping comes first because nothing else works without it.
 */
class SetupActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Setup")

        val ch = Channel.ACCESSIBILITY
        val on = Channels.isEnabled(this, ch)
        val ok = Channels.isSatisfied(this, ch)
        col.check(
            "Button remapping",
            if (ok) null else Channels.status(this, ch),
            on
        ) { checked ->
            Channels.setEnabled(this, ch, checked)
            if (checked && !Channels.isSatisfied(this, ch)) claim(ch)
            build()
        }
        if (on && !ok) {
            col.button("Turn it on") { claim(ch) }
            col.note("If the switch turns itself off, allow restricted settings in App info.")
            col.button("Open App info") {
                Channels.safeStart(this, Channels.appInfoIntent(this))
            }
        }

        val granted = PermissionsActivity.granted(this)
        col.row(
            "Permissions",
            state = if (granted.all { it }) "All granted"
            else (granted.size - granted.count { it }).toString() + " missing"
        ) { startActivity(Intent(this, PermissionsActivity::class.java)) }

        val s = License.state(this)
        col.row(
            "Licence",
            state = Summary.licenceChip(s.tier, s.trialDaysLeft)
        ) { startActivity(Intent(this, LicenseActivity::class.java)) }

        // Rebind has no internet permission, so it cannot look for a new version.
        // Ink Update, an app of its own, does that and tells the user.
        BundledRows.add(this, col, Bundled.UPDATE)
    }

    private fun claim(ch: Channel) {
        if (ch != Channel.ACCESSIBILITY) return claimNow(ch)
        AccessibilityDisclosure.show(this, onAgree = { claimNow(ch) })
    }

    private fun claimNow(ch: Channel) {
        if (ch == Channel.CAMERA) {
            Toast.makeText(this, "Choose Camera app, then pick Rebind", Toast.LENGTH_LONG).show()
        }
        if (!Channels.safeStart(this, Channels.claimIntent(this, ch))) {
            Toast.makeText(this, "This device has no screen for that", Toast.LENGTH_LONG)
                .show()
        }
    }
}
