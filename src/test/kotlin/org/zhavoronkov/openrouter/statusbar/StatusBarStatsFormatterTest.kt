package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.ZoneId

@DisplayName("StatusBarStatsFormatter Tests")
class StatusBarStatsFormatterTest {
    @Test
    fun `formatStatusTextFromCredits should show percentage when hidden costs`() {
        val text = StatusBarStatsFormatter.formatStatusTextFromCredits(5.0, 10.0, showCosts = false)

        assertTrue(text.contains("50.0%"))
    }

    @Test
    fun `formatStatusTextFromCredits should show costs when enabled`() {
        val text = StatusBarStatsFormatter.formatStatusTextFromCredits(5.0, 10.0, showCosts = true)

        assertTrue(text.contains("$5.000/$10.00"))
    }

    @Test
    fun `calculateActivityRows should include today usage`() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()
        val activities = listOf(
            ActivityData(
                date = today,
                model = "model",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 1.0,
                byokUsageInference = null,
                requests = 1,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            )
        )

        val rows = StatusBarStatsFormatter.calculateActivityRows(activities)

        assertTrue(rows.contains("Today:"))
    }

    @Test
    fun `formatStatusTooltipFromCredits should include activity rows`() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()
        val activities = listOf(
            ActivityData(
                date = today,
                model = "model",
                modelPermaslug = null,
                endpointId = null,
                providerName = null,
                usage = 2.0,
                byokUsageInference = null,
                requests = 1,
                promptTokens = null,
                completionTokens = null,
                reasoningTokens = null
            )
        )

        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            "Ready",
            2.0,
            10.0,
            activities
        )

        assertTrue(tooltip.contains("Activity"))
        assertTrue(tooltip.contains("Ready"))
    }

    @Nested
    @DisplayName("Activity rows")
    inner class ActivityRows {
        @Test
        fun `calculateActivityRows shows today from the activity`() {
            val today = LocalDate.now(ZoneId.of("UTC")).toString()
            val activities = listOf(
                ActivityData(
                    date = today,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 0.567,
                    byokUsageInference = null,
                    requests = 1,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            )

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities)

            // Should show the API value
            assertTrue(rows.contains("\$0.567"))
        }

        @Test
        fun `calculateActivityRows shows yesterday from the activity`() {
            val yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1).toString()
            val activities = listOf(
                ActivityData(
                    date = yesterday,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 0.789,
                    byokUsageInference = null,
                    requests = 5,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            )

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities)

            // Yesterday should still use API data
            assertTrue(rows.contains("\$0.789"))
        }

        @Test
        fun `calculateActivityRows should still show 7 Days from API data`() {
            // Create activities for the past week
            val activities = (0..6).map { daysAgo ->
                val date = LocalDate.now(ZoneId.of("UTC")).minusDays(daysAgo.toLong()).toString()
                ActivityData(
                    date = date,
                    model = "model",
                    modelPermaslug = null,
                    endpointId = null,
                    providerName = null,
                    usage = 1.0, // $1 per day = $7 total
                    byokUsageInference = null,
                    requests = 1,
                    promptTokens = null,
                    completionTokens = null,
                    reasoningTokens = null
                )
            }

            val rows = StatusBarStatsFormatter.calculateActivityRows(activities)

            // 7 Days should show sum from API (7 days * $1 = $7)
            assertTrue(rows.contains("\$7.000"))
        }
    }
}
