package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.progress.util.ProgressBarUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData

@DisplayName("OpenRouterStatsPopup Model Spend Aggregation Tests")
class OpenRouterStatsPopupModelSpendTest {

    @Nested
    @DisplayName("Model Spend Extraction")
    inner class ModelSpendExtractionTests {

        @Test
        fun `should aggregate spend by model`() {
            val activities = listOf(
                createActivity("openai/gpt-4", "2024-01-01", 0.001),
                createActivity("openai/gpt-4", "2024-01-02", 0.002),
                createActivity("anthropic/claude-3", "2024-01-01", 0.003)
            )

            val result = extractRecentModelsWithSpend(activities)

            assertEquals(2, result.size)
            val gpt4 = result.find { it.modelId == "openai/gpt-4" }
            assertEquals(0.003, gpt4!!.totalSpend, 0.0001)
            assertEquals("2024-01-02", gpt4.lastDate)

            val claude = result.find { it.modelId == "anthropic/claude-3" }
            assertEquals(0.003, claude!!.totalSpend, 0.0001)
        }

        @Test
        fun `should filter out activities without model`() {
            val activities = listOf(
                createActivity(null, "2024-01-01", 0.001),
                createActivity("openai/gpt-4", "2024-01-01", 0.002)
            )

            val result = extractRecentModelsWithSpend(activities)

            assertEquals(1, result.size)
            assertEquals("openai/gpt-4", result[0].modelId)
        }

        @Test
        fun `should filter out activities without date`() {
            val activities = listOf(
                createActivity("openai/gpt-4", null, 0.001),
                createActivity("openai/gpt-4", "2024-01-01", 0.002)
            )

            val result = extractRecentModelsWithSpend(activities)

            assertEquals(1, result.size)
            assertEquals(0.002, result[0].totalSpend, 0.0001)
        }

        @Test
        fun `should return empty list for empty input`() {
            val result = extractRecentModelsWithSpend(emptyList())
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Credits Bar Colour")
    inner class UsageStatusTests {

        private val warning = OpenRouterStatsPopup.WARNING_PERCENTAGE
        private val critical = OpenRouterStatsPopup.CRITICAL_PERCENTAGE

        private fun status(percentage: Int) = OpenRouterStatsPopup.usageStatus(percentage)

        @Test
        fun `the bar keeps the default colour below the warning share`() {
            assertNull(status(0))
            assertNull(status(warning - 1))
        }

        @Test
        fun `the bar warns from the warning share`() {
            assertEquals(ProgressBarUtil.WARNING_VALUE, status(warning))
            assertEquals(ProgressBarUtil.WARNING_VALUE, status(critical - 1))
        }

        @Test
        fun `the bar turns red from the critical share, overspent included`() {
            assertEquals(ProgressBarUtil.FAILED_VALUE, status(critical))
            assertEquals(ProgressBarUtil.FAILED_VALUE, status(120))
        }
    }

    @Nested
    @DisplayName("HTML List Building")
    inner class HtmlListBuildingTests {

        @Test
        fun `should format models with spend correctly`() {
            val modelsWithSpend = listOf(
                OpenRouterStatsPopup.ModelWithSpend(
                    "openai/gpt-4",
                    0.0015,
                    "2024-01-02"
                ),
                OpenRouterStatsPopup.ModelWithSpend(
                    "anthropic/claude-3",
                    0.003,
                    "2024-01-01"
                )
            )

            val html = buildModelsWithSpendHtmlList(modelsWithSpend)

            assertTrue(html.contains("openai/gpt-4"))
            assertTrue(html.contains("$0.0015"))
            assertTrue(html.contains("anthropic/claude-3"))
            assertTrue(html.contains("$0.0030"))
        }

        @Test
        fun `should show no recent models message for empty list`() {
            val html = buildModelsWithSpendHtmlList(emptyList())
            assertEquals("<html>Recent Models:<br/>• None</html>", html)
        }

        @Test
        fun `should list every model, however many`() {
            val modelsWithSpend = (1..7).map { i ->
                OpenRouterStatsPopup.ModelWithSpend("model/$i", 0.001 * i, "2024-01-0$i")
            }

            val html = buildModelsWithSpendHtmlList(modelsWithSpend)

            (1..7).forEach { assertTrue(html.contains("model/$it —"), "model/$it should be listed") }
            assertFalse(html.contains("more"))
        }
    }

    private fun createActivity(
        model: String?,
        date: String?,
        usage: Double
    ): ActivityData {
        return ActivityData(
            date = date,
            model = model,
            modelPermaslug = null,
            endpointId = null,
            providerName = null,
            usage = usage,
            byokUsageInference = null,
            requests = 1,
            promptTokens = null,
            completionTokens = null,
            reasoningTokens = null
        )
    }

    private fun extractRecentModelsWithSpend(
        activities: List<ActivityData>
    ): List<OpenRouterStatsPopupTestHelper.ModelWithSpend> {
        return activities
            .filter { it.model != null && it.date != null }
            .groupBy { it.model!! }
            .mapValues { (_, modelActivities) ->
                val totalSpend = modelActivities.sumOf { it.usage ?: 0.0 }
                val lastDate = modelActivities.maxOf { it.date!! }
                OpenRouterStatsPopupTestHelper.ModelWithSpend(
                    modelId = modelActivities.first().model!!,
                    totalSpend = totalSpend,
                    lastDate = lastDate
                )
            }
            .values
            .sortedWith(
                compareByDescending<OpenRouterStatsPopupTestHelper.ModelWithSpend> { it.lastDate }
                    .thenByDescending { it.totalSpend }
            )
    }

    private fun buildModelsWithSpendHtmlList(modelsWithSpend: List<OpenRouterStatsPopup.ModelWithSpend>): String =
        OpenRouterStatsPopup.buildModelsWithSpendHtmlList(modelsWithSpend)
}

/**
 * Helper object that mirrors the private logic from OpenRouterStatsPopup for testing
 */
object OpenRouterStatsPopupTestHelper {
    data class ModelWithSpend(
        val modelId: String,
        val totalSpend: Double,
        val lastDate: String
    )
}
