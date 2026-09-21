package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.row

/**
 * One row for each key this device has, and a way to build a combination.
 *
 * This is the hardware view of the app, for people who want to set a key at a
 * time. The guided flow reaches the same bindings from the other end.
 */
class KeysActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Keys")

        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.row("Key remapping is off", "Nothing bound here can fire yet", state = "Off") {
                startActivity(Intent(this, SetupActivity::class.java))
            }
        }

        col.header("Keys on this device")
        Device.keys(this).forEach { key -> keyRow(col, key) }

        col.row("Power", Summary.power(powerBindings())) {
            startActivity(Intent(this, PowerActivity::class.java))
        }

        col.header("Together")
        col.row(
            "Two-key combinations",
            "Hold one key and press another - any pair or larger set"
        ) { startActivity(Intent(this, ChordActivity::class.java)) }

        col.more("Keys", ABOUT)
    }

    private fun keyRow(col: LinearLayout, key: HwKey) {
        val hidden = ViwoodsBridge.hidesFromFilter(this, key)
        col.row(
            key.label,
            if (hidden) "A firmware hook is hiding this key - see Firmware key hooks"
            else boundSummary(setOf(key)),
            state = if (hidden) "Hidden" else null
        ) { startActivity(TriggerListActivity.intent(this, setOf(key))) }
    }

    private fun boundSummary(keys: Set<HwKey>): String {
        val b = Store.bindings(this)
        val bound = Trigger.allFor(keys).filter { b.isBound(it) }
        if (bound.isEmpty()) return "Default behaviour"
        return bound.joinToString(", ") { t -> shortGesture(t) + " → " + b.raw(t).describe() }
    }

    private fun shortGesture(t: Trigger): String = when {
        t.type == GestureType.HOLD -> "hold"
        t.count == 1 -> "tap"
        t.count == 2 -> "double"
        t.count == 3 -> "triple"
        else -> t.count.toString() + " taps"
    }

    private fun powerBindings(): Int =
        Store.bindings(this).all().keys.count { HwKey.POWER in it.keys }

    private companion object {
        const val ABOUT =
            "Each key has the same set of gestures: one to five taps, and press " +
                "and hold. Any of them can be bound on its own.\n\n" +
                "A key with no bindings is never taken from the system. A key " +
                "whose highest bound tap count is one fires as soon as it is " +
                "released, so you only pay the multi-tap wait on keys where you " +
                "asked for a double tap.\n\n" +
                "The Power button is not like the others and has a screen of its " +
                "own."
    }
}
