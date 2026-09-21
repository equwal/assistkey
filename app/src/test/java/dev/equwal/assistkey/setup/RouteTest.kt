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
        assertEquals(3, Route.gestures(power, direct).size)
        assertEquals(3, Route.gestures(setOf(HwKey.VOL_UP), sideDoors).size)
    }
}
