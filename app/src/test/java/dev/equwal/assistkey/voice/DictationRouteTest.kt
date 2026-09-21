package dev.equwal.assistkey.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationRouteTest {

    /**
     * Regression, 0.0.13 on the Viwoods reader: Rebind and Whisper both held
     * the microphone grant, and the speech service answered error 9
     * (ERROR_INSUFFICIENT_PERMISSIONS), because the firmware did not count the
     * background speech service as in use. The app said "not allowed" and
     * stopped. It must go to the speech screen of the speech app.
     */
    @Test fun `error 9 with a speech screen goes to that screen`() {
        assertTrue(Dictation.fallBackToScreen(error = 9, screenAvailable = true))
    }

    @Test fun `no speech screen, or another error, does not`() {
        assertFalse(Dictation.fallBackToScreen(error = 9, screenAvailable = false))
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13).forEach {
            assertFalse(Dictation.fallBackToScreen(error = it, screenAvailable = true))
        }
    }
}
