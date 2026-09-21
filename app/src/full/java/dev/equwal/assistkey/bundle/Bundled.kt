package dev.equwal.assistkey.bundle

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * Apps of other makers that this build carries and can install.
 *
 * Each app is the APK that its maker released, byte for byte, in the assets of
 * this build. Rebind does not change it and does not download it: this app has
 * no internet permission. Android asks the user before each install.
 *
 * Only the build for direct install has this. Google Play does not allow an
 * app to install other apps unless that is its main purpose.
 */
object Bundled {

    const val SUPPORTED = true

    class Item(
        /** The screen that lists the app: HOME or VOICE. */
        val group: String,
        val title: String,
        val hint: String,
        val pkg: String,
        val version: String,
        val licence: String,
        /** Where the maker publishes the source code and the releases. */
        val source: String,
        internal val asset: String
    )

    const val HOME = "home"
    const val VOICE = "voice"

    val items = listOf(
        Item(
            HOME, "CLauncher", "Minimal home screen", "app.clauncher", "v5.3.0", "GPL-3.0",
            "https://github.com/mlm-games/CLauncher", "bundled/clauncher-v5.3.0-universal.apk"
        ),
        Item(
            HOME, "Ink Recents", "Recent apps, drawn for e-ink", "dev.equwal.inkrecents", "0.1.1", "GPL-3.0",
            "https://github.com/equwal/ink-recents", "bundled/ink-recents-0.1.1.apk"
        ),
        Item(
            VOICE, "Whisper", "Speech to text on the device", "org.woheller69.whisper", "3.7", "MIT",
            "https://f-droid.org/packages/org.woheller69.whisper/", "bundled/org.woheller69.whisper_37.apk"
        )
    )

    /** Hands the APK to the package installer of Android. Android then asks the user. */
    fun install(a: Activity, item: Item) {
        runCatching {
            val installer = a.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(item.pkg)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                a.assets.open(item.asset).use { input ->
                    session.openWrite("base.apk", 0, -1).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val result = PendingIntent.getBroadcast(
                    a, id, Intent(a, Result::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(result.intentSender)
            }
        }.onFailure { Toast.makeText(a, "Could not start the install", Toast.LENGTH_LONG).show() }
    }

    /** Android answers here: first with the question for the user, then with the result. */
    class Result : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val ask = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    runCatching { c.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                PackageInstaller.STATUS_SUCCESS -> Toast.makeText(c, "Installed", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(c, "Not installed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
