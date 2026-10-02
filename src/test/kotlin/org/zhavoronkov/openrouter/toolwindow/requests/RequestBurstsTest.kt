package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import java.time.Instant
import java.time.ZoneOffset

@DisplayName("RequestBursts")
class RequestBurstsTest {

    private val start = Instant.parse("2026-10-01T19:29:59Z").toEpochMilli()

    private fun record(
        atMillis: Long,
        model: String = "google/gemini-2.5-flash",
        sender: String = "ktor-client",
        cost: Double? = 0.001,
        finishReason: String = "stop",
        promptTokens: Int? = 100,
        completionTokens: Int? = 10
    ) = RequestRecord(
        startedAtMillis = start + atMillis,
        durationMillis = 500,
        source = RequestSource.PROXY,
        sender = sender,
        requestedModel = model,
        reply = ReplyFacts(
            finishReason = finishReason,
            cost = cost,
            promptTokens = promptTokens,
            completionTokens = completionTokens
        )
    )

    /** The rows' kinds and requested ids, as the table would list them. */
    private fun shape(rows: List<RequestsRow>): List<String> = rows.map {
        when (it) {
            is RequestsRow.Single -> "single ${it.record.requestedModel}"
            is RequestsRow.Header -> "burst ${it.burst.requestedModel} x${it.burst.size}" +
                if (it.expanded) " open" else ""
            is RequestsRow.Member -> "  member ${it.record.requestedModel}"
        }
    }

    @Nested
    @DisplayName("grouping")
    inner class Grouping {

        @Test
        @DisplayName("requests one sender sends to one model within moments of each other fold into one burst")
        fun burst() {
            val records = listOf(record(1_500), record(1_000), record(400), record(0))

            assertEquals(listOf("burst google/gemini-2.5-flash x4"), shape(RequestBursts.rows(records, emptySet())))
        }

        @Test
        @DisplayName("fewer than three requests stay rows of their own, where they were")
        fun tooFewToFold() {
            val records = listOf(record(900), record(800, model = "meta-llama/llama-3.1-70b"), record(700))

            assertEquals(
                listOf(
                    "single google/gemini-2.5-flash",
                    "single meta-llama/llama-3.1-70b",
                    "single google/gemini-2.5-flash"
                ),
                shape(RequestBursts.rows(records, emptySet()))
            )
        }

        @Test
        @DisplayName("a request to another model in the middle neither breaks the burst nor joins it")
        fun interleaved() {
            val records = listOf(
                record(2_000, model = "meta-llama/llama-3.1-70b"),
                record(900),
                record(800, model = "openai/gpt-4o"),
                record(700),
                record(600)
            )

            assertEquals(
                listOf(
                    "single meta-llama/llama-3.1-70b",
                    "burst google/gemini-2.5-flash x3",
                    "single openai/gpt-4o"
                ),
                shape(RequestBursts.rows(records, emptySet()))
            )
        }

        @Test
        @DisplayName("another sender's requests to the same model are a burst of their own")
        fun otherSender() {
            val records = listOf(record(3), record(2, sender = "Junie"), record(1), record(0))

            assertEquals(
                listOf("burst google/gemini-2.5-flash x3", "single google/gemini-2.5-flash"),
                shape(RequestBursts.rows(records, emptySet()))
            )
        }

        @Test
        @DisplayName("a pause longer than the burst gap starts a new burst")
        fun pause() {
            // The later burst's first request starts just past the gap after the earlier one's last
            val later = 200 + RequestBursts.GAP_MILLIS + 1
            val records = listOf(later + 200, later + 100, later, 200, 100, 0).map { record(it) }

            assertEquals(
                listOf("burst google/gemini-2.5-flash x3", "burst google/gemini-2.5-flash x3"),
                shape(RequestBursts.rows(records, emptySet()))
            )
        }

        @Test
        @DisplayName("requests that finish out of the order they started in still fold together")
        fun outOfOrder() {
            val records = listOf(record(100), record(1_800), record(0), record(900))

            assertEquals(listOf("burst google/gemini-2.5-flash x4"), shape(RequestBursts.rows(records, emptySet())))
        }

        @Test
        @DisplayName("an expanded burst lists every request under it, in the table's order")
        fun expanded() {
            val records = listOf(record(300), record(200), record(100))
            val key = RequestBursts.rows(records, emptySet()).filterIsInstance<RequestsRow.Header>().single().burst.key

            val rows = RequestBursts.rows(records, setOf(key))

            assertEquals(
                listOf(
                    "burst google/gemini-2.5-flash x3 open",
                    "  member google/gemini-2.5-flash",
                    "  member google/gemini-2.5-flash",
                    "  member google/gemini-2.5-flash"
                ),
                shape(rows)
            )
            assertEquals(records, rows.filterIsInstance<RequestsRow.Member>().map { it.record })
        }

        @Test
        @DisplayName("a burst keeps its key, and so stays expanded, as newer requests join it")
        fun stableKey() {
            val older = listOf(record(200), record(100), record(0))
            val key = RequestBursts.rows(older, emptySet()).filterIsInstance<RequestsRow.Header>().single().burst.key

            val grown = RequestBursts.rows(listOf(record(400), record(300)) + older, setOf(key))

            assertEquals("burst google/gemini-2.5-flash x5 open", shape(grown).first())
        }

        @Test
        @DisplayName("with grouping off every request is a row of its own")
        fun ungrouped() {
            val records = listOf(record(2), record(1), record(0))

            assertEquals(
                List(3) { "single google/gemini-2.5-flash" },
                shape(RequestBursts.rows(records, emptySet(), grouped = false))
            )
        }
    }

    @Nested
    @DisplayName("a burst's facts")
    inner class Facts {

        private val burst = RequestBurst(
            listOf(
                record(1_200, cost = 0.002, promptTokens = 300, completionTokens = 30),
                record(600, cost = null, finishReason = "length", promptTokens = null, completionTokens = 5),
                record(0, cost = 0.001, promptTokens = 100, completionTokens = 10)
            )
        )

        @Test
        @DisplayName("its cost, tokens and warnings add up over the requests that report them")
        fun totals() {
            assertEquals(0.003, burst.cost!!, 1e-9)
            assertEquals(400, burst.promptTokens)
            assertEquals(45, burst.completionTokens)
            assertEquals(1, burst.warnings)
        }

        @Test
        @DisplayName("a burst none of whose requests reports a cost has none")
        fun noCost() {
            val unpriced = listOf(2L, 1L, 0L).map { record(it, cost = null) }

            assertNull(RequestBurst(unpriced).cost)
        }

        @Test
        @DisplayName("its row reads as the requested id with a count, the summed cost and how many went wrong")
        fun row() {
            val header = RequestsRow.Header(burst, expanded = false)
            val now = Instant.ofEpochMilli(start)

            assertEquals("▸ 19:30:00", RequestsView.text(header, RequestsColumn.TIME, now, ZoneOffset.UTC))
            assertEquals(
                "google/gemini-2.5-flash ×3",
                RequestsView.text(header, RequestsColumn.MODEL, now, ZoneOffset.UTC)
            )
            assertEquals("$0.003", RequestsView.text(header, RequestsColumn.COST, now, ZoneOffset.UTC))
            assertEquals(
                "1 of 3 requests went wrong",
                RequestsView.text(header, RequestsColumn.WARNING, now, ZoneOffset.UTC)
            )
            assertEquals(
                "▾ 19:30:00",
                RequestsView.text(header.copy(expanded = true), RequestsColumn.TIME, now, ZoneOffset.UTC)
            )
        }

        @Test
        @DisplayName("its details say how many requests went out together, when, and what they added up to")
        fun details() {
            assertEquals(
                listOf(
                    "Requests" to "3, sent together",
                    "Time" to "2026-10-01 19:29:59 – 19:30:00",
                    "Sent by" to "ktor-client (through the proxy)",
                    "Requested" to "google/gemini-2.5-flash",
                    "Tokens" to "400 in · 45 out",
                    "Cost" to "$0.003",
                    "Warnings" to "1"
                ),
                RequestsView.details(burst, ZoneOffset.UTC)
            )
        }
    }
}
