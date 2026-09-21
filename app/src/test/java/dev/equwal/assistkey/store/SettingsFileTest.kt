package dev.equwal.assistkey.store

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SettingsFileTest {

    private fun encode(s: Map<String, Map<String, Any?>>) = SettingsFile.encode(s, "test", "bench")

    // ---- round trip, on random allowed settings ------------------------------------------

    private fun randomText(r: Random): String {
        val alphabet = "abcXYZ 09_-./:\"\\{}[]\n\u00e9\u4e2d\u2603"
        return (0 until r.nextInt(12)).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun randomValue(r: Random): Any = when (r.nextInt(6)) {
        0 -> randomText(r)
        1 -> r.nextBoolean()
        2 -> r.nextInt()
        // A long outside the int range, so that the two number types stay apart.
        3 -> Int.MAX_VALUE.toLong() + 1 + r.nextInt(1000)
        4 -> r.nextInt(1000) / 8f
        else -> (0 until r.nextInt(4)).map { randomText(r) }.toSet()
    }

    private fun randomSettings(r: Random): Map<String, Map<String, Any>> {
        val out = LinkedHashMap<String, Map<String, Any>>()
        SettingsFile.ALLOWED.forEach { (file, keys) ->
            val names = keys?.filter { it != "bindings" } ?: listOf("accessibility", "assistant", "camera")
            val values = names.filter { r.nextBoolean() }.associateWith { randomValue(r) }
            if (values.isNotEmpty()) out[file] = values
        }
        return out
    }

    @Test fun `decode gives back what encode was given, for 500 random settings`() {
        val r = Random(20260921)
        repeat(500) {
            val settings = randomSettings(r)
            assertEquals(settings, SettingsFile.decode(encode(settings)))
        }
    }

    @Test fun `bindings go in as a JSON object and come back with the same content`() {
        val bindings = JSONObject()
            .put("power:tap:1", JSONObject().put("kind", "GLOBAL").put("payload", "BACK").put("label", "Back"))
            .put("ai:hold:1", JSONObject().put("kind", "VOICE").put("payload", "").put("label", "Voice typing"))
        val text = encode(mapOf("assistkey" to mapOf("bindings" to bindings.toString(), "hold_ms" to 450)))

        val stored = JSONObject(text).getJSONObject("settings").getJSONObject("assistkey").getJSONObject("bindings")
        assertEquals("json", stored.getString("t"))
        assertEquals("BACK", stored.getJSONObject("v").getJSONObject("power:tap:1").getString("payload"))

        val back = SettingsFile.decode(text).getValue("assistkey")
        val again = JSONObject(back.getValue("bindings") as String)
        assertEquals(2, again.length())
        assertEquals("BACK", again.getJSONObject("power:tap:1").getString("payload"))
        assertEquals("VOICE", again.getJSONObject("ai:hold:1").getString("kind"))
        assertEquals(450, back.getValue("hold_ms"))
    }

    // ---- what may not pass -----------------------------------------------------------------

    @Test fun `the licence and device facts never enter the file`() {
        val text = encode(
            mapOf(
                "assistkey_license" to mapOf("owned" to setOf("assistkey_pro"), "beta_tester" to true),
                "assistkey_power" to mapOf("wanted" to true, "taken" to true, "saved_power_button_short_press" to "4"),
                "assistkey_device" to mapOf("ai_key_returns" to false, "seen_keys" to setOf("page_up")),
                "assistkey_display" to mapOf("extra_dim" to 2)
            )
        )
        assertFalse(text.contains("assistkey_license"))
        assertFalse(text.contains("owned"))
        assertFalse(text.contains("taken"))
        assertFalse(text.contains("saved_"))
        assertFalse(text.contains("seen_keys"))
        assertFalse(text.contains("extra_dim"))
        assertTrue(text.contains("ai_key_returns"))
    }

    @Test fun `a file that names the licence cannot write it`() {
        val hostile = JSONObject()
            .put("app", SettingsFile.APP).put("format", SettingsFile.FORMAT)
            .put(
                "settings",
                JSONObject()
                    .put("assistkey_license", JSONObject().put("first_run_at", JSONObject().put("t", "l").put("v", 1)))
                    .put("assistkey_nav", JSONObject().put("keys", JSONObject().put("t", "b").put("v", true)))
            )
        assertEquals(mapOf("assistkey_nav" to mapOf("keys" to true)), SettingsFile.decode(hostile.toString()))
    }

    @Test fun `a value of the wrong type is dropped, not guessed`() {
        val odd = JSONObject()
            .put("app", SettingsFile.APP).put("format", SettingsFile.FORMAT)
            .put("settings", JSONObject().put("assistkey_nav", JSONObject().put("keys", JSONObject().put("t", "b").put("v", "yes"))))
        assertTrue(SettingsFile.decode(odd.toString()).isEmpty())
    }

    @Test fun `text that is not ours is refused with a reason`() {
        assertThrows(SettingsFile.BadFile::class.java) { SettingsFile.decode("hello") }
        assertThrows(SettingsFile.BadFile::class.java) { SettingsFile.decode("{\"app\":\"Other\",\"format\":1}") }
        assertThrows(SettingsFile.BadFile::class.java) {
            SettingsFile.decode("{\"app\":\"AssistKey\",\"format\":99,\"settings\":{}}")
        }
    }
}
