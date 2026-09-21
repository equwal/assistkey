package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.license.Sku
import dev.equwal.assistkey.license.Tip
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row

/**
 * Where the licence is shown, bought and restored.
 *
 * Prices are never written into the app. They come from Google Play at the
 * moment the screen opens, already converted and formatted for the buyer's
 * country, so changing a price is a Play Console edit and not a release.
 */
class LicenseActivity : Activity() {

    override fun onResume() {
        super.onResume()
        PlayBilling.onChange = { build() }
        build()
        PlayBilling.refresh(this)
    }

    override fun onPause() {
        super.onPause()
        PlayBilling.onChange = null
    }

    private fun build() {
        val state = License.state(this)
        val col = Ui.page(this, "Licence")
        status(col, state)
        // With no Google Play there is nothing to buy and nothing to restore.
        if (state.tier != License.Tier.NO_STORE) {
            if (state.tier != License.Tier.LICENSED) buy(col, state)
            restore(col)
        }
        tip(col)
        col.note("Version " + BuildConfig.VERSION_NAME)
    }

    /**
     * A link to give a tip. It unlocks nothing. It is only in the build for
     * direct install: Google Play does not allow a link to another way to pay
     * in an app that it distributes.
     */
    private fun tip(col: LinearLayout) {
        if (Tip.URL.isEmpty()) return
        col.header("Say thanks")
        col.row("Buy me a coffee", Tip.URL.removePrefix("https://") + " · unlocks nothing") {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Tip.URL)))
            }.onFailure { Toast.makeText(this, "No browser on this device", Toast.LENGTH_LONG).show() }
        }
    }

    // ---- status ---------------------------------------------------------------

    private fun status(col: LinearLayout, state: License.State) {
        when (state.tier) {
            License.Tier.LICENSED -> col.header("Unlocked")
            License.Tier.NO_STORE -> {
                col.header("Free on this device")
                col.note("No Google Play here, so everything is unlocked.")
            }
            License.Tier.BETA -> {
                col.header("Beta - free for now")
                col.note("When the beta closes, remapping stops until you buy.")
                col.note(
                    if (state.betaConfirmedByPlay) {
                        "Google Play confirms the beta is open."
                    } else {
                        "Free until " + BuildConfig.BETA_EXPIRES_DATE + " at the latest."
                    }
                )
            }
            License.Tier.TRIAL -> {
                col.header("Trial - " + days(state.trialDaysLeft) + " left")
                col.note("After it, remapping stops until you buy. Your bindings are kept.")
            }
            License.Tier.LOCKED -> {
                col.header("Locked")
                col.note("Remapping is off. Your bindings are kept.")
            }
        }
    }

    private fun days(n: Int): String = if (n == 1) "1 day" else n.toString() + " days"

    // ---- buying ---------------------------------------------------------------

    private fun buy(col: LinearLayout, state: License.State) {
        col.header("Buy")

        if (PlayBilling.purchasePending) {
            col.note("A payment is being processed. The app unlocks by itself.")
        }

        val tester = License.testerEligible(this)
        val fullPrice = PlayBilling.price(Sku.PRO)
        val testerPrice = PlayBilling.price(Sku.PRO_TESTER)

        when {
            fullPrice == null && testerPrice == null -> notOnSale(col, state)
            else -> {
                if (tester && testerPrice != null) {
                    col.note(
                        "Tester price" +
                            (if (fullPrice != null) ", instead of " + fullPrice else "") + "."
                    )
                    col.primaryButton("Buy - " + testerPrice) { purchase(Sku.PRO_TESTER) }
                } else if (fullPrice != null) {
                    col.primaryButton("Buy - " + fullPrice) { purchase(Sku.PRO) }
                }
                col.note("One payment. No subscription.")
            }
        }

        if (!tester) {
            col.row("I was a beta tester", "For the tester price") { askForCode() }
        }
    }

    /** No prices means Play gave us nothing to sell - say which kind of nothing. */
    private fun notOnSale(col: LinearLayout, state: License.State) {
        val problem = PlayBilling.lastProblem
        col.note(
            when {
                PlayBilling.availability == PlayBilling.Availability.UNKNOWN ->
                    "Asking Google Play..."
                problem != null -> problem
                state.tier == License.Tier.BETA -> "Not on sale yet."
                else ->
                    "Google Play is not offering it to this account. Install the app " +
                        "from Google Play."
            }
        )
    }

    private fun purchase(productId: String) {
        if (!PlayBilling.buy(this, productId)) {
            Toast.makeText(
                this,
                PlayBilling.lastProblem ?: "Could not start the purchase",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun askForCode() {
        Ui.textInput(this, "Tester code", "TESTER-XXXX-XXXX") { code ->
            val ok = License.redeemTesterCode(this, code)
            Toast.makeText(
                this,
                if (ok) "Tester price unlocked" else "That code was not recognised",
                Toast.LENGTH_LONG
            ).show()
            if (ok) build()
        }
    }

    // ---- restoring -------------------------------------------------------------

    private fun restore(col: LinearLayout) {
        col.header("Already bought it?")
        col.note("Sign in to the Google account that bought it.")
        col.button("Check again") {
            Toast.makeText(this, "Checking...", Toast.LENGTH_SHORT).show()
            PlayBilling.refresh(this) {
                val tier = License.state(this).tier
                Toast.makeText(
                    this,
                    if (tier == License.Tier.LICENSED) "Unlocked" else "No purchase found on this account",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
