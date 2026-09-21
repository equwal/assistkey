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
import dev.equwal.assistkey.ui.Ui.title

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
        val col = Ui.page(this)
        col.title("Extra-dim light")
        col.note(
            "The system will not set the light below a fixed floor" +
                (Device.brightnessFloor?.let { " (" + it + " of 255 on this device)" } ?: "") +
                ", though the hardware goes lower. These levels sit under that floor, " +
                "for reading in the dark."
        )

        if (!Shell.ready) {
            col.row("Shell access: " + Shell.describe(this), "Needed to reach the light directly") {
                startActivity(Intent(this, ShellActivity::class.java))
            }
            return
        }

        val current = ExtraDim.level(this)
        col.header("Level")
        col.row((if (current == 0) "* " else "   ") + "Off", "The system slider is in charge") { choose(0) }
        ExtraDim.levels().forEach { v ->
            col.row((if (current == v) "* " else "   ") + v + " of 255", null) { choose(v) }
        }
        col.note(
            "The level holds until the system's brightness slider is moved, and is " +
                "put back whenever the screen comes on. If this device does not let " +
                "the shell reach the light, choosing a level will say so."
        )
        col.note("To change the level from a key, bind a Light action: Extra dim toggle, darker or brighter. The toggle goes to the lowest level, and back to your system brightness.")
        if (Device.brightnessFloor == null) {
            col.note("This device's floor has not been measured, so these values are a guess.")
        }
    }

    private fun choose(level: Int) {
        ExtraDim.set(this, level) { ok ->
            if (!ok) {
                Toast.makeText(
                    this,
                    "This device would not let the shell change the light. It needs " +
                        "Shizuku started as root, or firmware that allows su.",
                    Toast.LENGTH_LONG
                ).show()
                ExtraDim.set(this, 0) {}
            }
            build()
        }
    }
}
