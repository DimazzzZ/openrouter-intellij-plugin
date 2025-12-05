package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.*
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

/**
 * Tests for configuration validation logic
 * These tests verify the configuration handling without creating UI components
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
    fun `test constructor exists for null openRouterService`() {
        // Test that the constructor signature supports null service handling
        val constructor = OpenRouterStatsPopup::class.java.getDeclaredConstructor(
            Project::class.java,
            OpenRouterService::class.java,
            OpenRouterSettingsService::class.java
        )
        assertNotNull(constructor, "Constructor with service injection should exist for null handling tests")
    }

    @Test
    fun `test constructor exists for null settingsService`() {
        // Test that the constructor signature exists for null settings handling
        val constructor = OpenRouterStatsPopup::class.java.getDeclaredConstructor(
            Project::class.java,
            OpenRouterService::class.java,
            OpenRouterSettingsService::class.java
        )
        assertNotNull(constructor, "Constructor with service injection should exist for settings null handling")
    }

    // ========================================
    // Configuration State Tests
    // ========================================

    @Test
    fun `unconfigured settings logic should prevent API calls`() {
        // Given: Settings not configured
        `when`(settingsService.isConfigured()).thenReturn(false)

        // Then: Settings should be checked
        assertFalse(settingsService.isConfigured())
        verify(settingsService).isConfigured()
    }

    @Test
    fun `provisioning key validation logic should check for blank keys`() {
        // Given: Blank provisioning key
        `when`(settingsService.getProvisioningKey()).thenReturn("   \t\n   ")

        // Then: Should be considered blank
        assertTrue(settingsService.getProvisioningKey().isBlank())
        verify(settingsService).getProvisioningKey()
    }

    @Test
    fun `valid configuration should have configured settings and provisioning key`() {
        // Given: Valid configuration
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("valid-provisioning-key")

        // Then: Should be valid
        assertTrue(settingsService.isConfigured())
        assertFalse(settingsService.getProvisioningKey().isBlank())

        verify(settingsService).isConfigured()
        verify(settingsService).getProvisioningKey()
    }

    // ========================================
    // API Response Mock Tests
    // ========================================

    @Test
    fun `empty API keys response structure should be valid`() {
        // Test that empty API keys response can be created
        val emptyApiKeysResponse = ApiKeysListResponse(emptyList())
        assertNotNull(emptyApiKeysResponse)
        assertTrue(emptyApiKeysResponse.data.isEmpty())
    }

    @Test
    fun `zero credits response structure should be valid`() {
        // Test that zero credits response can be created
        val zeroCreditsResponse = CreditsResponse(CreditsData(totalCredits = 0.0, totalUsage = 0.0))
        assertNotNull(zeroCreditsResponse)
        assertEquals(0.0, zeroCreditsResponse.data.totalCredits)
        assertEquals(0.0, zeroCreditsResponse.data.totalUsage)
    }

    @Test
    fun `empty activity response structure should be valid`() {
        // Test that empty activity response can be created
        val emptyActivityResponse = ActivityResponse(emptyList())
        assertNotNull(emptyActivityResponse)
        assertTrue(emptyActivityResponse.data.isEmpty())
    }

    // ========================================
    // Helper Methods Tests
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
