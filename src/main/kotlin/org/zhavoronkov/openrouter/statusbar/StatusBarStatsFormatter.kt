package org.zhavoronkov.openrouter.statusbar

import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracted helpers for OpenRouterStatusBarWidget formatting logic.
 */
object StatusBarStatsFormatter {

    private const val DAYS_IN_WEEK = 7
    private const val DATE_STRING_LENGTH = 10
    private const val PERCENTAGE_MULTIPLIER = 100

    data class ActivityCosts(
        var today: Double = 0.0,
        var yesterday: Double = 0.0,
        var lastWeek: Double = 0.0
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

    fun formatStatusTooltipFromCredits(
        statusText: String,
        used: Double,
        total: Double,
        activityList: List<ActivityData>? = null
    ): String {
        val usedText = "$${String.format(Locale.US, "%.3f", used)}"
        val totalText = if (total > 0) "$${String.format(Locale.US, "%.2f", total)}" else "Unlimited"

        val activityRows = if (activityList != null) {
            calculateActivityRows(activityList)
        } else {
            ""
        }

        return """
            <html>
            <table border='0' cellpadding='1' cellspacing='0'>
              <tr><td colspan='2'><b>Connection</b></td></tr>
              <tr height='2'><td></td></tr>
              <tr><td>Status:</td><td align='right' style='padding-left: 30px;'>$statusText</td></tr>
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

    fun calculateActivityRows(activityList: List<ActivityData>): String {
        val utcNow = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = utcNow.minusDays(1)
        val lastWeekStart = utcNow.minusDays((DAYS_IN_WEEK - 1).toLong())

        val costs = ActivityCosts()

        activityList.forEach { activity ->
            processActivity(activity, utcNow, yesterday, lastWeekStart, costs)
        }

        return formatActivityRowsHtml(costs.today, costs.yesterday, costs.lastWeek)
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
}
