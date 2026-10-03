package org.zhavoronkov.openrouter.toolwindow.status

import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Totals raw activity rows per model over a STATED period.
 *
 * This is the degraded-mode path, used when no provisioning key makes the
 * analytics API unreachable. The code it replaces summed every row the endpoint
 * happened to return and labelled the result "Recent Activity" with no period at
 * all - a number whose meaning nobody could state, which is half of why the tab
 * looked wrong.
 *
 * Pure apart from `java.time` and the plugin's own model type, both plain JVM
 * classes, so this runs in the fast headless task.
 */
object ActivityAggregator {

    /** @param days how many days back from `today`, inclusive of both ends. */
    enum class Period(val days: Int) {
        DAY(1),
        WEEK(7),
        MONTH(30)
    }

    /**
     * One row of [BreakdownBlock]'s list: a name, its spend and its request count over the
     * block's own stated period.
     *
     * [model] is [byModel]'s own grouping key, and stays that name here (G6, the key-spend
     * breakdown) even though [AnalyticsBreakdown.toModelSpend] now also produces this same shape
     * for the `api_key_id` dimension, where [model] actually holds a key's own name (e.g. `n8n`),
     * never a model id. Renaming the type/field to something dimension-neutral was considered and
     * rejected: [BreakdownBlock] renders exactly one list either way and never needs to tell the
     * two apart at the field level, so the only real payoff would be the name itself - not worth
     * the ripple through [byModel], [BreakdownBlock], [BreakdownRowTooltip], [StatusTabPanel] and
     * every existing test that already reads `.model`, this late on this branch, for a purely
     * cosmetic gain. This KDoc is the trade instead.
     */
    data class ModelSpend(val model: String, val usage: Double, val requests: Long)

    /**
     * @param today injected rather than read from the clock, so the boundary
     *   cases are testable without freezing time
     */
    fun byModel(rows: List<ActivityData>, period: Period, today: LocalDate): List<ModelSpend> {
        val earliest = today.minusDays(period.days.toLong() - 1)

        return rows
            .filter { it.model != null && withinPeriod(it.date, earliest, today) }
            // Unreachable branch: the filter above dropped every row whose model is null, so orEmpty never falls back
            .groupBy { it.model.orEmpty() }
            .map { (model, group) ->
                ModelSpend(
                    model = model,
                    usage = group.sumOf { it.usage ?: 0.0 },
                    requests = group.sumOf { (it.requests ?: 0).toLong() }
                )
            }
            .sortedByDescending { it.usage }
    }

    /**
     * A row whose date will not parse is dropped rather than thrown on: the
     * endpoint is outside our control, and one malformed row must not empty the
     * whole breakdown.
     */
    private fun withinPeriod(date: String?, earliest: LocalDate, today: LocalDate): Boolean {
        val parsed = parseDate(date) ?: return false
        return !parsed.isBefore(earliest) && !parsed.isAfter(today)
    }

    private fun parseDate(date: String?): LocalDate? {
        if (date.isNullOrBlank()) return null
        return try {
            LocalDate.parse(date.take(DATE_LENGTH))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Length of an ISO local date, so a full timestamp truncates to its date part. */
    private const val DATE_LENGTH = 10
}
