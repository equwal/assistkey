package dev.equwal.assistkey.ui

import android.app.Activity
import android.os.Build
import android.widget.LinearLayout
import dev.equwal.assistkey.device.Detect
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row

/**
 * What AssistKey found on this device: the buttons, and what the device lets
 * the app do.
 *
 * Detection is passive. The app reads Android's key tables, the input devices
 * the framework lists, and - with shell access - the key codes each input node
 * declares. It never asks anybody to press a button, and it records no press.
 *
 * "Unknown" is an answer. Android hides some settings from apps, so a state the
 * app may not read says so instead of guessing.
 */
class DetectActivity : Activity() {

    private var busy = false

    override fun onResume() {
        super.onResume()
        Shell.connect(this)
        build()
        if (Detect.stored(this) == null) detect()
    }

    private fun detect() {
        if (busy) return
        busy = true
        build()
        Detect.run(this) { r ->
            Detect.save(this, r)
            busy = false
            if (!isFinishing) build()
        }
    }

    private fun build() {
        val col = Ui.page(this, "Detect this device")

        val found = Detect.stored(this)
        if (found == null) {
            col.note(if (busy) "Detecting..." else "Nothing detected yet.")
            col.primaryButton("Detect again") { detect() }
            return
        }

        col.header("This device")
        col.row(found.name, found.profile + " · Android " + Build.VERSION.RELEASE)

        buttons(col, found)
        capabilities(col, found)

        if (Shell.SUPPORTED && !Shell.ready) {
            col.note("Shell access finds buttons Android does not declare.")
        }
        col.primaryButton(if (busy) "Detecting..." else "Detect again") { detect() }
    }

    private fun buttons(col: LinearLayout, found: Detect.Result) {
        col.header("Buttons found")
        if (found.keys.isEmpty()) {
            col.note("None found.")
            return
        }
        found.keys.forEach { key ->
            col.row(key.label, "Code " + key.code + (if (key.interceptable) "" else " · kept by the system"))
        }
    }

    private fun capabilities(col: LinearLayout, found: Detect.Result) {
        col.header("What it can do")
        Detect.capabilityNames.forEach { name ->
            col.row(name, state = found.capability(name).label)
        }
    }
}
