package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * The whole configuration surface.
 *
 * Rebuilt in onResume rather than onCreate, because almost every setup step
 * happens in another app - Settings, a role dialog - and the user comes back
 * expecting the status lines to have caught up.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.syncComponents(this)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("AssistKey")
        col.note(
            "Remaps the AI key, both volume keys and the power button. " +
                "Turn on the capture channels you want, then bind gestures below."
        )
        channels(col)
        buttons(col)
        extras(col)
    }

    // ---- channels ----------------------------------------------------------

    private fun channels(col: LinearLayout) {
        col.header("Capture channels")
        col.note(
            "Each one is a different way of getting a key routed to this app. " +
                "They are independent - tick any combination."
        )

        Channel.entries.forEach { ch ->
            val on = Channels.isEnabled(this, ch)
            val ok = Channels.isSatisfied(this, ch)
            col.check(ch.title, ch.summary, on) { checked ->
                Channels.setEnabled(this, ch, checked)
                if (checked && !Channels.isSatisfied(this, ch)) claim(ch)
                build()
            }
            col.row("Status: " + Channels.status(this, ch), null, enabled = on)
            if (on && !ok) col.button("Set up " + ch.title.lowercase()) { claim(ch) }
        }
    }

    private fun claim(ch: Channel) {
        if (ch == Channel.CAMERA) {
            Toast.makeText(
                this,
                "Choose Camera app, then pick AssistKey",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!Channels.safeStart(this, Channels.claimIntent(this, ch))) {
            Toast.makeText(this, "No settings screen for that on this firmware", Toast.LENGTH_LONG)
                .show()
        }
    }

    // ---- bindings ----------------------------------------------------------

    private fun buttons(col: LinearLayout) {
        col.header("Buttons")

        listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN).forEach { key ->
            col.row(key.label, boundSummary(setOf(key))) {
                startActivity(TriggerListActivity.intent(this, setOf(key)))
            }
        }

        col.row("Power", powerSummary()) { startActivity(Intent(this, PowerActivity::class.java)) }

        col.row(
            "Two-key combinations",
            "Hold one key and press another - any pair or larger set"
        ) { startActivity(Intent(this, ChordActivity::class.java)) }
    }

    private fun boundSummary(keys: Set<HwKey>): String {
        val b = Store.bindings(this)
        val bound = Trigger.allFor(keys).filter { b.isBound(it) }
        if (bound.isEmpty()) return "Default behaviour"
        return bound.joinToString(", ") { t ->
            shortGesture(t) + " → " + b.raw(t).describe()
        }
    }

    private fun shortGesture(t: Trigger): String = when {
        t.type == GestureType.HOLD -> "hold"
        t.count == 1 -> "tap"
        t.count == 2 -> "double"
        t.count == 3 -> "triple"
        else -> t.count.toString() + " taps"
    }

    private fun powerSummary(): String {
        val b = Store.bindings(this)
        val parts = ArrayList<String>()
        PowerNative.shortPressValue(this)?.let { v ->
            PowerNative.shortPress.firstOrNull { it.value == v }
                ?.let { parts.add("short → " + it.label.lowercase()) }
        }
        b[Channel.POWER_DOUBLE]?.let { parts.add("double → " + it.describe()) }
        b[Channel.POWER_HOLD]?.let { parts.add("hold → " + it.describe()) }
        return if (parts.isEmpty()) "Default behaviour" else parts.joinToString(", ")
    }

    // ---- everything else ---------------------------------------------------

    private fun extras(col: LinearLayout) {
        col.header("Advanced")

        col.row(
            "Firmware key hooks",
            if (Channels.isEnabled(this, Channel.VIWOODS)) "Viwoods channel settings"
            else "Enable the Viwoods channel to use these"
        ) { startActivity(Intent(this, ViwoodsActivity::class.java)) }

        val t = Store.timing(this)
        col.row(
            "Gesture timing",
            "Multi-tap " + t.multiTapMs + " ms · hold " + t.holdMs +
                " ms · chord " + t.chordMs + " ms"
        ) { startActivity(Intent(this, TimingActivity::class.java)) }

        col.header("If you get stuck")
        col.note(
            "Power + Volume up opens the power menu, whatever else is bound. " +
                "Uninstalling the app restores every key to firmware defaults, " +
                "except the ones written through the Viwoods channel - reset " +
                "those from Firmware key hooks first."
        )
    }
}
