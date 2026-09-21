package dev.equwal.assistkey.menu

import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
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

    fun spec(items: List<ActionSpec>): ActionSpec =
        ActionSpec(ActionKind.MENU, encode(items), "Menu: " + items.joinToString(", ") { it.describe() })
}
