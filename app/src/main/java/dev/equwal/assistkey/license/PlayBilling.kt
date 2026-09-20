package dev.equwal.assistkey.license

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct

/**
 * Everything that touches Google Play Billing.
 *
 * The rest of the app never sees a billing type: this object turns what Play
 * says into [License.recordOwned] and [License.recordCatalog], and the licence
 * is computed from those. That keeps the key-event hot path free of IPC, and
 * means a device with no Play Store at all simply never writes anything.
 *
 * All state here is confined to the main thread. Billing callbacks are
 * re-posted onto it rather than trusted to arrive there.
 */
object PlayBilling {

    private const val TAG = "AssistKey"

    enum class Availability { UNKNOWN, READY, UNAVAILABLE }

    /** Whether Play could be reached the last time anyone tried. */
    var availability = Availability.UNKNOWN
        private set

    /** The last problem worth showing a person, or null if there is none. */
    var lastProblem: String? = null
        private set

    /** True while a purchase is waiting on a slow payment method. */
    var purchasePending = false
        private set

    /** Set by whichever screen is in front, so it can redraw. */
    var onChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val details = HashMap<String, ProductDetails>()
    private val waiters = ArrayList<() -> Unit>()
    private var client: BillingClient? = null
    private var inFlight = false

    // ---- what the screens read ----------------------------------------------

    fun price(productId: String): String? = details[productId]?.let(::offerOf)?.formattedPrice

    fun canBuy(productId: String): Boolean = details.containsKey(productId)

    // ---- refresh --------------------------------------------------------------

    /**
     * Connects if need be, then re-reads the catalogue and the purchases.
     * Callers that arrive while one is running share its result.
     */
    fun refresh(c: Context, done: (() -> Unit)? = null) {
        val app = c.applicationContext
        main.post {
            done?.let { waiters.add(it) }
            if (inFlight) return@post
            inFlight = true
            connect(app) { ok ->
                if (!ok) finish() else queryCatalog(app) { queryOwned(app) { finish() } }
            }
        }
    }

    private fun finish() {
        inFlight = false
        val run = ArrayList(waiters)
        waiters.clear()
        run.forEach { it() }
        onChange?.invoke()
    }

    private fun connect(app: Context, then: (Boolean) -> Unit) {
        val existing = client
        if (existing != null && existing.isReady) {
            then(true)
            return
        }
        val fresh = existing ?: try {
            BillingClient.newBuilder(app)
                .setListener(purchaseListener(app))
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
                .also { client = it }
        } catch (e: Exception) {
            // No Play Store, or one too old to speak this protocol.
            Log.w(TAG, "billing client could not be built", e)
            unavailable("Google Play billing is not available on this device.")
            then(false)
            return
        }

        var answered = false
        fresh.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (answered) return@post
                    answered = true
                    Log.i(TAG, "billing setup: " + result.responseCode + " " + result.debugMessage)
                    if (result.responseCode == BillingResponseCode.OK) {
                        availability = Availability.READY
                        then(true)
                    } else {
                        unavailable(explain(result))
                        then(false)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Nothing to do here. Every refresh checks isReady and connects
                // again if need be, which is one attempt with a prompt answer.
                // The library's own auto-reconnection was tried and dropped: on
                // a device with the Play Store disabled - how this reader ships
                // - it retries three times per call and holds each answer back
                // for seconds.
            }
        })
    }

    private fun unavailable(why: String) {
        availability = Availability.UNAVAILABLE
        lastProblem = why
    }

    private fun queryCatalog(app: Context, then: () -> Unit) {
        val c = client ?: return then()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Sku.ALL.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(ProductType.INAPP)
                        .build()
                }
            )
            .build()

        c.queryProductDetailsAsync(params) { result, response ->
            main.post {
                if (result.responseCode == BillingResponseCode.OK) {
                    details.clear()
                    response.productDetailsList.forEach { details[it.productId] = it }
                    val missing = response.unfetchedProductList
                        .associate { it.productId to it.statusCode }
                    License.recordCatalog(
                        app,
                        pro = seen(Sku.PRO, missing),
                        beta = seen(Sku.BETA_OPEN, missing)
                    )
                    lastProblem = null
                } else {
                    // Keep whatever was cached; one bad answer proves nothing.
                    lastProblem = explain(result)
                    Log.i(TAG, "catalogue query: " + result.responseCode + " " + result.debugMessage)
                }
                then()
            }
        }
    }

    /**
     * Present means found. Absent means not found - unless Play said it could
     * not tell, in which case neither can we. A product whose only purchase
     * option has been switched off comes back as NO_ELIGIBLE_OFFER rather than
     * PRODUCT_NOT_FOUND; for a flag that means the same thing.
     */
    private fun seen(productId: String, missing: Map<String, Int>): Int = when {
        details.containsKey(productId) -> License.Seen.FOUND
        missing[productId] == UnfetchedProduct.StatusCode.UNKNOWN -> License.Seen.UNKNOWN
        else -> License.Seen.NOT_FOUND
    }

    private fun queryOwned(app: Context, then: () -> Unit) {
        val c = client ?: return then()
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        c.queryPurchasesAsync(params) { result, purchases ->
            main.post {
                if (result.responseCode == BillingResponseCode.OK) {
                    absorb(app, purchases, replace = true)
                } else {
                    Log.i(TAG, "purchase query: " + result.responseCode + " " + result.debugMessage)
                }
                then()
            }
        }
    }

    // ---- buying ---------------------------------------------------------------

    /** Returns false, with [lastProblem] set, if the purchase sheet could not open. */
    fun buy(activity: Activity, productId: String): Boolean {
        val product = details[productId]
        val c = client
        if (product == null || c == null || !c.isReady) {
            lastProblem = "Google Play has not answered yet. Try again in a moment."
            return false
        }
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        offerOf(product)?.offerToken?.takeIf { it.isNotEmpty() }?.let { item.setOfferToken(it) }

        val result = c.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build())).build()
        )
        if (result.responseCode == BillingResponseCode.OK) return true
        if (result.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) {
            refresh(activity)
            return true
        }
        lastProblem = explain(result)
        return false
    }

    private fun purchaseListener(app: Context) = PurchasesUpdatedListener { result, purchases ->
        main.post {
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    absorb(app, purchases ?: emptyList(), replace = false)
                    lastProblem = null
                }
                BillingResponseCode.USER_CANCELED -> Unit
                BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(app)
                else -> lastProblem = explain(result)
            }
            onChange?.invoke()
        }
    }

    /**
     * Records what is owned and acknowledges anything new. Acknowledging is not
     * optional: Play refunds a purchase that goes three days without it.
     */
    private fun absorb(app: Context, purchases: List<Purchase>, replace: Boolean) {
        val owned = HashSet<String>()
        var pending = false
        purchases.forEach { p ->
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    owned.addAll(p.products)
                    if (!p.isAcknowledged) acknowledge(p)
                }
                Purchase.PurchaseState.PENDING -> pending = true
                else -> Unit
            }
        }
        purchasePending = pending
        if (replace || owned.isNotEmpty()) License.recordOwned(app, owned)
    }

    private fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        client?.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingResponseCode.OK) {
                // Not fatal: the next refresh sees it unacknowledged and retries.
                Log.w(TAG, "acknowledge failed: " + result.responseCode + " " + result.debugMessage)
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------

    /** The cheapest way to buy a product, should it ever carry more than one offer. */
    private fun offerOf(p: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
        p.oneTimePurchaseOfferDetailsList?.minByOrNull { it.priceAmountMicros }
            ?: p.oneTimePurchaseOfferDetails

    private fun explain(r: BillingResult): String = when (r.responseCode) {
        BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play billing is not available for this device or account."
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.NETWORK_ERROR ->
            "Could not reach Google Play. Check the connection and try again."
        BillingResponseCode.ITEM_UNAVAILABLE ->
            "That product is not available for this account."
        BillingResponseCode.FEATURE_NOT_SUPPORTED ->
            "The Play Store app on this device is too old. Update it and try again."
        else -> "Google Play error " + r.responseCode +
            (if (r.debugMessage.isNullOrBlank()) "" else ": " + r.debugMessage)
    }
}
