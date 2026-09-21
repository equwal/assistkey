package dev.equwal.assistkey.device

import dev.equwal.assistkey.model.HwKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DeviceKeysTest {

    private val viwoods = listOf(HwKey.VOL_UP, HwKey.VOL_DOWN, HwKey.AI)

    /**
     * Regression, 0.0.10: the Viwoods reader declares a camera key and has
     * none. Detection stored ["ai","vol_up","vol_down","camera","power"], and
     * the drawing showed a Camera key. A declared key is not a button.
     */
    @Test fun `a key that the device only declares is not in the drawing`() {
        val shown = Device.listed(viwoods, seenTokens = emptySet())
        assertEquals(viwoods, shown)
        assertFalse(HwKey.CAMERA in shown)
    }

    @Test fun `a key that was really pressed is in the drawing, after the profile keys`() {
        assertEquals(viwoods + HwKey.PAGE_DOWN, Device.listed(viwoods, setOf("page_down")))
    }

    @Test fun `the drawing has the profile keys first, no key twice, and nothing that was not seen`() {
        val r = Random(5)
        repeat(300) {
            val seen = HwKey.entries.filter { r.nextInt(3) == 0 }.map { it.token }.toSet() + "no_such_key"
            val shown = Device.listed(viwoods, seen)
            assertEquals(viwoods, shown.take(viwoods.size))
            assertEquals(shown.distinct(), shown)
            assertTrue(shown.all { it in viwoods || (it.token in seen && it.interceptable) })
        }
    }
}
