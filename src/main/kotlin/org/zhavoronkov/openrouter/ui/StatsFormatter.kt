package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Handles formatting of statistics data for display
 */
object StatsFormatter {

    private const val HOURS_IN_DAY = 24
    private const val DATE_STRING_LENGTH = 10
    private const val DEFAULT_DECIMALS = 3
    private const val ACTIVITY_DECIMALS = 4

    fun formatCurrency(value: Double, decimals: Int = DEFAULT_DECIMALS): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    fun formatActivityText(requests: Long, usage: Double): String {
        val usageStr = formatCurrency(usage, decimals = ACTIVITY_DECIMALS)
        return "$requests requests, $$usageStr"
    }

    fun buildModelsHtmlList(models: List<String>): String {
        return if (models.isEmpty()) {
            "No models used"
        } else {
            val modelsList = models.joinToString("") { model ->
                "<li style='margin: 2px 0;'>$model</li>"
            }
            "<html><ul style='margin: 0; padding-left: 20px;'>$modelsList</ul></html>"
        }
    }

    fun calculateActivityStats(activities: List<ActivityData>): Pair<Long, Double> {
        val totalRequests = activities.sumOf { (it.requests ?: 0).toLong() }
        val totalUsage = activities.sumOf { it.usage ?: 0.0 }
        return Pair(totalRequests, totalUsage)
    }

    fun filterActivitiesByTime(
        activities: List<ActivityData>,
        hoursAgo: Int
    ): List<ActivityData> {
        val cutoffDate = LocalDate.now().minusDays((hoursAgo / HOURS_IN_DAY).toLong())
        return activities.filter { activity ->
            val dateStr = activity.date
            if (dateStr != null) {
                val activityDate = parseActivityDate(dateStr)
                activityDate != null && !activityDate.isBefore(cutoffDate)
            } else {
                false
            }
        }
    }

    fun extractRecentModelNames(activities: List<ActivityData>): List<String> {
        return activities
            .filter { it.model != null && it.date != null }
            .groupBy { it.model!! }
            .mapValues { (_, activityList) -> activityList.maxOf { it.date!! } }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun parseActivityDate(dateString: String): LocalDate? {
        return try {
            // Handle both "YYYY-MM-DD" and "YYYY-MM-DD HH:MM:SS" formats
            val datePart = if (dateString.length > DATE_STRING_LENGTH) {
                dateString.substring(0, DATE_STRING_LENGTH)
            } else {
                dateString
            }
            LocalDate.parse(datePart, DateTimeFormatter.ISO_DATE)
        } catch (e: DateTimeParseException) {
            PluginLogger.Service.error("Failed to parse activity date: $dateString", e)
            null
        }
    }
}
