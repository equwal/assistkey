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

    // Regression: "on" used the last level in use (or a middle level), not the lowest.
    @Test fun `on and off - on is the lowest level, from off`() {
        assertEquals(1, ExtraDim.toggled(0, levels))
    }

    @Test fun `on and off - off is the system brightness, from any level`() {
        levels.forEach { assertEquals(0, ExtraDim.toggled(it, levels)) }
    }

    @Test fun `system brightness steps are fine when low, coarse when high, and stay in range`() {
        assertEquals(10, ExtraDim.systemStep(5, up = true, floor = 5))
        assertEquals(5, ExtraDim.systemStep(8, up = false, floor = 5))
        assertEquals(250, ExtraDim.systemStep(200, up = true, floor = 5))
        assertEquals(255, ExtraDim.systemStep(250, up = true, floor = 5))
        assertEquals(5, ExtraDim.systemStep(5, up = false, floor = 5))
    }

    @Test fun `system brightness never leaves the range and never moves the wrong way`() {
        (0..255).forEach { v ->
            val down = ExtraDim.systemStep(v, up = false, floor = 5)
            val up = ExtraDim.systemStep(v, up = true, floor = 5)
            assert(down in 5..255 && up in 5..255) { "value $v gave $down and $up" }
            assert(down <= maxOf(v, 5) && up >= v) { "value $v moved the wrong way" }
        }
    }

    @Test fun `a device with no levels stays off`() {
        assertEquals(0, ExtraDim.toggled(0, emptyList()))
        assertEquals(0, ExtraDim.darker(0, emptyList()))
        assertEquals(0, ExtraDim.brighter(0, emptyList()))
    }
}
