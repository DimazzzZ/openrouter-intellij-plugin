package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate

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
        val today = LocalDate.now().toString()
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
        val today = LocalDate.now().toString()
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
}
