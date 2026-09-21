package dev.equwal.assistkey.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.voice.Dictation
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/** Setup for the Voice typing action: microphone, speech app, language. */
class VoiceActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        build()
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("Voice typing")
        col.note(
            "Bind the Voice typing action to a key. Press the key, speak, and the " +
                "words go where the cursor is. Press the key again to stop early."
        )
        col.note(
            "AssistKey has no speech engine and no internet access. A speech " +
                "recognition app on this device does the listening, and AssistKey " +
                "only receives the text."
        )

        col.header("1. Microphone")
        if (Dictation.hasMicrophone(this)) {
            col.row("Allowed", null, enabled = false)
        } else {
            col.button("Allow the microphone") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
            col.note("If nothing happens, allow it in App info > Permissions.")
            col.button("Open App info") {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                    )
                }
            }
        }

        col.header("2. Speech recognition app")
        val engines = Dictation.engines(this)
        val current = Dictation.engine(this)
        engines.forEach { e ->
            val mark = if (e.component == current) "* " else "   "
            val where = if (Dictation.isOnDevice(e)) "Recognises on this device" else "May send speech to its own server"
            col.row(mark + e.title, where) {
                Dictation.setEngine(this, e.component)
                build()
            }
        }
        if (engines.none(Dictation::isOnDevice)) {
            col.note(
                "No app on this device recognises speech offline. Whisper, from " +
                    "F-Droid, is free, works with no connection after its model is " +
                    "downloaded, and detects the language by itself."
            )
            col.button("Get Whisper") {
                val pages = listOf(
                    "market://details?id=" + Dictation.WHISPER_PACKAGE,
                    "https://f-droid.org/packages/" + Dictation.WHISPER_PACKAGE + "/"
                )
                pages.firstOrNull { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
                }
            }
            col.note(
                "In Whisper, download the multilingual model and allow the microphone. " +
                    "Then come back here: it appears in this list."
            )
        }

        col.header("3. Language")
        val lang = Dictation.language(this)
        col.note(
            "Leave this empty to let the speech app detect the language. Whisper " +
                "detects it from what you say, so you can change language from one " +
                "sentence to the next."
        )
        col.row(if (lang.isEmpty()) "Detect the language" else lang, "Or force one: a tag such as en-US or de-DE") {
            Ui.textInput(this, "Language tag", "en-US", lang) { Dictation.setLanguage(this, it); build() }
        }
        if (lang.isNotEmpty()) col.button("Clear the language") { Dictation.setLanguage(this, ""); build() }

        col.header("4. Key")
        col.note("Open a key on the main screen, choose a gesture, then Typing > Voice typing.")
        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.note("Key remapping is off. Voice typing needs it to reach the text field.")
        }
        col.note("Voice typing never writes into password fields.")
    }
}
