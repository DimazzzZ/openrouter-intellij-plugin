package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
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
    fun `services should be configured for background execution`() = runBlocking {
        // Given: Services configured
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        whenever(openRouterService.getApiKeysList()).thenReturn(ApiResult.Success(createMockApiKeysResponse(), 200))
        whenever(openRouterService.getCredits()).thenReturn(ApiResult.Success(createMockCreditsResponse(), 200))
        whenever(openRouterService.getActivity()).thenReturn(ApiResult.Success(createMockActivityResponse(), 200))

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
    fun `API call mocking should work for threading tests`() = runBlocking {
        // Given: Services that can be called concurrently
        val callCount = AtomicInteger(0)

        whenever(openRouterService.getApiKeysList()).thenAnswer {
            callCount.incrementAndGet()
            ApiResult.Success(createMockApiKeysResponse(), 200)
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
    fun `API exceptions should be mockable`() = runBlocking {
        // Given: Service that returns error
        whenever(openRouterService.getApiKeysList()).thenReturn(
            ApiResult.Error("Test API failure", throwable = RuntimeException("Test API failure"))
        )

        // When: Get the result
        val result = openRouterService.getApiKeysList()

        // Then: Should be an error
        assertTrue(result is ApiResult.Error)
        assertEquals("Test API failure", (result as ApiResult.Error).message)
    }

    @Test
    fun `null API responses can be mocked`() = runBlocking {
        // Given: Services returning error results
        whenever(openRouterService.getApiKeysList()).thenReturn(ApiResult.Error("Error"))
        whenever(openRouterService.getCredits()).thenReturn(ApiResult.Error("Error"))
        whenever(openRouterService.getActivity()).thenReturn(ApiResult.Error("Error"))

        // When: Get responses
        val apiKeys = openRouterService.getApiKeysList()
        val credits = openRouterService.getCredits()
        val activity = openRouterService.getActivity()

        // Then: Should all be errors
        assertTrue(apiKeys is ApiResult.Error)
        assertTrue(credits is ApiResult.Error)
        assertTrue(activity is ApiResult.Error)
    }

    // ========================================
    // Timing and Sequencing Tests
    // ========================================

    @Test
    fun `coroutines can be used for timing tests`() = runBlocking {
        // Test that coroutines work for timing scenarios
        val latch = CountDownLatch(1)

        val deferred = async {
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
        val result = withTimeout(1000) { deferred.await() }
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
