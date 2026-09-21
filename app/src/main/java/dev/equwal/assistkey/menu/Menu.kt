package dev.equwal.assistkey.menu

import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.Presets
import dev.equwal.assistkey.shell.Shell
import org.json.JSONArray

/**
 * A menu of actions, bound to a gesture like any other action.
 *
 * The menu lives in the payload of its own binding, as a JSON array of action
 * specs. There is no second store, so a menu is exported, imported and deleted
 * together with the binding that opens it. A menu cannot hold another menu.
 */
object Menu {

    fun encode(items: List<ActionSpec>): String =
        JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()

    fun decode(payload: String): List<ActionSpec> = runCatching {
        val a = JSONArray(payload)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .map(ActionSpec::fromJson)
            .filter { it.kind != ActionKind.MENU && it.kind != ActionKind.PASS_THROUGH }
    }.getOrDefault(emptyList())

    /**
     * Ready-made menus, for the groups of actions that belong together. Each is
     * an ordinary menu after it is bound, so the user can change it in the editor.
     * A preset that this device or this build cannot run is not offered.
     */
    fun presets(c: android.content.Context): List<Pair<String, List<ActionSpec>>> {
        fun global(a: GlobalAction) = ActionSpec(ActionKind.GLOBAL, a.name, a.label)
        val out = ArrayList<Pair<String, List<ActionSpec>>>()

        if (Shell.SUPPORTED) {
            out += "Brightness" to listOf("toggle", "darker", "brighter", "system_up", "system_down")
                .map { ActionSpec(ActionKind.DIM, it, "") }
        }
        out += "Navigation" to listOf(
            global(GlobalAction.BACK),
            global(GlobalAction.HOME_CLOSE_IME),
            ActionSpec(ActionKind.LAUNCH_COMPONENT, c.packageName + "/dev.equwal.assistkey.home.RecentsActivity", "Recent apps (cards)"),
            global(GlobalAction.NOTIFICATIONS),
            global(GlobalAction.QUICK_SETTINGS)
        )
        out += "System" to listOf(
            global(GlobalAction.SCREENSHOT),
            global(GlobalAction.LOCK_SCREEN),
            global(GlobalAction.POWER_DIALOG),
            ActionSpec(ActionKind.VOICE, "", "Voice typing")
        )
        out += "Sound and media" to listOf(
            ActionSpec(ActionKind.MEDIA, "play_pause", "Play / pause"),
            ActionSpec(ActionKind.MEDIA, "next", "Next track"),
            ActionSpec(ActionKind.MEDIA, "previous", "Previous track"),
            ActionSpec(ActionKind.VOLUME, "raise", "Volume up"),
            ActionSpec(ActionKind.VOLUME, "lower", "Volume down"),
            ActionSpec(ActionKind.VOLUME, "mute", "Mute / unmute")
        )
        out += "Page turning" to listOf(
            ActionSpec(ActionKind.SWIPE, "left", "Swipe left (next page)"),
            ActionSpec(ActionKind.SWIPE, "right", "Swipe right (previous page)"),
            ActionSpec(ActionKind.SCROLL, "forward", "Scroll forward"),
            ActionSpec(ActionKind.SCROLL, "backward", "Scroll backward")
        )
        if (Device.hasViwoodsActions) out += "Viwoods AI" to Presets.viwoods.map { it.toSpec() }
        return out
    }

    fun spec(items: List<ActionSpec>): ActionSpec =
        ActionSpec(ActionKind.MENU, encode(items), "Menu: " + items.joinToString(", ") { it.describe() })
}
