package dev.equwal.assistkey.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionSpecNameTest {

    @Test fun `a light action is named from its payload`() {
        assertEquals("Extra dim toggle", ActionSpec(ActionKind.DIM, "toggle", "").describe())
        assertEquals("Extra dim darker", ActionSpec(ActionKind.DIM, "darker", "").describe())
        assertEquals("Extra dim brighter", ActionSpec(ActionKind.DIM, "brighter", "").describe())
        assertEquals("Brightness up", ActionSpec(ActionKind.DIM, "system_up", "").describe())
        assertEquals("Brightness down", ActionSpec(ActionKind.DIM, "system_down", "").describe())
    }

    // A binding made by 0.0.6 stored the old label. It must show the new name.
    @Test fun `an old stored label does not win over the current name`() {
        assertEquals("Extra dim toggle", ActionSpec(ActionKind.DIM, "toggle", "Extra-dim: on and off").describe())
    }

    @Test fun `other actions keep their stored label`() {
        assertEquals("Back", ActionSpec(ActionKind.GLOBAL, "BACK", "Back").describe())
    }
}
