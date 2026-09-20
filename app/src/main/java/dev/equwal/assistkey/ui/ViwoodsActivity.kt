package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

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
        val col = Ui.page(this)
        col.title("Firmware key hooks")
        col.note(
            "The firmware has its own setting for each key. Android does not allow " +
                "apps to change these, so they are shown here for information, " +
                "with the adb command where one is needed."
        )
        ViwoodsBridge.keys().forEach { keyBlock(col, it) }
    }

    private fun keyBlock(col: LinearLayout, key: HwKey) {
        col.header(key.label)
        col.row("Firmware setting: " + ViwoodsBridge.describe(this, key), null, enabled = false)

        if (key == HwKey.AI) {
            col.note(
                "AssistKey sees the AI key whatever this says. The setting only " +
                    "decides what opens when a press is not bound to anything."
            )
            return
        }

        if (ViwoodsBridge.hidesFromFilter(this, key)) {
            col.note(
                "While this is set, the firmware keeps " + key.label.lowercase() +
                    " to itself and no app can see it - bindings made for it in " +
                    "AssistKey will not fire. Clear it once from a computer:"
            )
            ViwoodsBridge.unsetCommand(key)?.let { col.code(it) }
            col.note(
                "Changing this key in the device's own key settings sets it again."
            )
        } else {
            col.note("Not set, so AssistKey can see this key.")
        }
    }
}
