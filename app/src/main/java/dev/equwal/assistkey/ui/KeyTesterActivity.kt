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
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

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
        build()
        handler.postDelayed(tick, 400L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun build() {
        lastCount = KeyLog.count()
        val col = Ui.page(this)
        col.title("Key tester")

        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.note(
                "The accessibility key filter is not running, so nothing will " +
                    "appear here. Turn it on from the main screen first."
            )
            return
        }

        col.note(
            "Press a key. Anything that reaches the filter is listed below, " +
                "whether or not it has a binding. Power will never appear: the " +
                "window manager consumes it before any app can see it."
        )
        col.note(
            "Only real presses count. Events injected over adb bypass " +
                "accessibility input filters and will not show up."
        )

        col.button("Clear") { KeyLog.clear(); build() }

        val recent = KeyLog.recent()
        col.header(if (recent.isEmpty()) "Nothing yet" else "Most recent first")
        if (recent.isEmpty()) {
            col.note(
                "If a key never appears after pressing it, that key cannot be " +
                    "remapped through the accessibility channel on this " +
                    "firmware. Try the Viwoods native hooks instead."
            )
            return
        }
        recent.forEach { e ->
            col.row(e.describe(), "keycode " + e.keyCode, enabled = false)
        }
    }
}
