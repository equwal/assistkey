package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.display.ExtraDim
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/** The frontlight, below where the system's own slider stops. */
class DisplayActivity : Activity() {

    private val redraw: () -> Unit = { if (!isFinishing) build() }

    override fun onResume() {
        super.onResume()
        Shell.onChange(redraw)
        Shell.connect(this)
        build()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeOnChange(redraw)
    }

    private fun build() {
        val col = Ui.page(this, "Extra-dim light")
        col.note("Below the lowest the system allows.")

        if (!Shell.ready) {
            col.row(
                "Shell access",
                "Needed for this",
                state = Shell.describe(this)
            ) { startActivity(Intent(this, ShellActivity::class.java)) }
            return
        }

        val current = ExtraDim.level(this)
        col.header("Level")
        col.row(
            "Off",
            "The system is in charge",
            state = if (current == 0) "In use" else null
        ) { choose(0) }
        ExtraDim.levels().forEach { v ->
            col.row(v.toString() + " of 255", null, state = if (current == v) "In use" else null) {
                choose(v)
            }
        }
        if (Device.brightnessFloor == null) {
            col.note("These values are a guess on this device.")
        }
    }

    private fun choose(level: Int) {
        ExtraDim.set(this, level) { ok ->
            if (!ok) {
                Toast.makeText(
                    this,
                    "The light needs Shizuku started as root",
                    Toast.LENGTH_LONG
                ).show()
                ExtraDim.set(this, 0) {}
            }
            build()
        }
    }
}
