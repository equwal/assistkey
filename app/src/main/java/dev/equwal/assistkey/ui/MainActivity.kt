package dev.equwal.assistkey.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.PowerNative
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * The whole configuration surface.
 *
 * Rebuilt in onResume rather than onCreate, because almost every setup step
 * happens in another app - Settings, a role dialog - and the user comes back
 * expecting the status lines to have caught up.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.syncComponents(this)
    }

    override fun onResume() {
        super.onResume()
        build()
        // Play answers asynchronously; redraw only if the answer changed the tier.
        val before = License.state(this).tier
        PlayBilling.refresh(this) {
            if (License.state(this).tier != before && !isFinishing) build()
        }
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("AssistKey")
        col.note(
            "Remaps the AI key, both volume keys and the power button. " +
                "Turn on the capture channels you want, then bind gestures below."
        )
        licence(col)
        channels(col)
        buttons(col)
        extras(col)
    }

    // ---- licence -----------------------------------------------------------

    private fun licence(col: LinearLayout) {
        val s = License.state(this)
        val (title, subtitle) = when (s.tier) {
            License.Tier.LICENSED -> "Unlocked" to "Thank you for buying AssistKey"
            License.Tier.BETA ->
                "Beta - free for now" to "Everything works while the beta is open"
            License.Tier.TRIAL ->
                ("Trial - " + s.trialDaysLeft + (if (s.trialDaysLeft == 1) " day" else " days") +
                    " left") to "Tap to buy a licence"
            License.Tier.LOCKED ->
                "Locked - remapping is off" to "Tap to unlock. Your bindings are kept."
        }
        col.row(title, subtitle) { startActivity(Intent(this, LicenseActivity::class.java)) }
    }

    // ---- channels ----------------------------------------------------------

    private fun channels(col: LinearLayout) {
        col.header("Capture channels")
        col.note(
            "Each one is a different way of getting a key routed to this app. " +
                "They are independent - tick any combination."
        )

        Channel.entries.forEach { ch ->
            val on = Channels.isEnabled(this, ch)
            val ok = Channels.isSatisfied(this, ch)
            col.check(ch.title, ch.summary, on) { checked ->
                Channels.setEnabled(this, ch, checked)
                if (checked && !Channels.isSatisfied(this, ch)) claim(ch)
                build()
            }
            col.row("Status: " + Channels.status(this, ch), null, enabled = on)
            if (on && !ok) {
                col.button("Set up " + ch.title.lowercase()) { claim(ch) }
                if (ch == Channel.ACCESSIBILITY) restrictedSettingsHint(col)
            }
        }
    }

    /**
     * Sideloaded apps cannot be given accessibility access until the user
     * clears Android's restricted-settings block, and the symptom is that the
     * toggle appears to work and then quietly reverts. Nothing can detect this,
     * so it gets called out wherever it would bite.
     */
    private fun restrictedSettingsHint(col: LinearLayout) {
        col.note(
            "If the switch turns itself back off, Android is blocking it " +
                "because this app was installed outside an app store. Open " +
                "App info, tap the three-dot menu, choose Allow restricted " +
                "settings, then try again."
        )
        col.button("Open App info") {
            Channels.safeStart(this, Channels.appInfoIntent(this))
        }
    }

    /**
     * Google Play requires that an app using the accessibility API for anything
     * other than assistive technology says so, in the app, in plain words, and
     * gets a yes before sending anyone to the switch. It is also simply the
     * right thing to do with a permission this broad.
     */
    private fun claim(ch: Channel) {
        if (ch != Channel.ACCESSIBILITY) return claimNow(ch)
        AlertDialog.Builder(this)
            .setTitle("Accessibility service")
            .setMessage(
                "AssistKey uses Android's AccessibilityService API for one purpose: " +
                    "remapping this device's hardware keys.\n\n" +
                    "With the service switched on, AssistKey:\n\n" +
                    "- receives presses of the AI key and the volume keys, so that it " +
                    "can recognise taps, holds and combinations;\n\n" +
                    "- performs the action you chose - Back, Home, Recents, a swipe, " +
                    "a scroll - on your behalf;\n\n" +
                    "- looks at the window in front only to find its scrollable area, " +
                    "and only when you use the Scroll action.\n\n" +
                    "It does not record what you type or what is on your screen. It " +
                    "collects nothing, stores nothing and sends nothing: the app has no " +
                    "internet permission.\n\n" +
                    "Agree to continue to Android's accessibility settings."
            )
            .setPositiveButton("Agree") { _, _ -> claimNow(ch) }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun claimNow(ch: Channel) {
        if (ch == Channel.CAMERA) {
            Toast.makeText(
                this,
                "Choose Camera app, then pick AssistKey",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!Channels.safeStart(this, Channels.claimIntent(this, ch))) {
            Toast.makeText(this, "No settings screen for that on this firmware", Toast.LENGTH_LONG)
                .show()
        }
    }

    // ---- bindings ----------------------------------------------------------

    private fun buttons(col: LinearLayout) {
        col.header("Buttons")

        listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN).forEach { key ->
            val summary =
                if (ViwoodsBridge.hidesFromFilter(this, key)) {
                    "Hidden by a firmware hook - see Firmware key hooks below"
                } else {
                    boundSummary(setOf(key))
                }
            col.row(key.label, summary) {
                startActivity(TriggerListActivity.intent(this, setOf(key)))
            }
        }

        col.row("Power", powerSummary()) { startActivity(Intent(this, PowerActivity::class.java)) }

        col.row(
            "Navigation",
            "Button bar, swipe gestures, Power key combinations - in any mix"
        ) { startActivity(Intent(this, NavigationActivity::class.java)) }

        col.row(
            "Two-key combinations",
            "Hold one key and press another - any pair or larger set"
        ) { startActivity(Intent(this, ChordActivity::class.java)) }
    }

    private fun boundSummary(keys: Set<HwKey>): String {
        val b = Store.bindings(this)
        val bound = Trigger.allFor(keys).filter { b.isBound(it) }
        if (bound.isEmpty()) return "Default behaviour"
        return bound.joinToString(", ") { t ->
            shortGesture(t) + " → " + b.raw(t).describe()
        }
    }

    private fun shortGesture(t: Trigger): String = when {
        t.type == GestureType.HOLD -> "hold"
        t.count == 1 -> "tap"
        t.count == 2 -> "double"
        t.count == 3 -> "triple"
        else -> t.count.toString() + " taps"
    }

    private fun powerSummary(): String {
        val b = Store.bindings(this)
        val parts = ArrayList<String>()
        PowerNative.shortPressValue(this)?.let { v ->
            PowerNative.shortPress.firstOrNull { it.value == v }
                ?.let { parts.add("short → " + it.label.lowercase()) }
        }
        b[Channel.POWER_DOUBLE]?.let { parts.add("double → " + it.describe()) }
        b[Channel.POWER_HOLD]?.let { parts.add("hold → " + it.describe()) }
        return if (parts.isEmpty()) "Default behaviour" else parts.joinToString(", ")
    }

    // ---- everything else ---------------------------------------------------

    private fun extras(col: LinearLayout) {
        col.header("Advanced")

        val hidden = ViwoodsBridge.keys().filter { ViwoodsBridge.hidesFromFilter(this, it) }
        col.row(
            "Firmware key hooks",
            if (hidden.isEmpty()) "What the firmware itself does with each key"
            else "Hiding " + hidden.joinToString(" and ") { it.label.lowercase() } +
                " from this app - tap for the fix"
        ) { startActivity(Intent(this, ViwoodsActivity::class.java)) }

        col.row(
            "Key tester",
            "See exactly which keys reach the filter on this device"
        ) { startActivity(Intent(this, KeyTesterActivity::class.java)) }

        val t = Store.timing(this)
        col.row(
            "Gesture timing",
            "Multi-tap " + t.multiTapMs + " ms · hold " + t.holdMs +
                " ms · chord " + t.chordMs + " ms"
        ) { startActivity(Intent(this, TimingActivity::class.java)) }

        col.header("If you get stuck")
        col.note(
            "Power + Volume up opens the power menu, whatever else is bound. " +
                "Uninstalling the app restores every key to its firmware " +
                "behaviour; nothing it does outlives it."
        )
    }
}
