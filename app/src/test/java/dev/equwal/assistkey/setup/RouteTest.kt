package dev.equwal.assistkey.setup

import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.setup.Route.Need
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private val sideDoors = Route.Env(powerIsDirect = false)
    private val direct = Route.Env(powerIsDirect = true)
    private val power = setOf(HwKey.POWER)

    @Test fun `a volume key needs the key filter and nothing more`() {
        val t = Trigger(setOf(HwKey.VOL_DOWN), GestureType.TAP, 2)
        assertEquals(listOf(Need.KEY_FILTER), Route.plan(t, true, sideDoors).needs)
        assertEquals(listOf(Need.KEY_FILTER), Route.plan(t, false, sideDoors).needs)
    }

    @Test fun `Power hold goes through the assistant`() {
        val t = Trigger(power, GestureType.HOLD)
        assertEquals(listOf(Need.ASSISTANT), Route.plan(t, false, sideDoors).needs)
        assertEquals(listOf(Need.KEY_FILTER, Need.ASSISTANT), Route.plan(t, true, sideDoors).needs)
    }

    @Test fun `Power double press goes through the camera`() {
        val t = Trigger(power, GestureType.TAP, 2)
        assertEquals(listOf(Need.CAMERA), Route.plan(t, false, sideDoors).needs)
        assertEquals(listOf(Need.KEY_FILTER, Need.CAMERA), Route.plan(t, true, sideDoors).needs)
    }

    @Test fun `Power tap and triple tap cannot work with the side doors, and the plan says why`() {
        listOf(1, 3, 4, 5).forEach { n ->
            val plan = Route.plan(Trigger(power, GestureType.TAP, n), true, sideDoors)
            assertFalse(plan.possible)
            assertTrue(plan.needs.isEmpty())
            assertTrue(plan.blocked!!.contains("Hold and double press"))
        }
    }

    @Test fun `Power held then another button needs the filter, because the filter sees the second button`() {
        val plan = Route.plan(Trigger.powerThen(HwKey.VOL_UP), false, sideDoors)
        assertEquals(listOf(Need.KEY_FILTER, Need.ASSISTANT), plan.needs)
    }

    @Test fun `with direct Power access every Power gesture works through the key filter`() {
        listOf(
            Trigger(power, GestureType.TAP, 1),
            Trigger(power, GestureType.TAP, 5),
            Trigger(power, GestureType.HOLD),
            Trigger.powerThen(HwKey.VOL_DOWN)
        ).forEach { assertEquals(listOf(Need.KEY_FILTER), Route.plan(it, true, direct).needs) }
    }

    @Test fun `the gestures offered are the ones that can work`() {
        assertEquals(
            listOf(Trigger(power, GestureType.TAP, 2), Trigger(power, GestureType.HOLD)),
            Route.gestures(power, sideDoors)
        )
        assertEquals(Trigger.MAX_TAPS + 1, Route.gestures(power, direct).size)
        val volume = Route.gestures(setOf(HwKey.VOL_UP), sideDoors)
        assertEquals(Trigger.MAX_TAPS + 1, volume.size)
        // Tap, double tap, triple tap, hold. The long tap counts come last.
        assertEquals(Trigger(setOf(HwKey.VOL_UP), GestureType.HOLD), volume[3])
    }

    @Test fun `the on-screen button has one gesture, a tap, and needs the key filter that draws it`() {
        val screen = setOf(HwKey.SCREEN)
        listOf(sideDoors, direct).forEach { env ->
            assertEquals(listOf(Trigger(screen, GestureType.TAP, 1)), Route.gestures(screen, env))
            assertEquals(
                listOf(Need.KEY_FILTER),
                Route.plan(Trigger(screen, GestureType.TAP, 1), false, env).needs
            )
        }
    }

    @Test fun `Power with another button has one gesture, in every environment`() {
        val keys = setOf(HwKey.POWER, HwKey.VOL_UP)
        listOf(sideDoors, direct).forEach { env ->
            assertEquals(listOf(Trigger.powerThen(HwKey.VOL_UP)), Route.gestures(keys, env))
        }
    }

    @Test fun `two ordinary buttons together take every gesture`() {
        val keys = setOf(HwKey.VOL_UP, HwKey.VOL_DOWN)
        assertEquals(Trigger.MAX_TAPS + 1, Route.gestures(keys, sideDoors).size)
    }

    @Test fun `a tap in the drawing selects, a second tap lets go`() {
        assertEquals(listOf(HwKey.AI), Route.toggle(emptyList(), HwKey.AI))
        assertEquals(emptyList<HwKey>(), Route.toggle(listOf(HwKey.AI), HwKey.AI))
        assertEquals(listOf(HwKey.AI, HwKey.VOL_UP), Route.toggle(listOf(HwKey.AI), HwKey.VOL_UP))
    }

    @Test fun `a third button drops the oldest, and the on-screen button is always alone`() {
        assertEquals(
            listOf(HwKey.VOL_UP, HwKey.VOL_DOWN),
            Route.toggle(listOf(HwKey.AI, HwKey.VOL_UP), HwKey.VOL_DOWN)
        )
        assertEquals(listOf(HwKey.SCREEN), Route.toggle(listOf(HwKey.AI, HwKey.VOL_UP), HwKey.SCREEN))
        assertEquals(listOf(HwKey.AI), Route.toggle(listOf(HwKey.SCREEN), HwKey.AI))
    }

    @Test fun `a selection never grows past the limit, never repeats a button, and never mixes the on-screen button`() {
        val r = java.util.Random(7)
        repeat(500) {
            var sel = emptyList<HwKey>()
            repeat(12) {
                sel = Route.toggle(sel, HwKey.entries[r.nextInt(HwKey.entries.size)])
                assertTrue(sel.size <= Route.MAX_TOGETHER)
                assertEquals(sel.size, sel.toSet().size)
                assertTrue(HwKey.SCREEN !in sel || sel.size == 1)
            }
        }
    }

    @Test fun `where the build can have shell access, a hidden Power press asks for it and is not refused`() {
        val fullBuild = Route.Env(powerIsDirect = false, shellSupported = true)
        listOf(1, 3, 4, 5).forEach { n ->
            val plan = Route.plan(Trigger(power, GestureType.TAP, n), true, fullBuild)
            assertTrue(plan.possible)
            assertEquals(listOf(Need.KEY_FILTER, Need.SHELL_POWER), plan.needs)
        }
        // The doors of Android stay the first choice.
        assertEquals(listOf(Need.ASSISTANT), Route.plan(Trigger(power, GestureType.HOLD), false, fullBuild).needs)
        assertEquals(Trigger.MAX_TAPS + 1, Route.gestures(power, fullBuild).size)
    }
}
