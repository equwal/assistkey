package dev.equwal.assistkey.store

import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * A new version must read what an old version stored. These are the texts that
 * the 0.0.x versions wrote. If a change makes one of them unreadable, the
 * settings of every user are lost at the update. Do not edit the texts: add
 * a new one for a new format.
 */
class StoredFormatTest {

    /** The "bindings" preference, as 0.0.7 to 0.0.15 wrote it. One entry for each kind of action. */
    private val bindings = """
        {"power:tap:1":{"kind":"GLOBAL","payload":"BACK","label":"Back"},
         "power:hold:1":{"kind":"GLOBAL","payload":"HOME_CLOSE_IME","label":"Home"},
         "power:tap:2":{"kind":"LAUNCH_COMPONENT","payload":"dev.equwal.assistkey/dev.equwal.assistkey.home.RecentsActivity","label":"Recent apps"},
         "power:tap:3":{"kind":"GLOBAL","payload":"LOCK_SCREEN","label":"Lock screen"},
         "vol_up:tap:1":{"kind":"SWIPE","payload":"right","label":"Previous page"},
         "vol_down:tap:1":{"kind":"SCROLL","payload":"forward","label":"Scroll"},
         "ai:tap:2":{"kind":"VOICE","payload":"","label":"Voice typing"},
         "ai:hold:1":{"kind":"DIM","payload":"toggle","label":""},
         "ai+vol_up:tap:1":{"kind":"MENU","payload":"[]","label":"Menu"},
         "vol_down+power:tap:1":{"kind":"LAUNCH_APP","payload":"org.koreader.launcher","label":"KOReader"},
         "screen:tap:1":{"kind":"LAUNCH_COMPONENT","payload":"com.viwoods.viwoodsai/com.wisky.wiskyai.WebViewAiActivity?recodeKey=recode_key_start","label":"AI voice prompt"},
         "page_down:tap:1":{"kind":"NAV","payload":"toggle_bar","label":"Show or hide the button bar"},
         "page_up:tap:1":{"kind":"MEDIA","payload":"play_pause","label":"Play or pause"},
         "camera:tap:1":{"kind":"VOLUME","payload":"raise","label":"Volume up"},
         "f2:tap:1":{"kind":"LAUNCH_ACTION","payload":"dev.equwal.inkdim.TOGGLE","label":"TOGGLE"},
         "f3:tap:1":{"kind":"BROADCAST","payload":"com.example.PING","label":"PING"},
         "f4:tap:1":{"kind":"NONE","payload":"","label":"Disabled"}}
    """.trimIndent()

    @Test fun `every trigger and action that an old version stored still reads`() {
        val root = JSONObject(bindings)
        val kinds = HashSet<ActionKind>()
        root.keys().forEach { id ->
            val trigger = Trigger.parse(id)
            assertNotNull("trigger " + id, trigger)
            assertEquals(id, trigger!!.id)
            val spec = ActionSpec.fromJson(root.getJSONObject(id))
            assertEquals(root.getJSONObject(id).getString("kind"), spec.kind.name)
            assertEquals(root.getJSONObject(id).getString("payload"), spec.payload)
            kinds += spec.kind
        }
        // PASS_THROUGH is never stored: it means "no binding".
        assertEquals(ActionKind.entries.toSet() - ActionKind.PASS_THROUGH, kinds)
    }

    @Test fun `the tokens of the keys do not change`() {
        assertEquals(
            listOf("ai", "vol_up", "vol_down", "page_up", "page_down", "camera", "focus", "assist",
                "headset", "mute", "f2", "f3", "f4", "power", "screen"),
            HwKey.entries.map { it.token }
        )
        assertEquals(Trigger(setOf(HwKey.POWER), GestureType.HOLD), Trigger.parse("power:hold:1"))
    }

    /** A settings file as 0.0.9 exported it, with a home screen block that no longer exists. */
    @Test fun `an export of an old version still imports, and what is gone is skipped`() {
        val file = """
            {"app":"AssistKey","format":1,"version":"0.0.9-alpha-full","device":"Viwoods AiPaper Reader",
             "settings":{
               "assistkey":{"bindings":{"t":"json","v":{"ai:tap:2":{"kind":"VOICE","payload":"","label":"Voice typing"}}},
                            "hold_ms":{"t":"i","v":450},"multitap_ms":{"t":"i","v":280},"chord_ms":{"t":"i","v":140}},
               "assistkey_home":{"favourites":{"t":"s","v":"org.koreader.launcher"},"clock":{"t":"b","v":true}},
               "assistkey_nav":{"buttons":{"t":"b","v":true},"gestures":{"t":"b","v":false},"keys":{"t":"b","v":true}},
               "assistkey_voice":{"engine":{"t":"s","v":"org.woheller69.whisper/com.whispertflite.WhisperRecognitionService"},
                                  "language":{"t":"s","v":"ja-JP"}},
               "assistkey_power":{"wanted":{"t":"b","v":true}}}}
        """.trimIndent()
        val read = SettingsFile.decode(file)
        assertEquals(setOf("assistkey", "assistkey_nav", "assistkey_voice", "assistkey_power"), read.keys)
        assertEquals(450, read.getValue("assistkey")["hold_ms"])
        assertEquals("ja-JP", read.getValue("assistkey_voice")["language"])
    }
}
