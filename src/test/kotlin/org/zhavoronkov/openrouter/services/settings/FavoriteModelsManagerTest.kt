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

    // --- Grouped storage migration tests ---

    @Test
    fun `migration groups base models with no variants`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet")
            favoriteModelsGroupedMigrated = false
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}

        assertTrue(settings.favoriteModelsGroupedMigrated)
        val groups = manager.getGroups()
        assertEquals(2, groups.size)
        assertEquals("openai/gpt-4o", groups[0].baseId)
        assertTrue(groups[0].variants.isEmpty())
    }

    @Test
    fun `migration groups variants under the same base`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf(
                "x-ai/grok-4-fast",
                "x-ai/grok-4-fast:free",
                "x-ai/grok-4-fast:nitro"
            )
            favoriteModelsGroupedMigrated = false
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}

        val groups = manager.getGroups()
        assertEquals(1, groups.size, "All three should collapse into one base group")
        assertEquals("x-ai/grok-4-fast", groups[0].baseId)
        assertEquals(listOf(":free", ":nitro"), groups[0].variants)
    }

    @Test
    fun `migration is idempotent and does not rerun`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf("openai/gpt-4o")
            favoriteModelsGroupedMigrated = true // Already migrated
            favoriteModelGroups = mutableListOf() // Intentionally empty
        }
        FavoriteModelsManager(settings) {}

        // Migration should NOT run again — groups stay empty
        assertTrue(settings.favoriteModelGroups.isEmpty())
    }

    @Test
    fun `migration preserves flat list authoritatively`() {
        val original = mutableListOf("openai/gpt-4o:thinking", "anthropic/claude-3.5-sonnet")
        val settings = OpenRouterSettings().apply {
            favoriteModels = original.toMutableList()
            favoriteModelsGroupedMigrated = false
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}

        // Flat list must be unchanged (still the wire source of truth)
        assertEquals(original, manager.getFavoriteModels())
    }

    @Test
    fun `add favorite updates grouped representation`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf()
            favoriteModelsGroupedMigrated = true
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}

        manager.addFavoriteModel("x-ai/grok-4-fast:free")
        val groups = manager.getGroups()
        assertEquals(1, groups.size)
        assertEquals("x-ai/grok-4-fast", groups[0].baseId)
        assertEquals(listOf(":free"), groups[0].variants)
    }

    @Test
    fun `clear favorites also clears groups`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf("openai/gpt-4o")
            favoriteModelsGroupedMigrated = false
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}
        assertTrue(manager.getGroups().isNotEmpty())

        manager.clearFavoriteModels()
        assertTrue(manager.getGroups().isEmpty())
    }

    @Test
    fun `migration handles unknown variant suffix`() {
        val settings = OpenRouterSettings().apply {
            favoriteModels = mutableListOf("some/model:brand-new-variant")
            favoriteModelsGroupedMigrated = false
            favoriteModelGroups = mutableListOf()
        }
        val manager = FavoriteModelsManager(settings) {}

        val groups = manager.getGroups()
        assertEquals(1, groups.size)
        assertEquals("some/model", groups[0].baseId)
        assertEquals(listOf(":brand-new-variant"), groups[0].variants)
    }
}
