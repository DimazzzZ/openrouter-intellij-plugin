package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.presets.PresetEntry

class ChatPresetFollowerTest {

    private val shown = mutableListOf<ChatControls>()
    private var resets = 0
    private val follower = ChatPresetFollower(apply = { shown += it }, reset = { resets++ }, schemas = { emptyList() })

    private fun preset(slug: String, config: String = "{}") =
        PresetEntry(slug, slug, null, JsonParser.parseString(config).asJsonObject)

    @Test
    @DisplayName("picking a pair shows its preset in the controls")
    fun picksPair() {
        assertTrue(follower.follow(preset("research", """{"verbosity":"low"}""")))

        assertEquals(listOf(ChatControls(verbosity = "Low")), shown)
    }

    @Test
    @DisplayName("the preset already applied is left alone, so what the user changed since stays")
    fun samePresetAgain() {
        follower.follow(preset("research"))

        assertFalse(follower.follow(preset("research")))
        assertEquals(1, shown.size)
    }

    @Test
    @DisplayName("a preset edited since it was applied fills the controls again")
    fun editedPreset() {
        follower.follow(preset("research", """{"verbosity":"low"}"""))

        assertTrue(follower.follow(preset("research", """{"verbosity":"high"}""")))
        assertEquals(ChatControls(verbosity = "High"), shown.last())
    }

    @Test
    @DisplayName("leaving a pair for a plain model puts the controls back to their defaults, once")
    fun leavesPair() {
        follower.follow(preset("research"))

        assertTrue(follower.follow(null))
        assertFalse(follower.follow(null))
        assertEquals(1, resets)
    }

    @Test
    @DisplayName("a plain model from the start changes nothing")
    fun plainFromStart() {
        assertFalse(follower.follow(null))
        assertEquals(0, resets)
        assertTrue(shown.isEmpty())
    }
}
