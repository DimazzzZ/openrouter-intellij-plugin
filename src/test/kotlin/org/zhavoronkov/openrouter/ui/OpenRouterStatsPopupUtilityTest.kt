package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import com.intellij.openapi.project.Project
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.models.*
import java.time.LocalDate

/**
 * Tests for utility methods and edge cases in OpenRouterStatsPopup
 * These tests ensure that the helper methods work correctly and handle edge cases
 */
class OpenRouterStatsPopupUtilityTest {

    private lateinit var project: Project
    private lateinit var openRouterService: OpenRouterService
    private lateinit var settingsService: OpenRouterSettingsService
    private lateinit var popup: OpenRouterStatsPopup

    @BeforeEach
    fun setUp() {
        project = mock(Project::class.java)
        openRouterService = mock(OpenRouterService::class.java)
        settingsService = mock(OpenRouterSettingsService::class.java)

        // Create popup instance for testing utility methods
        popup = OpenRouterStatsPopup(project, openRouterService, settingsService)
    }

    // ========================================
    // Currency Formatting Tests
    // ========================================

    @Test
    fun `formatCurrency should handle zero values`() {
        val method = getPrivateMethod("formatCurrency", Double::class.java, Int::class.java)

        val result = method.invoke(popup, 0.0, 3) as String
        assertEquals("0.000", result)
    }

    @Test
    fun `formatCurrency should handle negative values`() {
        val method = getPrivateMethod("formatCurrency", Double::class.java, Int::class.java)

        val result = method.invoke(popup, -15.123, 2) as String
        assertEquals("-15.12", result)
    }

    @Test
    fun `formatCurrency should handle large values`() {
        val method = getPrivateMethod("formatCurrency", Double::class.java, Int::class.java)

        val result = method.invoke(popup, 1234567.89, 2) as String
        assertEquals("1234567.89", result)
    }

    @Test
    fun `formatCurrency should handle very small values`() {
        val method = getPrivateMethod("formatCurrency", Double::class.java, Int::class.java)

        val result = method.invoke(popup, 0.0001, 4) as String
        assertEquals("0.0001", result)
    }

    // ========================================
    // Activity Text Formatting Tests
    // ========================================

    @Test
    fun `formatActivityText should handle zero requests`() {
        val method = getPrivateMethod("formatActivityText", Long::class.java, Double::class.java)

        val result = method.invoke(popup, 0L, 0.0) as String
        assertEquals("0 requests, \$0.0000 spent", result)
    }

    @Test
    fun `formatActivityText should handle single request`() {
        val method = getPrivateMethod("formatActivityText", Long::class.java, Double::class.java)

        val result = method.invoke(popup, 1L, 0.0050) as String
        assertEquals("1 requests, \$0.0050 spent", result)
    }

    @Test
    fun `formatActivityText should handle large numbers`() {
        val method = getPrivateMethod("formatActivityText", Long::class.java, Double::class.java)

        val result = method.invoke(popup, 1000000L, 1234.5678) as String
        assertEquals("1000000 requests, \$1234.5678 spent", result)
    }

    // ========================================
    // Models HTML List Tests
    // ========================================

    @Test
    fun `buildModelsHtmlList should handle empty list`() {
        val method = getPrivateMethod("buildModelsHtmlList", List::class.java)

        val result = method.invoke(popup, emptyList<String>()) as String
        assertEquals("<html>Recent Models:<br/>• None</html>", result)
    }

    @Test
    fun `buildModelsHtmlList should handle single model`() {
        val method = getPrivateMethod("buildModelsHtmlList", List::class.java)

        val models = listOf("gpt-4")
        val result = method.invoke(popup, models) as String
        assertEquals("<html>Recent Models:<br/>• gpt-4</html>", result)
    }

    @Test
    fun `buildModelsHtmlList should limit to 5 models and show count`() {
        val method = getPrivateMethod("buildModelsHtmlList", List::class.java)

        val models = listOf("gpt-4", "gpt-3.5-turbo", "claude-3", "gemini-pro", "llama-2", "codex", "davinci")
        val result = method.invoke(popup, models) as String

        assertTrue(result.contains("gpt-4"))
        assertTrue(result.contains("gpt-3.5-turbo"))
        assertTrue(result.contains("claude-3"))
        assertTrue(result.contains("gemini-pro"))
        assertTrue(result.contains("llama-2"))
        assertTrue(result.contains("+2 more"))
        assertFalse(result.contains("codex"))
        assertFalse(result.contains("davinci"))
    }

    // ========================================
    // Activity Statistics Tests
    // ========================================

    @Test
    fun `calculateActivityStats should handle empty list`() {
        val method = getPrivateMethod("calculateActivityStats", List::class.java)

        val result = method.invoke(popup, emptyList<ActivityData>()) as Pair<*, *>
        assertEquals(0L, result.first)
        assertEquals(0.0, result.second)
    }

    @Test
    fun `calculateActivityStats should sum requests and usage correctly`() {
        val method = getPrivateMethod("calculateActivityStats", List::class.java)

        val activities = listOf(
            createActivityData("2024-12-05", requests = 10, usage = 1.5),
            createActivityData("2024-12-04", requests = 5, usage = 2.3),
            createActivityData("2024-12-03", requests = 20, usage = 0.7)
        )

        val result = method.invoke(popup, activities) as Pair<*, *>
        assertEquals(35L, result.first)  // 10 + 5 + 20
        assertEquals(4.5, result.second as Double, 0.001)  // 1.5 + 2.3 + 0.7
    }

    // ========================================
    // Date Parsing Tests
    // ========================================

    @Test
    fun `parseActivityDate should handle date-only format`() {
        val method = getPrivateMethod("parseActivityDate", String::class.java)

        val result = method.invoke(popup, "2024-12-05") as LocalDate?
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should handle datetime format`() {
        val method = getPrivateMethod("parseActivityDate", String::class.java)

        val result = method.invoke(popup, "2024-12-05 14:30:00") as LocalDate?
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should handle ISO format`() {
        val method = getPrivateMethod("parseActivityDate", String::class.java)

        val result = method.invoke(popup, "2024-12-05T14:30:00Z") as LocalDate?
        assertEquals(LocalDate.of(2024, 12, 5), result)
    }

    @Test
    fun `parseActivityDate should return null for invalid format`() {
        val method = getPrivateMethod("parseActivityDate", String::class.java)

        val result = method.invoke(popup, "invalid-date") as LocalDate?
        assertNull(result)
    }

    @Test
    fun `parseActivityDate should return null for empty string`() {
        val method = getPrivateMethod("parseActivityDate", String::class.java)

        val result = method.invoke(popup, "") as LocalDate?
        assertNull(result)
    }

    // ========================================
    // Activity Filtering Tests
    // ========================================

    @Test
    fun `filterActivitiesByTime should filter last 24h correctly`() {
        val method = getPrivateMethod(
            "filterActivitiesByTime",
            List::class.java, LocalDate::class.java, LocalDate::class.java, LocalDate::class.java, Boolean::class.java
        )

        val today = LocalDate.of(2024, 12, 5)
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val activities = listOf(
            createActivityData("2024-12-05"), // today
            createActivityData("2024-12-04"), // yesterday
            createActivityData("2024-12-03"), // 2 days ago
            createActivityData("2024-11-28")  // week ago
        )

        val result = method.invoke(popup, activities, today, yesterday, weekAgo, true) as List<*>
        assertEquals(2, result.size) // Should include today and yesterday only
    }

    @Test
    fun `filterActivitiesByTime should filter last week correctly`() {
        val method = getPrivateMethod(
            "filterActivitiesByTime",
            List::class.java, LocalDate::class.java, LocalDate::class.java, LocalDate::class.java, Boolean::class.java
        )

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

        val result = method.invoke(popup, activities, today, yesterday, weekAgo, false) as List<*>
        assertEquals(4, result.size) // Should include last 7 days + week ago
    }

    // ========================================
    // Model Name Extraction Tests
    // ========================================

    @Test
    fun `extractRecentModelNames should sort by most recent date`() {
        val method = getPrivateMethod("extractRecentModelNames", List::class.java)

        val activities = listOf(
            createActivityData("2024-12-03", model = "gpt-4"),
            createActivityData("2024-12-05", model = "claude-3"),
            createActivityData("2024-12-04", model = "gpt-3.5-turbo"),
            createActivityData("2024-12-01", model = "gpt-4") // older gpt-4 usage
        )

        val result = method.invoke(popup, activities) as List<*>
        assertEquals(3, result.size) // 3 unique models
        assertEquals("claude-3", result[0])     // most recent (2024-12-05)
        assertEquals("gpt-3.5-turbo", result[1]) // middle (2024-12-04)
        assertEquals("gpt-4", result[2])        // oldest recent (2024-12-03, not 12-01)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun getPrivateMethod(methodName: String, vararg parameterTypes: Class<*>) =
        OpenRouterStatsPopup::class.java.getDeclaredMethod(methodName, *parameterTypes).apply {
            isAccessible = true
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
