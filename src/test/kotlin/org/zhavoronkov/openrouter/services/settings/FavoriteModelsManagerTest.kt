package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("FavoriteModelsManager Tests")
class FavoriteModelsManagerTest {

    @Test
    fun `add and remove favorites updates settings`() {
        val settings = OpenRouterSettings()
        var changes = 0
        val manager = FavoriteModelsManager(settings) { changes++ }

        manager.addFavoriteModel("openai/gpt-4")
        manager.addFavoriteModel("openai/gpt-4")
        manager.removeFavoriteModel("openai/gpt-4")

        assertFalse(manager.isFavoriteModel("openai/gpt-4"))
        assertEquals(1, changes)
    }

    @Test
    fun `set and clear favorites`() {
        val settings = OpenRouterSettings()
        var changes = 0
        val manager = FavoriteModelsManager(settings) { changes++ }

        manager.setFavoriteModels(listOf("openai/gpt-4", "openai/gpt-4o"))
        assertEquals(2, manager.getFavoriteModels().size)

        manager.clearFavoriteModels()
        assertTrue(manager.getFavoriteModels().isEmpty())
        assertEquals(2, changes)
    }
}
