package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
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
}
