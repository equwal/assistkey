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
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

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
        val col = Ui.page(this)
        col.title(keys.sortedBy { it.ordinal }.joinToString(" + ") { it.label })

        if (!Channels.isEnabled(this, Channel.ACCESSIBILITY) ||
            !Channels.isSatisfied(this, Channel.ACCESSIBILITY)
        ) {
            col.note(
                "The accessibility key filter is not active, so nothing here " +
                    "will fire yet. Turn it on from the main screen."
            )
        }

        val b = Store.bindings(this)
        col.header("Taps")
        (1..Trigger.MAX_TAPS).forEach { n ->
            val t = Trigger(keys, GestureType.TAP, n)
            col.row(tapLabel(n), b.raw(t).describe()) { edit(t) }
        }

        col.header("Hold")
        val hold = Trigger(keys, GestureType.HOLD)
        col.row("Press and hold", b.raw(hold).describe()) { edit(hold) }

        col.header("Note")
        col.note(
            "Binding a double tap adds a short delay to the single tap, " +
                "because the app has to wait and see. Leave the higher tap " +
                "counts on default behaviour if you want the key to feel instant."
        )
    }

    private fun tapLabel(n: Int) = when (n) {
        1 -> "Single tap"
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
