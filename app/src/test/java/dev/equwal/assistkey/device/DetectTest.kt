package dev.equwal.assistkey.device

import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.store.SettingsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DetectTest {

    // ---- the stored result, round trip ------------------------------------

    private fun randomText(r: Random): String {
        val alphabet = "abcXYZ 09_-./:\"\\{}[]\n\u00e9\u4e2d\u2603"
        return (0 until r.nextInt(14)).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun randomResult(r: Random): Detect.Result = Detect.Result(
        fingerprint = randomText(r),
        appVersion = randomText(r),
        name = randomText(r),
        profile = randomText(r),
        keys = Detect.ordered(HwKey.entries.filter { r.nextBoolean() }),
        capabilities = Detect.capabilityNames
            .filter { r.nextBoolean() }
            .associateWith { Detect.State.entries[r.nextInt(Detect.State.entries.size)] }
    )

    @Test fun `decode gives back what encode was given, for 500 random results`() {
        val r = Random(20260921)
        repeat(500) {
            val found = randomResult(r)
            assertEquals(found, Detect.decode(Detect.encode(found)))
        }
    }

    @Test fun `text that is not a result is null, not an exception`() {
        listOf("", "hello", "{", "[1,2,3]", "null", "{}", "{\"format\":99}")
            .forEach { text -> assertNull(text, Detect.decode(text)) }
    }

    @Test fun `a result of the right format but the wrong shape is empty, not null`() {
        val found = Detect.decode("{\"format\":1,\"keys\":\"not an array\"}")!!
        assertEquals(emptyList<HwKey>(), found.keys)
        assertTrue(found.capabilities.isEmpty())
    }

    @Test fun `a key or a capability this build does not know is dropped`() {
        val text = "{\"format\":1,\"fingerprint\":\"f\",\"app\":\"1.0\",\"name\":\"n\"," +
            "\"profile\":\"GENERIC\",\"keys\":[\"vol_up\",\"jog_wheel\"]," +
            "\"capabilities\":{\"Shell access\":\"YES\",\"Teleport\":\"YES\",\"Key filter\":\"MAYBE\"}}"
        val found = Detect.decode(text)!!
        assertEquals(listOf(HwKey.VOL_UP), found.keys)
        assertEquals(mapOf(Detect.SHELL_ACCESS to Detect.State.YES), found.capabilities)
        assertEquals(Detect.State.UNKNOWN, found.capability(Detect.KEY_FILTER))
    }

    /** A device profile describes one device. It must never travel in an export. */
    @Test fun `the detection result is not a setting`() {
        assertFalse(SettingsFile.ALLOWED.containsKey(Detect.PREFS))
        assertFalse(SettingsFile.allowed(Detect.PREFS, "result"))
    }

    // ---- getevent ----------------------------------------------------------

    private val geteventSample = """
        add device 1: /dev/input/event0
          name:     "gpio-keys"
          events:
            KEY (0001): 0072  0073  0074
          input props:
            <none>
        add device 2: /dev/input/event1
          name:     "aw9523-key"
          events:
            KEY (0001): 003b
        add device 3: /dev/input/event2
          name:     "fts_ts"
          events:
            KEY (0001): 0066  014a
            ABS (0003): 0035  : value 0, min 0, max 1080, fuzz 0, flat 0, resolution 0
                        0036  : value 0, min 0, max 1920, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
    """.trimIndent()

    @Test fun `a real getevent listing gives the keys of every node`() {
        assertEquals(
            setOf(0x72, 0x73, 0x74, 0x3b, 0x66, 0x14a),
            Detect.scanCodesFromGetevent(geteventSample)
        )
        assertEquals(
            listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN, HwKey.POWER),
            Detect.keysFromGetevent(geteventSample)
        )
    }

    @Test fun `the touchscreen coordinates are not read as keys`() {
        // 0035 and 0036 belong to ABS, and would be F1-ish numbers if taken.
        assertFalse(Detect.scanCodesFromGetevent(geteventSample).contains(0x35))
        assertFalse(Detect.scanCodesFromGetevent(geteventSample).contains(0x36))
    }

    @Test fun `a node with no shell access gives nothing`() {
        assertEquals(emptyList<HwKey>(), Detect.keysFromGetevent("no shell access"))
        assertEquals(emptyList<HwKey>(), Detect.keysFromGetevent(""))
    }

    private fun randomLine(r: Random): String {
        val words = listOf(
            "add device 4: /dev/input/event9", "name:", "KEY (0001):", "ABS (0003):",
            "0072", "003b", "ffff", "0000", "zzzz", "-1", "0x74", "   ", "\t",
            "input props:", "<none>", "value 0, min 0", "\u2603", "99999999", ":",
            "KEY (0001): 0072 0073", "0247", "0210", "00e2"
        )
        return (0 until r.nextInt(6)).joinToString(" ") { words[r.nextInt(words.size)] }
    }

    @Test fun `the parser never throws, and names only keys this app knows`() {
        val r = Random(7301)
        val known = HwKey.entries.toSet()
        repeat(2000) {
            val text = (0 until r.nextInt(12)).joinToString("\n") { randomLine(r) }
            val keys = Detect.keysFromGetevent(text)
            assertTrue(text, keys.all { it in known })
            assertEquals(text, keys.distinct(), keys)
            assertEquals(text, Detect.ordered(keys), keys)
        }
    }

    @Test fun `every physical key this app can bind has one scan code of its own`() {
        // The on-screen button is not a physical key. It has no scan code.
        val physical = HwKey.entries.toSet() - HwKey.SCREEN
        assertEquals(physical, Detect.scanCodes.keys)
        assertEquals(physical.size, Detect.scanCodes.values.toSet().size)
    }

    // ---- the shell probe sections -----------------------------------------

    private val probeSample = geteventSample + "\n@@nav\n2\n@@light\npresent\n"

    @Test fun `each part of the probe output is read on its own`() {
        assertEquals(listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN, HwKey.POWER),
            Detect.keysFromGetevent(probeSample.substringBefore(Detect.MARK)))
        assertEquals(2, Detect.section(probeSample, "nav").trim().toInt())
        assertTrue(Detect.section(probeSample, "light").contains("present"))
    }

    @Test fun `a missing section is empty, whatever the text is`() {
        assertEquals("", Detect.section(probeSample, "nothing"))
        assertEquals("", Detect.section("", "nav"))
        assertEquals("", Detect.section("@@", "nav"))
        assertEquals("", Detect.section("@@nav", "nav"))
    }

    // ---- ordering ----------------------------------------------------------

    @Test fun `keys come back once each, in the order the app lists them`() {
        val mixed = listOf(HwKey.POWER, HwKey.AI, HwKey.POWER, HwKey.VOL_UP, HwKey.AI)
        assertEquals(listOf(HwKey.AI, HwKey.VOL_UP, HwKey.POWER), Detect.ordered(mixed))
        assertEquals(emptyList<HwKey>(), Detect.ordered(emptyList()))
    }
}
