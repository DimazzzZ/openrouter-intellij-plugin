package org.zhavoronkov.openrouter.toolwindow.requests

import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.requests.stopWarning
import org.zhavoronkov.openrouter.requests.warning
import org.zhavoronkov.openrouter.toolwindow.chat.ReplySummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the Requests tab narrows its table to. A null [sender] or [model] means every one; the
 * sender is who sent the request - a Consumer's name, or "Chat" - and the model is the id it asked
 * for, since that is what the sender chose.
 */
data class RequestFilter(
    val sender: String? = null,
    val model: String? = null,
    val warningsOnly: Boolean = false
) {
    fun matches(record: RequestRecord): Boolean =
        (sender == null || record.sender == sender) &&
            (model == null || record.requestedModel == model) &&
            (!warningsOnly || record.warning != null)
}

/** Today's count, cost and warnings over whatever the filters leave. */
data class TodayTotals(val count: Int, val cost: Double, val warnings: Int)

/**
 * Everything the Requests tab shows, worked out from [RequestRecord]s without Swing, so the fast
 * headless test task covers it. Costs are written as the chat's footer writes them
 * ([ReplySummary.formatCost]) and warnings in the one wording [stopWarning] keeps, so a request
 * reads the same here as under the reply it produced.
 */
object RequestsView {

    const val LOGS_URL = "https://openrouter.ai/logs"

    private const val SEPARATOR = " · "
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DAY_AND_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US)
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** The senders and models present in [records], for the filter lists, in a stable order. */
    fun senders(records: List<RequestRecord>): List<String> = records.map { it.sender }.distinct().sorted()

    fun models(records: List<RequestRecord>): List<String> = records.map { it.requestedModel }.distinct().sorted()

    fun today(records: List<RequestRecord>, now: Instant, zone: ZoneId): TodayTotals {
        val today = LocalDate.ofInstant(now, zone)
        val todays = records.filter { startedAt(it, zone).toLocalDate() == today }
        return TodayTotals(
            count = todays.size,
            cost = todays.sumOf { it.reply.cost ?: 0.0 },
            warnings = todays.count { it.warning != null }
        )
    }

    /** The line above the table, e.g. "Today: 12 requests · $0.034 · 2 warnings". */
    fun todayLine(totals: TodayTotals): String = listOfNotNull(
        "Today: ${plural(totals.count, "request")}",
        ReplySummary.formatCost(totals.cost),
        totals.warnings.takeIf { it > 0 }?.let { plural(it, "warning") }
    ).joinToString(SEPARATOR)

    /**
     * The table's time: the time of day for a request made today, and the day as well for one made
     * earlier, since the log keeps many days and a bare time would make yesterday look like today.
     */
    fun time(record: RequestRecord, now: Instant, zone: ZoneId): String {
        val at = startedAt(record, zone)
        return if (at.toLocalDate() == LocalDate.ofInstant(now, zone)) TIME.format(at) else DAY_AND_TIME.format(at)
    }

    private fun startedAt(record: RequestRecord, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(record.startedAtMillis).atZone(zone)

    fun cost(record: RequestRecord): String = record.reply.cost?.let(ReplySummary::formatCost).orEmpty()

    /**
     * Every fact kept for [record], as label and value, in reading order. A fact the reply did not
     * report is left out rather than shown blank, as the chat's footer does.
     */
    fun details(record: RequestRecord, zone: ZoneId): List<Pair<String, String>> {
        val reply = record.reply
        return listOfNotNull(
            "Time" to DATE_TIME.format(startedAt(record, zone)),
            "Sent by" to sentBy(record),
            "Requested" to record.requestedModel,
            reply.answeringModel?.takeIf { it.isNotBlank() }?.let { "Answered by" to it },
            reply.provider?.let { "Provider" to it },
            tokens(record)?.let { "Tokens" to it },
            reply.cost?.let { "Cost" to ReplySummary.formatCost(it) },
            reply.finishReason?.let { "Stop reason" to it },
            reply.webSearches.takeIf { it > 0 }?.let { "Web searches" to it.toString() },
            "Duration" to duration(record.durationMillis),
            reply.generationId?.let { "Generation" to it },
            record.error?.let { "Error" to it },
            record.fixAt?.let { "Fix in" to it.path },
            stopWarning(reply.finishReason)?.let { "Warning" to it }
        )
    }

    private fun sentBy(record: RequestRecord): String = when (record.source) {
        RequestSource.CHAT -> record.sender
        RequestSource.PROXY -> "${record.sender} (through the proxy)"
    }

    private fun tokens(record: RequestRecord): String? {
        val prompt = record.reply.promptTokens
        val completion = record.reply.completionTokens
        if (prompt == null && completion == null) return null
        return "${prompt ?: "?"} in$SEPARATOR${completion ?: "?"} out"
    }

    private fun duration(millis: Long): String =
        if (millis < MILLIS_PER_SECOND) {
            "$millis ms"
        } else {
            String.format(Locale.US, "%.1f s", millis / MILLIS_PER_SECOND.toDouble())
        }

    private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

    private const val MILLIS_PER_SECOND = 1000
}
