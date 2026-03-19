package org.zhavoronkov.openrouter.statusbar

import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.services.CreditUsageHistoryService
import org.zhavoronkov.openrouter.services.OpenRouterGenerationTrackingService
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracted helpers for OpenRouterStatusBarWidget formatting logic.
 */
@Suppress("TooManyFunctions")
object StatusBarStatsFormatter {

    private const val DAYS_IN_WEEK = 7
    private const val DATE_STRING_LENGTH = 10
    private const val PERCENTAGE_MULTIPLIER = 100

    data class ActivityCosts(
        var today: Double = 0.0,
        var yesterday: Double = 0.0,
        var lastWeek: Double = 0.0
    )

    /**
     * Parameters for tooltip formatting.
     */
    data class TooltipParams(
        val statusText: String,
        val used: Double,
        val total: Double,
        val activityList: List<ActivityData>? = null,
        val trackingService: OpenRouterGenerationTrackingService? = null,
        val creditsData: CreditsData? = null
    )

    fun formatStatusTextFromCredits(used: Double, total: Double, showCosts: Boolean): String {
        return if (total > 0) {
            if (showCosts) {
                val usedFormatted = String.format(Locale.US, "%.3f", used)
                val totalFormatted = String.format(Locale.US, "%.2f", total)
                "Status: Ready - $$usedFormatted/$$totalFormatted"
            } else {
                val percentage = (used / total) * PERCENTAGE_MULTIPLIER
                "Status: Ready - ${String.format(Locale.US, "%.1f", percentage)}% used"
            }
        } else {
            "Status: Ready - $${String.format(Locale.US, "%.3f", used)} (no credits)"
        }
    }

    @Suppress("LongParameterList")
    fun formatStatusTooltipFromCredits(
        statusText: String,
        used: Double,
        total: Double,
        activityList: List<ActivityData>? = null,
        trackingService: OpenRouterGenerationTrackingService? = null,
        creditsData: CreditsData? = null
    ): String {
        val params = TooltipParams(statusText, used, total, activityList, trackingService, creditsData)
        return formatStatusTooltip(params)
    }

    private fun formatStatusTooltip(params: TooltipParams): String {
        val usedText = "$${String.format(Locale.US, "%.3f", params.used)}"
        val totalText = if (params.total > 0) {
            "$${String.format(Locale.US, "%.2f", params.total)}"
        } else {
            "Unlimited"
        }
        val remaining = if (params.total > 0) params.total - params.used else 0.0

        val activityRows = if (params.activityList != null) {
            calculateActivityRowsWithHistory(
                params.activityList,
                params.trackingService,
                params.creditsData,
                remaining
            )
        } else {
            ""
        }

        return """
            <html>
            <table border='0' cellpadding='1' cellspacing='0'>
              <tr><td colspan='2'><b>Connection</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>${params.statusText}</td></tr>
              <tr><td>Auth:</td><td align='right' style='padding-left: 30px;'>Provisioning Key</td></tr>
              <tr height='8'><td></td></tr>
              <tr><td colspan='2'><b>Credits</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Used:</td><td align='right' style='padding-left: 30px;'>$usedText</td></tr>
              <tr><td>Total:</td><td align='right' style='padding-left: 30px;'>$totalText</td></tr>
              $activityRows
            </table>
            </html>
        """.trimIndent()
    }

    /**
     * Calculate activity rows using API data for all metrics.
     */
    fun calculateActivityRows(activityList: List<ActivityData>): String {
        return calculateActivityRows(activityList, null)
    }

    /**
     * Calculate activity rows with optional local tracking for "Today" metric.
     */
    fun calculateActivityRows(
        activityList: List<ActivityData>,
        trackingService: OpenRouterGenerationTrackingService?
    ): String {
        val utcNow = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = utcNow.minusDays(1)
        val lastWeekStart = utcNow.minusDays((DAYS_IN_WEEK - 1).toLong())

        val costs = ActivityCosts()

        activityList.forEach { activity ->
            processActivity(activity, utcNow, yesterday, lastWeekStart, costs)
        }

        val todayCost = trackingService?.getTodayCost() ?: costs.today

        return formatActivityRowsHtml(todayCost, costs.yesterday, costs.lastWeek)
    }

    fun processActivity(
        activity: ActivityData,
        utcNow: LocalDate,
        yesterday: LocalDate,
        lastWeekStart: LocalDate,
        costs: ActivityCosts
    ) {
        val dateStr = activity.date ?: return
        val usage = activity.usage ?: 0.0
        val activityDate = parseActivityDate(dateStr)

        when {
            activityDate.isEqual(utcNow) -> costs.today += usage
            activityDate.isEqual(yesterday) -> costs.yesterday += usage
        }

        if (!activityDate.isBefore(lastWeekStart) && !activityDate.isAfter(utcNow)) {
            costs.lastWeek += usage
        }
    }

    fun parseActivityDate(dateStr: String): LocalDate {
        val datePart = if (dateStr.length > DATE_STRING_LENGTH) {
            dateStr.substring(0, DATE_STRING_LENGTH)
        } else {
            dateStr
        }
        return LocalDate.parse(datePart, DateTimeFormatter.ISO_DATE)
    }

    private fun formatActivityRowsHtml(todayCost: Double, yesterdayCost: Double, lastWeekCost: Double): String {
        val todayText = "$${String.format(Locale.US, "%.3f", todayCost)}"
        val yesterdayText = "$${String.format(Locale.US, "%.3f", yesterdayCost)}"
        val lastWeekText = "$${String.format(Locale.US, "%.3f", lastWeekCost)}"

        return """
          <tr height='8'><td></td></tr>
          <tr><td colspan='2'><b>Activity</b></td></tr>
          <tr height='2'><td></td></tr>
          <tr><td>Today:</td><td align='right' style='padding-left: 30px;'>$todayText</td></tr>
          <tr><td>Yesterday:</td><td align='right' style='padding-left: 30px;'>$yesterdayText</td></tr>
          <tr><td>7 Days:</td><td align='right' style='padding-left: 30px;'>$lastWeekText</td></tr>
        """.trimIndent()
    }

    /**
     * Calculate activity rows using credit usage history service for accurate "today" calculation.
     */
    fun calculateActivityRowsWithHistory(
        activityList: List<ActivityData>,
        trackingService: OpenRouterGenerationTrackingService?,
        creditsData: CreditsData?,
        remainingCredits: Double
    ): String {
        val utcNow = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = utcNow.minusDays(1)
        val lastWeekStart = utcNow.minusDays((DAYS_IN_WEEK - 1).toLong())

        val costs = ActivityCosts()

        activityList.forEach { activity ->
            processActivity(activity, utcNow, yesterday, lastWeekStart, costs)
        }

        val todayCost = calculateTodayCostFromHistory(creditsData)
            ?: trackingService?.getTodayCost()
            ?: costs.today

        val yesterdayCost = costs.yesterday
        val daysRemaining = calculateDaysRemainingFromHistory(remainingCredits, yesterdayCost)

        return formatActivityRowsHtmlWithDays(todayCost, yesterdayCost, costs.lastWeek, daysRemaining)
    }

    @Suppress("SwallowedException")
    private fun calculateTodayCostFromHistory(creditsData: CreditsData?): Double? {
        if (creditsData == null) return null

        return try {
            val historyService = CreditUsageHistoryService.getInstance()
            historyService.calculateTodaySpent(creditsData.totalUsage)
        } catch (_: IllegalStateException) {
            // Service not available during initialization - fallback to other methods
            null
        }
    }

    @Suppress("SwallowedException")
    private fun calculateDaysRemainingFromHistory(remainingCredits: Double, yesterdaySpent: Double): Int? {
        if (yesterdaySpent <= 0) return null

        return try {
            val historyService = CreditUsageHistoryService.getInstance()
            historyService.calculateDaysRemaining(remainingCredits, yesterdaySpent)
        } catch (_: IllegalStateException) {
            // Service not available - use fallback calculation
            if (yesterdaySpent > 0) (remainingCredits / yesterdaySpent).toInt() else null
        }
    }

    private fun formatActivityRowsHtmlWithDays(
        todayCost: Double,
        yesterdayCost: Double,
        lastWeekCost: Double,
        daysRemaining: Int?
    ): String {
        val todayText = "$${String.format(Locale.US, "%.3f", todayCost)}"
        val yesterdayText = "$${String.format(Locale.US, "%.3f", yesterdayCost)}"
        val lastWeekText = "$${String.format(Locale.US, "%.3f", lastWeekCost)}"
        val daysText = daysRemaining?.let { "~$it days" } ?: "N/A"

        return """
          <tr height='8'><td></td></tr>
          <tr><td colspan='2'><b>Activity</b></td></tr>
          <tr height='2'><td></td></tr>
          <tr><td>Today:</td><td align='right' style='padding-left: 30px;'>$todayText</td></tr>
          <tr><td>Yesterday:</td><td align='right' style='padding-left: 30px;'>$yesterdayText</td></tr>
          <tr><td>7 Days:</td><td align='right' style='padding-left: 30px;'>$lastWeekText</td></tr>
          <tr><td>Remaining:</td><td align='right' style='padding-left: 30px;'>$daysText</td></tr>
        """.trimIndent()
    }
}
