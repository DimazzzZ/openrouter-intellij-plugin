package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("OpenRouter Settings Service Migration Tests")
class OpenRouterSettingsServiceMigrationTest {

    @Test
    fun `loadState should promote authScope when provisioning key exists`() {
        val service = OpenRouterSettingsService()
        val state = OpenRouterSettings(
            authScope = AuthScope.REGULAR,
            provisioningKey = "pk-test"
        )

        service.loadState(state)

        assertEquals(AuthScope.EXTENDED, service.getState().authScope)
        assertNotNull(service.apiKeyManager)
    }

    @Test
    fun `loadState should leave authScope untouched when provisioning key is blank`() {
        val service = OpenRouterSettingsService()
        val state = OpenRouterSettings(
            authScope = AuthScope.REGULAR,
            provisioningKey = ""
        )

        service.loadState(state)

        // Blank key means the migration guard should NOT fire.
        assertEquals(AuthScope.REGULAR, service.getState().authScope)
    }

    @Test
    fun `favorites migration should return early when list is empty`() {
        val service = OpenRouterSettingsService()
        val emptyFavorites = mutableListOf<String>()
        val state = OpenRouterSettings(favoriteModels = emptyFavorites)

        service.loadState(state)

        // Early-return branch: no rewrite happens, same instance survives.
        assertTrue(service.getState().favoriteModels.isEmpty())
        assertSame(emptyFavorites, service.getState().favoriteModels)
    }

    @Test
    fun `favorites migration should strip deprecated variant suffixes`() {
        val service = OpenRouterSettingsService()
        val state = OpenRouterSettings(
            favoriteModels = mutableListOf(
                "openai/gpt-4o:extended",
                "anthropic/claude-3.5-sonnet:thinking",
                "perplexity/sonar:online"
            )
        )

        service.loadState(state)

        val migrated = service.getState().favoriteModels
        assertEquals(
            listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet", "perplexity/sonar"),
            migrated
        )
    }

    @Test
    fun `favorites migration should preserve list when no deprecated suffixes are present`() {
        val service = OpenRouterSettingsService()
        val pristine = mutableListOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet")
        val state = OpenRouterSettings(favoriteModels = pristine)

        service.loadState(state)

        // Not-changed branch: no rewrite path is taken.
        assertEquals(listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet"), service.getState().favoriteModels)
    }

    @Test
    fun `favorites migration should collapse duplicates that appear after stripping`() {
        val service = OpenRouterSettingsService()
        val state = OpenRouterSettings(
            favoriteModels = mutableListOf(
                "openai/gpt-4o",
                "openai/gpt-4o:extended",
                "openai/gpt-4o:thinking"
            )
        )

        service.loadState(state)

        assertEquals(listOf("openai/gpt-4o"), service.getState().favoriteModels)
    }
}
