package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.license.Sku
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

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
        val col = Ui.page(this)
        col.title("Licence")
        status(col, state)
        if (state.tier != License.Tier.LICENSED) buy(col, state)
        restore(col)
        col.note("Version " + BuildConfig.VERSION_NAME)
    }

    // ---- status ---------------------------------------------------------------

    private fun status(col: LinearLayout, state: License.State) {
        when (state.tier) {
            License.Tier.LICENSED -> {
                col.header("Unlocked")
                col.note(
                    "This Google account owns AssistKey. It stays unlocked on every " +
                        "device signed in to the same account. Thank you."
                )
            }
            License.Tier.BETA -> {
                col.header("Beta - free for now")
                col.note(
                    "Everything works, at no charge, for as long as the beta is open. " +
                        "When it closes, remapping stops until a licence is bought. " +
                        "Because you were here for the beta, yours will be at the " +
                        "tester price."
                )
                col.note(
                    if (state.betaConfirmedByPlay) {
                        "Google Play confirms the beta is open."
                    } else {
                        "This build's free access ends on " + BuildConfig.BETA_EXPIRES_DATE +
                            " at the latest, and sooner if the beta is closed before then."
                    }
                )
            }
            License.Tier.TRIAL -> {
                col.header("Trial - " + days(state.trialDaysLeft) + " left")
                col.note(
                    "Everything works during the trial. After it, remapping stops " +
                        "until a licence is bought. Your bindings are kept either way."
                )
            }
            License.Tier.LOCKED -> {
                col.header("Locked")
                col.note(
                    "Remapping is switched off and every key behaves as the firmware " +
                        "intends. Your bindings are kept and come back the moment the " +
                        "app is unlocked."
                )
            }
        }
    }

    private fun days(n: Int): String = if (n == 1) "1 day" else n.toString() + " days"

    // ---- buying ---------------------------------------------------------------

    private fun buy(col: LinearLayout, state: License.State) {
        col.header("Buy")

        if (PlayBilling.purchasePending) {
            col.note(
                "A payment is still being processed by Google Play. The app unlocks " +
                    "by itself when it clears; nothing more is needed."
            )
        }

        val tester = License.testerEligible(this)
        val fullPrice = PlayBilling.price(Sku.PRO)
        val testerPrice = PlayBilling.price(Sku.PRO_TESTER)

        when {
            fullPrice == null && testerPrice == null -> notOnSale(col, state)
            else -> {
                if (tester && testerPrice != null) {
                    col.note(
                        "You took part in the beta, so the licence is " + testerPrice +
                            (if (fullPrice != null) " instead of " + fullPrice else "") + "."
                    )
                    col.button("Buy at the tester price - " + testerPrice) {
                        purchase(Sku.PRO_TESTER)
                    }
                } else if (fullPrice != null) {
                    col.button("Buy AssistKey - " + fullPrice) { purchase(Sku.PRO) }
                }
                col.note("One payment through Google Play. No subscription, no account.")
            }
        }

        if (!tester) {
            col.row(
                "I was a beta tester",
                "Enter the code you were given to get the tester price"
            ) { askForCode() }
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
                state.tier == License.Tier.BETA ->
                    "The licence is not on sale yet. There is nothing to buy during " +
                        "this stage of the beta."
                else ->
                    "Google Play is not offering the licence to this account. If the " +
                        "app was installed from a file, install it from Google Play " +
                        "instead and try again."
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
        col.note(
            "Purchases belong to the Google account that made them. Sign in to that " +
                "account in the Play Store, then check again."
        )
        col.button("Check Google Play again") {
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
