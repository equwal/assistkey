package dev.equwal.assistkey.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings as one JSON document, to move them to another device or to share.
 *
 * This file has no Android types, so a test can run it with no device. The
 * caller reads and writes the preference files; this object turns their content
 * into text and back, and decides what may pass.
 *
 * Only the names in [ALLOWED] pass, in both directions. The licence, the
 * firmware values the app saved, and what is true of one device only (the keys
 * it has, its light level) are not settings and never enter the file. A file
 * from a stranger therefore cannot unlock the app or write a value this list
 * does not name.
 *
 * Each value carries its type, because JSON has one number type and a
 * preference file has three.
 */
object SettingsFile {

    const val APP = "AssistKey"
    const val FORMAT = 1

    /** Preference file name to the keys that may pass. `null` means every key in that file. */
    val ALLOWED: Map<String, Set<String>?> = mapOf(
        "assistkey" to setOf("bindings", "multitap_ms", "hold_ms", "chord_ms"),
        "assistkey_channels" to null,
        "assistkey_nav" to setOf("buttons", "gestures", "keys"),
        "assistkey_home" to setOf("favourites", "hidden", "clock", "auto_open"),
        "assistkey_voice" to setOf("engine", "language"),
        "assistkey_device" to setOf("ai_key_returns"),
        "assistkey_power" to setOf("wanted")
    )

    class BadFile(message: String) : Exception(message)

    fun allowed(file: String, key: String): Boolean =
        ALLOWED.containsKey(file) && (ALLOWED[file]?.contains(key) ?: true)

    /** [settings] is preference file name to its key-value content. Entries that may not pass are dropped. */
    fun encode(settings: Map<String, Map<String, Any?>>, appVersion: String, device: String): String {
        val files = JSONObject()
        settings.toSortedMap().forEach { (file, values) ->
            val out = JSONObject()
            values.toSortedMap().forEach { (key, value) ->
                if (allowed(file, key)) wrap(key, value)?.let { out.put(key, it) }
            }
            if (out.length() > 0) files.put(file, out)
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("version", appVersion)
            .put("device", device)
            .put("settings", files)
            .toString(2)
    }

    /** The opposite of [encode]. Throws [BadFile] when the text is not a settings file of this app. */
    fun decode(text: String): Map<String, Map<String, Any>> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BadFile("This is not a settings file.")
        }
        if (root.optString("app") != APP) throw BadFile("This is not an AssistKey settings file.")
        if (root.optInt("format", -1) != FORMAT) {
            throw BadFile("This settings file is from a newer version of AssistKey.")
        }
        val files = root.optJSONObject("settings") ?: throw BadFile("This settings file is empty.")

        val result = LinkedHashMap<String, Map<String, Any>>()
        files.keys().forEach { file ->
            val content = files.optJSONObject(file) ?: return@forEach
            val values = LinkedHashMap<String, Any>()
            content.keys().forEach { key ->
                if (allowed(file, key)) unwrap(key, content.optJSONObject(key))?.let { values[key] = it }
            }
            if (values.isNotEmpty()) result[file] = values
        }
        return result
    }

    // ---- one value -------------------------------------------------------------------------

    /** Bindings are JSON already; they go in as an object, so a person can read and edit them. */
    private fun wrap(key: String, value: Any?): JSONObject? = when {
        value == null -> null
        key == "bindings" && value is String ->
            runCatching { JSONObject().put("t", "json").put("v", JSONObject(value)) }.getOrNull()
        value is String -> JSONObject().put("t", "s").put("v", value)
        value is Boolean -> JSONObject().put("t", "b").put("v", value)
        value is Int -> JSONObject().put("t", "i").put("v", value)
        value is Long -> JSONObject().put("t", "l").put("v", value)
        value is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        value is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }.sorted()))
        else -> null
    }

    private fun unwrap(key: String, o: JSONObject?): Any? {
        if (o == null || !o.has("v")) return null
        return when (o.optString("t")) {
            "json" -> if (key == "bindings") o.optJSONObject("v")?.toString() else null
            "s" -> o.opt("v") as? String
            "b" -> o.opt("v") as? Boolean
            "i" -> (o.opt("v") as? Number)?.toInt()
            "l" -> (o.opt("v") as? Number)?.toLong()
            "f" -> (o.opt("v") as? Number)?.toFloat()
            "set" -> o.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
            else -> null
        }
    }
}
