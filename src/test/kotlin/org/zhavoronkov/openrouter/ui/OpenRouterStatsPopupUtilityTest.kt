package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.*
import java.time.LocalDate

/**
 * Tests for utility methods and edge cases in OpenRouterStatsPopup
 * These tests verify static utility functions without UI components
 */
class OpenRouterStatsPopupUtilityTest {

    // ========================================
    // Currency Formatting Tests
    // ========================================

    @Test
    fun `formatCurrency should handle zero values`() {
        val formatted = OpenRouterStatsUtils.formatCurrency(0.0, 3)
        assertEquals("0.000", formatted)
    }

    @Test
    fun `formatCurrency should handle negative values`() {
        val formatted = OpenRouterStatsUtils.formatCurrency(-15.123, 2)
        assertEquals("-15.12", formatted)
    }

    @Test
    fun `formatCurrency should handle large values`() {
        val formatted = OpenRouterStatsUtils.formatCurrency(1234567.89, 2)
        assertEquals("1234567.89", formatted)
    }

    @Test
    fun `formatCurrency should handle very small values`() {
        val formatted = OpenRouterStatsUtils.formatCurrency(0.0001, 4)
        assertEquals("0.0001", formatted)
    }

    // ========================================
    // Activity Text Formatting Tests
    // ========================================

    @Test
    fun `formatActivityText should handle zero requests`() {
        val text = OpenRouterStatsUtils.formatActivityText(0L, 0.0)
        assertEquals("0 requests, $0.0000 spent", text)
    }

    @Test
    fun `formatActivityText should handle single request`() {
        val text = OpenRouterStatsUtils.formatActivityText(1L, 0.0050)
        assertEquals("1 requests, $0.0050 spent", text)
    }

    @Test
    fun `formatActivityText should handle large numbers`() {
        val text = OpenRouterStatsUtils.formatActivityText(1000000L, 1234.5678)
        assertEquals("1000000 requests, $1234.5678 spent", text)
    }

    // ========================================
    // Models HTML List Tests
    // ========================================

    @Test
    fun `buildModelsHtmlList should handle empty list`() {
        val html = OpenRouterStatsUtils.buildModelsHtmlList(emptyList())
        assertEquals("<html>Recent Models:<br/>• None</html>", html)
    }

    @Test
    fun `buildModelsHtmlList should handle single model`() {
        val models = listOf("gpt-4")
        val html = OpenRouterStatsUtils.buildModelsHtmlList(models)
        assertEquals("<html>Recent Models:<br/>• gpt-4</html>", html)
    }

    @Test
    fun `buildModelsHtmlList should limit to 5 models and show count`() {
        val models = listOf("gpt-4", "gpt-3.5-turbo", "claude-3", "gemini-pro", "llama-2", "codex", "davinci")
        val html = OpenRouterStatsUtils.buildModelsHtmlList(models)

        assertTrue(html.contains("gpt-4"))
        assertTrue(html.contains("gpt-3.5-turbo"))
        assertTrue(html.contains("claude-3"))
        assertTrue(html.contains("gemini-pro"))
        assertTrue(html.contains("llama-2"))
        assertTrue(html.contains("+2 more"))
        assertFalse(html.contains("codex"))
        assertFalse(html.contains("davinci"))
    }

    // ========================================
    // Activity Statistics Tests
    // ========================================

    @Test
    fun `calculateActivityStats should handle empty list`() {
        val result = OpenRouterStatsUtils.calculateActivityStats(emptyList())
        assertEquals(0L, result.first)
        assertEquals(0.0, result.second)
    }

    @Test
    fun `calculateActivityStats should sum requests and usage correctly`() {
        val activities = listOf(
            createActivityData("2024-12-05", requests = 10, usage = 1.5),
            createActivityData("2024-12-04", requests = 5, usage = 2.3),
            createActivityData("2024-12-03", requests = 20, usage = 0.7)
        )

        val result = OpenRouterStatsUtils.calculateActivityStats(activities)
        assertEquals(35L, result.first)  // 10 + 5 + 20
        assertEquals(4.5, result.second, 0.001)  // 1.5 + 2.3 + 0.7
    }

    // ========================================
    // Date Parsing Tests
    // ========================================

    @Test
    fun `parseActivityDate should handle date-only format`() {
        val result = OpenRouterStatsUtils.parseActivityDate("2024-12-05")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should handle datetime format`() {
        val result = OpenRouterStatsUtils.parseActivityDate("2024-12-05 14:30:00")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should handle ISO format`() {
        val result = OpenRouterStatsUtils.parseActivityDate("2024-12-05T14:30:00Z")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should return null for invalid format`() {
        val result = OpenRouterStatsUtils.parseActivityDate("invalid-date")
        assertNull(result)
    }

    @Test
    fun `parseActivityDate should return null for empty string`() {
        val result = OpenRouterStatsUtils.parseActivityDate("")
        assertNull(result)
    }

    // ========================================
    // Activity Filtering Tests
    // ========================================

    @Test
    fun `filterActivitiesByTime should filter last 24h correctly`() {
        val today = LocalDate.of(2024, 12, 5)
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val activities = listOf(
            createActivityData("2024-12-05"), // today
            createActivityData("2024-12-04"), // yesterday
            createActivityData("2024-12-03"), // 2 days ago
            createActivityData("2024-11-28")  // week ago
        )

        val result = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, true)
        assertEquals(2, result.size) // Should include today and yesterday only
    }

    @Test
    fun `filterActivitiesByTime should filter last week correctly`() {
        val today = LocalDate.of(2024, 12, 5)
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val activities = listOf(
            createActivityData("2024-12-05"), // today
            createActivityData("2024-12-04"), // yesterday
            createActivityData("2024-12-03"), // 2 days ago
            createActivityData("2024-11-28"), // exactly week ago
            createActivityData("2024-11-27")  // older than week
        )

        val result = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, false)
        assertEquals(4, result.size) // Should include last 7 days + week ago
    }

    // ========================================
    // Model Name Extraction Tests
    // ========================================

    @Test
    fun `extractRecentModelNames should sort by most recent date`() {
        val activities = listOf(
            createActivityData("2024-12-03", model = "gpt-4"),
            createActivityData("2024-12-05", model = "claude-3"),
            createActivityData("2024-12-04", model = "gpt-3.5-turbo"),
            createActivityData("2024-12-01", model = "gpt-4") // older gpt-4 usage
        )

        val result = OpenRouterStatsUtils.extractRecentModelNames(activities)
        assertEquals(3, result.size) // 3 unique models
        assertEquals("claude-3", result[0])     // most recent (2024-12-05)
        assertEquals("gpt-3.5-turbo", result[1]) // middle (2024-12-04)
        assertEquals("gpt-4", result[2])        // oldest recent (2024-12-03, not 12-01)
    }

    private fun createActivityData(
        date: String,
        model: String = "gpt-4",
        requests: Int = 1,
        usage: Double = 1.0
    ): ActivityData {
        return ActivityData(
            date = date,
            model = model,
            modelPermaslug = model,
            endpointId = "test-endpoint",
            providerName = "openai",
            usage = usage,
            byokUsageInference = 0.0,
            requests = requests,
            promptTokens = 100,
            completionTokens = 50,
            reasoningTokens = 0
        )
    }
}
