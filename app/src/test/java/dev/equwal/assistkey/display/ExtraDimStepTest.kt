package dev.equwal.assistkey.display

import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of light levels that the darker and brighter key actions walk. */
class ExtraDimStepTest {

    // Floor 5: the levels under it, from high to low. 0 means off.
    private val levels = listOf(4, 3, 2, 1)

    @Test fun `darker goes from off through every level and stops at the lowest`() {
        val walk = generateSequence(0) { ExtraDim.darker(it, levels) }.take(7).toList()
        assertEquals(listOf(0, 4, 3, 2, 1, 1, 1), walk)
    }

    @Test fun `brighter goes back up and ends at off`() {
        val walk = generateSequence(1) { ExtraDim.brighter(it, levels) }.take(7).toList()
        assertEquals(listOf(1, 2, 3, 4, 0, 0, 0), walk)
    }

    @Test fun `brighter undoes darker at every level but the ends`() {
        listOf(4, 3, 2).forEach { assertEquals(it, ExtraDim.brighter(ExtraDim.darker(it, levels), levels)) }
    }

    @Test fun `a device with no levels stays off`() {
        assertEquals(0, ExtraDim.darker(0, emptyList()))
        assertEquals(0, ExtraDim.brighter(0, emptyList()))
    }
}
