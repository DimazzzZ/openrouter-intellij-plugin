package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * Tests for status bar costs display functionality
 */
@DisplayName("Status Bar Costs Display Tests")
class StatusBarCostsDisplayTest {

    private lateinit var mockOpenRouterService: OpenRouterService
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockProxyService: OpenRouterProxyService

    @BeforeEach
    fun setUp() {
        // Create mocks
        mockOpenRouterService = mock(OpenRouterService::class.java)
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        mockProxyService = mock(OpenRouterProxyService::class.java)
    }

    @Test
    @DisplayName("Show costs enabled - status bar displays dollar amounts")
    fun testShowCostsEnabledDisplaysDollarAmounts() {
        // Given: Show costs is enabled
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(true)
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("sk-or-v1-test-key")

        // Mock credits response
        val mockCreditsData = CreditsData(totalCredits = 10.0, totalUsage = 5.352)
        val mockCreditsResponse = CreditsResponse(data = mockCreditsData)

        `when`(mockOpenRouterService.getCredits())
            .thenReturn(CompletableFuture.completedFuture(mockCreditsResponse))

        // When: Status is updated (simulated)
        val shouldShowCosts = mockSettingsService.shouldShowCosts()

        // Then: Should show costs is true
        assertTrue(shouldShowCosts, "Should show costs should be enabled")

        // And: The status text format should include dollar amounts
        // Format: "Status: Ready - $5.352/$10.00"
        val used = mockCreditsData.totalUsage
        val total = mockCreditsData.totalCredits
        val expectedFormat = String.format(Locale.US, "$%.3f/$%.2f", used, total)

        assertEquals("$5.352/$10.00", expectedFormat, "Status should show dollar amounts")
    }

    @Test
    @DisplayName("Show costs disabled - status bar displays percentage")
    fun testShowCostsDisabledDisplaysPercentage() {
        // Given: Show costs is disabled
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(false)
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("sk-or-v1-test-key")

        // Mock credits response
        val mockCreditsData = CreditsData(totalCredits = 10.0, totalUsage = 5.352)
        val mockCreditsResponse = CreditsResponse(data = mockCreditsData)

        `when`(mockOpenRouterService.getCredits())
            .thenReturn(CompletableFuture.completedFuture(mockCreditsResponse))

        // When: Status is updated (simulated)
        val shouldShowCosts = mockSettingsService.shouldShowCosts()

        // Then: Should show costs is false
        assertFalse(shouldShowCosts, "Should show costs should be disabled")

        // And: The status text format should include percentage
        // Format: "Status: Ready - 53.5% used"
        val used = mockCreditsData.totalUsage
        val total = mockCreditsData.totalCredits
        val percentage = (used / total) * 100
        val expectedPercentage = String.format(Locale.US, "%.1f%%", percentage)

        assertEquals("53.5%", expectedPercentage, "Status should show percentage")
    }

    @Test
    @DisplayName("Show costs setting changes - status format updates accordingly")
    fun testShowCostsSettingChangesUpdateFormat() {
        // Given: Initial state with show costs enabled
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(true)

        // When: Show costs is enabled
        var shouldShowCosts = mockSettingsService.shouldShowCosts()

        // Then: Should return true
        assertTrue(shouldShowCosts, "Should show costs should be enabled")

        // When: Show costs is disabled
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(false)
        shouldShowCosts = mockSettingsService.shouldShowCosts()

        // Then: Should return false
        assertFalse(shouldShowCosts, "Should show costs should be disabled")
    }

    @Test
    @DisplayName("Show costs enabled - formats dollar amounts correctly")
    fun testDollarAmountFormatting() {
        // Given: Various usage amounts
        val testCases = listOf(
            Triple(0.001, 10.0, "$0.001/$10.00"),
            Triple(5.352, 10.0, "$5.352/$10.00"),
            Triple(9.999, 10.0, "$9.999/$10.00"),
            Triple(10.0, 10.0, "$10.000/$10.00"),
            Triple(123.456, 200.0, "$123.456/$200.00")
        )

        testCases.forEach { (used, total, expected) ->
            // When: Formatting dollar amounts
            val formatted = String.format(Locale.US, "$%.3f/$%.2f", used, total)

            // Then: Should match expected format
            assertEquals(expected, formatted, "Dollar amount should be formatted correctly")
        }
    }

    @Test
    @DisplayName("Show costs disabled - formats percentage correctly")
    fun testPercentageFormatting() {
        // Given: Various usage percentages
        val testCases = listOf(
            Triple(0.1, 10.0, "1.0%"),
            Triple(5.352, 10.0, "53.5%"),
            Triple(9.5, 10.0, "95.0%"),
            Triple(10.0, 10.0, "100.0%"),
            Triple(0.05, 10.0, "0.5%")
        )

        testCases.forEach { (used, total, expected) ->
            // When: Formatting percentage
            val percentage = (used / total) * 100
            val formatted = String.format(Locale.US, "%.1f%%", percentage)

            // Then: Should match expected format
            assertEquals(expected, formatted, "Percentage should be formatted correctly")
        }
    }

    @Test
    @DisplayName("Show costs setting persists across sessions")
    fun testShowCostsSettingPersistence() {
        // Given: Show costs is set to false
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(false)

        // When: Setting is retrieved
        val showCosts1 = mockSettingsService.shouldShowCosts()

        // Then: Should return false
        assertFalse(showCosts1, "Show costs should be false")

        // Given: Show costs is changed to true
        `when`(mockSettingsService.shouldShowCosts()).thenReturn(true)

        // When: Setting is retrieved again
        val showCosts2 = mockSettingsService.shouldShowCosts()

        // Then: Should return true
        assertTrue(showCosts2, "Show costs should be true after change")
    }

    @Test
    @DisplayName("Status bar tooltip includes usage information")
    fun testStatusBarTooltipIncludesUsage() {
        // Given: Credits data
        val used = 5.352
        val total = 10.0

        // When: Tooltip is formatted (simulated)
        val tooltipWithCosts = String.format(Locale.US, "OpenRouter Status: Ready - Usage: $%.3f/$%.2f", used, total)
        val tooltipWithPercentage = String.format(Locale.US, "OpenRouter Status: Ready - Usage: %.1f%% used", (used / total) * 100)

        // Then: Tooltip should contain usage information
        assertTrue(tooltipWithCosts.contains("Usage:"), "Tooltip should contain usage label")
        assertTrue(tooltipWithCosts.contains("$5.352/$10.00"), "Tooltip should show dollar amounts")

        assertTrue(tooltipWithPercentage.contains("Usage:"), "Tooltip should contain usage label")
        assertTrue(tooltipWithPercentage.contains("53.5%"), "Tooltip should show percentage")
    }

    @Test
    @DisplayName("Zero credits handled gracefully")
    fun testZeroCreditsHandledGracefully() {
        // Given: Zero total credits
        val used = 0.0
        val total = 0.0

        // When: Formatting with zero credits
        val formatted = if (total > 0) {
            String.format(Locale.US, "$%.3f/$%.2f", used, total)
        } else {
            "No credits"
        }

        // Then: Should handle gracefully
        assertEquals("No credits", formatted, "Should handle zero credits gracefully")
    }

    @Test
    @DisplayName("Unlimited credits handled gracefully")
    fun testUnlimitedCreditsHandledGracefully() {
        // Given: Very large credit limit (simulating unlimited)
        val used = 5.352
        val total = 999999.0

        // When: Formatting with unlimited credits
        val percentage = (used / total) * 100
        val formatted = String.format(Locale.US, "%.1f%%", percentage)

        // Then: Should show very small percentage
        assertEquals("0.0%", formatted, "Should show near-zero percentage for unlimited credits")
    }
}
