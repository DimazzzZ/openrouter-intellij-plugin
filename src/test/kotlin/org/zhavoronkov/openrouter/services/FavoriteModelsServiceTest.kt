package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

/**
 * Tests for FavoriteModelsService
 *
 * Note: These tests use a direct instance of FavoriteModelsService with a mocked
 * OpenRouterSettingsService to avoid IntelliJ Platform initialization requirements.
 */
@DisplayName("Favorite Models Service Tests")
class FavoriteModelsServiceTest {

    private lateinit var service: FavoriteModelsService
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private val favoriteModelsStorage = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        // Create mock settings service
        mockSettingsService = mock(OpenRouterSettingsService::class.java)

        // Setup mock behavior to use in-memory storage
        favoriteModelsStorage.clear()
        `when`(mockSettingsService.getFavoriteModels()).thenAnswer { favoriteModelsStorage.toList() }
        doAnswer { invocation ->
            val models = invocation.getArgument<List<String>>(0)
            favoriteModelsStorage.clear()
            favoriteModelsStorage.addAll(models)
            null
        }.`when`(mockSettingsService).setFavoriteModels(anyList())
        `when`(mockSettingsService.isFavoriteModel(anyString())).thenAnswer { invocation ->
            val modelId = invocation.getArgument<String>(0)
            favoriteModelsStorage.contains(modelId)
        }

        // Create service instance with mocked settings
        service = FavoriteModelsService(mockSettingsService)
        service.clearCache()
    }

    @Nested
    @DisplayName("Favorite Management")
    inner class FavoriteManagement {

        @Test
        fun `should add favorite model`() {
            val model = createTestModel("openai/gpt-4")
            
            val added = service.addFavoriteModel(model)
            
            assertTrue(added, "Model should be added")
            assertTrue(service.isFavorite("openai/gpt-4"), "Model should be in favorites")
        }

        @Test
        fun `should not add duplicate favorite`() {
            val model = createTestModel("openai/gpt-4")
            
            service.addFavoriteModel(model)
            val addedAgain = service.addFavoriteModel(model)
            
            assertFalse(addedAgain, "Duplicate should not be added")
            assertEquals(1, service.getFavoriteModels().size, "Should have only one favorite")
        }

        @Test
        fun `should remove favorite model`() {
            val model = createTestModel("openai/gpt-4")
            service.addFavoriteModel(model)
            
            val removed = service.removeFavoriteModel("openai/gpt-4")
            
            assertTrue(removed, "Model should be removed")
            assertFalse(service.isFavorite("openai/gpt-4"), "Model should not be in favorites")
        }

        @Test
        fun `should return false when removing non-existent favorite`() {
            val removed = service.removeFavoriteModel("non-existent")
            
            assertFalse(removed, "Should return false for non-existent model")
        }

        @Test
        fun `should clear all favorites`() {
            service.addFavoriteModel(createTestModel("openai/gpt-4"))
            service.addFavoriteModel(createTestModel("anthropic/claude-3"))
            
            service.clearAllFavorites()
            
            assertEquals(0, service.getFavoriteModels().size, "All favorites should be cleared")
        }
    }

    @Nested
    @DisplayName("Favorite Ordering")
    inner class FavoriteOrdering {

        @Test
        fun `should preserve favorite order`() {
            val models = listOf(
                createTestModel("openai/gpt-4"),
                createTestModel("anthropic/claude-3"),
                createTestModel("google/gemini-pro")
            )
            
            service.setFavoriteModels(models)
            val favorites = service.getFavoriteModels()
            
            assertEquals(3, favorites.size, "Should have 3 favorites")
            assertEquals("openai/gpt-4", favorites[0].id, "First model should be gpt-4")
            assertEquals("anthropic/claude-3", favorites[1].id, "Second model should be claude-3")
            assertEquals("google/gemini-pro", favorites[2].id, "Third model should be gemini-pro")
        }

        @Test
        fun `should reorder favorites`() {
            val models = listOf(
                createTestModel("openai/gpt-4"),
                createTestModel("anthropic/claude-3"),
                createTestModel("google/gemini-pro")
            )
            service.setFavoriteModels(models)
            
            service.reorderFavorites(0, 2) // Move first to last
            val favorites = service.getFavoriteModels()
            
            assertEquals("anthropic/claude-3", favorites[0].id, "First should now be claude-3")
            assertEquals("google/gemini-pro", favorites[1].id, "Second should now be gemini-pro")
            assertEquals("openai/gpt-4", favorites[2].id, "Third should now be gpt-4")
        }

        @Test
        fun `should handle invalid reorder indices`() {
            val models = listOf(
                createTestModel("openai/gpt-4"),
                createTestModel("anthropic/claude-3")
            )
            service.setFavoriteModels(models)
            
            service.reorderFavorites(-1, 1) // Invalid from index
            service.reorderFavorites(0, 10) // Invalid to index
            
            val favorites = service.getFavoriteModels()
            assertEquals(2, favorites.size, "Should still have 2 favorites")
            assertEquals("openai/gpt-4", favorites[0].id, "Order should be unchanged")
        }
    }

    @Nested
    @DisplayName("State Persistence")
    inner class StatePersistence {

        @Test
        fun `should persist favorites to settings`() {
            val models = listOf(
                createTestModel("openai/gpt-4"),
                createTestModel("anthropic/claude-3")
            )

            service.setFavoriteModels(models)

            val savedIds = mockSettingsService.getFavoriteModels()
            assertEquals(2, savedIds.size, "Should have 2 saved favorites")
            assertTrue(savedIds.contains("openai/gpt-4"), "Should contain gpt-4")
            assertTrue(savedIds.contains("anthropic/claude-3"), "Should contain claude-3")
        }

        @Test
        fun `should load favorites from settings`() {
            mockSettingsService.setFavoriteModels(listOf("openai/gpt-4", "anthropic/claude-3"))

            val favorites = service.getFavoriteModels()

            assertEquals(2, favorites.size, "Should load 2 favorites")
            assertEquals("openai/gpt-4", favorites[0].id, "First should be gpt-4")
            assertEquals("anthropic/claude-3", favorites[1].id, "Second should be claude-3")
        }
    }

    @Nested
    @DisplayName("Cache Management")
    inner class CacheManagement {

        @Test
        fun `should clear cache`() {
            service.clearCache()
            
            // Cache should be empty - this is verified by the service implementation
            // We can't directly test the cache state, but we can verify it doesn't crash
            assertDoesNotThrow { service.clearCache() }
        }
    }

    private fun createTestModel(id: String): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = id,
            name = id,
            created = System.currentTimeMillis() / 1000,
            description = "Test model",
            architecture = null,
            topProvider = null,
            pricing = null,
            contextLength = 8192,
            perRequestLimits = null
        )
    }
}

