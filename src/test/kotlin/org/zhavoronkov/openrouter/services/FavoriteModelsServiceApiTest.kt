package org.zhavoronkov.openrouter.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ModelsCountData
import org.zhavoronkov.openrouter.models.ModelsCountResponse
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager

/**
 * B11 coverage batch - API-backed and cache paths of FavoriteModelsService.
 *
 * The existing FavoriteModelsServiceTest injects only a mocked settings service,
 * leaving getAvailableModels / getModelsCount (both routed through OpenRouterService)
 * and the cache-hit / minimal-model-fallback / dispose paths uncovered. This suite
 * injects a mocked OpenRouterService too so those domain paths become deterministic
 * without any IntelliJ platform initialization or real network.
 */
@DisplayName("FavoriteModelsService API + cache paths")
class FavoriteModelsServiceApiTest {

    private lateinit var service: FavoriteModelsService
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockFavoriteModelsManager: FavoriteModelsManager
    private lateinit var mockRouterService: OpenRouterService
    private val favoriteModelsStorage = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        mockSettingsService = Mockito.mock(OpenRouterSettingsService::class.java)
        mockFavoriteModelsManager = Mockito.mock(FavoriteModelsManager::class.java)
        mockRouterService = Mockito.mock(OpenRouterService::class.java)

        favoriteModelsStorage.clear()
        Mockito.`when`(mockFavoriteModelsManager.getFavoriteModels()).thenAnswer { favoriteModelsStorage.toList() }
        Mockito.doAnswer { invocation ->
            val models = invocation.getArgument<List<String>>(0)
            favoriteModelsStorage.clear()
            favoriteModelsStorage.addAll(models)
            null
        }.`when`(mockFavoriteModelsManager).setFavoriteModels(anyList())
        Mockito.`when`(mockFavoriteModelsManager.isFavoriteModel(anyString())).thenAnswer { invocation ->
            favoriteModelsStorage.contains(invocation.getArgument<String>(0))
        }
        Mockito.`when`(mockSettingsService.favoriteModelsManager).thenReturn(mockFavoriteModelsManager)

        service = FavoriteModelsService(mockSettingsService, mockRouterService)
        service.clearCache()
    }

    private fun model(id: String) = OpenRouterModelInfo(
        id = id, name = id, created = 0L, description = null, architecture = null,
        topProvider = null, pricing = null, contextLength = null, perRequestLimits = null
    )

    @Nested
    @DisplayName("getAvailableModels")
    inner class GetAvailableModels {

        @Test
        fun `fetches and caches models on success`() = runBlocking {
            val models = listOf(model("openai/gpt-4"), model("anthropic/claude"))
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(models), 200))
            val result = service.getAvailableModels()
            assertNotNull(result)
            assertEquals(2, result?.size)
            Mockito.verify(mockRouterService).getModels()
        }

        @Test
        fun `returns cached models without a second fetch`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(listOf(model("m1"))), 200))
            service.getAvailableModels()
            val second = service.getAvailableModels()
            assertEquals(1, second?.size)
            Mockito.verify(mockRouterService, Mockito.times(1)).getModels()
        }

        @Test
        fun `forceRefresh bypasses a valid cache`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(listOf(model("m1"))), 200))
            service.getAvailableModels()
            service.getAvailableModels(forceRefresh = true)
            Mockito.verify(mockRouterService, Mockito.times(2)).getModels()
        }

        @Test
        fun `returns null when API reports an error`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Error("boom", statusCode = 500))
            assertNull(service.getAvailableModels())
        }

        @Test
        fun `returns null when the fetch throws IOException`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenAnswer { throw java.io.IOException("network down") }
            assertNull(service.getAvailableModels())
        }

        @Test
        fun `returns null when the fetch throws IllegalStateException`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenThrow(IllegalStateException("bad state"))
            assertNull(service.getAvailableModels())
        }

        @Test
        fun `returns null when the fetch surfaces a TimeoutException`() = runBlocking {
            // An underlying HTTP client can surface java.util.concurrent.TimeoutException
            // directly (distinct from kotlinx's TimeoutCancellationException); this drives
            // the dedicated TimeoutException catch arm.
            Mockito.`when`(mockRouterService.getModels())
                .thenAnswer { throw java.util.concurrent.TimeoutException("slow upstream") }
            assertNull(service.getAvailableModels())
        }
    }

    @Nested
    @DisplayName("getModelById / getFavoriteModels cache interplay")
    inner class CacheLookup {

        @Test
        fun `getModelById returns cached model when present`() = runBlocking {
            val target = model("openai/gpt-4")
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(listOf(target)), 200))
            service.getAvailableModels()
            assertEquals("openai/gpt-4", service.getModelById("openai/gpt-4")?.id)
        }

        @Test
        fun `getModelById returns null when cache is empty or missing`() {
            assertNull(service.getModelById("missing/model"))
        }

        @Test
        fun `getFavoriteModels resolves from cache when available`() = runBlocking {
            val cached = model("openai/gpt-4")
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(listOf(cached)), 200))
            service.getAvailableModels()
            favoriteModelsStorage.add("openai/gpt-4")
            val favorites = service.getFavoriteModels()
            assertEquals(1, favorites.size)
            assertEquals("openai/gpt-4", favorites[0].id)
        }

        @Test
        fun `getFavoriteModels falls back to a minimal model when not cached`() {
            favoriteModelsStorage.add("unknown/model")
            val favorites = service.getFavoriteModels()
            assertEquals(1, favorites.size)
            assertEquals("unknown/model", favorites[0].id)
            assertEquals("unknown/model", favorites[0].name)
            assertNull(favorites[0].pricing)
        }
    }

    @Nested
    @DisplayName("getModelsCount")
    inner class GetModelsCount {

        @Test
        fun `returns count on success`() = runBlocking {
            Mockito.`when`(mockRouterService.getModelsCount())
                .thenReturn(ApiResult.Success(ModelsCountResponse(ModelsCountData(321)), 200))
            assertEquals(321, service.getModelsCount())
        }

        @Test
        fun `returns null on API error`() = runBlocking {
            Mockito.`when`(mockRouterService.getModelsCount())
                .thenReturn(ApiResult.Error("nope", statusCode = 404))
            assertNull(service.getModelsCount())
        }

        @Test
        fun `returns null on IOException`() = runBlocking {
            Mockito.`when`(mockRouterService.getModelsCount())
                .thenAnswer { throw java.io.IOException("offline") }
            assertNull(service.getModelsCount())
        }

        @Test
        fun `returns null on unexpected throwable`() = runBlocking {
            Mockito.`when`(mockRouterService.getModelsCount())
                .thenThrow(RuntimeException("unexpected"))
            assertNull(service.getModelsCount())
        }
    }

    @Nested
    @DisplayName("reorderFavorites bounds (areIndicesValid)")
    inner class Reorder {

        @Test
        fun `reorders when indices are valid`() {
            favoriteModelsStorage.addAll(listOf("a", "b", "c"))
            service.reorderFavorites(0, 2)
            assertEquals(listOf("b", "c", "a"), favoriteModelsStorage.toList())
        }

        @Test
        fun `ignores an out-of-range fromIndex`() {
            favoriteModelsStorage.addAll(listOf("a", "b"))
            service.reorderFavorites(5, 0)
            assertEquals(listOf("a", "b"), favoriteModelsStorage.toList())
        }

        @Test
        fun `ignores a negative toIndex`() {
            favoriteModelsStorage.addAll(listOf("a", "b"))
            service.reorderFavorites(0, -1)
            assertEquals(listOf("a", "b"), favoriteModelsStorage.toList())
        }
    }

    @Nested
    @DisplayName("dispose")
    inner class Dispose {

        @Test
        fun `dispose clears the model cache`() = runBlocking {
            Mockito.`when`(mockRouterService.getModels())
                .thenReturn(ApiResult.Success(OpenRouterModelsResponse(listOf(model("m1"))), 200))
            service.getAvailableModels()
            service.dispose()
            assertNull(service.getModelById("m1"))
        }
    }
}
