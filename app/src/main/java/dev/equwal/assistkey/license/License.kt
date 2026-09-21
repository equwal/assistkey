package dev.equwal.assistkey.license

import android.content.Context
import android.content.SharedPreferences
import dev.equwal.assistkey.BuildConfig
import java.security.MessageDigest

/**
 * Whether the app is allowed to do its job, and why.
 *
 * There are four tiers, tried in order:
 *
 *  1. LICENSED - Google Play says this account owns one of the two paid
 *     products. Cached, so it survives being offline.
 *  2. BETA     - the free beta is still open. Everything works.
 *  3. TRIAL    - the beta is over; seven days from first launch to decide.
 *  4. LOCKED   - nothing is remapped until a licence is bought.
 *
 * "Is the beta open" is answered by Google Play, not by a server of ours. The
 * catalogue holds a third product, [Sku.BETA_OPEN], that is never sold; it is
 * only a flag. While it is active the beta is open. Deactivating it in Play
 * Console ends the beta for every install at once, sideloaded or not. The
 * state "the paid product is visible but the flag is gone" is the only one read
 * as closed, so an install that cannot reach Play - or a build that predates
 * the Play listing entirely - stays open, up to the date compiled into it.
 *
 * Anyone who ran the app while the beta was open is remembered as a tester and
 * is offered [Sku.PRO_TESTER], the same licence at a lower price.
 */
object License {

    /**
     * NO_STORE: the device has no Google Play, so there is no way to buy. The
     * app is then free and complete. A user can get this tier on purpose by
     * turning Google Play off. The owner accepts that: the users who can pay
     * have Google Play, and most of them do not do this.
     */
    enum class Tier { LICENSED, NO_STORE, BETA, TRIAL, LOCKED }

    data class State(
        val tier: Tier,
        /** Whole days of trial left, rounded up; zero outside TRIAL. */
        val trialDaysLeft: Int = 0,
        /** True when Play has positively confirmed the beta flag, not just a date. */
        val betaConfirmedByPlay: Boolean = false
    ) {
        val active: Boolean get() = tier != Tier.LOCKED
    }

    /** What a catalogue lookup said about one product. */
    object Seen {
        const val UNKNOWN = 0
        const val FOUND = 1
        const val NOT_FOUND = 2
    }

    private const val PREFS = "assistkey_license"
    private const val K_FIRST_RUN = "first_run_at"
    private const val K_TESTER = "beta_tester"
    private const val K_CODE_OK = "tester_code_ok"
    private const val K_OWNED = "owned"
    private const val K_CAT_PRO = "catalog_pro"
    private const val K_CAT_BETA = "catalog_beta"
    private const val K_CAT_AT = "catalog_at"

    private const val DAY_MS = 24L * 60 * 60 * 1000
    const val TRIAL_DAYS = 7

    /**
     * A catalogue answer older than this is not trusted to say the beta is
     * still open; the compiled-in date takes over again.
     */
    private const val CATALOG_FRESH_MS = 14 * DAY_MS

    /** The key filter asks on every key event, so the answer is held briefly. */
    private const val STATE_TTL_MS = 30_000L

    @Volatile private var cached: State? = null
    @Volatile private var cachedAt = 0L

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- reading ------------------------------------------------------------

    fun state(c: Context): State {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < STATE_TTL_MS) return it }
        return compute(c, now).also {
            cached = it
            cachedAt = now
        }
    }

    fun active(c: Context): Boolean = state(c).active

    fun invalidate() {
        cached = null
    }

    private fun compute(c: Context, now: Long): State {
        val p = prefs(c)
        val firstRun = firstRunAt(p, now)

        val owned = p.getStringSet(K_OWNED, emptySet()) ?: emptySet()
        val fresh = now - p.getLong(K_CAT_AT, 0L) < CATALOG_FRESH_MS
        val pro = if (fresh) p.getInt(K_CAT_PRO, Seen.UNKNOWN) else Seen.UNKNOWN
        val beta = if (fresh) p.getInt(K_CAT_BETA, Seen.UNKNOWN) else Seen.UNKNOWN

        val state = decide(owned, storePresent(c), pro, beta, now, firstRun, BuildConfig.BETA_EXPIRES_MS)
        if (state.tier == Tier.BETA && !p.getBoolean(K_TESTER, false)) p.edit().putBoolean(K_TESTER, true).apply()
        return state
    }

    /** The rules, with no Android types, so that a test can run them. */
    fun decide(
        owned: Set<String>,
        storePresent: Boolean,
        pro: Int,
        beta: Int,
        now: Long,
        firstRun: Long,
        betaExpires: Long
    ): State {
        if (owned.any { it in Sku.LICENCES }) return State(Tier.LICENSED)
        if (!storePresent) return State(Tier.NO_STORE)

        val open = when {
            beta == Seen.FOUND -> true
            pro == Seen.FOUND && beta == Seen.NOT_FOUND -> false
            else -> now < betaExpires
        }
        if (open) return State(Tier.BETA, betaConfirmedByPlay = beta == Seen.FOUND)

        val left = firstRun + TRIAL_DAYS * DAY_MS - now
        if (left > 0) {
            return State(Tier.TRIAL, trialDaysLeft = ((left + DAY_MS - 1) / DAY_MS).toInt())
        }
        return State(Tier.LOCKED)
    }

    /** True when the Google Play app is installed and not turned off. */
    private fun storePresent(c: Context): Boolean =
        runCatching { c.packageManager.getApplicationInfo(PLAY_STORE, 0).enabled }.getOrDefault(false)

    private const val PLAY_STORE = "com.android.vending"

    private fun firstRunAt(p: SharedPreferences, now: Long): Long {
        val at = p.getLong(K_FIRST_RUN, 0L)
        if (at in 1..now) return at
        p.edit().putLong(K_FIRST_RUN, now).apply()
        return now
    }

    /** Ran the app during the beta, or typed the code a tester was given. */
    fun testerEligible(c: Context): Boolean {
        val p = prefs(c)
        return p.getBoolean(K_TESTER, false) || p.getBoolean(K_CODE_OK, false)
    }

    // ---- writing, from PlayBilling ------------------------------------------

    fun recordOwned(c: Context, productIds: Set<String>) {
        prefs(c).edit().putStringSet(K_OWNED, HashSet(productIds)).apply()
        invalidate()
    }

    fun recordCatalog(c: Context, pro: Int, beta: Int) {
        prefs(c).edit()
            .putInt(K_CAT_PRO, pro)
            .putInt(K_CAT_BETA, beta)
            .putLong(K_CAT_AT, System.currentTimeMillis())
            .apply()
        invalidate()
    }

    // ---- tester code ---------------------------------------------------------

    /**
     * For testers who cannot carry the marker over - a new device, or a Play
     * install that refused to upgrade in place over the sideloaded beta.
     */
    fun redeemTesterCode(c: Context, code: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(code.trim().uppercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val ok = MessageDigest.isEqual(
            digest.toByteArray(), BuildConfig.TESTER_CODE_SHA256.toByteArray()
        )
        if (ok) prefs(c).edit().putBoolean(K_CODE_OK, true).apply()
        return ok
    }
}
