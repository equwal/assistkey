package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/** Every gesture available on one key or chord, with what it currently does. */
class TriggerListActivity : Activity() {

    private lateinit var keys: Set<HwKey>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keys = parse(intent.getStringExtra(EXTRA_KEYS))
        if (keys.isEmpty()) { finish(); return }
    }

    override fun onResume() {
        super.onResume()
        if (keys.isEmpty()) return
        build()
    }

    private fun build() {
        val col = Ui.page(this, keys.sortedBy { it.ordinal }.joinToString(" + ") { it.label })

        if (!Channels.isEnabled(this, Channel.ACCESSIBILITY) ||
            !Channels.isSatisfied(this, Channel.ACCESSIBILITY)
        ) {
            col.row(
                "Button remapping",
                "Nothing here can fire",
                state = "Off"
            ) { startActivity(Intent(this, SetupActivity::class.java)) }
        }

        keys.filter { ViwoodsBridge.hidesFromFilter(this, it) }.forEach { k ->
            col.note(
                "The device keeps " + k.label.lowercase() +
                    " to itself - see Device button settings."
            )
        }

        if (keys == setOf(HwKey.AI) && dev.equwal.assistkey.device.Device.isViwoods) {
            col.check(
                "In the AI or crop screen, go back",
                null,
                dev.equwal.assistkey.device.Device.aiKeyReturns(this)
            ) { dev.equwal.assistkey.device.Device.setAiKeyReturns(this, it) }
        }
        if (HwKey.AI in keys) {
            col.note(
                if (ViwoodsBridge.aiHookedToUs(this)) {
                    "Only taps of the AI key on its own can fire."
                } else {
                    "The AI screen also opens - see Device button settings."
                }
            )
        }

        val b = Store.bindings(this)
        (1..Trigger.MAX_TAPS).forEach { n ->
            val t = Trigger(keys, GestureType.TAP, n)
            col.row(tapLabel(n), b.raw(t).describe()) { edit(t) }
        }

        val hold = Trigger(keys, GestureType.HOLD)
        col.row("Hold", b.raw(hold).describe()) { edit(hold) }

    }

    private fun tapLabel(n: Int) = when (n) {
        1 -> "Tap"
        2 -> "Double tap"
        3 -> "Triple tap"
        else -> n.toString() + " taps"
    }

    private fun edit(t: Trigger) = startActivity(ActionPickerActivity.intent(this, t))

    companion object {
        private const val EXTRA_KEYS = "keys"

        fun intent(c: Context, keys: Set<HwKey>): Intent =
            Intent(c, TriggerListActivity::class.java).putExtra(
                EXTRA_KEYS, keys.sortedBy { it.ordinal }.joinToString("+") { it.token }
            )

        private fun parse(s: String?): Set<HwKey> =
            s?.split("+")?.mapNotNull { HwKey.fromToken(it) }?.toSet() ?: emptySet()
    }
}
