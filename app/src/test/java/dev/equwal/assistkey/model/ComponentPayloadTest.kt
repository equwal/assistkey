package dev.equwal.assistkey.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class ComponentPayloadTest {

    @Test fun `a payload from before has no extras and reads as it did`() {
        val old = "com.viwoods.viwoodsai/com.wisky.wiskyai.AIActivity"
        assertEquals(ComponentPayload.Parts(old, emptyMap()), ComponentPayload.parse(old))
    }

    @Test fun `the Viwoods voice prompt carries its one extra`() {
        val parts = ComponentPayload.parse(
            "com.viwoods.viwoodsai/com.wisky.wiskyai.WebViewAiActivity?recodeKey=recode_key_start"
        )
        assertEquals("com.viwoods.viwoodsai/com.wisky.wiskyai.WebViewAiActivity", parts.component)
        assertEquals(mapOf("recodeKey" to "recode_key_start"), parts.extras)
    }

    @Test fun `junk after the question mark is skipped, not thrown`() {
        assertEquals(mapOf("a" to "1"), ComponentPayload.parse("p/c?&=x&novalue&a=1").extras)
        assertEquals(mapOf("a" to "b=c"), ComponentPayload.parse("p/c?a=b=c").extras)
    }

    @Test fun `parse gives back what format was given`() {
        val r = Random(21)
        val letters = "abcXYZ019_."
        fun word() = (0..r.nextInt(6)).map { letters[r.nextInt(letters.length)] }.joinToString("")
        repeat(500) {
            val extras = (0 until r.nextInt(4)).associate { word() to word() }
            val parts = ComponentPayload.parse(ComponentPayload.format("pkg/" + word(), extras))
            assertEquals(extras, parts.extras)
        }
    }
}
