package dev.equwal.assistkey.bundle

import android.app.Activity
import android.content.Intent
import android.net.Uri

/**
 * The Google Play build carries no apps of other makers and cannot install
 * any: Google Play allows that only for an app whose main purpose it is. It
 * names the same apps and opens the page of the maker.
 */
object Bundled {

    const val SUPPORTED = false

    class Item(
        /** The screen that lists the app: HOME or VOICE. */
        val group: String,
        val title: String,
        val hint: String,
        val pkg: String,
        val version: String,
        val licence: String,
        val source: String
    )

    const val HOME = "home"
    const val VOICE = "voice"

    val items = listOf(
        Item(
            HOME, "inkOS", "Text home screen for e-ink", "app.inkos", "v0.6", "GPL-3.0",
            "https://github.com/gezimos/inkOS"
        ),
        Item(
            HOME, "ThinkLauncher", "Minimal home screen for e-ink", "org.matiasdesu.thinklauncherv2", "v3.0", "GPL-3.0",
            "https://github.com/MatiasDesuu/ThinkLauncher"
        ),
        Item(
            HOME, "Ink Recents", "Recent apps, drawn for e-ink", "dev.equwal.inkrecents", "0.1.1", "GPL-3.0",
            "https://github.com/equwal/ink-recents"
        ),
        Item(
            VOICE, "Whisper", "Speech to text on the device", "org.woheller69.whisper", "3.7", "MIT",
            "https://f-droid.org/packages/org.woheller69.whisper/"
        )
    )

    /** Opens the page where the maker offers the app. */
    fun install(a: Activity, item: Item) {
        runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.source))) }
    }
}
