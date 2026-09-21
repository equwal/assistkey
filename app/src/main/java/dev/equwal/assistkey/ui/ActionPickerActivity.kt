package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.Presets
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row

/**
 * Everything a gesture can be bound to, in one list.
 *
 * The Viwoods section is the point of parity with the stock key settings; the
 * rest is what the stock screen does not offer.
 */
class ActionPickerActivity : Activity() {

    private lateinit var trigger: Trigger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trigger = Trigger.parse(intent.getStringExtra(EXTRA_TRIGGER).orEmpty())
            ?: run { finish(); return }
        pickOnly = intent.getBooleanExtra(EXTRA_PICK_ONLY, false)
        build()
    }

    /** True when the choice goes back to the caller and is not bound to the trigger. */
    private var pickOnly = false

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // The menu editor saved: nothing more to pick here.
        if (requestCode == MENU && resultCode == RESULT_OK) finish()
    }

    private fun build() {
        val col = Ui.page(this, if (pickOnly) "Add to the menu" else trigger.label())
        if (!pickOnly) {
            col.row("Now", Store.bindings(this).raw(trigger).describe(), enabled = false)
            col.header("More than one")
            col.row("Build a menu", "As many actions as you want") {
                startActivityForResult(
                    dev.equwal.assistkey.menu.MenuEditActivity.intent(this, trigger)
                        .putExtra(EXTRA_NO_ASK, intent.getBooleanExtra(EXTRA_NO_ASK, false)),
                    MENU
                )
            }
            dev.equwal.assistkey.menu.Menu.presets(this).forEach { (name, items) ->
                col.row(name + " menu", items.joinToString(", ") { it.describe() }) {
                    choose(dev.equwal.assistkey.menu.Menu.spec(items))
                }
            }
        }

        if (!pickOnly) basics(col)
        viwoods(col)
        navigation(col)
        if (dev.equwal.assistkey.shell.Shell.SUPPORTED) {
            col.header("Light")
            listOf("toggle", "darker", "brighter", "system_up", "system_down").forEach { what ->
                val spec = ActionSpec(ActionKind.DIM, what, "")
                col.row(spec.describe(), if (what == "toggle") "Lowest level, or back to the system" else null) {
                    choose(spec)
                }
            }
        }
        col.header("Typing")
        col.row("Voice typing", "Speak, and the words appear") {
            choose(ActionSpec(ActionKind.VOICE, "", "Voice typing"))
        }
        own(col)
        reading(col)
        soundAndMedia(col)
        custom(col)
    }

    private fun choose(spec: ActionSpec) {
        if (pickOnly) {
            setResult(RESULT_OK, Intent().putExtra(RESULT_SPEC, spec.toJson().toString()))
        } else {
            Store.bind(this, trigger, spec)
            // Ask now for what this binding needs. The screen closes itself when nothing is missing.
            if (spec.kind != ActionKind.PASS_THROUGH && !intent.getBooleanExtra(EXTRA_NO_ASK, false)) {
                dev.equwal.assistkey.setup.GuidedSetupActivity.askIfMissing(this, trigger)
            }
        }
        finish()
    }

    // ---- sections ----------------------------------------------------------

    private fun basics(col: LinearLayout) {
        col.header("Basics")
        col.row("Default behaviour", "The system handles this button") {
            choose(ActionSpec.PASS)
        }
        col.row("Do nothing", "Switches the button off") {
            choose(ActionSpec.NOTHING)
        }
    }

    private fun viwoods(col: LinearLayout) {
        if (!dev.equwal.assistkey.device.Device.hasViwoodsActions) return
        col.header("Viwoods AI")
        Presets.viwoods.forEach { p ->
            col.row(p.label, null) { choose(p.toSpec()) }
        }
    }

    private fun own(col: LinearLayout) {
        col.header("Rebind")
        val label = "Recent apps (cards)"
        col.row(label, null) {
            choose(ActionSpec(ActionKind.LAUNCH_COMPONENT, packageName + "/dev.equwal.assistkey.home.RecentsActivity", label))
        }
    }

    private fun navigation(col: LinearLayout) {
        col.header("Navigation")
        col.note("These need button remapping.")
        GlobalAction.usable().forEach { g ->
            col.row(g.label, null) {
                choose(ActionSpec(ActionKind.GLOBAL, g.name, g.label))
            }
        }
    }

    private fun reading(col: LinearLayout) {
        col.header("Page turning")
        col.note("For reader apps that only take touch.")
        listOf(
            "left" to "Swipe left (next page)",
            "right" to "Swipe right (previous page)",
            "up" to "Swipe up",
            "down" to "Swipe down"
        ).forEach { (dir, label) ->
            col.row(label, null) { choose(ActionSpec(ActionKind.SWIPE, dir, label)) }
        }
        listOf(
            "forward" to "Scroll forward",
            "backward" to "Scroll backward"
        ).forEach { (dir, label) ->
            col.row(label, null) { choose(ActionSpec(ActionKind.SCROLL, dir, label)) }
        }
    }

    private fun soundAndMedia(col: LinearLayout) {
        col.header("Sound")
        listOf(
            "raise" to "Volume up",
            "lower" to "Volume down",
            "mute" to "Mute / unmute",
            "panel" to "Show the volume panel"
        ).forEach { (p, label) ->
            col.row(label, null) { choose(ActionSpec(ActionKind.VOLUME, p, label)) }
        }

        col.header("Media")
        listOf(
            "play_pause" to "Play / pause",
            "next" to "Next track",
            "previous" to "Previous track",
            "stop" to "Stop"
        ).forEach { (p, label) ->
            col.row(label, null) { choose(ActionSpec(ActionKind.MEDIA, p, label)) }
        }
    }

    private fun custom(col: LinearLayout) {
        col.header("Anything else")

        col.row("Open an app", "Pick from everything installed") { pickApp() }

        col.row(
            "Open a specific screen",
            "For a screen with no icon"
        ) {
            Ui.textInput(this, "Component", "com.example/com.example.SomeActivity") { v ->
                choose(ActionSpec(ActionKind.LAUNCH_COMPONENT, v, shortLabel(v)))
            }
        }

        col.row("Send an intent action", "For example android.intent.action.VOICE_COMMAND") {
            Ui.textInput(this, "Intent action", "android.intent.action.…") { v ->
                choose(ActionSpec(ActionKind.LAUNCH_ACTION, v, shortLabel(v)))
            }
        }

        col.row("Send a broadcast", "For apps that listen for one") {
            Ui.textInput(this, "Broadcast action", "com.example.ACTION") { v ->
                choose(ActionSpec(ActionKind.BROADCAST, v, "Broadcast " + shortLabel(v)))
            }
        }
    }

    private fun shortLabel(v: String) = v.substringAfterLast('.').ifBlank { v }

    /**
     * Only launchable apps, which is also exactly the set the manifest
     * <queries> block makes visible to us.
     */
    private fun pickApp() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        if (apps.isEmpty()) {
            Ui.textInput(this, "Package name", "com.example.app") { v ->
                choose(ActionSpec(ActionKind.LAUNCH_APP, v, v))
            }
            return
        }
        Ui.pick(this, "Open an app", apps.map { it.first }) { i ->
            val (label, pkg) = apps[i]
            choose(ActionSpec(ActionKind.LAUNCH_APP, pkg, label))
        }
    }

    companion object {
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_PICK_ONLY = "pick_only"

        /** Set by the guided setup, which asks for what a binding needs by itself. */
        const val EXTRA_NO_ASK = "no_ask"
        private const val MENU = 7
        const val RESULT_SPEC = "spec"

        /** Opens the picker to choose one action for a menu; the result carries [RESULT_SPEC]. */
        fun pickIntent(c: Context, t: Trigger): Intent = intent(c, t).putExtra(EXTRA_PICK_ONLY, true)

        fun intent(c: Context, t: Trigger): Intent =
            Intent(c, ActionPickerActivity::class.java).putExtra(EXTRA_TRIGGER, t.id)
    }
}
