package dev.equwal.assistkey.ui

import dev.equwal.assistkey.license.License
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hub's one-line state text, which is the only logic in the new screens. */
class SummaryTest {

    @Test
    fun `a caption names what the button does now, each action once`() {
        assertEquals("Not set", Summary.caption(emptyList()))
        assertEquals("Back", Summary.caption(listOf("Back")))
        assertEquals("Back \u00b7 Home", Summary.caption(listOf("Back", "Home", "Back")))
    }

    @Test
    fun `keys counts the bindings, not the buttons`() {
        assertEquals("Nothing bound yet", Summary.keys(3, 0))
        assertEquals("1 action on 1 button", Summary.keys(1, 1))
        assertEquals("5 actions on 3 buttons", Summary.keys(3, 5))
    }

    @Test
    fun `detected says whether it has run, then what it found`() {
        assertEquals("Not run yet", Summary.detected(null))
        assertEquals("The device declares no named keys", Summary.detected(0))
        assertEquals("1 key declared by the device", Summary.detected(1))
        assertEquals("4 keys declared by the device", Summary.detected(4))
    }

    @Test
    fun `power falls back to the firmware wording`() {
        assertEquals("Default behaviour", Summary.power(0))
        assertEquals("1 action bound", Summary.power(1))
        assertEquals("4 actions bound", Summary.power(4))
    }

    @Test
    fun `navigation lists only what is on`() {
        assertEquals("Nothing switched on", Summary.navigation(false, false, false))
        assertEquals("Bar", Summary.navigation(true, false, false))
        assertEquals("Power button", Summary.navigation(false, false, true))
        assertEquals("Bar · Gestures · Power button", Summary.navigation(true, true, true))
    }

    @Test
    fun `voice names the first thing that is missing`() {
        assertEquals("Needs a speech app", Summary.voice(microphone = true, speechApps = 0, onDevice = false))
        assertEquals("Needs a speech app", Summary.voice(microphone = false, speechApps = 0, onDevice = false))
        assertEquals("Needs the microphone", Summary.voice(microphone = false, speechApps = 2, onDevice = true))
        assertEquals("Ready, on this device", Summary.voice(microphone = true, speechApps = 1, onDevice = true))
        assertEquals("Ready", Summary.voice(microphone = true, speechApps = 1, onDevice = false))
    }

    @Test
    fun `home and recents says what is in use`() {
        assertEquals("Home screens, Ink Recents", Summary.homeAndRecents(0, 3))
        assertEquals("1 of 3 installed", Summary.homeAndRecents(1, 3))
        assertEquals("All installed", Summary.homeAndRecents(3, 3))
    }

    @Test
    fun `setup says the blocker before any count`() {
        assertEquals("Button remapping is off", Summary.setup(false, 4, 4))
        assertEquals("All set", Summary.setup(true, 4, 4))
        assertEquals("2 to set up", Summary.setup(true, 2, 4))
    }

    @Test
    fun `the licence chip stays short`() {
        assertEquals("Unlocked", Summary.licenceChip(License.Tier.LICENSED, 0))
        assertEquals("Free", Summary.licenceChip(License.Tier.NO_STORE, 0))
        assertEquals("Beta", Summary.licenceChip(License.Tier.BETA, 0))
        assertEquals("1 day left", Summary.licenceChip(License.Tier.TRIAL, 1))
        assertEquals("7 days left", Summary.licenceChip(License.Tier.TRIAL, 7))
        assertEquals("Locked", Summary.licenceChip(License.Tier.LOCKED, 0))
    }

    @Test
    fun `count makes the plural`() {
        assertEquals("0 keys", Summary.count(0, "key"))
        assertEquals("1 key", Summary.count(1, "key"))
        assertEquals("2 keys", Summary.count(2, "key"))
    }
}
