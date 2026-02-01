package org.zhavoronkov.openrouter.aiassistant

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

@DisplayName("OpenRouter Model Provider Tests")
class OpenRouterModelProviderTest {

    @Nested
    @DisplayName("getAvailableModels")
    inner class GetAvailableModelsTests {

        @Test
        @DisplayName("should return models from /models endpoint when configured")
        fun shouldReturnModelsFromEndpoint() = runBlocking {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)

            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("api-key")

            val model = OpenRouterModelInfo(
                id = "xiaomi/mimo-v2-flash",
                name = "MiMo v2 Flash",
                created = 1L,
                description = "Fast model",
                contextLength = 131072,
                architecture = null,
                topProvider = null,
                pricing = null,
                perRequestLimits = null
            )
            Mockito.`when`(favoriteModelsService.getAvailableModels(forceRefresh = false))
                .thenReturn(listOf(model))

            val provider = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)

            val models = provider.getAvailableModels()

            assertEquals(1, models.size)
            assertEquals("xiaomi/mimo-v2-flash", models.first().id)
            assertEquals("MiMo v2 Flash", models.first().name)
            assertEquals("Fast model", models.first().description)
            assertEquals(131072, models.first().contextLength)
        }

        @Test
        @DisplayName("should fall back to default models when /models returns empty")
        fun shouldFallbackWhenEmpty() = runBlocking {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)

            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("api-key")
            Mockito.`when`(favoriteModelsService.getAvailableModels(forceRefresh = false))
                .thenReturn(emptyList())

            val provider = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)

            val models = provider.getAvailableModels()

            assertTrue(models.isNotEmpty())
            assertTrue(models.any { it.id == "openai/gpt-4o" })
        }

        @Test
        @DisplayName("should return empty list when not configured")
        fun shouldReturnEmptyWhenNotConfigured() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)

            Mockito.`when`(settingsService.isConfigured()).thenReturn(false)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("")

            val provider = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)

            val models = provider.getAvailableModels()

            assertTrue(models.isEmpty())
        }
    }

    @Nested
    @DisplayName("testConnection")
    inner class TestConnectionTests {

        @Test
        @DisplayName("should return true when testConnection succeeds")
        fun shouldReturnTrueOnSuccess() = runBlocking {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)

            Mockito.`when`(settingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("api-key")
            Mockito.`when`(openRouterService.testConnection()).thenReturn(ApiResult.Success(true, 200))

            val provider = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)

            assertTrue(provider.testConnection())
        }

        @Test
        @DisplayName("should return false when not configured")
        fun shouldReturnFalseWhenNotConfigured() {
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val favoriteModelsService = mock(FavoriteModelsService::class.java)
            val openRouterService = mock(OpenRouterService::class.java)

            Mockito.`when`(settingsService.isConfigured()).thenReturn(false)
            Mockito.`when`(settingsService.getApiKey()).thenReturn("")

            val provider = OpenRouterModelProvider(openRouterService, settingsService, favoriteModelsService)

            assertFalse(provider.testConnection())
        }
    }
}
