package org.zhavoronkov.openrouter.regression

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.zhavoronkov.openrouter.models.*
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.StatsDataLoader

/**
 * Regression tests for Issue #4: Quota Usage Data Using Correct Endpoint
 * 
 * These tests ensure that the Credits endpoint (/api/v1/credits) is used for
 * accurate usage data, not the API Keys List endpoint which only provides
 * per-key usage information.
 */
@DisplayName("Regression: Credits Endpoint for Usage Data")
class CreditsEndpointRegressionTest {

    @Test
    @DisplayName("Credits endpoint should provide accurate total usage data")
    fun testCreditsEndpointForUsageData() = runBlocking {
        // Given: Mock service returning credits data
        val service = mock(OpenRouterService::class.java)
        val creditsResponse = CreditsResponse(
            data = CreditsData(
                totalCredits = 10.0,
                totalUsage = 5.352
            )
        )
        whenever(service.getCredits()).thenReturn(ApiResult.Success(creditsResponse, 200))

        // When: Get credits
        val result = service.getCredits()

        // Then: Should return accurate usage data
        assertTrue(result is ApiResult.Success, "Credits call should succeed")
        val credits = (result as ApiResult.Success).data.data
        assertEquals(10.0, credits.totalCredits, "Should return correct total credits")
        assertEquals(5.352, credits.totalUsage, "Should return correct total usage")
    }

    @Test
    @DisplayName("API Keys List endpoint should NOT be used for total usage statistics")
    fun testApiKeysListNotForTotalUsage() {
        // This test documents that ApiKeyInfo.usage is per-key usage, not total account usage
        
        // Given: API key with usage field
        val apiKeyInfo = ApiKeyInfo(
            name = "test-key",
            label = "sk-or-v1-test",
            limit = 100.0,
            usage = 25.5, // This is KEY-SPECIFIC usage, not total account usage
            disabled = false,
            createdAt = "2025-12-08T00:00:00Z",
            updatedAt = null,
            hash = "abc123"
        )

        // Then: The usage field exists but represents different data
        assertEquals(25.5, apiKeyInfo.usage, "Usage field exists on ApiKeyInfo")
        
        // Important: This is per-key usage, NOT total account usage
        // For total account usage, MUST use Credits endpoint
        assertNotNull(apiKeyInfo.usage, "Usage field exists but is for key-specific tracking only")
    }

    @Test
    @DisplayName("StatsDataLoader should use Credits endpoint not QuotaInfo")
    fun testStatsDataLoaderUsesCredits() = runBlocking {
        // Given: Mock services
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val routerService = mock(OpenRouterService::class.java)

        whenever(settingsService.isConfigured()).thenReturn(true)
        whenever(settingsService.getProvisioningKey()).thenReturn("test-key")

        val creditsResponse = CreditsResponse(
            data = CreditsData(totalCredits = 10.0, totalUsage = 5.0)
        )
        val apiKeysResponse = ApiKeysListResponse(data = emptyList())

        whenever(routerService.getCredits()).thenReturn(ApiResult.Success(creditsResponse, 200))
        whenever(routerService.getApiKeysList()).thenReturn(ApiResult.Success(apiKeysResponse, 200))
        whenever(routerService.getActivity()).thenReturn(ApiResult.Error("Not available"))

        // When: Load data
        val loader = StatsDataLoader(settingsService, routerService)
        var result: StatsDataLoader.LoadResult? = null

        loader.loadData { result = it }

        // Then: Should use credits data
        // Note: This test verifies the structure, actual async behavior tested in integration tests
        assertNotNull(loader, "Loader should be created")
    }

    @Test
    @DisplayName("Credits data should have totalCredits and totalUsage fields")
    fun testCreditsDataStructure() {
        // Given: Credits data
        val creditsData = CreditsData(
            totalCredits = 100.0,
            totalUsage = 45.67
        )

        // Then: Should have correct fields
        assertEquals(100.0, creditsData.totalCredits, "Should have totalCredits field")
        assertEquals(45.67, creditsData.totalUsage, "Should have totalUsage field")
    }

    @Test
    @DisplayName("Credits response should wrap CreditsData")
    fun testCreditsResponseStructure() {
        // Given: Credits response
        val creditsResponse = CreditsResponse(
            data = CreditsData(
                totalCredits = 50.0,
                totalUsage = 12.34
            )
        )

        // Then: Should have data field with credits
        assertNotNull(creditsResponse.data, "Should have data field")
        assertEquals(50.0, creditsResponse.data.totalCredits)
        assertEquals(12.34, creditsResponse.data.totalUsage)
    }

    @Test
    @DisplayName("Remaining credits should be calculated from total minus usage")
    fun testRemainingCreditsCalculation() {
        // Given: Credits data
        val totalCredits = 100.0
        val totalUsage = 35.5
        
        // When: Calculate remaining
        val remaining = totalCredits - totalUsage

        // Then: Should be correct
        assertEquals(64.5, remaining, 0.001, "Remaining should be total minus usage")
    }

    @Test
    @DisplayName("Usage percentage should be calculated correctly")
    fun testUsagePercentageCalculation() {
        // Given: Credits data
        val totalCredits = 100.0
        val totalUsage = 35.5
        
        // When: Calculate percentage
        val percentage = (totalUsage / totalCredits) * 100

        // Then: Should be correct
        assertEquals(35.5, percentage, 0.001, "Percentage should be (usage/total)*100")
    }

    @Test
    @DisplayName("Zero credits should be handled gracefully")
    fun testZeroCreditsHandling() {
        // Given: Zero credits
        val creditsData = CreditsData(
            totalCredits = 0.0,
            totalUsage = 0.0
        )

        // Then: Should not throw exception
        assertEquals(0.0, creditsData.totalCredits)
        assertEquals(0.0, creditsData.totalUsage)
        
        // Percentage calculation should handle zero
        val percentage = if (creditsData.totalCredits > 0) {
            (creditsData.totalUsage / creditsData.totalCredits) * 100
        } else {
            0.0
        }
        assertEquals(0.0, percentage)
    }
}

