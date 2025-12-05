package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.util.*

class OpenRouterStatsPopupTest {

    @Test
    fun `formatCurrency returns correct format with default decimals`() {
        val formatted = OpenRouterStatsUtils.formatCurrency(3.14159, 3)
        assertEquals("3.142", formatted) // Rounded to 3 decimal places
    }

    @Test
    fun `formatCurrency works with different decimal places`() {
        // Test with various decimal places
        assertEquals("3.14", OpenRouterStatsUtils.formatCurrency(3.14159, 2))
        assertEquals("3.142", OpenRouterStatsUtils.formatCurrency(3.14159, 3))
        assertEquals("3.1416", OpenRouterStatsUtils.formatCurrency(3.14159, 4))
        assertEquals("3", OpenRouterStatsUtils.formatCurrency(3.14159, 0))
    }

    @Test
    fun `formatLargeNumber returns correct format with commas`() {
        val formatted = OpenRouterStatsUtils.formatLargeNumber(1234567L)
        assertEquals("1,234,567", formatted)
    }

    @Test
    fun `calculateActivityStats returns correct Long and Double pair`() {
        // Test the function that had the type mismatch error
        val activities = listOf(
            ActivityData(
                date = "2024-01-01",
                model = "gpt-4",
                modelPermaslug = "gpt-4",
                endpointId = "endpoint1",
                providerName = "openai",
                usage = 1.5,
                byokUsageInference = 0.0,
                requests = 10,
                promptTokens = 100,
                completionTokens = 50,
                reasoningTokens = 0
            ),
            ActivityData(
                date = "2024-01-01",
                model = "gpt-3.5-turbo",
                modelPermaslug = "gpt-3.5-turbo",
                endpointId = "endpoint2",
                providerName = "openai",
                usage = 0.5,
                byokUsageInference = 0.0,
                requests = 5,
                promptTokens = 50,
                completionTokens = 25,
                reasoningTokens = 0
            )
        )

        val result = OpenRouterStatsUtils.calculateActivityStats(activities)

        // Check that the first element is Long (total requests = 15)
        assertEquals(15L, result.first)
        assertTrue(result.first is Long, "First element should be Long")

        // Check that the second element is Double (total usage = 2.0)
        assertEquals(2.0, result.second)
        assertTrue(result.second is Double, "Second element should be Double")
    }

    @Test
    fun `buildModelsHtmlList returns empty list html when no models`() {
        val html = OpenRouterStatsUtils.buildModelsHtmlList(emptyList())
        assertEquals("<html>Recent Models:<br/>• None</html>", html)
    }

    @Test
    fun `buildModelsHtmlList returns formatted html with models`() {
        val models = listOf("gpt-4", "gpt-3.5-turbo", "claude-3")
        val html = OpenRouterStatsUtils.buildModelsHtmlList(models)
        assertEquals("<html>Recent Models:<br/>• gpt-4<br/>• gpt-3.5-turbo<br/>• claude-3</html>", html)
    }

    @Test
    fun `buildModelsHtmlList handles more than 5 models with truncation`() {
        val models = listOf("model1", "model2", "model3", "model4", "model5", "model6")
        val html = OpenRouterStatsUtils.buildModelsHtmlList(models)
        assertEquals(
            "<html>Recent Models:<br/>• model1<br/>• model2<br/>• model3<br/>• model4<br/>• model5<br/>• +1 more</html>",
            html
        )
    }

    @Test
    fun `formatActivityText formats activity correctly`() {
        val text = OpenRouterStatsUtils.formatActivityText(50L, 2.5)
        assertEquals("50 requests, $2.5000 spent", text)
    }

    @Test
    fun `filterActivitiesByTime filters correctly for 24h period`() {
        val today = LocalDate.of(2024, 1, 2)
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val activities = listOf(
            ActivityData("2024-01-02", "model1", "model1", "e1", "p1", 1.0, 0.0, 1, 10, 5, 0), // Today
            ActivityData("2024-01-01", "model2", "model2", "e2", "p2", 1.0, 0.0, 1, 10, 5, 0), // Yesterday
            ActivityData("2023-12-26", "model3", "model3", "e3", "p3", 1.0, 0.0, 1, 10, 5, 0) // Week ago
        )

        // Test 24h filter (should include today and yesterday)
        val last24hResult = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, true)
        assertEquals(2, last24hResult.size)
        assertTrue(last24hResult.any { it.model == "model1" })
        assertTrue(last24hResult.any { it.model == "model2" })

        // Test week filter (should include all)
        val lastWeekResult = OpenRouterStatsUtils.filterActivitiesByTime(activities, today, yesterday, weekAgo, false)
        assertEquals(3, lastWeekResult.size)
    }

    @Test
    fun `parseActivityDate handles different date formats`() {
        // Test date only format
        val dateOnly = OpenRouterStatsUtils.parseActivityDate("2024-01-01")
        assertEquals(LocalDate.of(2024, 1, 1), dateOnly)

        // Test datetime format (should extract date part)
        val dateTime = OpenRouterStatsUtils.parseActivityDate("2024-01-01 12:30:45")
        assertEquals(LocalDate.of(2024, 1, 1), dateTime)

        // Test invalid format
        val invalid = OpenRouterStatsUtils.parseActivityDate("invalid-date")
        assertNull(invalid)
    }

    @Test
    fun `extractRecentModelNames sorts models by most recent usage`() {
        val activities = listOf(
            ActivityData("2024-01-01", "gpt-4", "gpt-4", "e1", "p1", 1.0, 0.0, 1, 10, 5, 0),
            ActivityData("2024-01-03", "claude-3", "claude-3", "e3", "p3", 1.0, 0.0, 1, 10, 5, 0),
            ActivityData("2024-01-02", "gpt-3.5-turbo", "gpt-3.5-turbo", "e2", "p2", 1.0, 0.0, 1, 10, 5, 0)
        )

        val modelNames = OpenRouterStatsUtils.extractRecentModelNames(activities)

        // Should be sorted by date descending (most recent first)
        assertEquals(3, modelNames.size)
        assertEquals("claude-3", modelNames[0]) // 2024-01-03 (most recent)
        assertEquals("gpt-3.5-turbo", modelNames[1]) // 2024-01-02
        assertEquals("gpt-4", modelNames[2]) // 2024-01-01 (oldest)
    }
}
