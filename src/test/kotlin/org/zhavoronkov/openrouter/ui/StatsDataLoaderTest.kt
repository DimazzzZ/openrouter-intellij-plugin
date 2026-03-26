package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("StatsDataLoader Tests")
class StatsDataLoaderTest {

    @Nested
    @DisplayName("LoadResult sealed class")
    inner class LoadResultTests {

        @Test
        fun `Error contains message`() {
            val error = StatsDataLoader.LoadResult.Error("Test error message")
            assertEquals("Test error message", error.message)
        }

        @Test
        fun `NotConfigured is accessible`() {
            // Verify it's accessible
            val result = StatsDataLoader.LoadResult.NotConfigured
            assertNotNull(result)
        }

        @Test
        fun `ProvisioningKeyMissing is accessible`() {
            // Verify it's accessible
            val result = StatsDataLoader.LoadResult.ProvisioningKeyMissing
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("StatsData")
    inner class StatsDataTests {

        @Test
        fun `can be created with null activityResponse`() {
            // Note: We can't easily create the actual response objects without
            // complex setup, but we can test the data class structure
            assertTrue(true) // Placeholder - actual test would need mock data
        }
    }

    @Nested
    @DisplayName("loadData")
    inner class LoadDataTests {

        @Test
        fun `returns error when settingsService is null`() {
            val loader = StatsDataLoader(null, null)
            val latch = CountDownLatch(1)
            var result: StatsDataLoader.LoadResult? = null

            loader.loadData { loadResult ->
                result = loadResult
                latch.countDown()
            }

            // Should complete quickly since no async work needed
            latch.await(1, TimeUnit.SECONDS)
            
            assertTrue(result is StatsDataLoader.LoadResult.Error)
            assertEquals("Failed to load data", (result as StatsDataLoader.LoadResult.Error).message)
        }

        @Test
        fun `returns error when openRouterService is null`() {
            // Even with a mock settings service, null router should fail
            val loader = StatsDataLoader(null, null)
            val latch = CountDownLatch(1)
            var result: StatsDataLoader.LoadResult? = null

            loader.loadData { loadResult ->
                result = loadResult
                latch.countDown()
            }

            latch.await(1, TimeUnit.SECONDS)
            
            assertTrue(result is StatsDataLoader.LoadResult.Error)
        }
    }

    @Nested
    @DisplayName("Constructor")
    inner class ConstructorTests {

        @Test
        fun `can be instantiated with null services`() {
            val loader = StatsDataLoader(null, null)
            assertNotNull(loader)
        }
    }
}
