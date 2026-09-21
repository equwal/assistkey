package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.NavNative
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.check
import dev.equwal.assistkey.ui.Ui.code
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title

/**
 * How you get around the reader: the button bar, swipe gestures, Power key
 * combinations, in any mix.
 *
 * The three are not equals. Power combinations are this app's own and are
 * switched here. The bar and the gestures belong to the system, and Android
 * lets no ordinary app switch either - so for those this screen works out what
 * has to change, shows where things stand now, and hands over the commands.
 */
class NavigationActivity : Activity() {

    private data class Setup(
        val name: String,
        val blurb: String,
        val buttons: Boolean,
        val gestures: Boolean,
        val keys: Boolean
    )

    private val setups = listOf(
        Setup("Buttons only", "Three-button bar at the bottom. No gestures at all.", true, false, false),
        Setup("Gestures only", "Swipes, no button bar.", false, true, false),
        Setup("Power key combos only", "No bar and no gestures - the whole screen is yours.", false, false, true),
        Setup("Power key combos + buttons", "Bar and keys, no gestures.", true, false, true),
        Setup("Power key combos + buttons + gestures", "Everything at once.", true, true, true)
    )

    private var redrawnForInsets = false

    override fun onResume() {
        super.onResume()
        build()
    }

    // ---- wanted state, remembered ------------------------------------------------

    private fun prefs() = getSharedPreferences("assistkey_nav", Context.MODE_PRIVATE)

    private fun wanted(name: String, fallback: Boolean): Boolean =
        if (prefs().contains(name)) prefs().getBoolean(name, fallback) else fallback

    private fun choose(buttons: Boolean, gestures: Boolean, keys: Boolean) {
        if (!buttons && !gestures && !keys) {
            Toast.makeText(this, "That would leave no way to navigate at all", Toast.LENGTH_LONG).show()
            build()
            return
        }
        prefs().edit()
            .putBoolean("buttons", buttons)
            .putBoolean("gestures", gestures)
            .putBoolean("keys", keys)
            .apply()
        applyKeys(keys)
        build()
    }

    // ---- the part this app owns -----------------------------------------------------

    private val defaults = listOf(
        HwKey.VOL_UP to GlobalAction.BACK,
        HwKey.AI to GlobalAction.HOME,
        HwKey.VOL_DOWN to GlobalAction.RECENTS
    )

    private fun keysOn(): Boolean = Store.bindings(this).hasPowerCombos

    /**
     * On: make sure the two channels that carry a Power combination are ticked,
     * and fill any empty slot with a navigation default. Slots the user has
     * already bound are theirs and are left alone. Off: clear the three slots.
     */
    private fun applyKeys(on: Boolean) {
        if (on) {
            Channels.setEnabled(this, Channel.ACCESSIBILITY, true)
            Channels.setEnabled(this, Channel.ASSISTANT, true)
            val b = Store.bindings(this)
            defaults.forEach { (key, action) ->
                val t = Trigger.powerThen(key)
                if (!b.isBound(t)) {
                    Store.bind(this, t, ActionSpec(ActionKind.GLOBAL, action.name, action.label))
                }
            }
        } else {
            HwKey.interceptable.forEach { Store.bind(this, Trigger.powerThen(it), ActionSpec.PASS) }
        }
    }

    // ---- screen ---------------------------------------------------------------------------

    private fun build() {
        val buttonsNow = NavNative.buttonsShowing(this)
        if (buttonsNow == null && !redrawnForInsets) {
            // Insets only exist once the window is attached.
            redrawnForInsets = true
            window.decorView.post { if (!isFinishing) build() }
        }
        val gesturesNow = NavNative.gesturesOn(this)
        val keysNow = keysOn()

        val wantButtons = wanted("buttons", buttonsNow ?: true)
        val wantGestures = wanted("gestures", gesturesNow ?: true)
        val wantKeys = wanted("keys", keysNow)

        val col = Ui.page(this)
        col.title("Navigation")
        col.note(
            "Three ways to get around, in any mix: the button bar, swipe " +
                "gestures, and Power key combinations."
        )

        col.header("Pick a setup")
        setups.forEach { s ->
            val current = s.buttons == wantButtons && s.gestures == wantGestures && s.keys == wantKeys
            col.row((if (current) "* " else "") + s.name, s.blurb) {
                choose(s.buttons, s.gestures, s.keys)
            }
        }

        col.header("Or mix your own")
        col.check("Button bar", "Back, Home and Recents along the bottom edge", wantButtons) {
            choose(it, wantGestures, wantKeys)
        }
        col.check(
            "Swipe gestures",
            "Swipe up from the bottom edge for Home; without the bar, swipe in " +
                "from a side edge for Back",
            wantGestures
        ) { choose(wantButtons, it, wantKeys) }
        col.check(
            "Power key combinations",
            "Hold Power, then press Volume up, the AI key or Volume down",
            wantKeys
        ) { choose(wantButtons, wantGestures, it) }

        if (wantKeys) keys(col)
        system(col, wantButtons, wantGestures, buttonsNow, gesturesNow)
    }

    private fun keys(col: LinearLayout) {
        col.header("Power key combinations")
        val b = Store.bindings(this)
        HwKey.interceptable.forEach { key ->
            val t = Trigger.powerThen(key)
            col.row("Power, then " + Ui.inSentence(key), b.raw(t).describe()) {
                startActivity(ActionPickerActivity.intent(this, t))
            }
        }

        val a11y = Channels.isSatisfied(this, Channel.ACCESSIBILITY)
        val assistant = Channels.isSatisfied(this, Channel.ASSISTANT)
        if (a11y && assistant) {
            col.note(
                "Ready. Hold Power for about half a second, then press the " +
                    "second key while still holding."
            )
        }
        if (!assistant) {
            col.note("Holding Power has to reach this app: make AssistKey the digital assistant.")
            col.button("Set up digital assistant") {
                Channels.safeStart(this, Channels.claimIntent(this, Channel.ASSISTANT))
            }
        }
        if (!a11y) {
            col.note(
                "The second key is read by the accessibility key filter, which is " +
                    "off. Turn it on from the main screen."
            )
        }
        if (!ViwoodsBridge.aiHookedToUs(this) && b.powerCombo(HwKey.AI) != null) {
            col.note(
                "Power, then the AI key will also open the firmware's AI screen " +
                    "until the AI key hook points at AssistKey. See Advanced > " +
                    "Firmware key hooks."
            )
        }
        HwKey.interceptable.filter { ViwoodsBridge.hidesFromFilter(this, it) }.forEach { k ->
            col.note(
                "A firmware hook is hiding " + k.label.lowercase() + " from this app, so its " +
                    "combination cannot fire. See Advanced > Firmware key hooks."
            )
        }
        col.note(
            "Back, Home and Recents on these combinations keep working even if " +
                "the app is locked, so a reader with no bar and no gestures can " +
                "never be left without a way out."
        )
    }

    private fun system(
        col: LinearLayout,
        wantButtons: Boolean,
        wantGestures: Boolean,
        buttonsNow: Boolean?,
        gesturesNow: Boolean?
    ) {
        col.header("Button bar and gestures")
        col.row("Button bar: " + now(buttonsNow, "showing", "hidden") + want(buttonsNow, wantButtons), null, enabled = false)
        col.row("Swipe-up gesture: " + now(gesturesNow, "on", "off") + want(gesturesNow, wantGestures), null, enabled = false)

        val settled = buttonsNow == wantButtons && gesturesNow == wantGestures
        if (settled) {
            col.note("The system already matches. Nothing more to do.")
            return
        }
        col.note(
            "Android does not let apps switch these, so they are set once from a " +
                "computer with the reader plugged in and USB debugging on. They " +
                "survive restarts. Run them in this order:"
        )
        col.code(NavNative.commands(wantButtons, wantGestures).joinToString("\n"))
        col.note(
            "The repository has tools/nav-mode, which runs the same commands: " +
                "nav-mode " + (if (wantButtons) "buttons" else "nobuttons") + " " +
                (if (wantGestures) "gestures" else "nogestures")
        )
        if (!wantButtons && !wantGestures) {
            col.note(
                "With no bar and no gestures, set up and try the Power " +
                    "combinations before running these. Power + Volume up still " +
                    "opens the power menu whatever happens."
            )
        }
    }

    private fun now(v: Boolean?, yes: String, no: String): String = when (v) {
        true -> yes
        false -> no
        null -> "unknown"
    }

    private fun want(now: Boolean?, wanted: Boolean): String =
        if (now == wanted) "" else "  -  you want it " + (if (wanted) "on" else "off")
}
