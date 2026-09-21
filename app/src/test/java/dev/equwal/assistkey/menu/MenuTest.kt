package dev.equwal.assistkey.menu

import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuTest {

    private val back = ActionSpec(ActionKind.GLOBAL, "BACK", "Back")
    private val voice = ActionSpec(ActionKind.VOICE, "", "Voice typing")
    private val app = ActionSpec(ActionKind.LAUNCH_APP, "org.koreader.launcher", "KOReader \"nightly\"")

    @Test fun `decode gives back the items encode was given, in order`() {
        val items = listOf(back, voice, app)
        assertEquals(items, Menu.decode(Menu.encode(items)))
    }

    @Test fun `a menu inside a menu is dropped`() {
        val inner = Menu.spec(listOf(back))
        assertEquals(listOf(voice), Menu.decode(Menu.encode(listOf(inner, voice))))
    }

    @Test fun `text that is not a menu is an empty menu`() {
        assertTrue(Menu.decode("not json").isEmpty())
        assertTrue(Menu.decode("").isEmpty())
    }

    @Test fun `the binding label names the actions`() {
        assertEquals("Menu: Back, Voice typing", Menu.spec(listOf(back, voice)).label)
    }
}
