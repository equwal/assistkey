package dev.equwal.assistkey.license

import dev.equwal.assistkey.license.License.Seen
import dev.equwal.assistkey.license.License.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LicenseDecideTest {

    private val day = 24L * 60 * 60 * 1000
    private val expires = 1_000 * day

    private fun decide(
        owned: Set<String> = emptySet(),
        store: Boolean = true,
        pro: Int = Seen.UNKNOWN,
        beta: Int = Seen.UNKNOWN,
        now: Long = expires + 100 * day,
        firstRun: Long = expires
    ) = License.decide(owned, store, pro, beta, now, firstRun, expires)

    @Test fun `a device with no Google Play has everything, for ever`() {
        assertEquals(Tier.NO_STORE, decide(store = false).tier)
        assertTrue(decide(store = false).active)
        // The same device with Google Play is locked at this date.
        assertEquals(Tier.LOCKED, decide(store = true).tier)
    }

    @Test fun `a bought licence comes first, with or without Google Play`() {
        assertEquals(Tier.LICENSED, decide(owned = setOf(Sku.PRO), store = false).tier)
        assertEquals(Tier.LICENSED, decide(owned = setOf(Sku.PRO_TESTER)).tier)
        // The beta flag is not a licence.
        assertEquals(Tier.LOCKED, decide(owned = setOf(Sku.BETA_OPEN)).tier)
    }

    @Test fun `with Google Play the old rules hold - beta, then trial, then locked`() {
        assertEquals(Tier.BETA, decide(now = expires - 1).tier)
        assertEquals(Tier.BETA, decide(beta = Seen.FOUND).tier)
        assertEquals(Tier.TRIAL, decide(now = expires + day, firstRun = expires).tier)
        assertEquals(7, decide(now = expires, firstRun = expires).trialDaysLeft)
        assertEquals(Tier.LOCKED, decide(pro = Seen.FOUND, beta = Seen.NOT_FOUND, now = 0, firstRun = -30 * day).tier)
    }

    @Test fun `the app is locked only where there is a store to buy it from`() {
        val r = Random(99)
        val seen = listOf(Seen.UNKNOWN, Seen.FOUND, Seen.NOT_FOUND)
        repeat(2000) {
            val store = r.nextBoolean()
            val owned = if (r.nextInt(4) == 0) setOf(Sku.PRO) else emptySet()
            val state = License.decide(
                owned, store, seen[r.nextInt(3)], seen[r.nextInt(3)],
                r.nextInt(3000) * day, r.nextInt(3000) * day, expires
            )
            if (!store) assertTrue(state.active)
            if (state.tier == Tier.LOCKED) assertTrue(store && owned.isEmpty())
            if (owned.isNotEmpty()) assertEquals(Tier.LICENSED, state.tier)
        }
    }
}
