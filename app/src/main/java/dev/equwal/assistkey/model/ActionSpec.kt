package dev.equwal.assistkey.model

import org.json.JSONObject

/**
 * What a trigger does. [payload] is interpreted per [kind]; keeping it a plain
 * string keeps persistence trivial and lets the action set grow without a
 * schema migration.
 */
enum class ActionKind {
    /** Swallow the key and do nothing - this is how a key gets disabled. */
    NONE,

    /** Do not intercept; let the system handle the key natively. */
    PASS_THROUGH,

    /** AccessibilityService.performGlobalAction; payload = GlobalAction.name */
    GLOBAL,

    /** payload = one of raise|lower|mute|panel */
    VOLUME,

    /** payload = one of play_pause|next|previous|stop */
    MEDIA,

    /** payload = package name; launches its main activity */
    LAUNCH_APP,

    /** payload = "pkg/class"; explicit component, no intent-filter needed */
    LAUNCH_COMPONENT,

    /** payload = intent action string; sent as an implicit activity intent */
    LAUNCH_ACTION,

    /** payload = intent action string; sent as a broadcast */
    BROADCAST,

    /** payload = up|down|left|right; dispatches a synthetic swipe */
    SWIPE,

    /** payload = forward|backward; scrolls the focused scrollable node */
    SCROLL,

    /** Speak, and the words go where the cursor is. The payload is not used. */
    VOICE,

    /** The extra-dim light. The payload is "darker", "brighter" or "toggle". */
    DIM,

    /** Opens a menu of actions. The payload is a JSON array of action specs. */
    MENU;

    companion object {
        fun fromName(n: String): ActionKind =
            entries.firstOrNull { it.name == n } ?: NONE
    }
}

data class ActionSpec(
    val kind: ActionKind,
    val payload: String = "",
    /** Cached display label so the UI need not resolve packages on every draw. */
    val label: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name)
        .put("payload", payload)
        .put("label", label)

    /**
     * The name of a light action comes from its payload, not from the stored
     * label, so that a binding made by an older version shows the current name.
     */
    private fun dimName(): String = when (payload) {
        "darker" -> "Extra dim darker"
        "brighter" -> "Extra dim brighter"
        else -> "Extra dim toggle"
    }

    fun describe(): String = if (kind == ActionKind.DIM) dimName() else label.ifBlank {
        when (kind) {
            ActionKind.NONE -> "Disabled"
            ActionKind.PASS_THROUGH -> "Default behaviour"
            else -> if (payload.isBlank()) kind.name else "${kind.name}: $payload"
        }
    }

    companion object {
        val NOTHING = ActionSpec(ActionKind.NONE, label = "Disabled")
        val PASS = ActionSpec(ActionKind.PASS_THROUGH, label = "Default behaviour")

        fun fromJson(o: JSONObject): ActionSpec = ActionSpec(
            kind = ActionKind.fromName(o.optString("kind", "NONE")),
            payload = o.optString("payload", ""),
            label = o.optString("label", "")
        )
    }
}
