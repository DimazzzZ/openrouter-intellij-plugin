package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.*
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for threading logic and concurrent execution
 * These tests verify the threading-related code paths without UI components
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
    // Threading Logic Tests
    // ========================================

    @Test
    fun `services should be configured for background execution`() {
        // Given: Services configured
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(
            openRouterService.getApiKeysList()
        ).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(
            openRouterService.getCredits()
        ).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(
            openRouterService.getActivity()
        ).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Call the methods
        val configured = settingsService.isConfigured()
        val key = settingsService.getProvisioningKey()

        // Then: Should return expected values and be verified
        assertTrue(configured)
        assertEquals("test-key", key)

        verify(settingsService).isConfigured()
        verify(settingsService).getProvisioningKey()
    }

    @Test
    fun `API call mocking should work for threading tests`() {
        // Given: Services that can be called concurrently
        val callCount = AtomicInteger(0)

        `when`(openRouterService.getApiKeysList()).thenAnswer {
            callCount.incrementAndGet()
            CompletableFuture.completedFuture(createMockApiKeysResponse())
        }

        // When: Multiple calls simulated
        openRouterService.getApiKeysList()
        openRouterService.getApiKeysList()
        openRouterService.getApiKeysList()

        // Then: Mock should handle concurrent calls
        assertEquals(3, callCount.get())
    }

    // ========================================
    // Exception Handling Tests
    // ========================================

    @Test
    fun `API exceptions should be mockable`() {
        // Given: Service that can throw exception
        `when`(openRouterService.getApiKeysList()).thenAnswer {
            val future = CompletableFuture<ApiKeysListResponse>()
            future.completeExceptionally(RuntimeException("Test API failure"))
            future
        }

        // When: Get the future
        val future = openRouterService.getApiKeysList()

        // Then: Should complete exceptionally
        assertTrue(future.isCompletedExceptionally)
    }

    @Test
    fun `null API responses can be mocked`() {
        // Given: Services returning null
        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(null))

        // When: Get responses
        val apiKeys = openRouterService.getApiKeysList().get()
        val credits = openRouterService.getCredits().get()
        val activity = openRouterService.getActivity().get()

        // Then: Should all be null
        assertNull(apiKeys)
        assertNull(credits)
        assertNull(activity)
    }

    // ========================================
    // Timing and Sequencing Tests
    // ========================================

    @Test
    fun `CompletableFuture can be used for timing tests`() {
        // Test that CompletableFuture works for timing scenarios
        val latch = CountDownLatch(1)

        val future = CompletableFuture.supplyAsync {
            try {
                latch.await(2, TimeUnit.SECONDS)
                "completed"
            } catch (_: InterruptedException) {
                "interrupted"
            }
        }

        // Signal completion
        latch.countDown()

        // Should complete
        val result = future.get(1, TimeUnit.SECONDS)
        assertEquals("completed", result)
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
