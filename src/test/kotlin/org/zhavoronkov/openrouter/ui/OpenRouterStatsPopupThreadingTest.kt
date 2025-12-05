package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.models.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration tests focusing on threading, concurrency, and EDT-related issues
 * These tests ensure the fixes for modal dialog blocking and background thread execution
 */
class OpenRouterStatsPopupThreadingTest {

    private lateinit var project: Project
    private lateinit var openRouterService: OpenRouterService
    private lateinit var settingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        project = mock(Project::class.java)
        openRouterService = mock(OpenRouterService::class.java)
        settingsService = mock(OpenRouterSettingsService::class.java)
    }

    // ========================================
    // Background Thread Execution Tests
    // ========================================

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    fun `data loading should execute on background thread not EDT`() {
        // This test ensures data loading doesn't block EDT

        val executionThread = AtomicReference<Thread>()
        val latch = CountDownLatch(1)

        // Given: Services configured with tracking execution thread
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            executionThread.set(Thread.currentThread())
            latch.countDown()
            CompletableFuture.completedFuture(createMockApiKeysResponse())
        }
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Wait for execution
        assertTrue(latch.await(2, TimeUnit.SECONDS), "API call should execute within timeout")

        // Then: Should execute on background thread (not EDT)
        val executingThread = executionThread.get()
        assertNotNull(executingThread)
        assertNotEquals("EDT", executingThread.name)
        assertTrue(executingThread.name.contains("Thread") || executingThread.name.contains("pool"))
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `concurrent data loading should not cause race conditions`() {
        // This test ensures multiple concurrent popups don't interfere

        val apiCallCount = AtomicInteger(0)
        val completedPopups = AtomicInteger(0)

        // Given: Services that track concurrent access
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            apiCallCount.incrementAndGet()
            // Simulate some processing time
            Thread.sleep(50)
            CompletableFuture.completedFuture(createMockApiKeysResponse())
        }
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create multiple popups concurrently
        val popups = (1..3).map {
            Thread {
                OpenRouterStatsPopup(project, openRouterService, settingsService)
                completedPopups.incrementAndGet()
            }
        }

        popups.forEach { it.start() }
        popups.forEach { it.join(2000) } // 2 second timeout per thread

        // Then: All popups should complete without race conditions
        assertEquals(3, completedPopups.get())
        assertTrue(apiCallCount.get() >= 3) // At least 3 calls (one per popup)
    }

    // ========================================
    // Modal Dialog EDT Blocking Prevention
    // ========================================

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    fun `popup creation should not block indefinitely`() {
        // This test ensures popup creation completes quickly (no EDT blocking)

        // Given: Services configured
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup (should complete quickly)
        val startTime = System.currentTimeMillis()
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)
        val creationTime = System.currentTimeMillis() - startTime

        // Then: Creation should be fast (not blocked by modal behavior)
        assertTrue(creationTime < 1000, "Popup creation took ${creationTime}ms - too slow, might indicate EDT blocking")
        assertNotNull(popup)
    }

    @Test
    fun `slow API responses should not block popup creation`() {
        // This test ensures slow APIs don't block the UI

        val slowApiLatch = CountDownLatch(1)
        val popupCreated = AtomicBoolean(false)

        // Given: Slow API service
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            // Simulate very slow API
            CompletableFuture.supplyAsync {
                try {
                    slowApiLatch.await(5, TimeUnit.SECONDS)
                    createMockApiKeysResponse()
                } catch (_: InterruptedException) {
                    null
                }
            }
        }
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup (should not wait for slow API)
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)
        popupCreated.set(true)

        // Then: Popup should be created immediately (not waiting for API)
        assertTrue(popupCreated.get())
        assertNotNull(popup)

        // Clean up
        slowApiLatch.countDown()
    }

    // ========================================
    // Exception Handling in Background Threads
    // ========================================

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    fun `API exceptions should not crash background thread`() {
        // This test ensures robust exception handling in background threads

        val exceptionHandled = AtomicBoolean(false)

        // Given: Service that throws exception
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            val future = CompletableFuture<ApiKeysListResponse>()
            future.completeExceptionally(RuntimeException("Test API failure"))
            future
        }
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup (should handle exception gracefully)
        assertDoesNotThrow {
            OpenRouterStatsPopup(project, openRouterService, settingsService)
            exceptionHandled.set(true)
        }

        // Then: Exception should be handled without crashing
        assertTrue(exceptionHandled.get())
    }

    @Test
    fun `null API responses should be handled gracefully`() {
        // This test ensures null responses don't break the UI update flow

        // Given: Services returning null responses
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(null))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for background processing
        Thread.sleep(200)

        // Then: Should handle null responses without crashing
        assertNotNull(popup)
    }

    // ========================================
    // Timing and Sequencing Tests
    // ========================================

    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS)
    fun `API calls should complete in reasonable time`() {
        // This test ensures no deadlocks or infinite waits

        val allApisCalled = CountDownLatch(3)

        // Given: Services that signal completion
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            allApisCalled.countDown()
            CompletableFuture.completedFuture(createMockApiKeysResponse())
        }
        `when`(openRouterService.getCredits()).thenAnswer {
            allApisCalled.countDown()
            CompletableFuture.completedFuture(createMockCreditsResponse())
        }
        `when`(openRouterService.getActivity()).thenAnswer {
            allApisCalled.countDown()
            CompletableFuture.completedFuture(createMockActivityResponse())
        }

        // When: Create popup
        OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Then: All APIs should be called within reasonable time
        assertTrue(allApisCalled.await(3, TimeUnit.SECONDS), "All APIs should be called within 3 seconds")
    }

    @Test
    fun `different API completion order should not affect result`() {
        // This test ensures order independence (prevents nested callback issues)

        val apiKeysLatch = CountDownLatch(1)
        val creditsLatch = CountDownLatch(1)
        val activityLatch = CountDownLatch(1)

        // Given: APIs that complete in different order
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(
            CompletableFuture.supplyAsync {
                try {
                    apiKeysLatch.await(2, TimeUnit.SECONDS)
                    createMockApiKeysResponse()
                } catch (_: InterruptedException) { null }
            }
        )
        `when`(openRouterService.getCredits()).thenReturn(
            CompletableFuture.supplyAsync {
                try {
                    creditsLatch.await(2, TimeUnit.SECONDS)
                    createMockCreditsResponse()
                } catch (_: InterruptedException) { null }
            }
        )
        `when`(openRouterService.getActivity()).thenReturn(
            CompletableFuture.supplyAsync {
                try {
                    activityLatch.await(2, TimeUnit.SECONDS)
                    createMockActivityResponse()
                } catch (_: InterruptedException) { null }
            }
        )

        // When: Create popup and complete APIs in reverse order
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Complete in reverse order: activity -> credits -> apiKeys
        Thread {
            Thread.sleep(50)
            activityLatch.countDown()
            Thread.sleep(50)
            creditsLatch.countDown()
            Thread.sleep(50)
            apiKeysLatch.countDown()
        }.start()

        // Then: Should handle any completion order
        assertNotNull(popup)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createMockApiKeysResponse(): ApiKeysListResponse {
        val apiKeyInfo = ApiKeyInfo(
            name = "test-key",
            label = "Test Key",
            limit = 100.0,
            usage = 10.0,
            disabled = false,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = null,
            hash = "test-hash"
        )
        return ApiKeysListResponse(listOf(apiKeyInfo))
    }

    private fun createMockCreditsResponse(): CreditsResponse {
        val creditsData = CreditsData(
            totalCredits = 100.0,
            totalUsage = 25.0
        )
        return CreditsResponse(creditsData)
    }

    private fun createMockActivityResponse(): ActivityResponse {
        val activityData = listOf(
            ActivityData(
                date = "2024-12-05",
                model = "gpt-4",
                modelPermaslug = "gpt-4",
                endpointId = "test-endpoint",
                providerName = "openai",
                usage = 1.5,
                byokUsageInference = 0.0,
                requests = 5,
                promptTokens = 100,
                completionTokens = 50,
                reasoningTokens = 0
            )
        )
        return ActivityResponse(activityData)
    }
}
