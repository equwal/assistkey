package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.native.NavNative
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.title

/**
 * A description of this device, for getting it supported.
 *
 * Every device maker wires keys, gestures and lights differently, and the only
 * way to support one is to know what it has. This screen gathers that - model,
 * firmware, the input devices and the keys they declare, the navigation
 * overlays, a handful of key-related settings - shows every line of it, and
 * sends it nowhere. The user sends it, by email or however they like, if they
 * choose to. The app has no internet permission and could not send it itself.
 *
 * What is deliberately not in it: installed apps, accounts, identifiers, the
 * contents of anything, and any key that was actually pressed outside the key
 * tester.
 */
class ReportActivity : Activity() {

    private var report = ""
    private val to = "truex@equwal.com"

    override fun onResume() {
        super.onResume()
        build("Gathering...")
        gather { text ->
            report = text
            if (!isFinishing) build(text)
        }
    }

    private fun build(text: String) {
        val col = Ui.page(this)
        col.title("Device report")
        col.note(
            "Help get " + Device.name + " fully supported. This is everything the " +
                "report contains - nothing is sent unless you send it, and AssistKey " +
                "has no internet permission to send it with."
        )
        col.button("Send by email") {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + to))
                .putExtra(Intent.EXTRA_SUBJECT, "AssistKey device report: " + Device.name)
                .putExtra(Intent.EXTRA_TEXT, report)
            if (runCatching { startActivity(i) }.isFailure) share()
        }
        col.button("Share another way") { share() }
        col.button("Copy") {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("AssistKey device report", report))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        col.header("The report")
        col.code(text)
        if (!Shell.ready) {
            col.note("With shell access the report also lists the input devices and their keys, which is the most useful part.")
        }
    }

    private fun share() {
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "AssistKey device report: " + Device.name)
                        .putExtra(Intent.EXTRA_TEXT, report),
                    "Send report"
                )
            )
        }
    }

    // ---- gathering ------------------------------------------------------------------------------

    private fun gather(done: (String) -> Unit) {
        val sb = StringBuilder()
        fun line(k: String, v: Any?) { sb.append(k).append(": ").append(v).append('\n') }

        sb.append("# AssistKey device report\n")
        line("app", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
        line("manufacturer", Build.MANUFACTURER)
        line("brand", Build.BRAND)
        line("model", Build.MODEL)
        line("device", Build.DEVICE)
        line("product", Build.PRODUCT)
        line("android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        line("firmware", Build.DISPLAY)
        line("build type", Build.TYPE)
        line("profile", Device.profile)
        val dm = resources.displayMetrics
        line("screen", dm.widthPixels.toString() + " x " + dm.heightPixels + " @ " + dm.densityDpi + " dpi")

        sb.append("\n# State\n")
        line("key filter", Channels.status(this, Channel.ACCESSIBILITY))
        line("shell access", Shell.state(this))
        line("power button managed", PowerControl.active)
        line("button bar showing", NavNative.buttonsShowing(this))
        line("keys shown", Device.keys(this).joinToString { it.token })
        line("keys seen in tester", Device.unknownSeen(this).ifEmpty { listOf("none") }.joinToString("; "))

        if (!Shell.ready) return done(sb.toString())

        // Names and shapes only. The settings query is a whitelist of key and
        // navigation switches; per-app settings, which would name installed
        // apps, are left out on purpose.
        val wanted = "custom.*key|power_button|key_chord|navigation_mode|disable_gesture_bottom|" +
            "double_tap_power|camera_double_tap|screen_brightness\$|back_gesture_inset"
        val script = listOf(
            "echo '# Input devices'; getevent -p 2>/dev/null | grep -E 'add device|name:|KEY \\(0001\\)|^ {20}[0-9a-f]' | head -80",
            "echo; echo '# Navigation overlays'; cmd overlay list 2>/dev/null | grep -i navbar",
            "echo; echo '# Settings'; for t in system secure global; do settings list \$t 2>/dev/null | grep -iE '$wanted' | grep -v 'disable_gesture_[a-z]*\\.' | sed \"s/^/[\$t] /\"; done",
            "echo; echo '# Light'; for n in /sys/class/leds/lcd-backlight /sys/class/backlight/*; do [ -e \$n/brightness ] && echo \$n now=\$(cat \$n/brightness) max=\$(cat \$n/max_brightness 2>/dev/null) min=\$(cat \$n/min_brightness 2>/dev/null); done",
            "echo; echo '# Shell'; id | cut -d' ' -f1"
        ).joinToString("; ")
        Shell.run(script) { r ->
            sb.append('\n').append(r.output.trim()).append('\n')
            done(sb.toString())
        }
    }
}
