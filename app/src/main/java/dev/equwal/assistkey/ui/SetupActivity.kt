package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * The master switch, what the app has been allowed to do, and the licence.
 *
 * Key remapping comes first because nothing else works without it.
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
            "Key remapping",
            if (ok) "On. Your bindings can fire." else Channels.status(this, ch),
            on
        ) { checked ->
            Channels.setEnabled(this, ch, checked)
            if (checked && !Channels.isSatisfied(this, ch)) claim(ch)
            build()
        }
        if (on && !ok) {
            col.button("Turn on the key filter") { claim(ch) }
            col.note("If the switch turns itself back off, Android is blocking it.")
            col.button("Open App info") {
                Channels.safeStart(this, Channels.appInfoIntent(this))
            }
            col.more("Restricted settings", RESTRICTED)
        }

        val granted = PermissionsActivity.granted(this)
        col.row(
            "Permissions",
            "What the app may do, and a way to grant it again",
            state = if (granted.all { it }) "All granted"
            else (granted.size - granted.count { it }).toString() + " missing"
        ) { startActivity(Intent(this, PermissionsActivity::class.java)) }

        val s = License.state(this)
        col.row(
            "Licence",
            Summary.licence(s.tier, s.trialDaysLeft),
            state = Summary.licenceChip(s.tier, s.trialDaysLeft)
        ) { startActivity(Intent(this, LicenseActivity::class.java)) }

        stuck(col)
    }

    private fun stuck(col: LinearLayout) {
        col.header("If you get stuck")
        col.note("Power + Volume up opens the power menu, whatever else is bound.")
        col.more("If you get stuck", STUCK)
    }

    private fun claim(ch: Channel) {
        if (ch != Channel.ACCESSIBILITY) return claimNow(ch)
        AccessibilityDisclosure.show(this, onAgree = { claimNow(ch) })
    }

    private fun claimNow(ch: Channel) {
        if (ch == Channel.CAMERA) {
            Toast.makeText(this, "Choose Camera app, then pick AssistKey", Toast.LENGTH_LONG).show()
        }
        if (!Channels.safeStart(this, Channels.claimIntent(this, ch))) {
            Toast.makeText(this, "No settings screen for that on this firmware", Toast.LENGTH_LONG)
                .show()
        }
    }

    private companion object {

        const val RESTRICTED =
            "Android blocks restricted settings for an app that was installed " +
                "outside an app store, and accessibility is one of them. The " +
                "symptom is silent: the switch appears to turn on, then reverts " +
                "a moment later with no message.\n\n" +
                "Open App info, tap the three-dot menu, choose Allow restricted " +
                "settings, then try again."

        const val STUCK =
            "Power + Volume up always opens the power menu, whatever else is " +
                "bound to those keys. It is the way out of any setup.\n\n" +
                "Uninstalling AssistKey restores every key to its firmware " +
                "behaviour. The app writes nothing that outlives it."
    }
}
