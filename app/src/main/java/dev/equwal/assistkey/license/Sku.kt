package dev.equwal.assistkey.license

/**
 * The three product ids, exactly as they must be typed into Play Console.
 * Product ids can never be changed or reused once created, so neither can
 * these.
 */
object Sku {
    /** The licence. One-time, non-consumable. */
    const val PRO = "assistkey_pro"

    /** The same licence at the tester price. Only offered to [License.testerEligible]. */
    const val PRO_TESTER = "assistkey_pro_tester"

    /** Never sold. Active means the beta is open; deactivate it to end the beta. */
    const val BETA_OPEN = "assistkey_beta_open"

    val LICENCES = setOf(PRO, PRO_TESTER)
    val ALL = listOf(PRO, PRO_TESTER, BETA_OPEN)
}
