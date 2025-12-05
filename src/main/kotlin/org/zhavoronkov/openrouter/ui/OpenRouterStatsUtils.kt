package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.util.Locale

/**
 * Utility class containing static methods used by OpenRouterStatsPopup
 * Extracted for better testability
 */
object OpenRouterStatsUtils {

    private const val NO_RECENT_MODELS_HTML = "<html>Recent Models:<br/>• None</html>"

    /**
     * Formats a currency value as a string with dollar sign and appropriate precision
     */
    fun formatCurrency(value: Double, decimals: Int = 3): String {
        return String.format(Locale.US, "%." + decimals + "f", value)
    }

    /**
     * Formats a large integer value with comma separators
     */
    fun formatLargeNumber(value: Long): String {
        return String.format(Locale.US, "%,d", value)
    }

    /**
     * Builds HTML-formatted recent models list
     */
    fun buildModelsHtmlList(models: List<String>): String {
        return when {
            models.isEmpty() -> NO_RECENT_MODELS_HTML
            else -> {
                val displayModels = models.take(5) // Show up to 5 models
                val bullets = displayModels.joinToString("<br/>") { "• $it" }
                val moreText = if (models.size > 5) "<br/>• +${models.size - 5} more" else ""
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
    }

    /**
     * Creates formatted activity text for requests/usage
     */
    fun formatActivityText(requests: Long, usage: Double): String {
        return "$requests requests, $${formatCurrency(usage, 4)} spent"
    }

    /**
     * Calculates total requests and usage from activity list
     */
    fun calculateActivityStats(activities: List<ActivityData>): Pair<Long, Double> {
        val requests = activities.sumOf { it.requests.toLong() }
        val usage = activities.sumOf { it.usage }
        return Pair(requests, usage)
    }

    /**
     * Filters activities by time period
     */
    fun filterActivitiesByTime(activities: List<ActivityData>,
                               today: LocalDate, yesterday: LocalDate, weekAgo: LocalDate,
                               isLast24h: Boolean): List<ActivityData> {
        return if (isLast24h) {
            activities.filter { activity ->
                val activityDate = parseActivityDate(activity.date)
                activityDate?.let { date ->
                    date.isEqual(today) || date.isEqual(yesterday)
                } ?: false
            }
        } else {
            activities.filter { activity ->
                val activityDate = parseActivityDate(activity.date)
                activityDate?.let { date ->
                    date.isAfter(weekAgo) || date.isEqual(weekAgo)
                } ?: false
            }
        }
    }

    /**
     * Extracts model names sorted by most recent usage
     */
    fun extractRecentModelNames(activities: List<ActivityData>): List<String> {
        return activities
            .groupBy { it.model }
            .mapValues { (_, activities) -> activities.maxOf { it.date } }
            .toList()
            .sortedByDescending { it.second } // Sort by date descending (latest first)
            .map { it.first } // Extract just the model names
    }

    /**
     * Parse activity date which can be in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM:SS"
     */
    fun parseActivityDate(dateString: String): LocalDate? {
        return try {
            // Try parsing as date only first
            if (dateString.length == 10) {
                LocalDate.parse(dateString)
            } else {
                // Extract just the date part from datetime string
                val datePart = dateString.substring(0, 10)
                LocalDate.parse(datePart)
            }
        } catch (e: Exception) {
            // Log the problematic date format for debugging
            println("Failed to parse activity date: '$dateString' - ${e.message}")
            null
        }
    }
}
