package dev.equwal.assistkey.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The rule that chooses the speech recognition app when the user has not. */
class DictationPickTest {

    private val claude = "com.anthropic.claude/.bell.assist.ClaudeRecognitionService"
    private val whisper = "org.woheller69.whisper/.WhisperRecognitionService"
    private val google = "com.google.android.tts/.service.GoogleTTSRecognitionService"

    @Test fun `an on-device engine wins over a server engine that was installed first`() {
        assertEquals(whisper, Dictation.pick(null, listOf(claude, whisper)))
    }

    @Test fun `the choice of the user wins over an on-device engine`() {
        assertEquals(claude, Dictation.pick(claude, listOf(claude, whisper)))
    }

    @Test fun `a saved engine that is no longer installed is ignored`() {
        assertEquals(whisper, Dictation.pick("gone.app/.Service", listOf(claude, whisper)))
    }

    @Test fun `the only engine is used when none is on-device`() {
        assertEquals(claude, Dictation.pick(null, listOf(claude)))
    }

    @Test fun `with several server engines and no choice the system decides`() {
        assertNull(Dictation.pick(null, listOf(claude, google)))
    }

    @Test fun `with no engine there is nothing to pick`() {
        assertNull(Dictation.pick(null, emptyList()))
    }
}
