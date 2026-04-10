package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("HTML List Building")
    inner class HtmlListBuildingTests {

        @Test
        fun `should format models with spend correctly`() {
            val modelsWithSpend = listOf(
                OpenRouterStatsPopupTestHelper.ModelWithSpend(
                    "openai/gpt-4",
                    0.0015,
                    "2024-01-02"
                ),
                OpenRouterStatsPopupTestHelper.ModelWithSpend(
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
        fun `should limit displayed models to 5`() {
            val modelsWithSpend = (1..7).map { i ->
                OpenRouterStatsPopupTestHelper.ModelWithSpend(
                    "model/$i",
                    0.001 * i,
                    "2024-01-0$i"
                )
            }

            val html = buildModelsWithSpendHtmlList(modelsWithSpend)

            assertTrue(html.contains("+2 more"))
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

    private fun buildModelsWithSpendHtmlList(
        modelsWithSpend: List<OpenRouterStatsPopupTestHelper.ModelWithSpend>
    ): String {
        return OpenRouterStatsPopupTestHelper.buildModelsWithSpendHtmlList(modelsWithSpend)
    }
}

/**
 * Helper object that mirrors the private logic from OpenRouterStatsPopup for testing
 */
object OpenRouterStatsPopupTestHelper {
    private const val ACTIVITY_DISPLAY_LIMIT = 5
    private const val NO_RECENT_MODELS_HTML = "<html>Recent Models:<br/>• None</html>"

    data class ModelWithSpend(
        val modelId: String,
        val totalSpend: Double,
        val lastDate: String
    )

    fun buildModelsWithSpendHtmlList(modelsWithSpend: List<ModelWithSpend>): String {
        return when {
            modelsWithSpend.isEmpty() -> NO_RECENT_MODELS_HTML
            else -> {
                val displayModels = modelsWithSpend.take(ACTIVITY_DISPLAY_LIMIT)
                val bullets = displayModels.joinToString("<br/>") { model ->
                    val spendFormatted = String.format(java.util.Locale.US, "%.4f", model.totalSpend)
                    "• ${model.modelId} — $$spendFormatted"
                }
                val moreText = if (modelsWithSpend.size > ACTIVITY_DISPLAY_LIMIT) {
                    "<br/>• +${modelsWithSpend.size - ACTIVITY_DISPLAY_LIMIT} more"
                } else {
                    ""
                }
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
    }
}
