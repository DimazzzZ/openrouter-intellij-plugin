package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate

/**
 * Comprehensive tests for StatsUtils
 *
 * Tests cover all formatting and activity processing methods
 * with various edge cases and scenarios.
 */
class StatsUtilsTest {

    // ========================================
    // FORMATTING METHOD TESTS
    // ========================================

    @Test
    fun `formatCurrency formats with default 4 decimal places`() {
        assertEquals("1.2346", StatsUtils.formatCurrency(1.23456789))
        assertEquals("0.0001", StatsUtils.formatCurrency(0.0001))
        assertEquals("1234.5679", StatsUtils.formatCurrency(1234.56789))
    }

    @Test
    fun `formatCurrency formats with custom decimal places`() {
        assertEquals("1.23", StatsUtils.formatCurrency(1.23456789, 2))
        assertEquals("1.235", StatsUtils.formatCurrency(1.23456789, 3))
        assertEquals("1", StatsUtils.formatCurrency(1.23456789, 0))
        assertEquals("1.2346", StatsUtils.formatCurrency(1.23456789, 4))
    }

    @Test
    fun `formatCurrency handles edge cases`() {
        assertEquals("0.0000", StatsUtils.formatCurrency(0.0))
        assertEquals("-1.5000", StatsUtils.formatCurrency(-1.5))
        assertEquals("1000000.0000", StatsUtils.formatCurrency(1000000.0))
        assertEquals("0.0001", StatsUtils.formatCurrency(0.00005)) // rounds up
    }

    @Test
    fun `formatPercentage formats with default 4 decimal places`() {
        assertEquals("25.5000", StatsUtils.formatPercentage(25.5))
        assertEquals("0.1234", StatsUtils.formatPercentage(0.1234))
        assertEquals("100.0000", StatsUtils.formatPercentage(100.0))
    }

    @Test
    fun `formatPercentage formats with custom decimal places`() {
        assertEquals("25.5", StatsUtils.formatPercentage(25.5, 1))
        assertEquals("25.50", StatsUtils.formatPercentage(25.5, 2))
        assertEquals("26", StatsUtils.formatPercentage(25.5, 0)) // 25.5 rounds up to 26
    }

    @Test
    fun `formatLargeNumber formats with comma separators`() {
        assertEquals("1,000", StatsUtils.formatLargeNumber(1000L))
        assertEquals("10,000", StatsUtils.formatLargeNumber(10000L))
        assertEquals("1,234,567", StatsUtils.formatLargeNumber(1234567L))
        assertEquals("0", StatsUtils.formatLargeNumber(0L))
        assertEquals("-1,000", StatsUtils.formatLargeNumber(-1000L))
    }

    @Test
    fun `formatCurrency works for various use cases`() {
        // Test that formatCurrency can be used for status, tooltip, table, and activity
        assertEquals("1.2345", StatsUtils.formatCurrency(1.2345))
        assertEquals("0.0001", StatsUtils.formatCurrency(0.0001))
        assertEquals("100.0000", StatsUtils.formatCurrency(100.0))
    }

    @Test
    fun `formatCurrencyRange formats min-max ranges correctly`() {
        assertEquals("1.0000 - 2.5000", StatsUtils.formatCurrencyRange(1.0, 2.5))
        assertEquals("0.0001 - 0.0010", StatsUtils.formatCurrencyRange(0.0001, 0.001))
        assertEquals("-1.0000 - 1.0000", StatsUtils.formatCurrencyRange(-1.0, 1.0))
    }

    @Test
    fun `formatCurrencyDifference formats positive differences`() {
        assertEquals("+$1.5000", StatsUtils.formatCurrencyDifference(1.5))
        assertEquals("+$0.0001", StatsUtils.formatCurrencyDifference(0.0001))
        assertEquals("+$100.0000", StatsUtils.formatCurrencyDifference(100.0))
    }

    @Test
    fun `formatCurrencyDifference formats negative differences`() {
        assertEquals("-$1.5000", StatsUtils.formatCurrencyDifference(-1.5))
        assertEquals("-$0.0001", StatsUtils.formatCurrencyDifference(-0.0001))
        assertEquals("-$100.0000", StatsUtils.formatCurrencyDifference(-100.0))
    }

    @Test
    fun `formatCurrencyDifference handles zero difference`() {
        assertEquals("+$0.0000", StatsUtils.formatCurrencyDifference(0.0))
    }

    // ========================================
    // ACTIVITY TEXT FORMATTING TESTS
    // ========================================

    @Test
    fun `formatActivityText formats requests and usage correctly`() {
        assertEquals("0 requests, $0.0000 spent", StatsUtils.formatActivityText(0L, 0.0))
        assertEquals("1 requests, $1.5000 spent", StatsUtils.formatActivityText(1L, 1.5))
        assertEquals("1000 requests, $123.4568 spent", StatsUtils.formatActivityText(1000L, 123.456789))
    }

    // ========================================
    // MODELS HTML LIST TESTS
    // ========================================

    @Test
    fun `buildModelsHtmlList handles empty list`() {
        val result = StatsUtils.buildModelsHtmlList(emptyList())
        assertEquals("<html>Recent Models:<br/>• None</html>", result)
    }

    @Test
    fun `buildModelsHtmlList handles single model`() {
        val models = listOf("gpt-4")
        val result = StatsUtils.buildModelsHtmlList(models)
        assertEquals("<html>Recent Models:<br/>• gpt-4</html>", result)
    }

    @Test
    fun `buildModelsHtmlList handles multiple models up to limit`() {
        val models = listOf("gpt-4", "claude-3", "gemini-pro")
        val result = StatsUtils.buildModelsHtmlList(models)
        assertEquals("<html>Recent Models:<br/>• gpt-4<br/>• claude-3<br/>• gemini-pro</html>", result)
    }

    @Test
    fun `buildModelsHtmlList truncates models over limit`() {
        val models = listOf("model1", "model2", "model3", "model4", "model5", "model6", "model7")
        val result = StatsUtils.buildModelsHtmlList(models)
        assertTrue(result.contains("model1"))
        assertTrue(result.contains("model2"))
        assertTrue(result.contains("model3"))
        assertTrue(result.contains("model4"))
        assertTrue(result.contains("model5"))
        assertTrue(result.contains("+2 more"))
        assertFalse(result.contains("model6"))
        assertFalse(result.contains("model7"))
    }

    @Test
    fun `buildSimpleModelsHtmlList handles empty list`() {
        val result = StatsUtils.buildSimpleModelsHtmlList(emptyList())
        assertEquals("No models used", result)
    }

    @Test
    fun `buildSimpleModelsHtmlList handles single model`() {
        val models = listOf("gpt-4")
        val result = StatsUtils.buildSimpleModelsHtmlList(models)
        assertEquals(
            "<html><ul style='margin: 0; padding-left: 20px;'><li style='margin: 2px 0;'>gpt-4</li></ul></html>",
            result
        )
    }

    @Test
    fun `buildSimpleModelsHtmlList handles multiple models`() {
        val models = listOf("gpt-4", "claude-3")
        val result = StatsUtils.buildSimpleModelsHtmlList(models)
        assertTrue(result.contains("gpt-4"))
        assertTrue(result.contains("claude-3"))
        assertTrue(result.startsWith("<html><ul"))
        assertTrue(result.endsWith("</ul></html>"))
    }

    // ========================================
    // ACTIVITY STATISTICS TESTS
    // ========================================

    @Test
    fun `calculateActivityStats handles empty list`() {
        val result = StatsUtils.calculateActivityStats(emptyList())
        assertEquals(0L, result.totalRequests) // requests
        assertEquals(0.0, result.totalUsage) // usage
    }

    @Test
    fun `calculateActivityStats sums requests and usage correctly`() {
        val activities = listOf(
            createActivityData(requests = 10, usage = 1.5),
            createActivityData(requests = 5, usage = 2.3),
            createActivityData(requests = 20, usage = 0.7)
        )

        val result = StatsUtils.calculateActivityStats(activities)
        assertEquals(35L, result.totalRequests) // 10 + 5 + 20
        assertEquals(4.5, result.totalUsage, 0.001) // 1.5 + 2.3 + 0.7
    }

    @Test
    fun `calculateActivityStats handles null values gracefully`() {
        val activities = listOf(
            createActivityData(requests = null, usage = null), // null values treated as 0
            createActivityData(requests = 5, usage = 2.3),
            createActivityData(requests = null, usage = 1.5)
        )

        val result = StatsUtils.calculateActivityStats(activities)
        assertEquals(5L, result.totalRequests) // only non-null requests
        assertEquals(3.8, result.totalUsage, 0.001) // 2.3 + 1.5 (null treated as 0.0)
    }

    // ========================================
    // ACTIVITY FILTERING TESTS
    // ========================================

    @Test
    fun `filterActivitiesByTime filters last 24h correctly`() {
        val activities = listOf(
            createActivityData(date = LocalDate.now().toString()), // today
            createActivityData(date = LocalDate.now().minusDays(1).toString()), // yesterday
            createActivityData(date = LocalDate.now().minusDays(2).toString()), // 2 days ago
            createActivityData(date = LocalDate.now().minusDays(7).toString()) // week ago
        )

        val result = StatsUtils.filterActivitiesByTime(activities, true)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterActivitiesByTime filters last week correctly`() {
        val activities = listOf(
            createActivityData(date = LocalDate.now().toString()), // today
            createActivityData(date = LocalDate.now().minusDays(1).toString()), // yesterday
            createActivityData(date = LocalDate.now().minusDays(2).toString()), // 2 days ago
            createActivityData(date = LocalDate.now().minusDays(7).toString()), // exactly week ago
            createActivityData(date = LocalDate.now().minusDays(8).toString()) // older than week
        )

        val result = StatsUtils.filterActivitiesByTime(activities, false)
        assertTrue(result.size >= 4) // Should include last 7 days
    }

    @Test
    fun `filterActivitiesByHours filters correctly`() {
        val activities = listOf(
            createActivityData(date = LocalDate.now().toString()), // today
            createActivityData(date = LocalDate.now().minusDays(1).toString()), // yesterday
            createActivityData(date = LocalDate.now().minusDays(3).toString()) // 3 days ago
        )

        val result24h = StatsUtils.filterActivitiesByHours(activities, 24)
        assertEquals(2, result24h.size) // Today and yesterday (last 24 hours)

        val result48h = StatsUtils.filterActivitiesByHours(activities, 48)
        assertEquals(2, result48h.size) // Today and yesterday (last 48 hours)

        val result96h = StatsUtils.filterActivitiesByHours(activities, 96)
        assertEquals(3, result96h.size) // All three (last 96 hours)
    }

    @Test
    fun `filterActivitiesByTime handles null dates gracefully`() {
        val activities = listOf(
            createActivityData(date = LocalDate.now().toString()),
            createActivityData(date = null), // null date
            createActivityData(date = LocalDate.now().minusDays(1).toString())
        )

        val result = StatsUtils.filterActivitiesByTime(activities, true)
        assertEquals(2, result.size) // Should exclude null date
    }

    // ========================================
    // MODEL NAME EXTRACTION TESTS
    // ========================================

    @Test
    fun `extractRecentModelNames sorts by most recent date`() {
        val activities = listOf(
            createActivityData(date = "2024-12-03", model = "gpt-4"),
            createActivityData(date = "2024-12-05", model = "claude-3"),
            createActivityData(date = "2024-12-04", model = "gpt-3.5-turbo"),
            createActivityData(date = "2024-12-01", model = "gpt-4") // older gpt-4 usage
        )

        val result = StatsUtils.extractRecentModelNames(activities)
        assertEquals(3, result.size) // 3 unique models
        assertEquals("claude-3", result[0]) // most recent (2024-12-05)
        assertEquals("gpt-3.5-turbo", result[1]) // middle (2024-12-04)
        assertEquals("gpt-4", result[2]) // oldest recent (2024-12-03, not 12-01)
    }

    @Test
    fun `extractRecentModelNames handles null values gracefully`() {
        val activities = listOf(
            createActivityData(date = "2024-12-05", model = "gpt-4"),
            createActivityData(date = null, model = "claude-3"), // null date
            createActivityData(date = "2024-12-04", model = null), // null model
            createActivityData(date = "2024-12-03", model = "gpt-4") // duplicate model
        )

        val result = StatsUtils.extractRecentModelNames(activities)
        assertEquals(1, result.size) // Only gpt-4 should be included
        assertEquals("gpt-4", result[0])
    }

    @Test
    fun `extractRecentModelNames handles empty list`() {
        val result = StatsUtils.extractRecentModelNames(emptyList())
        assertTrue(result.isEmpty())
    }

    // ========================================
    // DATE PARSING TESTS
    // ========================================

    @Test
    fun `parseActivityDate handles date-only format`() {
        val result = StatsUtils.parseActivityDate("2024-12-05")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate handles datetime format`() {
        val result = StatsUtils.parseActivityDate("2024-12-05 14:30:00")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate handles ISO format with T separator`() {
        val result = StatsUtils.parseActivityDate("2024-12-05T14:30:00Z")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate handles ISO format with timezone`() {
        val result = StatsUtils.parseActivityDate("2024-12-05T14:30:00+02:00")
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate returns null for invalid format`() {
        assertNull(StatsUtils.parseActivityDate("invalid-date"))
        assertNull(StatsUtils.parseActivityDate("2024/12/05"))
        assertNull(StatsUtils.parseActivityDate("12-05-2024"))
        assertNull(StatsUtils.parseActivityDate(""))
        assertNull(StatsUtils.parseActivityDate("2024-12-05-extra-stuff"))
    }

    @Test
    fun `parseActivityDate handles short strings gracefully`() {
        assertNull(StatsUtils.parseActivityDate("2024"))
        assertNull(StatsUtils.parseActivityDate("2024-12"))
        assertNull(StatsUtils.parseActivityDate("abcd"))
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private fun createActivityData(
        date: String? = "2024-01-01",
        model: String? = "gpt-4",
        requests: Int? = 1,
        usage: Double? = 1.0
    ): ActivityData {
        return ActivityData(
            date = date,
            model = model,
            modelPermaslug = model ?: "gpt-4",
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
