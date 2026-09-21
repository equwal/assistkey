package dev.equwal.assistkey.ui

import dev.equwal.assistkey.license.License
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hub's one-line state text, which is the only logic in the new screens. */
class SummaryTest {

    @Test
    fun `gesture names the key and what you do to it`() {
        assertEquals("Power, hold", Summary.gesture(listOf("Power"), hold = true, taps = 1))
        assertEquals("AI key, tap", Summary.gesture(listOf("AI key"), hold = false, taps = 1))
        assertEquals("AI key, double tap", Summary.gesture(listOf("AI key"), hold = false, taps = 2))
        assertEquals("AI key, triple tap", Summary.gesture(listOf("AI key"), hold = false, taps = 3))
        assertEquals("AI key, 5 taps", Summary.gesture(listOf("AI key"), hold = false, taps = 5))
    }

    @Test
    fun `gesture joins a combination with a plus`() {
        assertEquals(
            "Volume up + Volume down, tap",
            Summary.gesture(listOf("Volume up", "Volume down"), hold = false, taps = 1)
        )
    }

    @Test
    fun `a hold never counts taps`() {
        assertEquals("Power, hold", Summary.gesture(listOf("Power"), hold = true, taps = 3))
    }

    @Test
    fun `binding reads as gesture then action`() {
        assertEquals("Power, hold: Home", Summary.binding("Power, hold", "Home"))
    }

    @Test
    fun `keys counts the bindings, not the keys`() {
        assertEquals("Nothing bound yet", Summary.keys(3, 0))
        assertEquals("1 action on 1 key", Summary.keys(1, 1))
        assertEquals("5 actions on 3 keys", Summary.keys(3, 5))
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
        assertEquals("Buttons", Summary.navigation(true, false, false))
        assertEquals("Power key", Summary.navigation(false, false, true))
        assertEquals("Buttons · Gestures · Power key", Summary.navigation(true, true, true))
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
        assertEquals("Home screen in use", Summary.homeAndRecents(homeOffered = true, isDefault = true))
        assertEquals("Home screen ready", Summary.homeAndRecents(homeOffered = true, isDefault = false))
        assertEquals("Recent apps only", Summary.homeAndRecents(homeOffered = false, isDefault = false))
    }

    @Test
    fun `setup says the blocker before any count`() {
        assertEquals("Key remapping is off", Summary.setup(false, 4, 4))
        assertEquals("All set", Summary.setup(true, 4, 4))
        assertEquals("2 to set up", Summary.setup(true, 2, 4))
    }

    @Test
    fun `the licence chip stays short`() {
        assertEquals("Unlocked", Summary.licenceChip(License.Tier.LICENSED, 0))
        assertEquals("Beta", Summary.licenceChip(License.Tier.BETA, 0))
        assertEquals("1 day left", Summary.licenceChip(License.Tier.TRIAL, 1))
        assertEquals("7 days left", Summary.licenceChip(License.Tier.TRIAL, 7))
        assertEquals("Locked", Summary.licenceChip(License.Tier.LOCKED, 0))
    }

    @Test
    fun `the licence line explains the tier`() {
        assertEquals("Bought. Thank you.", Summary.licence(License.Tier.LICENSED, 0))
        assertEquals("Free while the beta is open", Summary.licence(License.Tier.BETA, 0))
        assertEquals("3 days of the trial left", Summary.licence(License.Tier.TRIAL, 3))
        assertEquals(
            "Remapping is off until a licence is bought",
            Summary.licence(License.Tier.LOCKED, 0)
        )
    }

    @Test
    fun `count makes the plural`() {
        assertEquals("0 keys", Summary.count(0, "key"))
        assertEquals("1 key", Summary.count(1, "key"))
        assertEquals("2 keys", Summary.count(2, "key"))
    }
}
