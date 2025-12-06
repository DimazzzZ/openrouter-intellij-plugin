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

    fun formatCurrency(value: Double, decimals: Int = 3): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    fun formatActivityText(requests: Long, usage: Double): String {
        val usageStr = formatCurrency(usage, decimals = 4)
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
        val totalRequests = activities.sumOf { it.requests.toLong() }
        val totalUsage = activities.sumOf { it.usage }
        return Pair(totalRequests, totalUsage)
    }

    fun filterActivitiesByTime(
        activities: List<ActivityData>,
        hoursAgo: Int
    ): List<ActivityData> {
        val cutoffDate = LocalDate.now().minusDays((hoursAgo / HOURS_IN_DAY).toLong())
        return activities.filter { activity ->
            val activityDate = parseActivityDate(activity.date)
            activityDate != null && !activityDate.isBefore(cutoffDate)
        }
    }

    fun extractRecentModelNames(activities: List<ActivityData>): List<String> {
        return activities
            .groupBy { it.model }
            .mapValues { (_, activityList) -> activityList.maxOf { it.date } }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun parseActivityDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE)
        } catch (e: DateTimeParseException) {
            PluginLogger.Service.error("Failed to parse activity date: $dateString", e)
            null
        }
    }
}
