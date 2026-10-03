package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData
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
    @DisplayName("credits block leads with the total and then what is left, never what was spent")
    fun creditsBlockShowsTotalThenRemaining() {
        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Ready",
            used = 2.5,
            total = 10.0
        )

        assertTrue(tooltip.contains("Remaining:"), "Expected a Remaining row, got: $tooltip")
        assertFalse(tooltip.contains("Used:"), "What was spent is no longer shown: $tooltip")
        assertTrue(tooltip.contains("\$7.500"), "Remaining must be total minus used, got: $tooltip")
        assertTrue(
            tooltip.indexOf("Total:") < tooltip.indexOf("Remaining:"),
            "The total must come first, got: $tooltip"
        )
    }

    /**
     * The activity block estimates how many DAYS are left, so it cannot share a label with the
     * credits block's own remaining balance - one is a number of days, the other an amount of
     * money, and a tooltip carrying both rows under one name is unreadable.
     */
    @Test
    @DisplayName("the days estimate is labelled apart from the remaining balance")
    fun daysEstimateDoesNotCollideWithRemainingBalance() {
        val yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1).toString()
        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Ready",
            used = 5.0,
            total = 10.0,
            activityList = listOf(activity(yesterday, 1.0))
        )

        assertTrue(tooltip.contains("Days Left:"), "Expected a days-left row, got: $tooltip")
        assertEquals(
            1,
            Regex("Remaining:").findAll(tooltip).count(),
            "Only the credits balance may be called Remaining, got: $tooltip"
        )
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

    @Test
    @DisplayName("processActivity ignores a future-dated row - it is outside the trailing week too")
    fun processIgnoresFutureDate() {
        val tomorrow = LocalDate.now(ZoneId.of("UTC")).plusDays(1).toString()
        val rows = StatusBarStatsFormatter.calculateActivityRows(listOf(activity(tomorrow, 9.0)))
        assertTrue(rows.contains("7 Days:"))
        assertTrue(rows.contains("\$0.000"))
    }

    @Test
    @DisplayName("a positive yesterday spend yields a days-remaining estimate without the history service")
    fun tooltipRemainingFromYesterdaySpend() {
        // Before the fix this raised NPE: the guard around CreditUsageHistoryService.getInstance()
        // caught IllegalStateException, but an absent application makes getInstance() throw NPE, so
        // the documented fallback was unreachable and the exception escaped to the caller.
        val yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1).toString()

        val tooltip = StatusBarStatsFormatter.formatStatusTooltipFromCredits(
            statusText = "Status: Ready",
            used = 2.0,
            total = 12.0,
            activityList = listOf(activity(yesterday, 2.0)),
            creditsData = null
        )

        // remaining = 12 - 2 = 10, yesterday spend = 2.0 -> ~5 days
        assertTrue(tooltip.contains("~5 days"), "Expected a days-remaining estimate, got: $tooltip")
    }

    @Test
    @DisplayName("without the history service, today's cost is what the activity says")
    fun todayFromActivityWithoutHistory() {
        val today = LocalDate.now(ZoneId.of("UTC")).toString()

        val rows = StatusBarStatsFormatter.calculateActivityRowsWithHistory(
            activityList = listOf(activity(today, 0.5)),
            creditsData = CreditsData(totalCredits = 10.0, totalUsage = 3.0),
            remainingCredits = 7.0
        )

        assertTrue(rows.contains("\$0.500"), "got: $rows")
    }
}
