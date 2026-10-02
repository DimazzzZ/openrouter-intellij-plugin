package org.zhavoronkov.openrouter.toolwindow.requests

import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.warning

/**
 * Requests one sender sent to one model within moments of each other - AI Assistant's context
 * checks, which go out in parallel by the dozen for one chat message, say - that the Requests table
 * folds into one row. [records] are in the table's order, newest first.
 */
data class RequestBurst(val records: List<RequestRecord>) {

    init {
        require(records.isNotEmpty()) { "A burst has at least one request" }
    }

    val size: Int get() = records.size
    val sender: String get() = records.first().sender
    val requestedModel: String get() = records.first().requestedModel

    /** The request that started first. */
    val earliest: RequestRecord get() = records.minBy { it.startedAtMillis }

    /** The request that started last. */
    val latest: RequestRecord get() = records.maxBy { it.startedAtMillis }

    /**
     * What the burst is known by while the table is open, so it stays expanded as newer requests
     * join it: its oldest request in the table's order, which a newer one cannot displace.
     */
    val key: RequestRecord get() = records.last()

    /** The summed cost of the requests that report one, or null when none does. */
    val cost: Double? get() = records.mapNotNull { it.reply.cost }.takeIf { it.isNotEmpty() }?.sum()

    val promptTokens: Int? get() = records.mapNotNull { it.reply.promptTokens }.takeIf { it.isNotEmpty() }?.sum()

    val completionTokens: Int? get() =
        records.mapNotNull { it.reply.completionTokens }.takeIf { it.isNotEmpty() }?.sum()

    /** How many of its requests went wrong. */
    val warnings: Int get() = records.count { it.warning != null }
}

/** One row of the Requests table. */
sealed interface RequestsRow {

    /** A request not sent in a burst. */
    data class Single(val record: RequestRecord) : RequestsRow

    /** A burst, folded into one row, with its requests listed under it when [expanded]. */
    data class Header(val burst: RequestBurst, val expanded: Boolean) : RequestsRow

    /** A request of an expanded [burst], listed under its header. */
    data class Member(val record: RequestRecord, val burst: RequestBurst) : RequestsRow
}

/**
 * Folds the Requests table's records into bursts. Free of Swing, so the fast headless test task
 * covers it.
 *
 * A burst is at least [MIN_SIZE] requests from one sender to one requested id, each started within
 * [GAP_MILLIS] of another in it. Requests to other models in between neither break it nor join it,
 * and the log lists requests as they finish, not as they started, so membership goes by start
 * time, not by position. Fewer requests than that stay rows of their own, where they were.
 */
object RequestBursts {

    /** How far apart two requests of one burst may start. */
    const val GAP_MILLIS = 2_000L

    /** The fewest requests folded into a burst: two rows read as easily as one. */
    const val MIN_SIZE = 3

    /**
     * The table's rows for [records], newest first: each burst as a header at its newest request's
     * place, with its requests under it when its key is in [expanded]; or, when not [grouped], every
     * request as a row of its own.
     */
    fun rows(records: List<RequestRecord>, expanded: Set<RequestRecord>, grouped: Boolean = true): List<RequestsRow> {
        if (!grouped) return records.map(RequestsRow::Single)
        val burstOf = bursts(records)
        val listed = mutableSetOf<RequestBurst>()
        return records.flatMap { record ->
            val burst = burstOf[record]
            when {
                burst == null -> listOf(RequestsRow.Single(record))
                !listed.add(burst) -> emptyList()
                burst.key in expanded ->
                    listOf(RequestsRow.Header(burst, expanded = true)) +
                        burst.records.map { RequestsRow.Member(it, burst) }
                else -> listOf(RequestsRow.Header(burst, expanded = false))
            }
        }
    }

    /** The burst each folded record belongs to; a record in no burst is absent. */
    private fun bursts(records: List<RequestRecord>): Map<RequestRecord, RequestBurst> {
        val open = mutableMapOf<Pair<String, String>, Group>()
        val groups = mutableListOf<Group>()
        records.forEach { record ->
            val key = record.sender to record.requestedModel
            val group = open[key]?.takeIf { it.reaches(record.startedAtMillis) }
                ?: Group().also {
                    open[key] = it
                    groups += it
                }
            group.add(record)
        }
        return groups.filter { it.records.size >= MIN_SIZE }
            .flatMap { group -> RequestBurst(group.records).let { burst -> group.records.map { it to burst } } }
            .toMap()
    }

    private class Group {
        val records = mutableListOf<RequestRecord>()
        private var earliest = Long.MAX_VALUE
        private var latest = Long.MIN_VALUE

        fun reaches(startedAt: Long) = startedAt in (earliest - GAP_MILLIS)..(latest + GAP_MILLIS)

        fun add(record: RequestRecord) {
            records += record
            earliest = minOf(earliest, record.startedAtMillis)
            latest = maxOf(latest, record.startedAtMillis)
        }
    }
}
