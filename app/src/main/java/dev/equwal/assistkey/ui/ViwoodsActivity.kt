package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Presets
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * The firmware's own key settings, edited directly.
 *
 * Worth having even though the accessibility channel is more capable: this one
 * survives the accessibility service being killed, works on the lock screen,
 * and costs nothing in latency. It just cannot do more than one action per key.
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
            "These are the same settings the stock key screen writes. Changing " +
                "them here affects the whole system, not just this app, and they " +
                "survive uninstalling it - reset them before you remove the app."
        )

        if (!ViwoodsBridge.canWrite(this)) {
            col.note("This needs permission to modify system settings.")
            col.button("Grant permission") {
                Channels.safeStart(this, Channels.claimIntent(this, Channel.VIWOODS))
            }
            return
        }

        ViwoodsBridge.keys().forEach { key -> keyBlock(col, key) }

        col.header("Reset")
        col.button("Restore all three keys to factory") {
            var ok = true
            ViwoodsBridge.keys().forEach { if (!ViwoodsBridge.restore(this, it)) ok = false }
            toast(if (ok) "Restored" else "Some keys could not be written")
            build()
        }
    }

    private fun keyBlock(col: LinearLayout, key: HwKey) {
        col.header(key.label)
        col.row("Currently: " + ViwoodsBridge.describe(this, key), null, enabled = false)

        col.row(
            "Send to AssistKey",
            "Routes this key into the gesture engine without the accessibility service"
        ) {
            toast(if (ViwoodsBridge.bindToUs(this, key)) "Bound" else "Write refused")
            build()
        }

        col.row("Viwoods AI target", "Pick one of the built-in AI screens") {
            Ui.pick(this, key.label, Presets.viwoods.map { it.label }) { i ->
                val p = Presets.viwoods[i]
                toast(
                    if (ViwoodsBridge.bindComponent(this, key, p.component)) "Bound"
                    else "Write refused"
                )
                build()
            }
        }

        if (key != HwKey.AI) {
            col.row("Built-in behaviour", "Volume, screenshot, or open an app") {
                Ui.pick(this, key.label, ViwoodsBridge.volumeTokens.map { it.second }) { i ->
                    val token = ViwoodsBridge.volumeTokens[i].first
                    if (token == ViwoodsBridge.TOKEN_APP) pickApp(key) else {
                        toast(
                            if (ViwoodsBridge.bindToken(this, key, token)) "Bound"
                            else "Write refused"
                        )
                        build()
                    }
                }
            }
        }

        col.row("Open an app", null) { pickApp(key) }
        col.row("Restore factory value", null) {
            toast(if (ViwoodsBridge.restore(this, key)) "Restored" else "Write refused")
            build()
        }
    }

    private fun pickApp(key: HwKey) {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map {
                it.loadLabel(pm).toString() to
                    (it.activityInfo.packageName + "/" + it.activityInfo.name)
            }
            .sortedBy { it.first.lowercase() }

        if (apps.isEmpty()) { toast("No launchable apps visible"); return }

        Ui.pick(this, "Open an app", apps.map { it.first }) { i ->
            toast(
                if (ViwoodsBridge.bindComponent(this, key, apps[i].second)) "Bound"
                else "Write refused"
            )
            build()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
