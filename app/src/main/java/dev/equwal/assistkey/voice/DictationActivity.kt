package dev.equwal.assistkey.voice

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import dev.equwal.assistkey.route.ServiceHolder

/**
 * Listens, and hands the words to [TextInsert].
 *
 * The activity exists so that this app is in front while the microphone is in
 * use; Android allows nothing else. It is see-through, and its window cannot
 * take focus or touches, so the text field behind it keeps the cursor and the
 * keyboard. The only thing it draws is a one-line label.
 */
class DictationActivity : Activity() {

    private companion object {
        /** Time for the window behind to be the front window again. */
        const val INSERT_DELAY_MS = 300L
    }

    private var recognizer: SpeechRecognizer? = null
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        label = TextView(this).apply {
            text = "Listening..."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p * 3, p, p)
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    label,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                    )
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (recognizer == null) listen()
    }

    override fun onDestroy() {
        close()
        super.onDestroy()
    }

    private fun listen() {
        val r = runCatching {
            Dictation.engine(this)?.let { SpeechRecognizer.createSpeechRecognizer(this, it) }
                ?: SpeechRecognizer.createSpeechRecognizer(this)
        }.getOrNull()
        if (r == null) {
            Dictation.toast(this, "The speech app did not start")
            return done(null)
        }
        recognizer = r
        Dictation.stop = { r.stopListening() }
        r.setRecognitionListener(listener)
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                Dictation.language(this@DictationActivity).takeIf { it.isNotEmpty() }
                    ?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
        )
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) =
            done(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())

        override fun onError(error: Int) {
            Log.i("AssistKey", "speech recognition error " + error)
            Dictation.toast(
                this@DictationActivity,
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Heard nothing"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "The microphone is not allowed"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "The speech app has no connection"
                    else -> "Speech failed (" + error + ")"
                }
            )
            done(null)
        }

        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { label.text = it }
        }

        override fun onEndOfSpeech() { label.text = "Working..." }
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun close() {
        Dictation.stop = null
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    /** Leaves first, then writes: the field must be the front window again. */
    private fun done(text: String?) {
        Log.i("AssistKey", "voice typing: " + if (text.isNullOrBlank()) "no text" else text.length.toString() + " characters")
        close()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (text.isNullOrBlank()) return
        val app = applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            val svc = ServiceHolder.service ?: return@postDelayed
            when (TextInsert.insert(svc, text)) {
                TextInsert.Result.DONE -> Unit
                TextInsert.Result.NO_FIELD -> Dictation.toast(app, "No text field has the cursor")
                TextInsert.Result.PASSWORD -> Dictation.toast(app, "Not into a password field")
                TextInsert.Result.REFUSED -> Dictation.toast(app, "That field did not accept the text")
            }
        }, INSERT_DELAY_MS)
    }
}
