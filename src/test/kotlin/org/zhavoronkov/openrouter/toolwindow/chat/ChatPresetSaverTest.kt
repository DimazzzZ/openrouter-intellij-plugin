package org.zhavoronkov.openrouter.toolwindow.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.settings.presets.PresetDraft

@DisplayName("ChatPresetSaver")
class ChatPresetSaverTest {

    private var named = "fresh"
    private val asked = mutableListOf<String>()
    private var replace = true
    private val offeredTaken = mutableListOf<List<String>>()

    private val saver = ChatPresetSaver(
        edit = { draft, taken ->
            offeredTaken += taken
            draft.apply { slug = named }
        },
        confirmReplace = { slug ->
            asked += slug
            replace
        }
    )

    private val taken = listOf("research", "web-json")

    @Test
    @DisplayName("a new slug is saved without asking")
    fun newSlug() {
        assertEquals("fresh", saver.choose(PresetDraft.empty(""), taken)?.slug)
        assertTrue(asked.isEmpty())
    }

    @Test
    @DisplayName("the dialog lets an existing slug be typed, since saving gives it a new version")
    fun existingSlugIsAllowedInTheDialog() {
        saver.choose(PresetDraft.empty(""), taken)

        assertEquals(listOf(emptyList<String>()), offeredTaken)
    }

    @Test
    @DisplayName("an existing slug is saved as its new version once the user confirms")
    fun existingSlugConfirmed() {
        named = "research"

        assertEquals("research", saver.choose(PresetDraft.empty(""), taken)?.slug)
        assertEquals(listOf("research"), asked)
    }

    @Test
    @DisplayName("an existing slug is matched without regard to case, and nothing is saved when declined")
    fun existingSlugDeclined() {
        named = "Research"
        replace = false

        assertNull(saver.choose(PresetDraft.empty(""), taken))
        assertEquals(listOf("Research"), asked)
    }

    @Test
    @DisplayName("a cancelled dialog saves nothing and asks nothing")
    fun cancelled() {
        val cancelling = ChatPresetSaver(edit = { _, _ -> null }, confirmReplace = { asked += it; true })

        assertNull(cancelling.choose(PresetDraft.empty(""), taken))
        assertTrue(asked.isEmpty())
    }
}
