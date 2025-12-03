package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.models.ActivityData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import com.intellij.openapi.project.Project
import java.time.LocalDate
import java.util.*

class OpenRouterStatsPopupTest {

    private lateinit var project: Project
    private lateinit var popup: OpenRouterStatsPopup

    @BeforeEach
    fun setUp() {
        project = mock(Project::class.java)
        // Use the test-friendly constructor with null services
        popup = OpenRouterStatsPopup(project, null, null)
    }

    @Test
    fun `formatCurrency returns correct format with default decimals`() {
        // Test the formatCurrency function that had the compilation error
        val formatted = popup.javaClass.getDeclaredMethod("formatCurrency", Double::class.java, Int::class.java)
            .apply { isAccessible = true }
            .invoke(popup, 3.14159, 3) as String

        assertEquals("3.142", formatted) // Rounded to 3 decimal places
    }

    @Test
    fun `formatCurrency works with different decimal places`() {
        val formattedMethod = popup.javaClass.getDeclaredMethod("formatCurrency", Double::class.java, Int::class.java)
            .apply { isAccessible = true }

        // Test with various decimal places
        assertEquals("3.14", formattedMethod.invoke(popup, 3.14159, 2) as String)
        assertEquals("3.142", formattedMethod.invoke(popup, 3.14159, 3) as String)
        assertEquals("3.1416", formattedMethod.invoke(popup, 3.14159, 4) as String)
        assertEquals("3", formattedMethod.invoke(popup, 3.14159, 0) as String)
    }

    @Test
    fun `formatLargeNumber returns correct format with commas`() {
        // Test the formatLargeNumber function
        val formatted = popup.javaClass.getDeclaredMethod("formatLargeNumber", Long::class.java)
            .apply { isAccessible = true }
            .invoke(popup, 1234567L) as String

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

        val method = popup.javaClass.getDeclaredMethod("calculateActivityStats", List::class.java)
            .apply { isAccessible = true }
        val result = method.invoke(popup, activities) as Pair<*, *>

        // Check that the first element is Long (total requests = 15)
        assertEquals(15L, result.first)
        assertTrue(result.first is Long, "First element should be Long")

        // Check that the second element is Double (total usage = 2.0)
        assertEquals(2.0, result.second)
        assertTrue(result.second is Double, "Second element should be Double")
    }

    @Test
    fun `buildModelsHtmlList returns empty list html when no models`() {
        val html = popup.javaClass.getDeclaredMethod("buildModelsHtmlList", List::class.java)
            .apply { isAccessible = true }
            .invoke(popup, emptyList<String>()) as String

        assertEquals("<html>Recent Models:<br/>• None</html>", html)
    }

    @Test
    fun `buildModelsHtmlList returns formatted html with models`() {
        val models = listOf("gpt-4", "gpt-3.5-turbo", "claude-3")
        val html = popup.javaClass.getDeclaredMethod("buildModelsHtmlList", List::class.java)
            .apply { isAccessible = true }
            .invoke(popup, models) as String

        assertEquals("<html>Recent Models:<br/>• gpt-4<br/>• gpt-3.5-turbo<br/>• claude-3</html>", html)
    }

    @Test
    fun `buildModelsHtmlList handles more than 5 models with truncation`() {
        val models = listOf("model1", "model2", "model3", "model4", "model5", "model6")
        val html = popup.javaClass.getDeclaredMethod("buildModelsHtmlList", List::class.java)
            .apply { isAccessible = true }
            .invoke(popup, models) as String

        assertEquals("<html>Recent Models:<br/>• model1<br/>• model2<br/>• model3<br/>• model4<br/>• model5<br/>• +1 more</html>", html)
    }

    @Test
    fun `formatActivityText formats activity correctly`() {
        val text = popup.javaClass.getDeclaredMethod("formatActivityText", Long::class.java, Double::class.java)
            .apply { isAccessible = true }
            .invoke(popup, 50L, 2.5) as String

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
            ActivityData("2023-12-26", "model3", "model3", "e3", "p3", 1.0, 0.0, 1, 10, 5, 0)  // Week ago
        )

        val method = popup.javaClass.getDeclaredMethod("filterActivitiesByTime", List::class.java, LocalDate::class.java, LocalDate::class.java, LocalDate::class.java, Boolean::class.java)
            .apply { isAccessible = true }

        // Test 24h filter (should include today and yesterday)
        val last24hResult = method.invoke(popup, activities, today, yesterday, weekAgo, true) as List<*>
        assertEquals(2, last24hResult.size)
        assertTrue(last24hResult.any { (it as ActivityData).model == "model1" })
        assertTrue(last24hResult.any { (it as ActivityData).model == "model2" })

        // Test week filter (should include all)
        val lastWeekResult = method.invoke(popup, activities, today, yesterday, weekAgo, false) as List<*>
        assertEquals(3, lastWeekResult.size)
    }

    @Test
    fun `parseActivityDate handles different date formats`() {
        val method = popup.javaClass.getDeclaredMethod("parseActivityDate", String::class.java)
            .apply { isAccessible = true }

        // Test date only format
        val dateOnly = method.invoke(popup, "2024-01-01") as LocalDate?
        assertEquals(LocalDate.of(2024, 1, 1), dateOnly)

        // Test datetime format (should extract date part)
        val dateTime = method.invoke(popup, "2024-01-01 12:30:45") as LocalDate?
        assertEquals(LocalDate.of(2024, 1, 1), dateTime)

        // Test invalid format
        val invalid = method.invoke(popup, "invalid-date")
        assertNull(invalid)
    }

    @Test
    fun `constantsAreDefined ensures all required constants exist`() {
        // Test that verifies the companion object constants are properly defined
        // This is important for the refactored code that depends on these constants

        // Access the companion object to verify constants exist
        OpenRouterStatsPopup::class.java.getDeclaredField("Companion")
            .apply { isAccessible = true }
        
        // Verify key constants used by the refactored methods
        val defaultLoadingText = OpenRouterStatsPopup::class.java.getDeclaredField("DEFAULT_LOADING_TEXT")
            .apply { isAccessible = true }.get(null)
        assertEquals("Loading...", defaultLoadingText)

        val loadingModelsHtml = OpenRouterStatsPopup::class.java.getDeclaredField("LOADING_MODELS_HTML")
            ?.apply { isAccessible = true }?.get(null)
        assertEquals("<html>Recent Models:<br/>• Loading...</html>", loadingModelsHtml)
    }
}
