package dev.equwal.assistkey.voice

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.widget.Toast

/**
 * Voice typing: speak, and the words go where the cursor is.
 *
 * This app has no speech engine and no network access. It uses the Android
 * SpeechRecognizer interface, so the recognition is done by a speech app the
 * user already has - the system one, or an offline Whisper app, or any other
 * app that offers a RecognitionService. The words come back as text, and
 * [TextInsert] puts them into the focused field.
 *
 * This object holds the settings and starts [DictationActivity], which listens.
 */
object Dictation {

    private const val TAG = "AssistKey"
    private const val PREFS = "assistkey_voice"
    private const val K_ENGINE = "engine"
    private const val K_LANGUAGE = "language"

    // ---- configuration -------------------------------------------------------------------

    data class Engine(val title: String, val component: ComponentName)

    fun engines(c: Context): List<Engine> =
        c.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).map {
            Engine(
                it.loadLabel(c.packageManager).toString(),
                ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
            )
        }

    /**
     * Package name fragments of speech apps that recognise on the device. One of
     * these is the default when the user has not chosen, because voice typing
     * must not send speech to a server unless the user asks for that.
     */
    private val onDevice = listOf("whisper", "futo", "vosk", "sayboard", "sherpa")

    /** The F-Droid app that recognises on the device, in many languages, with language detection. */
    const val WHISPER_PACKAGE = "org.woheller69.whisper"

    fun isOnDevice(e: Engine): Boolean = isOnDevice(e.component.packageName)

    fun isOnDevice(packageName: String): Boolean = onDevice.any { it in packageName.lowercase() }

    /**
     * The choice rule, on plain strings so that a test can run it with no
     * device. [engines] are flattened component names, `package/class`.
     * Order: the saved one if it is still installed, else the first on-device
     * one, else the only one, else null (the system default).
     */
    fun pick(saved: String?, engines: List<String>): String? =
        engines.firstOrNull { it == saved }
            ?: engines.firstOrNull { isOnDevice(it.substringBefore('/')) }
            ?: engines.singleOrNull()

    /** The engine the user chose, else an on-device one, else the only one, else the system default (null). */
    fun engine(c: Context): ComponentName? {
        val saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_ENGINE, null)
        return pick(saved, engines(c).map { it.component.flattenToString() })
            ?.let(ComponentName::unflattenFromString)
    }

    fun setEngine(c: Context, component: ComponentName?) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_ENGINE, component?.flattenToString()).apply()
    }

    /** A language tag such as en-US. Empty means the engine decides. */
    fun language(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LANGUAGE, "").orEmpty()

    fun setLanguage(c: Context, tag: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_LANGUAGE, tag.trim()).apply()
    }

    fun hasMicrophone(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun available(c: Context): Boolean = engines(c).isNotEmpty() || SpeechRecognizer.isRecognitionAvailable(c)

    // ---- the action ------------------------------------------------------------------------

    /** Set by [DictationActivity] while it listens; stops it and uses what was heard. */
    @Volatile var stop: (() -> Unit)? = null

    val listening: Boolean get() = stop != null

    /**
     * Starts listening. A second call while listening stops early.
     *
     * Android gives the microphone to the app in front only, and an accessibility
     * service does not count. So the listening runs in [DictationActivity], which
     * is see-through and cannot take focus: this app is in front, and the text
     * field behind it keeps the cursor.
     */
    fun toggle(svc: AccessibilityService): Boolean {
        stop?.let {
            it()
            return true
        }
        if (!hasMicrophone(svc)) return fail(svc, "Voice typing needs the microphone")
        if (!available(svc)) return fail(svc, "No speech app on this device")
        return runCatching {
            svc.startActivity(
                Intent(svc, DictationActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            true
        }.getOrDefault(false)
    }

    fun toast(c: Context, message: String) = Toast.makeText(c, message, Toast.LENGTH_LONG).show()

    private fun fail(c: Context, message: String): Boolean {
        toast(c, message)
        return false
    }
}
