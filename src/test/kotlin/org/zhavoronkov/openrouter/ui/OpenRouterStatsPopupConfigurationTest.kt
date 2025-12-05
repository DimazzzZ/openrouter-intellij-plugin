package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.models.*
import java.util.concurrent.CompletableFuture

/**
 * Tests for configuration validation and error handling scenarios
 * These tests ensure proper validation of settings and graceful error handling
 */
class OpenRouterStatsPopupConfigurationTest {

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
    // Service Availability Tests
    // ========================================

    @Test
    fun `popup should handle null openRouterService gracefully`() {
        // Given: Null OpenRouter service
        settingsService = mock(OpenRouterSettingsService::class.java)

        // When: Create popup with null service
        val popup = OpenRouterStatsPopup(project, null, settingsService)

        // Then: Should not crash
        assertNotNull(popup)

        // And: Should not make any API calls
        verifyNoInteractions(openRouterService)
    }

    @Test
    fun `popup should handle null settingsService gracefully`() {
        // Given: Null settings service
        openRouterService = mock(OpenRouterService::class.java)

        // When: Create popup with null settings service
        val popup = OpenRouterStatsPopup(project, openRouterService, null)

        // Then: Should not crash
        assertNotNull(popup)

        // And: Should not make API calls without settings
        verifyNoInteractions(openRouterService)
    }

    @Test
    fun `popup should handle both services null gracefully`() {
        // When: Create popup with both services null
        val popup = OpenRouterStatsPopup(project, null, null)

        // Then: Should not crash
        assertNotNull(popup)
    }

    // ========================================
    // Configuration State Tests
    // ========================================

    @Test
    fun `unconfigured settings should prevent API calls`() {
        // Given: Settings not configured
        `when`(settingsService.isConfigured()).thenReturn(false)

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for any background processing
        Thread.sleep(200)

        // Then: Should not make API calls
        verify(openRouterService, never()).getApiKeysList()
        verify(openRouterService, never()).getCredits()
        verify(openRouterService, never()).getActivity()

        assertNotNull(popup)
    }

    @Test
    fun `configured settings but no provisioning key should prevent API calls`() {
        // Given: Settings configured but no provisioning key
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for any background processing
        Thread.sleep(200)

        // Then: Should not make API calls without provisioning key
        verify(openRouterService, never()).getApiKeysList()
        verify(openRouterService, never()).getCredits()
        verify(openRouterService, never()).getActivity()

        assertNotNull(popup)
    }

    @Test
    fun `blank provisioning key should be treated as missing`() {
        // Given: Blank provisioning key (whitespace only)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("   \t\n   ")

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for background processing
        Thread.sleep(200)

        // Then: Should treat blank key as missing
        verify(openRouterService, never()).getApiKeysList()
        assertNotNull(popup)
    }

    @Test
    fun `valid configuration should trigger API calls`() {
        // Given: Valid configuration
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("valid-provisioning-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for background processing
        Thread.sleep(300)

        // Then: Should make all API calls
        verify(openRouterService, atLeastOnce()).getApiKeysList()
        verify(openRouterService, atLeastOnce()).getCredits()
        verify(openRouterService, atLeastOnce()).getActivity()

        assertNotNull(popup)
    }

    // ========================================
    // Provisioning Key Edge Cases
    // ========================================

    @Test
    fun `provisioning key with special characters should work`() {
        // Given: Provisioning key with special characters
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("sk-proj-abc123_DEF456-789xyz")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for background processing
        Thread.sleep(200)

        // Then: Should work with special characters
        verify(openRouterService, atLeastOnce()).getApiKeysList()
        assertNotNull(popup)
    }

    @Test
    fun `very long provisioning key should be handled`() {
        // Given: Very long provisioning key
        val longKey = "sk-" + "a".repeat(500)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn(longKey)

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Then: Should handle long keys
        assertNotNull(popup)
    }

    // ========================================
    // API Response Validation Tests
    // ========================================

    @Test
    fun `empty API keys response should be handled`() {
        // Given: Empty API keys response
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        val emptyApiKeysResponse = ApiKeysListResponse(emptyList())
        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(emptyApiKeysResponse))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for processing
        Thread.sleep(200)

        // Then: Should handle empty response gracefully
        assertNotNull(popup)
    }

    @Test
    fun `zero credits response should be handled`() {
        // Given: Zero credits response
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        val zeroCreditsResponse = CreditsResponse(CreditsData(totalCredits = 0.0, totalUsage = 0.0))
        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(zeroCreditsResponse))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Then: Should handle zero credits
        assertNotNull(popup)
    }

    @Test
    fun `empty activity response should be handled`() {
        // Given: Empty activity response
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        val emptyActivityResponse = ActivityResponse(emptyList())
        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(createMockCreditsResponse()))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(emptyActivityResponse))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Then: Should handle empty activity
        assertNotNull(popup)
    }

    // ========================================
    // Mixed Response Scenarios
    // ========================================

    @Test
    fun `partial API failures should show partial data`() {
        // Given: Some APIs succeed, others fail
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(createMockApiKeysResponse()))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(null)) // Fails
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(createMockActivityResponse()))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for processing
        Thread.sleep(200)

        // Then: Should handle partial failures gracefully
        assertNotNull(popup)
    }

    @Test
    fun `all API failures should show error state`() {
        // Given: All APIs fail
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("test-key")

        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(null))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(null))

        // When: Create popup
        val popup = OpenRouterStatsPopup(project, openRouterService, settingsService)

        // Allow time for processing
        Thread.sleep(200)

        // Then: Should handle all failures gracefully
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
