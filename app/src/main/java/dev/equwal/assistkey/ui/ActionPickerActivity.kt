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
import dev.equwal.assistkey.ui.Ui.title

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
        val col = Ui.page(this)
        col.title(if (pickOnly) "Add to the menu" else trigger.label())
        if (!pickOnly) {
            col.note("Currently: " + Store.bindings(this).raw(trigger).describe())
            col.header("More than one")
            col.row("Menu of actions", "This gesture opens a menu, and the menu holds as many actions as you want") {
                startActivityForResult(dev.equwal.assistkey.menu.MenuEditActivity.intent(this, trigger), MENU)
            }
        }

        if (!pickOnly) basics(col)
        viwoods(col)
        navigation(col)
        if (dev.equwal.assistkey.shell.Shell.SUPPORTED) {
            col.header("Light")
            listOf(
                "Extra-dim: darker" to "darker",
                "Extra-dim: brighter" to "brighter",
                "Extra-dim: on and off" to "toggle"
            ).forEach { (label, what) ->
                col.row(label, null) { choose(ActionSpec(ActionKind.DIM, what, label)) }
            }
        }
        col.header("Typing")
        col.row("Voice typing", "Speak, and the words go where the cursor is") {
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
        }
        finish()
    }

    // ---- sections ----------------------------------------------------------

    private fun basics(col: LinearLayout) {
        col.header("Basics")
        col.row("Default behaviour", "Let the system handle this key normally") {
            choose(ActionSpec.PASS)
        }
        col.row("Do nothing", "Swallow the key - this is how you disable a button") {
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
        col.header("AssistKey")
        listOf(
            "Recent apps (cards)" to "dev.equwal.assistkey.home.RecentsActivity",
            "App search" to "dev.equwal.assistkey.home.HomeActivity"
        ).filter { (_, cls) ->
            // The home screen is switched off until asked for; a disabled
            // activity cannot be launched, so it is not offered.
            packageManager.getComponentEnabledSetting(android.content.ComponentName(packageName, cls)) !=
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                (cls.endsWith("RecentsActivity") ||
                    packageManager.getComponentEnabledSetting(android.content.ComponentName(packageName, cls)) ==
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }.forEach { (label, cls) ->
            col.row(label, null) {
                choose(ActionSpec(ActionKind.LAUNCH_COMPONENT, packageName + "/" + cls, label))
            }
        }
    }

    private fun navigation(col: LinearLayout) {
        col.header("Navigation")
        col.note("These need the accessibility key filter to be active.")
        GlobalAction.usable().forEach { g ->
            col.row(g.label, null) {
                choose(ActionSpec(ActionKind.GLOBAL, g.name, g.label))
            }
        }
    }

    private fun reading(col: LinearLayout) {
        col.header("Page turning")
        col.note("Synthetic swipes and scrolls, for reader apps that only take touch.")
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
            "package/class - reaches activities with no launcher icon"
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

        col.row("Send a broadcast", "For apps that listen for a custom action") {
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
        private const val MENU = 7
        const val RESULT_SPEC = "spec"

        /** Opens the picker to choose one action for a menu; the result carries [RESULT_SPEC]. */
        fun pickIntent(c: Context, t: Trigger): Intent = intent(c, t).putExtra(EXTRA_PICK_ONLY, true)

        fun intent(c: Context, t: Trigger): Intent =
            Intent(c, ActionPickerActivity::class.java).putExtra(EXTRA_TRIGGER, t.id)
    }
}
