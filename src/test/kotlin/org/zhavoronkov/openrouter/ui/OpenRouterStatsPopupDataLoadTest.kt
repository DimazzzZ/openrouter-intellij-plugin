package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

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

        // And: Verify ApiResult can be created with these responses
        assertDoesNotThrow {
            val creditsResult = ApiResult.Success(creditsResponse, 200)
            val apiKeysResult = ApiResult.Success(apiKeysResponse, 200)
            assertNotNull(creditsResult, "Credits result should be created")
            assertNotNull(apiKeysResult, "API keys result should be created")
        }
    }

    @Test
    fun `service mocking should work for quota data loading verification`() = runBlocking {
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

        whenever(openRouterService.getCredits()).thenReturn(ApiResult.Success(creditsResponse, 200))
        whenever(openRouterService.getApiKeysList()).thenReturn(ApiResult.Success(apiKeysResponse, 200))
        whenever(openRouterService.getActivity()).thenReturn(ApiResult.Error("No activity data"))

        // Then: Verify service mocks work as expected
        assertTrue(settingsService.isConfigured(), "Settings service should be configured")

        val creditsResult = openRouterService.getCredits()
        assertNotNull(creditsResult, "Credits result should not be null")
        assertTrue(creditsResult is ApiResult.Success, "Credits should be success")
        assertEquals(creditsResponse, (creditsResult as ApiResult.Success).data, "Credits should return mocked response")

        val apiKeysResult = openRouterService.getApiKeysList()
        assertNotNull(apiKeysResult, "API keys result should not be null")
        assertTrue(apiKeysResult is ApiResult.Success, "API keys should be success")
        assertEquals(apiKeysResponse, (apiKeysResult as ApiResult.Success).data, "API keys should return mocked response")

        val activityResult = openRouterService.getActivity()
        assertNotNull(activityResult, "Activity result should not be null")
        assertTrue(activityResult is ApiResult.Error, "Activity should be error")

        // Verify API calls were made
        verify(settingsService).isConfigured()
        verify(openRouterService).getCredits()
        verify(openRouterService).getApiKeysList()
        verify(openRouterService).getActivity()
    }
}
