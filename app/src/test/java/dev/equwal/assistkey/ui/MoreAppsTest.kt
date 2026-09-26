package dev.equwal.assistkey.ui

import dev.equwal.assistkey.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list of the More apps section on the Licence screen, and when it shows. */
class MoreAppsTest {

    @Test
    fun `the list leaves out Rebind`() {
        assertFalse(MoreApps.ALL.any { it.name == "Rebind" || "equwal/rebind" in it.url || "equwal/assistkey" in it.url })
    }

    @Test
    fun `each link opens an https page`() {
        for (app in MoreApps.ALL) assertTrue(app.url, app.url.startsWith("https://"))
    }

    @Test
    fun `the list keeps the order of the catalog`() {
        assertEquals(
            listOf("SubRead", "Book Simulator", "honjimaku.com", "sbm Sync"),
            MoreApps.ALL.take(4).map { it.name }
        )
        assertEquals("All projects", MoreApps.ALL.last().name)
        // The catalog has 15 entries. Rebind is the one that is not in the list.
        assertEquals(14, MoreApps.ALL.size)
    }

    @Test
    fun `only the Google Play build leaves the section out`() {
        assertEquals(BuildConfig.FLAVOR != "play", MoreApps.shown)
    }
}
