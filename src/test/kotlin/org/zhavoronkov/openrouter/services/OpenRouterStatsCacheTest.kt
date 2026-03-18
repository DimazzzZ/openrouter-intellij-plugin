package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse

/**
 * Tests for OpenRouterStatsCache service.
 *
 * Note: These tests verify the class structure, Disposable implementation,
 * and basic cache operations without requiring IntelliJ platform initialization.
 */
@DisplayName("OpenRouterStatsCache Tests")
class OpenRouterStatsCacheTest {

    @Nested
    @DisplayName("Service Structure Tests")
    inner class ServiceStructureTests {

        @Test
        @DisplayName("OpenRouterStatsCache should implement Disposable")
        fun testImplementsDisposable() {
            assertTrue(
                com.intellij.openapi.Disposable::class.java.isAssignableFrom(OpenRouterStatsCache::class.java),
                "OpenRouterStatsCache must implement Disposable for dynamic plugin support"
            )
        }

        @Test
        @DisplayName("OpenRouterStatsCache should have getInstance companion method")
        fun testHasGetInstanceMethod() {
            val method = OpenRouterStatsCache.Companion::class.java.getDeclaredMethod("getInstance")
            assertNotNull(method, "OpenRouterStatsCache should have getInstance() method")
        }

        @Test
        @DisplayName("OpenRouterStatsCache should have refresh method")
        fun testHasRefreshMethod() {
            val method = OpenRouterStatsCache::class.java.getDeclaredMethod("refresh")
            assertNotNull(method, "OpenRouterStatsCache should have refresh() method")
        }

        @Test
        @DisplayName("OpenRouterStatsCache should have clearCache method")
        fun testHasClearCacheMethod() {
            val method = OpenRouterStatsCache::class.java.getDeclaredMethod("clearCache")
            assertNotNull(method, "OpenRouterStatsCache should have clearCache() method")
        }

        @Test
        @DisplayName("OpenRouterStatsCache should have dispose method")
        fun testHasDisposeMethod() {
            val method = OpenRouterStatsCache::class.java.getDeclaredMethod("dispose")
            assertNotNull(method, "OpenRouterStatsCache should have dispose() method")
        }
    }

    @Nested
    @DisplayName("Cache Accessor Tests")
    inner class CacheAccessorTests {

        @Test
        @DisplayName("getCachedCredits should return null initially")
        fun testGetCachedCreditsInitiallyNull() {
            val cache = OpenRouterStatsCache()
            assertNull(cache.getCachedCredits(), "getCachedCredits should return null initially")
        }

        @Test
        @DisplayName("getCachedActivity should return null initially")
        fun testGetCachedActivityInitiallyNull() {
            val cache = OpenRouterStatsCache()
            assertNull(cache.getCachedActivity(), "getCachedActivity should return null initially")
        }

        @Test
        @DisplayName("getCachedApiKeys should return null initially")
        fun testGetCachedApiKeysInitiallyNull() {
            val cache = OpenRouterStatsCache()
            assertNull(cache.getCachedApiKeys(), "getCachedApiKeys should return null initially")
        }

        @Test
        @DisplayName("getLastError should return null initially")
        fun testGetLastErrorInitiallyNull() {
            val cache = OpenRouterStatsCache()
            assertNull(cache.getLastError(), "getLastError should return null initially")
        }

        @Test
        @DisplayName("getLastUpdateTimestamp should return 0 initially")
        fun testGetLastUpdateTimestampInitiallyZero() {
            val cache = OpenRouterStatsCache()
            assertEquals(0L, cache.getLastUpdateTimestamp(), "getLastUpdateTimestamp should return 0 initially")
        }

        @Test
        @DisplayName("isLoading should return false initially")
        fun testIsLoadingInitiallyFalse() {
            val cache = OpenRouterStatsCache()
            assertFalse(cache.isLoading(), "isLoading should return false initially")
        }

        @Test
        @DisplayName("hasCachedData should return false initially")
        fun testHasCachedDataInitiallyFalse() {
            val cache = OpenRouterStatsCache()
            assertFalse(cache.hasCachedData(), "hasCachedData should return false initially")
        }
    }

    @Nested
    @DisplayName("Clear Cache Tests")
    inner class ClearCacheTests {

        @Test
        @DisplayName("clearCache should reset all cached data")
        fun testClearCacheResetsAllData() {
            val cache = OpenRouterStatsCache()

            // Clear the cache (should work even when empty)
            cache.clearCache()

            // Verify everything is reset
            assertNull(cache.getCachedCredits(), "Credits should be null after clear")
            assertNull(cache.getCachedActivity(), "Activity should be null after clear")
            assertNull(cache.getCachedApiKeys(), "ApiKeys should be null after clear")
            assertNull(cache.getLastError(), "LastError should be null after clear")
            assertEquals(0L, cache.getLastUpdateTimestamp(), "Timestamp should be 0 after clear")
        }

        @Test
        @DisplayName("clearCache should be idempotent")
        fun testClearCacheIdempotent() {
            val cache = OpenRouterStatsCache()

            // Call clear multiple times - should not throw
            cache.clearCache()
            cache.clearCache()
            cache.clearCache()

            // Still valid state
            assertFalse(cache.hasCachedData())
        }
    }

    @Nested
    @DisplayName("Dispose Tests")
    inner class DisposeTests {

        @Test
        @DisplayName("dispose should clear cache and not throw")
        fun testDisposeNotThrows() {
            val cache = OpenRouterStatsCache()

            // Dispose should not throw
            cache.dispose()

            // Cache should be cleared
            assertNull(cache.getCachedCredits())
            assertNull(cache.getCachedActivity())
        }

        @Test
        @DisplayName("dispose should be safe to call multiple times")
        fun testDisposeMultipleCalls() {
            val cache = OpenRouterStatsCache()

            // Multiple dispose calls should be safe
            cache.dispose()
            cache.dispose()
            cache.dispose()
        }
    }

    @Nested
    @DisplayName("Method Existence Tests")
    inner class MethodExistenceTests {

        @Test
        @DisplayName("refresh method should exist and be callable")
        fun testRefreshMethodExists() {
            // Verify the refresh() method exists (it requires IntelliJ platform to run)
            val method = OpenRouterStatsCache::class.java.getDeclaredMethod("refresh")
            assertNotNull(method, "refresh() method should exist")
        }
    }

    @Nested
    @DisplayName("UpdateFromPopup Tests")
    inner class UpdateFromPopupTests {

        private fun createTestCreditsResponse(): CreditsResponse {
            return CreditsResponse(
                data = CreditsData(totalCredits = 100.0, totalUsage = 25.0)
            )
        }

        private fun createTestActivityResponse(): ActivityResponse {
            return ActivityResponse(
                data = listOf(
                    ActivityData(
                        date = "2025-03-18",
                        model = "gpt-4",
                        modelPermaslug = "gpt-4",
                        endpointId = "ep-1",
                        providerName = "openai",
                        usage = 5.0,
                        byokUsageInference = null,
                        requests = 10,
                        promptTokens = 100,
                        completionTokens = 50,
                        reasoningTokens = null
                    )
                )
            )
        }

        private fun createTestApiKeysResponse(): ApiKeysListResponse {
            return ApiKeysListResponse(
                data = listOf(
                    ApiKeyInfo(
                        name = "Test Key",
                        label = "test",
                        limit = 1000.0,
                        usage = 25.0,
                        disabled = false,
                        createdAt = "2025-01-01T00:00:00Z",
                        updatedAt = "2025-01-01T00:00:00Z",
                        hash = "abc123"
                    )
                )
            )
        }

        @Test
        @DisplayName("updateFromPopup should store credits data")
        fun testUpdateFromPopupStoresCredits() {
            val cache = OpenRouterStatsCache()
            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)

            assertNotNull(cache.getCachedCredits(), "Credits should be stored")
            assertEquals(100.0, cache.getCachedCredits()?.totalCredits)
            assertEquals(25.0, cache.getCachedCredits()?.totalUsage)
        }

        @Test
        @DisplayName("updateFromPopup should store activity data when provided")
        fun testUpdateFromPopupStoresActivity() {
            val cache = OpenRouterStatsCache()
            val creditsResponse = createTestCreditsResponse()
            val activityResponse = createTestActivityResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            cache.updateFromPopup(creditsResponse, activityResponse, apiKeysResponse)

            assertNotNull(cache.getCachedActivity(), "Activity should be stored")
            assertEquals(1, cache.getCachedActivity()?.size)
            assertEquals("gpt-4", cache.getCachedActivity()?.firstOrNull()?.model)
        }

        @Test
        @DisplayName("updateFromPopup should store null activity when not provided")
        fun testUpdateFromPopupStoresNullActivity() {
            val cache = OpenRouterStatsCache()
            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)

            assertNull(cache.getCachedActivity(), "Activity should be null when not provided")
        }

        @Test
        @DisplayName("updateFromPopup should store API keys")
        fun testUpdateFromPopupStoresApiKeys() {
            val cache = OpenRouterStatsCache()
            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)

            assertNotNull(cache.getCachedApiKeys(), "API keys should be stored")
            assertEquals(1, cache.getCachedApiKeys()?.data?.size)
            assertEquals("Test Key", cache.getCachedApiKeys()?.data?.firstOrNull()?.name)
        }

        @Test
        @DisplayName("updateFromPopup should update timestamp")
        fun testUpdateFromPopupUpdatesTimestamp() {
            val cache = OpenRouterStatsCache()
            assertEquals(0L, cache.getLastUpdateTimestamp(), "Timestamp should be 0 initially")

            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            val beforeUpdate = System.currentTimeMillis()
            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)
            val afterUpdate = System.currentTimeMillis()

            assertTrue(
                cache.getLastUpdateTimestamp() in beforeUpdate..afterUpdate,
                "Timestamp should be updated to current time"
            )
        }

        @Test
        @DisplayName("updateFromPopup should clear last error")
        fun testUpdateFromPopupClearsError() {
            val cache = OpenRouterStatsCache()
            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            // First update should clear any error
            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)

            assertNull(cache.getLastError(), "Last error should be null after successful update")
        }

        @Test
        @DisplayName("updateFromPopup should make hasCachedData return true")
        fun testUpdateFromPopupMakesHasCachedDataTrue() {
            val cache = OpenRouterStatsCache()
            assertFalse(cache.hasCachedData(), "Should not have cached data initially")

            val creditsResponse = createTestCreditsResponse()
            val apiKeysResponse = createTestApiKeysResponse()

            cache.updateFromPopup(creditsResponse, null, apiKeysResponse)

            assertTrue(cache.hasCachedData(), "Should have cached data after update")
        }

        @Test
        @DisplayName("updateFromPopup should overwrite previous data")
        fun testUpdateFromPopupOverwritesPreviousData() {
            val cache = OpenRouterStatsCache()

            // First update
            val firstCredits = CreditsResponse(data = CreditsData(totalCredits = 50.0, totalUsage = 10.0))
            val firstApiKeys = createTestApiKeysResponse()
            cache.updateFromPopup(firstCredits, null, firstApiKeys)

            assertEquals(50.0, cache.getCachedCredits()?.totalCredits)

            // Second update with different values
            val secondCredits = CreditsResponse(data = CreditsData(totalCredits = 200.0, totalUsage = 100.0))
            cache.updateFromPopup(secondCredits, null, firstApiKeys)

            assertEquals(200.0, cache.getCachedCredits()?.totalCredits)
            assertEquals(100.0, cache.getCachedCredits()?.totalUsage)
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    inner class ThreadSafetyTests {

        @Test
        @DisplayName("Cache should handle concurrent access to accessors")
        fun testConcurrentAccessToAccessors() {
            val cache = OpenRouterStatsCache()

            // Run multiple threads accessing cache state
            val threads = (1..10).map {
                Thread {
                    repeat(100) {
                        cache.getCachedCredits()
                        cache.getCachedActivity()
                        cache.getCachedApiKeys()
                        cache.getLastError()
                        cache.getLastUpdateTimestamp()
                        cache.isLoading()
                        cache.hasCachedData()
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // If we get here without exceptions, thread safety is maintained
        }

        @Test
        @DisplayName("Cache should handle concurrent clearCache calls")
        fun testConcurrentClearCache() {
            val cache = OpenRouterStatsCache()

            // Run multiple threads calling clearCache
            val threads = (1..10).map {
                Thread {
                    repeat(100) {
                        cache.clearCache()
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // If we get here without exceptions, thread safety is maintained
        }
    }
}
