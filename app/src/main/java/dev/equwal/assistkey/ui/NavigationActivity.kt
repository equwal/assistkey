package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.NavNative
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.more
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * How you get around: the button bar, swipe gestures, the Power key - any mix.
 *
 * One screen, one decision. Tick what you want and it is applied: the Power
 * key part always by the app, the bar and the gestures through shell access
 * when there is any. Only without shell access does this fall back to handing
 * over commands for a computer.
 */
class NavigationActivity : Activity() {

    private data class Setup(val name: String, val buttons: Boolean, val gestures: Boolean, val keys: Boolean)

    private val setups = listOf(
        Setup("Buttons only", true, false, false),
        Setup("Gestures only", false, true, false),
        Setup("Power key only", false, false, true),
        Setup("Power key + buttons", true, false, true),
        Setup("Power key + gestures", false, true, true),
        Setup("Buttons + gestures", true, true, false),
        Setup("Power key + buttons + gestures", true, true, true)
    )

    private var redrawnForInsets = false
    private var applying = false
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

    // ---- what is wanted -------------------------------------------------------------------

    private fun prefs() = getSharedPreferences("assistkey_nav", Context.MODE_PRIVATE)

    private fun wanted(name: String, fallback: Boolean): Boolean =
        if (prefs().contains(name)) prefs().getBoolean(name, fallback) else fallback

    private fun choose(buttons: Boolean, gestures: Boolean, keys: Boolean) {
        if (!buttons && !gestures && !keys) {
            Toast.makeText(this, "That would leave no way to navigate at all", Toast.LENGTH_LONG).show()
            build()
            return
        }
        prefs().edit().putBoolean("buttons", buttons).putBoolean("gestures", gestures)
            .putBoolean("keys", keys).apply()
        applyKeys(keys)
        if (Shell.ready) applySystem(buttons, gestures) else build()
    }

    // ---- the Power key ---------------------------------------------------------------------

    private val power = setOf(HwKey.POWER)

    /** Tap back, hold home, double tap recents; triple tap keeps a way to lock. */
    private val defaults = listOf(
        Trigger(power, GestureType.TAP, 1) to GlobalAction.BACK,
        Trigger(power, GestureType.HOLD) to GlobalAction.HOME_CLOSE_IME,
        Trigger(power, GestureType.TAP, 2) to GlobalAction.RECENTS,
        Trigger(power, GestureType.TAP, 3) to GlobalAction.LOCK_SCREEN
    )

    private fun keysOn(): Boolean =
        Store.bindings(this).all().keys.any { it.keys == power }

    /**
     * On fills empty slots with the defaults and leaves anything already bound
     * alone. Off clears exactly the slots that still hold a default, so a
     * binding the user made by hand survives being toggled.
     */
    private fun applyKeys(on: Boolean) {
        val b = Store.bindings(this)
        defaults.forEach { (t, action) ->
            val spec = ActionSpec(ActionKind.GLOBAL, action.name, action.label)
            if (on && !b.isBound(t)) Store.bind(this, t, spec)
            if (!on && b[t] == spec) Store.bind(this, t, ActionSpec.PASS)
        }
        if (on) {
            Channels.setEnabled(this, Channel.ACCESSIBILITY, true)
            PowerControl.setWanted(this, true)
            // The side doors, for whenever shell access is not there.
            Channels.setEnabled(this, Channel.ASSISTANT, true)
        }
        (ServiceHolder.service as? KeyFilterService)?.syncPower()
    }

    // ---- the bar and the gestures ----------------------------------------------------------

    private fun applySystem(buttons: Boolean, gestures: Boolean) {
        applying = true
        build()
        Shell.runAll(NavNative.shellCommands(buttons, gestures)) { ok ->
            applying = false
            if (!ok) Toast.makeText(this, "The system refused part of that", Toast.LENGTH_LONG).show()
            // The bar comes and goes with a relayout; give the insets a moment.
            window.decorView.postDelayed({ if (!isFinishing) build() }, 1200L)
        }
    }

    // ---- screen -----------------------------------------------------------------------------

    private fun build() {
        val buttonsNow = NavNative.buttonsShowing(this)
        if (buttonsNow == null && !redrawnForInsets) {
            redrawnForInsets = true
            window.decorView.post { if (!isFinishing) build() } // insets need an attached window
        }
        // Stock Android has no separate gesture switch: gestures are on exactly when the bar is off.
        val gesturesNow = NavNative.gesturesOn(this) ?: buttonsNow?.not()

        val wantButtons = wanted("buttons", buttonsNow ?: true)
        val wantGestures = wanted("gestures", gesturesNow ?: true)
        val wantKeys = wanted("keys", keysOn())

        val col = Ui.page(this, "Navigation")
        col.note("The button bar, swipe gestures and the Power key, in any mix.")

        col.check("Button bar", "Back, Home and Recents along the bottom edge", wantButtons) {
            choose(it, wantGestures, wantKeys)
        }
        col.check("Swipe gestures", "Swipe up for Home; without the bar, swipe in from a side for Back", wantGestures) {
            choose(wantButtons, it, wantKeys)
        }
        col.check(
            "Power key",
            if (Shell.SUPPORTED) "Tap for Back, hold for Home, double tap for Recents"
            else "Hold for Home, double press for Recents",
            wantKeys
        ) {
            choose(wantButtons, wantGestures, it)
        }

        col.header("Or pick a setup")
        val labels = setups.map { s ->
            (if (s.buttons == wantButtons && s.gestures == wantGestures && s.keys == wantKeys) "* " else "   ") + s.name
        }
        col.button("Choose...") {
            Ui.pick(this, "Navigation setup", labels) { i ->
                setups[i].let { choose(it.buttons, it.gestures, it.keys) }
            }
        }

        if (wantKeys) powerKey(col)
        system(col, wantButtons, wantGestures, buttonsNow, gesturesNow)
    }

    private fun powerKey(col: LinearLayout) {
        col.header("Power key")
        val b = Store.bindings(this)
        val direct = Shell.ready && PowerControl.wanted(this)
        defaults.forEach { (t, _) ->
            val label = when {
                t.type == GestureType.HOLD -> "Hold"
                t.count == 1 -> "Tap"
                t.count == 2 -> "Double tap"
                else -> "Triple tap"
            }
            // Without shell access only hold and double press have a way in.
            val reachable = direct || t == Channel.POWER_HOLD || t == Channel.POWER_DOUBLE
            if (!reachable && !Shell.SUPPORTED) return@forEach
            col.row(label + ": " + b.raw(t).describe(), if (reachable) null else "Needs shell access", reachable) {
                startActivity(ActionPickerActivity.intent(this, t))
            }
        }
        col.row(
            if (Shell.SUPPORTED) "More Power button gestures" else "Set up the Power button",
            if (Shell.SUPPORTED) "More taps, combinations with other keys, and how it works"
            else "Make AssistKey the assistant for hold, and the camera app for double press"
        ) {
            startActivity(Intent(this, PowerActivity::class.java))
        }
        if (!Channels.isSatisfied(this, Channel.ACCESSIBILITY)) {
            col.row("Key remapping is off", "Back and Home cannot fire yet", state = "Off") {
                startActivity(Intent(this, SetupActivity::class.java))
            }
        }
        if (!direct) {
            col.note(
                (if (Shell.SUPPORTED) "Without shell access a tap cannot reach any app. "
                else "A tap cannot reach any app. ") +
                    "Hold and double press still work."
            )
            col.more("The Power key as navigation", POWER_KEY_ABOUT)
        }
    }

    private fun system(
        col: LinearLayout,
        wantButtons: Boolean,
        wantGestures: Boolean,
        buttonsNow: Boolean?,
        gesturesNow: Boolean?
    ) {
        col.header("Button bar and gestures")
        col.row("Button bar", null, enabled = false, state = now(buttonsNow, "Showing", "Hidden"))
        col.row("Swipe-up gesture", null, enabled = false, state = now(gesturesNow, "On", "Off"))

        if (applying) {
            col.note("Applying... the screen may redraw once.")
            return
        }
        if (buttonsNow == wantButtons && gesturesNow == wantGestures) return

        if (Shell.ready) {
            col.button("Apply") { applySystem(wantButtons, wantGestures) }
            return
        }
        if (Shell.SUPPORTED) {
            col.note("Android does not let apps switch these. Shell access lets AssistKey do it from here:")
            col.row(
                "Shell access",
                "Set it up once, on the device, with no computer",
                state = Shell.describe(this)
            ) { startActivity(Intent(this, ShellActivity::class.java)) }
            col.note("Or, with a computer and USB debugging:")
        } else {
            col.note(
                "Android does not let apps switch these. Do it once from a computer, " +
                    "with the device plugged in and USB debugging on. It survives restarts."
            )
        }
        col.code(NavNative.commands(wantButtons, wantGestures).joinToString("\n"))
    }

    private fun now(v: Boolean?, yes: String, no: String): String = when (v) {
        true -> yes
        false -> no
        null -> "Unknown"
    }

    private companion object {
        const val POWER_KEY_ABOUT =
            "The window manager takes the Power key before any app can see it, " +
                "so a single tap cannot reach AssistKey.\n\n" +
                "Press and hold arrives as an assistant request, so it works " +
                "once AssistKey is the digital assistant.\n\n" +
                "Double press arrives as a camera launch, so it works once " +
                "AssistKey is the default camera app.\n\n" +
                "Both are set up on the Power button screen.\n\n" +
                "Back, Home and Recents on a Power gesture keep working when " +
                "the app is locked, so a reader with no bar and no gestures is " +
                "never left without a way to navigate."
    }
}
