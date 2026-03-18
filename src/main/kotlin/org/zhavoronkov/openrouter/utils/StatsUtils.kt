package org.zhavoronkov.openrouter.utils

import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Clean, well-organized utility classes for statistics-related operations.
 *
 * This replaces the previous monolithic StatsUtils with focused, single-purpose classes:
 * - CurrencyUtils: Pure currency/percentage formatting
 * - ActivityUtils: Activity data processing and filtering
 * - DateUtils: Date parsing and manipulation
 * - ModelUtils: Model-related operations
 */

// ===== CURRENCY & PERCENTAGE FORMATTING =====

/**
 * Pure currency and percentage formatting utilities.
 * Stateless, thread-safe, focused on formatting only.
 */
object CurrencyUtils {

    /**
     * Format currency with consistent decimal precision
     */
    fun formatCurrency(value: Double, decimals: Int = 4): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    /**
     * Format percentage with consistent decimal precision
     */
    fun formatPercentage(value: Double, decimals: Int = 4): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    /**
     * Format large numbers with comma separators
     */
    fun formatNumber(value: Long): String {
        return String.format(Locale.US, "%,d", value)
    }

    /**
     * Format currency range for display
     */
    fun formatCurrencyRange(min: Double, max: Double, decimals: Int = 4): String {
        val minFormatted = formatCurrency(min, decimals)
        val maxFormatted = formatCurrency(max, decimals)
        return "$minFormatted - $maxFormatted"
    }

    /**
     * Format currency difference with sign
     */
    fun formatCurrencyDifference(difference: Double, decimals: Int = 4): String {
        val formatted = formatCurrency(kotlin.math.abs(difference), decimals)
        return if (difference >= 0) "+$$formatted" else "-$$formatted"
    }
}

// ===== DATE PARSING & MANIPULATION =====

/**
 * Date parsing and manipulation utilities for activity data.
 */
object DateUtils {

    private const val DATE_ONLY_LENGTH = 10
    private const val ISO_DATE_FORMAT = "\\d{4}-\\d{2}-\\d{2}"
    private const val WEEK_DAYS = 7L

    /**
     * Parse activity date string to LocalDate.
     * Supports formats: "YYYY-MM-DD", "YYYY-MM-DD HH:MM:SS", "YYYY-MM-DDTHH:MM:SS..."
     */
    fun parseActivityDate(dateString: String): LocalDate? {
        if (dateString.isBlank()) return null

        return try {
            when {
                // Date only: "YYYY-MM-DD"
                dateString.matches(Regex("^$ISO_DATE_FORMAT$")) -> {
                    LocalDate.parse(dateString)
                }
                // Date with time: "YYYY-MM-DD HH:MM:SS"
                dateString.matches(Regex("^$ISO_DATE_FORMAT \\d{2}:\\d{2}:\\d{2}$")) -> {
                    val datePart = dateString.substring(0, DATE_ONLY_LENGTH)
                    LocalDate.parse(datePart, DateTimeFormatter.ISO_DATE)
                }
                // ISO format: "YYYY-MM-DDTHH:MM:SS..."
                dateString.length > DATE_ONLY_LENGTH &&
                    dateString[DATE_ONLY_LENGTH] == 'T' -> {
                    val datePart = dateString.substring(0, DATE_ONLY_LENGTH)
                    LocalDate.parse(datePart, DateTimeFormatter.ISO_DATE)
                }
                else -> null
            }
        } catch (@Suppress("SwallowedException") e: DateTimeParseException) {
            // Invalid date format - return null silently as this is expected for some formats
            null
        }
    }

    /**
     * Get date ranges for filtering activities
     */
    fun getDateRanges(): DateRanges {
        val today = LocalDate.now()
        return DateRanges(
            today = today,
            yesterday = today.minusDays(1),
            weekAgo = today.minusDays(WEEK_DAYS)
        )
    }

    /**
     * Data class for date ranges used in filtering
     */
    data class DateRanges(
        val today: LocalDate,
        val yesterday: LocalDate,
        val weekAgo: LocalDate
    )
}

// ===== ACTIVITY DATA PROCESSING =====

/**
 * Activity data processing and statistics calculation utilities.
 */
@Suppress("MagicNumber")
object ActivityUtils {

    private const val HOURS_PER_DAY = 24

    /**
     * Calculate total requests and usage from activity list
     */
    fun calculateStats(activities: List<ActivityData>): ActivityStats {
        if (activities.isEmpty()) {
            return ActivityStats(0L, 0.0)
        }

        val totalRequests = activities.sumOf { it.requests?.toLong() ?: 0L }
        val totalUsage = activities.sumOf { it.usage ?: 0.0 }

        return ActivityStats(totalRequests, totalUsage)
    }

    /**
     * Filter activities for specific time periods
     */
    fun filterByTime(activities: List<ActivityData>, isLast24h: Boolean): List<ActivityData> {
        if (activities.isEmpty()) return emptyList()

        val ranges = DateUtils.getDateRanges()

        return activities.filter { activity ->
            val activityDate = activity.date?.let { DateUtils.parseActivityDate(it) }
                ?: return@filter false

            when {
                isLast24h -> {
                    activityDate == ranges.today || activityDate == ranges.yesterday
                }
                else -> {
                    !activityDate.isBefore(ranges.weekAgo) && !activityDate.isAfter(ranges.today)
                }
            }
        }
    }

    /**
     * Filter activities by hours ago
     */
    fun filterByHours(activities: List<ActivityData>, hoursAgo: Int): List<ActivityData> {
        if (activities.isEmpty()) return emptyList()

        val cutoffDate = LocalDate.now().minusDays((hoursAgo / HOURS_PER_DAY).toLong())

        return activities.filter { activity ->
            val activityDate = activity.date?.let { DateUtils.parseActivityDate(it) }
            activityDate != null && !activityDate.isBefore(cutoffDate)
        }
    }

    /**
     * Extract and sort model names by most recent usage
     */
    fun extractRecentModels(activities: List<ActivityData>, maxModels: Int = 5): List<String> {
        if (activities.isEmpty()) return emptyList()

        return activities
            .filter { it.model != null && it.date != null }
            .groupBy { it.model!! }
            .mapValues { (_, activities) -> activities.maxOf { it.date!! } }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .take(maxModels)
    }

    /**
     * Data class for activity statistics
     */
    data class ActivityStats(
        val totalRequests: Long,
        val totalUsage: Double
    )
}

// ===== TEXT FORMATTING =====

/**
 * Text formatting utilities for display purposes.
 */
@Suppress("MagicNumber")
object TextUtils {

    private const val MAX_MODELS_DISPLAY = 5

    /**
     * Format activity summary text
     */
    fun formatActivityText(requests: Long, usage: Double): String {
        val usageFormatted = CurrencyUtils.formatCurrency(usage)
        return "$requests requests, $$usageFormatted spent"
    }

    /**
     * Build HTML list of model names for display
     */
    fun buildModelsHtmlList(models: List<String>): String {
        return when {
            models.isEmpty() -> "<html>Recent Models:<br/>• None</html>"
            else -> {
                val displayModels = models.take(MAX_MODELS_DISPLAY)
                val bullets = displayModels.joinToString("<br/>") { "• $it" }
                val moreText = if (models.size > MAX_MODELS_DISPLAY) {
                    "<br/>• +${models.size - MAX_MODELS_DISPLAY} more"
                } else {
                    ""
                }
                "<html>Recent Models:<br/>$bullets$moreText</html>"
            }
        }
    }

    /**
     * Build simple HTML list for model display
     */
    fun buildSimpleModelsHtml(models: List<String>): String {
        return if (models.isEmpty()) {
            "No models used"
        } else {
            val modelsList = models.joinToString("") { model ->
                "<li style='margin: 2px 0;'>$model</li>"
            }
            "<html><ul style='margin: 0; padding-left: 20px;'>$modelsList</ul></html>"
        }
    }
}

// ===== MAIN UNIFIED ACCESS (OPTIONAL) =====

/**
 * Main entry point for unified access to all stats utilities.
 * Provides convenience methods that delegate to the specialized utilities above.
 */
@Suppress("TooManyFunctions")
object StatsUtils {

    // Currency formatting
    fun formatCurrency(value: Double, decimals: Int = 4) = CurrencyUtils.formatCurrency(value, decimals)
    fun formatPercentage(value: Double, decimals: Int = 4) = CurrencyUtils.formatPercentage(value, decimals)
    fun formatLargeNumber(value: Long) = CurrencyUtils.formatNumber(value)
    fun formatCurrencyRange(min: Double, max: Double, decimals: Int = 4) = CurrencyUtils.formatCurrencyRange(
        min,
        max,
        decimals
    )
    fun formatCurrencyDifference(difference: Double, decimals: Int = 4) = CurrencyUtils.formatCurrencyDifference(
        difference,
        decimals
    )

    // Activity processing
    fun formatActivityText(requests: Long, usage: Double) = TextUtils.formatActivityText(requests, usage)
    fun buildModelsHtmlList(models: List<String>) = TextUtils.buildModelsHtmlList(models)
    fun buildSimpleModelsHtmlList(models: List<String>) = TextUtils.buildSimpleModelsHtml(models)
    fun calculateActivityStats(activities: List<ActivityData>) = ActivityUtils.calculateStats(activities)
    fun filterActivitiesByTime(activities: List<ActivityData>, isLast24h: Boolean) = ActivityUtils.filterByTime(
        activities,
        isLast24h
    )
    fun filterActivitiesByHours(activities: List<ActivityData>, hoursAgo: Int) = ActivityUtils.filterByHours(
        activities,
        hoursAgo
    )
    fun extractRecentModelNames(activities: List<ActivityData>) = ActivityUtils.extractRecentModels(activities)

    // Date processing
    fun parseActivityDate(dateString: String) = DateUtils.parseActivityDate(dateString)
}
