package dev.equwal.assistkey.model

/**
 * The payload of [ActionKind.LAUNCH_COMPONENT]: "pkg/class", and after a "?"
 * the text extras of the intent as "key=value" with "&" between them.
 *
 * The extras exist for a screen that does something else when it gets one.
 * The Viwoods AI screen starts its voice prompt with recodeKey=recode_key_start.
 * A payload from before has no "?" and reads as it always did.
 */
object ComponentPayload {

    data class Parts(val component: String, val extras: Map<String, String>)

    fun parse(payload: String): Parts {
        val component = payload.substringBefore('?')
        val extras = LinkedHashMap<String, String>()
        if ('?' in payload) {
            payload.substringAfter('?').split('&').forEach { pair ->
                val key = pair.substringBefore('=')
                if (key.isNotEmpty() && '=' in pair) extras[key] = pair.substringAfter('=')
            }
        }
        return Parts(component, extras)
    }

    fun format(component: String, extras: Map<String, String>): String =
        if (extras.isEmpty()) component
        else component + "?" + extras.entries.joinToString("&") { it.key + "=" + it.value }
}
