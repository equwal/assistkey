package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row

/**
 * Builds a key set, then hands it to the ordinary gesture list.
 *
 * Power is absent on purpose: it never reaches the input filter, so it can
 * never be half of a chord no matter what the other key is.
 */
class ChordActivity : Activity() {

    private val picked = linkedSetOf<HwKey>()

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Key combinations")
        col.note("Hold one key and press the others within the chord window.")

        col.header("Pick the keys")
        HwKey.entries.filter { it.interceptable }.forEach { key ->
            col.check(key.label, null, key in picked) { on ->
                if (on) picked.add(key) else picked.remove(key)
                build()
            }
        }

        if (picked.size >= 2) {
            col.primaryButton("Configure " + picked.joinToString(" + ") { it.label }) {
                startActivity(TriggerListActivity.intent(this, picked.toSet()))
            }
        } else {
            col.note("Choose at least two keys.")
        }

        existing(col)

        col.more("Key combinations", ABOUT)
    }

    /** Anything already bound, so a chord is easy to find again. */
    private fun existing(col: LinearLayout) {
        val sets = Store.bindings(this).all().keys
            .filter { it.isChord && dev.equwal.assistkey.model.HwKey.POWER !in it.keys }
            .map { it.keys }
            .distinct()
        if (sets.isEmpty()) return

        col.header("Already configured")
        sets.forEach { keys ->
            val n = Trigger.allFor(keys).count { Store.bindings(this).isBound(it) }
            col.row(
                keys.sortedBy { it.ordinal }.joinToString(" + ") { it.label },
                n.toString() + (if (n == 1) " gesture bound" else " gestures bound")
            ) { startActivity(TriggerListActivity.intent(this, keys)) }
        }
    }

    private companion object {
        const val ABOUT =
            "Every gesture that works on a single key works on a combination " +
                "too, including multi-tap and hold.\n\n" +
                "Power is absent on purpose. The system takes the Power key " +
                "before any app can see it, so it can never be half of an " +
                "ordinary combination.\n\n" +
                "Power + Volume up is reserved by the firmware for the power " +
                "menu, and AssistKey leaves it alone as a way out."
    }
}
