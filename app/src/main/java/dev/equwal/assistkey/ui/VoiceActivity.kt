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
        val col = Ui.page(this, "Voice typing")
        col.note("Press a button, speak, and the words appear.")

        if (Dictation.hasMicrophone(this)) {
            col.row("Microphone", null, enabled = false, state = "Allowed")
        } else {
            col.button("Allow the microphone") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
            col.note("If nothing happens, allow it in App info.")
            col.button("Open App info") {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                    )
                }
            }
        }

        col.header("Speech app")
        val engines = Dictation.engines(this)
        val current = Dictation.engine(this)
        engines.forEach { e ->
            val where = if (Dictation.isOnDevice(e)) "Recognises on this device"
            else "May send speech to its own server"
            col.row(e.title, where, state = if (e.component == current) "In use" else null) {
                Dictation.setEngine(this, e.component)
                build()
            }
        }
        if (engines.none(Dictation::isOnDevice)) {
            col.note("No app here recognises speech offline. Whisper, from F-Droid, is free.")
            col.button("Get Whisper") {
                val pages = listOf(
                    "market://details?id=" + Dictation.WHISPER_PACKAGE,
                    "https://f-droid.org/packages/" + Dictation.WHISPER_PACKAGE + "/"
                )
                pages.firstOrNull { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
                }
            }
            col.note("In Whisper, download a model and allow the microphone.")
        }

        col.header("Language")
        val lang = Dictation.language(this)
        col.row(
            if (lang.isEmpty()) "Detect the language" else lang,
            "A tag such as en-US"
        ) {
            Ui.textInput(this, "Language tag", "en-US", lang) { Dictation.setLanguage(this, it); build() }
        }
        if (lang.isNotEmpty()) col.button("Clear the language") { Dictation.setLanguage(this, ""); build() }

        col.header("Button")
        col.note("Set up a button, then choose Voice typing.")
        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.row(
                "Button remapping",
                "Voice typing needs it",
                state = "Off"
            ) { startActivity(Intent(this, SetupActivity::class.java)) }
        }
    }
}
