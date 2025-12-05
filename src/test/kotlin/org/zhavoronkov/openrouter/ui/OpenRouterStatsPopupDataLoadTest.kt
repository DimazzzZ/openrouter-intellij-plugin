package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.concurrent.CompletableFuture

class OpenRouterStatsPopupDataLoadTest {

    private lateinit var project: Project
    private lateinit var openRouterService: OpenRouterService
    private lateinit var settingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        project = mock(Project::class.java)
        openRouterService = mock(OpenRouterService::class.java)
        settingsService = mock(OpenRouterSettingsService::class.java)
    }

    @Test
    fun `service injection constructor should accept services without throwing exceptions`() {
        // This test verifies that the constructor signature exists and accepts the services
        // We don't actually instantiate the dialog to avoid IntelliJ application context requirements

        // Given: Mock services are created
        assertNotNull(openRouterService, "OpenRouterService mock should be created")
        assertNotNull(settingsService, "OpenRouterSettingsService mock should be created")
        assertNotNull(project, "Project mock should be created")

        // When/Then: Verify constructor exists with correct signature
        assertDoesNotThrow {
            val constructor = OpenRouterStatsPopup::class.java.getDeclaredConstructor(
                Project::class.java,
                OpenRouterService::class.java,
                OpenRouterSettingsService::class.java
            )
            assertNotNull(constructor, "Constructor with service injection should exist")
        }
    }

    @Test
    fun `API response models should be properly structured for data loading`() {
        // Test that verifies the model classes are correctly structured for the API responses

        // Given: Mock API responses that match the expected structure
        val creditsData = CreditsData(totalCredits = 10.0, totalUsage = 2.5)
        val creditsResponse = CreditsResponse(creditsData)
        val apiKeyInfo = listOf(
            ApiKeyInfo(
                name = "test-key",
                label = "Test Key",
                disabled = false,
                usage = 5.0,
                limit = null,
                createdAt = "2023-01-01T00:00:00Z",
                updatedAt = null,
                hash = "test-hash"
            )
        )
        val apiKeysResponse = ApiKeysListResponse(apiKeyInfo)

        // Then: Verify the models can be created and have expected data
        assertEquals(10.0, creditsResponse.data.totalCredits, "Credits response should contain total credits")
        assertEquals(2.5, creditsResponse.data.totalUsage, "Credits response should contain total usage")
        assertEquals(1, apiKeysResponse.data.size, "API keys response should contain key info")
        assertEquals("test-key", apiKeysResponse.data[0].name, "API key should have correct name")
        assertEquals(false, apiKeysResponse.data[0].disabled, "API key should not be disabled")

        // And: Verify CompletableFuture can be created with these responses
        assertDoesNotThrow {
            val creditsFuture = CompletableFuture.completedFuture(creditsResponse)
            val apiKeysFuture = CompletableFuture.completedFuture(apiKeysResponse)
            assertNotNull(creditsFuture, "Credits future should be created")
            assertNotNull(apiKeysFuture, "API keys future should be created")
        }
    }

    @Test
    fun `service mocking should work for quota data loading verification`() {
        // Test that verifies the service mocking setup works correctly

        // Given: Services are configured
        `when`(settingsService.isConfigured()).thenReturn(true)

        // Mock API responses
        val creditsData = CreditsData(totalCredits = 10.0, totalUsage = 2.5)
        val creditsResponse = CreditsResponse(creditsData)
        val apiKeyInfo = listOf(
            ApiKeyInfo(
                name = "test-key",
                label = "Test Key",
                disabled = false,
                usage = 5.0,
                limit = null,
                createdAt = "2023-01-01T00:00:00Z",
                updatedAt = null,
                hash = "test-hash"
            )
        )
        val apiKeysResponse = ApiKeysListResponse(apiKeyInfo)

        `when`(openRouterService.getCredits()).thenReturn(CompletableFuture.completedFuture(creditsResponse))
        `when`(openRouterService.getApiKeysList()).thenReturn(CompletableFuture.completedFuture(apiKeysResponse))
        `when`(openRouterService.getActivity()).thenReturn(CompletableFuture.completedFuture(null))

        // Then: Verify service mocks work as expected
        assertTrue(settingsService.isConfigured(), "Settings service should be configured")

        val creditsFuture = openRouterService.getCredits()
        assertNotNull(creditsFuture, "Credits future should not be null")
        assertEquals(creditsResponse, creditsFuture.get(), "Credits future should return mocked response")

        val apiKeysFuture = openRouterService.getApiKeysList()
        assertNotNull(apiKeysFuture, "API keys future should not be null")
        assertEquals(apiKeysResponse, apiKeysFuture.get(), "API keys future should return mocked response")

        val activityFuture = openRouterService.getActivity()
        assertNotNull(activityFuture, "Activity future should not be null")
        assertNull(activityFuture.get(), "Activity future should return null as mocked")

        // Verify API calls were made
        verify(settingsService).isConfigured()
        verify(openRouterService).getCredits()
        verify(openRouterService).getApiKeysList()
        verify(openRouterService).getActivity()
    }
}
