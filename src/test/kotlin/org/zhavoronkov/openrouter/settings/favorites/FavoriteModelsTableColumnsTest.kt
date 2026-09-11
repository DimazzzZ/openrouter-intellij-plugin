package org.zhavoronkov.openrouter.settings.favorites

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.CATALOG
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GEMINI_AUDIO
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GPT4O
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GROK_FREE
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.LLAMA_SMALL
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.SONNET
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.UNKNOWN_CONTEXT

class FavoriteModelsTableColumnsTest {

    private val state = FavoriteModelsPageState(listOf(GPT4O.id)).also { it.setCatalog(CATALOG) }
    private val columns = FavoriteModelsTableColumns(state)

    @Test
    fun `five columns in order`() {
        assertEquals(listOf("★", "Model", "Context", "Input", "Output"), columns.asArray().map { it.name })
    }

    @Test
    fun `favorite column is an editable Boolean mirroring state`() {
        assertEquals(java.lang.Boolean::class.java, columns.favorite.columnClass)
        assertTrue(columns.favorite.isCellEditable(GPT4O))
        assertEquals(true, columns.favorite.valueOf(GPT4O))
        assertEquals(false, columns.favorite.valueOf(SONNET))
        assertNull(columns.favorite.comparator)
    }

    @Test
    fun `setting the favorite column mutates state`() {
        columns.favorite.setValue(SONNET, true)
        columns.favorite.setValue(GPT4O, false)

        assertEquals(listOf(SONNET.id), state.favorites)
    }

    @Test
    fun `other columns are read-only`() {
        listOf(columns.model, columns.context, columns.input, columns.output).forEach {
            assertFalse(it.isCellEditable(GPT4O), "${it.name} should not be editable")
        }
    }

    @Test
    fun `model column keeps the variant suffix and sorts by id`() {
        assertEquals("x-ai/grok-4-fast:free", columns.model.valueOf(GROK_FREE))
        val sorted = listOf(SONNET, GPT4O).sortedWith(columns.model.comparator!!)
        assertEquals(listOf(SONNET, GPT4O), sorted)
    }

    @Test
    fun `context column exposes raw length and sorts nulls last`() {
        assertEquals(1_000_000, columns.context.valueOf(GEMINI_AUDIO))
        assertNull(columns.context.valueOf(UNKNOWN_CONTEXT))

        val sorted = listOf(UNKNOWN_CONTEXT, GEMINI_AUDIO, LLAMA_SMALL).sortedWith(columns.context.comparator!!)
        assertEquals(listOf(LLAMA_SMALL, GEMINI_AUDIO, UNKNOWN_CONTEXT), sorted)
    }

    @Test
    fun `price columns render formatted text`() {
        assertEquals("Free", columns.input.valueOf(GROK_FREE))
        assertEquals("—", columns.input.valueOf(UNKNOWN_CONTEXT))
        assertEquals("$2.5000", columns.input.valueOf(GPT4O))
        assertEquals("$10.0000", columns.output.valueOf(GPT4O))
    }

    @Test
    fun `price columns sort numerically with unknown last`() {
        val byInput = listOf(UNKNOWN_CONTEXT, GPT4O, GROK_FREE, SONNET).sortedWith(columns.input.comparator!!)
        assertEquals(listOf(GROK_FREE, GPT4O, SONNET, UNKNOWN_CONTEXT), byInput)

        val byOutput = listOf(SONNET, UNKNOWN_CONTEXT, GPT4O).sortedWith(columns.output.comparator!!)
        assertEquals(listOf(GPT4O, SONNET, UNKNOWN_CONTEXT), byOutput)
    }
}
