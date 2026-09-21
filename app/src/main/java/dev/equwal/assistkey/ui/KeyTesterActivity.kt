package dev.equwal.assistkey.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.engine.KeyLog
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * Shows every key event the filter receives.
 *
 * This is the only honest way to answer "can this key be remapped at all?" on a
 * given device. A key the window manager consumes upstream never reaches any
 * input filter and simply will not appear - that silence is the answer, and it
 * is what Power looks like on every Android device.
 */
class KeyTesterActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCount = -1

    private val tick = object : Runnable {
        override fun run() {
            if (KeyLog.count() != lastCount) build()
            handler.postDelayed(this, 400L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyLog.clear()
    }

    override fun onResume() {
        super.onResume()
        KeyLog.recording = true
        build()
        handler.postDelayed(tick, 400L)
    }

    override fun onPause() {
        super.onPause()
        KeyLog.recording = false
        handler.removeCallbacks(tick)
    }

    private fun build() {
        lastCount = KeyLog.count()
        val col = Ui.page(this, "Key tester")

        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.row(
                "Key remapping is off",
                "Nothing will appear here until it is on",
                state = "Off"
            ) { startActivity(android.content.Intent(this, SetupActivity::class.java)) }
            return
        }

        col.note("Press a key. Anything that reaches the filter is listed below.")
        col.more("Key tester", ABOUT)

        col.button("Clear") { KeyLog.clear(); build() }

        val recent = KeyLog.recent()
        col.header(if (recent.isEmpty()) "Nothing yet" else "Most recent first")
        if (recent.isEmpty()) {
            col.note(
                "If a key never appears after pressing it, the firmware is " +
                    "keeping it to itself. For a volume key that usually means " +
                    "a firmware hook is set - see Firmware key hooks."
            )
            return
        }
        recent.forEach { e ->
            col.row(e.describe(), "keycode " + e.keyCode, enabled = false)
        }
    }

    private companion object {
        const val ABOUT =
            "Every key that reaches the filter is listed, whether or not it " +
                "has a binding.\n\n" +
                "Power will never appear. The window manager takes it before " +
                "any app can see it, on every Android device.\n\n" +
                "Only real presses count. Events injected with adb shell input " +
                "bypass accessibility filters entirely and will not show up.\n\n" +
                "Keys are listed only while this screen is open, and are never " +
                "stored."
    }
}
