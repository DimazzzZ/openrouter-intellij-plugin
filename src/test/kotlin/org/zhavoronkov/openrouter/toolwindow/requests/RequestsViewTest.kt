package org.zhavoronkov.openrouter.toolwindow.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.requests.warning
import java.time.Instant
import java.time.ZoneOffset

class RequestsViewTest {

    private val zone = ZoneOffset.UTC
    private val noon = Instant.parse("2026-09-29T12:00:00Z")

    private fun record(
        at: Instant = noon,
        sender: String = "Junie",
        model: String = "openai/gpt-4o",
        reply: ReplyFacts = ReplyFacts(finishReason = "stop", cost = 0.01),
        error: String? = null,
        source: RequestSource = RequestSource.PROXY
    ) = RequestRecord(at.toEpochMilli(), 1_500, source, sender, model, reply, error)

    @Nested
    @DisplayName("Warnings")
    inner class Warnings {

        @Test
        @DisplayName("a normal stop is no warning")
        fun normalStop() {
            assertNull(record().warning)
            assertNull(record(reply = ReplyFacts(finishReason = "tool_calls")).warning)
        }

        @Test
        @DisplayName("a reply cut off reads as the chat footer says it")
        fun cutOff() {
            assertEquals(
                "Cut off at the token limit",
                record(reply = ReplyFacts(finishReason = "length")).warning
            )
        }

        @Test
        @DisplayName("an error is the warning")
        fun error() {
            assertEquals("Insufficient credits", record(error = "Insufficient credits").warning)
        }
    }

    @Nested
    @DisplayName("Filters")
    inner class Filters {

        private val records = listOf(
            record(sender = "Junie", model = "a/x"),
            record(sender = "Chat", model = "a/y", source = RequestSource.CHAT),
            record(sender = "Junie", model = "a/y", reply = ReplyFacts(finishReason = "length"))
        )

        @Test
        @DisplayName("no filter keeps everything")
        fun none() {
            assertEquals(3, records.count(RequestFilter()::matches))
        }

        @Test
        @DisplayName("sender, model and warnings-only each narrow, and combine")
        fun narrow() {
            assertEquals(2, records.count(RequestFilter(sender = "Junie")::matches))
            assertEquals(2, records.count(RequestFilter(model = "a/y")::matches))
            assertEquals(1, records.count(RequestFilter(warningsOnly = true)::matches))
            assertEquals(1, records.count(RequestFilter(sender = "Junie", model = "a/y")::matches))
        }

        @Test
        @DisplayName("the filter lists offer each sender and model once, sorted")
        fun choices() {
            assertEquals(listOf("Chat", "Junie"), RequestsView.senders(records))
            assertEquals(listOf("a/x", "a/y"), RequestsView.models(records))
        }
    }

    @Nested
    @DisplayName("Today")
    inner class Today {

        @Test
        @DisplayName("only today's requests are counted, in the user's time zone")
        fun onlyToday() {
            val records = listOf(
                record(reply = ReplyFacts(cost = 0.02, finishReason = "stop")),
                record(error = "boom", reply = ReplyFacts()),
                record(at = noon.minusSeconds(86_400))
            )

            assertEquals(TodayTotals(2, 0.02, 1), RequestsView.today(records, noon, zone))
        }

        @Test
        @DisplayName("the line names count, cost and warnings, leaving out no warnings")
        fun line() {
            assertEquals("Today: 1 request · $0.02", RequestsView.todayLine(TodayTotals(1, 0.02, 0)))
            assertEquals("Today: 3 requests · $0 · 2 warnings", RequestsView.todayLine(TodayTotals(3, 0.0, 2)))
        }
    }

    @Nested
    @DisplayName("Details")
    inner class Details {

        @Test
        @DisplayName("every recorded fact is listed, in reading order")
        fun everyFact() {
            val record = record(
                reply = ReplyFacts(
                    generationId = "gen-1",
                    answeringModel = "openai/gpt-4o-2024",
                    provider = "OpenAI",
                    promptTokens = 10,
                    completionTokens = 5,
                    cost = 0.003,
                    finishReason = "length",
                    webSearches = 2
                )
            )

            assertEquals(
                listOf(
                    "Time" to "2026-09-29 12:00:00",
                    "Sent by" to "Junie (through the proxy)",
                    "Requested" to "openai/gpt-4o",
                    "Answered by" to "openai/gpt-4o-2024",
                    "Provider" to "OpenAI",
                    "Tokens" to "10 in · 5 out",
                    "Cost" to "$0.003",
                    "Stop reason" to "length",
                    "Web searches" to "2",
                    "Duration" to "1.5 s",
                    "Generation" to "gen-1",
                    "Warning" to "Cut off at the token limit"
                ),
                RequestsView.details(record, zone)
            )
        }

        @Test
        @DisplayName("a request the plugin refused names the page that fixes it")
        fun fixIn() {
            val refused = record(reply = ReplyFacts(), error = "OpenRouter plugin: ...").copy(fixAt = FixPage.FAVORITE_MODELS)

            val fixIn = RequestsView.details(refused, zone).toMap()["Fix in"]
            assertEquals("Settings → Tools → OpenRouter → Favorite Models", fixIn)
        }

        @Test
        @DisplayName("a fact the reply did not report is left out, and an error is named as one")
        fun failed() {
            val labels = RequestsView.details(record(reply = ReplyFacts(), error = "boom"), zone).toMap()

            assertFalse("Provider" in labels)
            assertFalse("Generation" in labels)
            assertEquals("boom", labels["Error"])
            assertTrue("Duration" in labels)
        }

        @Test
        @DisplayName("an error and a stop that was not normal are both shown")
        fun errorAndStop() {
            val labels = RequestsView.details(record(reply = ReplyFacts(finishReason = "length"), error = "boom"), zone)
                .toMap()

            assertEquals("boom", labels["Error"])
            assertEquals("Cut off at the token limit", labels["Warning"])
        }

        /** The chat footer says "Routed to X" for a Router; here the label already says it answered. */
        @Test
        @DisplayName("the answering model is its plain slug, even for a Router")
        fun answeringModelForARouter() {
            val routed = record(model = "openrouter/auto", reply = ReplyFacts(answeringModel = "openai/gpt-4o"))

            assertEquals("openai/gpt-4o", RequestsView.details(routed, zone).toMap()["Answered by"])
        }
    }

    @Nested
    @DisplayName("Time")
    inner class Time {

        @Test
        @DisplayName("a request from today shows its time, an earlier one its day as well")
        fun dayWhenNotToday() {
            assertEquals("12:00:00", RequestsView.time(record(), noon, zone))
            assertEquals("Sep 28 12:00", RequestsView.time(record(at = noon.minusSeconds(86_400)), noon, zone))
        }
    }
}
