package dev.equwal.assistkey.debug

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Debug builds only. Answers every request with the same sentence after one second. */
class FakeRecognitionService : RecognitionService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening(intent: Intent, callback: Callback) {
        callback.readyForSpeech(Bundle())
        callback.beginningOfSpeech()
        main.postDelayed({
            callback.endOfSpeech()
            callback.results(
                Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(SENTENCE))
                }
            )
        }, 1000L)
    }

    override fun onStopListening(callback: Callback) = Unit
    override fun onCancel(callback: Callback) = main.removeCallbacksAndMessages(null)

    companion object {
        const val SENTENCE = "hello from the test bench"
    }
}
