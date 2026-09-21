package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import dev.equwal.assistkey.engine.GestureEngine
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * The three windows the gesture engine runs on. Exposed because the right
 * values are a matter of the user's hands, and because an e-ink device is slow
 * enough that the defaults can feel wrong either way.
 */
class TimingActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Gesture timing")
        val cfg = Store.timing(this)

        section(
            col,
            "Multi-tap window",
            cfg.multiTapMs,
            listOf(180L, 220L, 280L, 350L, 450L),
            "How long to wait for another tap. Longer is easier to hit, but " +
                "adds that much delay to every single tap on a key that has a " +
                "double tap bound."
        ) { Store.setTiming(this, cfg.copy(multiTapMs = it)) }

        section(
            col,
            "Hold threshold",
            cfg.holdMs,
            listOf(300L, 400L, 450L, 600L, 800L),
            "How long a key must stay down before it counts as a hold."
        ) { Store.setTiming(this, cfg.copy(holdMs = it)) }

        section(
            col,
            "Combination window",
            cfg.chordMs,
            listOf(80L, 120L, 140L, 200L, 300L),
            "How close together two keys must go down to count as a combination."
        ) { Store.setTiming(this, cfg.copy(chordMs = it)) }

        col.header("Defaults")
        val d = GestureEngine.Config()
        col.row(
            "Restore " + d.multiTapMs + " / " + d.holdMs + " / " + d.chordMs + " ms",
            null
        ) { Store.setTiming(this, d); push(); build() }
    }

    private fun section(
        col: LinearLayout,
        title: String,
        current: Long,
        options: List<Long>,
        blurb: String,
        onPick: (Long) -> Unit
    ) {
        col.header(title)
        col.note(blurb)
        col.row(title, "Tap to change", state = current.toString() + " ms") {
            val labels = options.map { v ->
                (if (v == current) "* " else "   ") + v + " ms"
            }
            Ui.pick(this, title, labels) { i -> onPick(options[i]); push(); build() }
        }
    }

    /**
     * The accessibility service and this screen share a process, so the running
     * engine can simply be handed the new numbers instead of being restarted.
     */
    private fun push() {
        (ServiceHolder.service as? dev.equwal.assistkey.engine.KeyFilterService)
            ?.reloadTiming()
    }
}
