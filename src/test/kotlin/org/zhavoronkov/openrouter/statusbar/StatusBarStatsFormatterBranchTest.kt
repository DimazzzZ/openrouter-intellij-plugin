package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.ZoneId

@DisplayName("StatusBarStatsFormatter Branch Tests")
class StatusBarStatsFormatterBranchTest {

    private fun activity(date: String?, usage: Double?) = ActivityData(
        date = date, model = "m", modelPermaslug = null, endpointId = null, providerName = null,
        usage = usage, byokUsageInference = null, requests = 1,
        promptTokens = null, completionTokens = null, reasoningTokens = null
    )

    @Test
    @DisplayName("status text reports no-credits when total is zero")
    fun statusTextNoCredits() {
        val text = StatusBarStatsFormatter.formatStatusTextFromCredits(1.5, 0.0, showCosts = true)
        assertTrue(text.contains("no credits"))
    }

    @Test
    @DisplayName("tooltip shows Unlimited when total is zero and no activity list")
    fun tooltipUnlimitedNoActivity() {
        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Status: Ready",
            used = 2.0,
            total = 0.0,
            activityList = null
        )
        assertTrue(tooltip.contains("Unlimited"))
        assertFalse(tooltip.contains("<b>Activity</b>"))
    }

    @Test
    @DisplayName("tooltip renders activity rows when total is positive and list is present")
    fun tooltipWithActivity() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()
        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Status: Ready",
            used = 2.0,
            total = 10.0,
            activityList = listOf(activity(today, 1.0))
        )
        assertTrue(tooltip.contains("<b>Activity</b>"))
    }

    @Test
    @DisplayName("processActivity skips entries with a null date")
    fun processSkipsNullDate() {
        val rows = StatusBarStatsFormatter.calculateActivityRows(listOf(activity(null, 5.0)))
        assertTrue(rows.contains("\$0.000"))
    }

    @Test
    @DisplayName("processActivity treats a null usage as zero")
    fun processNullUsageIsZero() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()
        val rows = StatusBarStatsFormatter.calculateActivityRows(listOf(activity(today, null)))
        assertTrue(rows.contains("Today:"))
    }

    @Test
    @DisplayName("processActivity ignores dates outside the trailing-week window")
    fun processIgnoresOutOfWindow() {
        val old = LocalDate.now(ZoneId.of("UTC")).minusDays(30).toString()
        val rows = StatusBarStatsFormatter.calculateActivityRows(listOf(activity(old, 9.0)))
        assertTrue(rows.contains("7 Days:"))
        assertTrue(rows.contains("\$0.000"))
    }

    @Test
    @DisplayName("parseActivityDate accepts a short date string unchanged")
    fun parseShortDate() {
        assertTrue(StatusBarStatsFormatter.parseActivityDate("2024-01-02") == LocalDate.of(2024, 1, 2))
    }

    @Test
    @DisplayName("parseActivityDate trims a long timestamp to the date part")
    fun parseLongDate() {
        assertTrue(StatusBarStatsFormatter.parseActivityDate("2024-01-02T13:45:00Z") == LocalDate.of(2024, 1, 2))
    }

    @Test
    @DisplayName("tooltip renders N/A remaining when yesterday spend is zero")
    fun tooltipRemainingNa() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()
        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Status: Ready",
            used = 2.0,
            total = 10.0,
            activityList = listOf(activity(today, 1.0)),
            creditsData = null
        )
        assertTrue(tooltip.contains("N/A"))
    }
}
