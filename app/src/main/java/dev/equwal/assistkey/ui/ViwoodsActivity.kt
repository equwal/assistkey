package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * Shows what the firmware itself has bound to each key.
 *
 * Read-only: Android does not let an ordinary app write these settings, however
 * many permissions it holds. The screen earns its place anyway, because a set
 * volume hook is the one thing that makes a key silently unmappable, and
 * nothing else on the device will tell you that is what happened.
 */
class ViwoodsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this, "Device button settings")
        col.note("Apps cannot change these. Run the command from a computer.")
        ViwoodsBridge.keys().forEach { keyBlock(col, it) }
    }

    private fun keyBlock(col: LinearLayout, key: HwKey) {
        col.header(key.label)
        col.row(
            "Device setting",
            ViwoodsBridge.describe(this, key),
            enabled = false,
            state = if (ViwoodsBridge.hidesFromFilter(this, key)) "Hiding" else "Clear"
        )

        if (key == HwKey.AI) {
            if (ViwoodsBridge.aiHookedToUs(this)) {
                col.note("Taps work; hold and combinations do not. To give the button back:")
                col.code(ViwoodsBridge.aiUnhookCommand())
            } else {
                col.note("The AI screen opens on every press. To stop it:")
                col.code(ViwoodsBridge.aiHookCommand(this))
                col.note("Then taps work, but hold and combinations stop.")
            }
            return
        }

        if (ViwoodsBridge.hidesFromFilter(this, key)) {
            col.note(
                "The device keeps " + key.label.lowercase() + " to itself. To clear it:"
            )
            ViwoodsBridge.unsetCommand(key)?.let { col.code(it) }
            col.note("The device's own button settings set it again.")
        }
    }
}
