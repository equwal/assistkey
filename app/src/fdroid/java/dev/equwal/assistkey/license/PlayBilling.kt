package dev.equwal.assistkey.license

import android.app.Activity
import android.content.Context

/**
 * The F-Droid build has no Google Play Billing. This object has the same
 * calls as the real one in src/billing, and each call does nothing.
 *
 * The F-Droid build is free and complete, so nothing here is ever needed.
 */
object PlayBilling {

    enum class Availability { UNKNOWN, READY, UNAVAILABLE }

    val availability = Availability.UNAVAILABLE
    val lastProblem: String? = null
    val purchasePending = false
    var onChange: (() -> Unit)? = null

    fun price(productId: String): String? = null

    fun canBuy(productId: String): Boolean = false

    fun refresh(c: Context, done: (() -> Unit)? = null) {
        done?.invoke()
    }

    fun buy(activity: Activity, productId: String): Boolean = false
}
