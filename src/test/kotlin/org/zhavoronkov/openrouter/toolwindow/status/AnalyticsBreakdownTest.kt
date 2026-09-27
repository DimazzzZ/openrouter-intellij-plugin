package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import java.time.LocalDate
import java.time.OffsetDateTime

@DisplayName("AnalyticsBreakdown")
class AnalyticsBreakdownTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun activityRow(date: String, model: String) = ActivityData(
        date = date,
        model = model,
        modelPermaslug = null,
        endpointId = null,
        providerName = null,
        usage = 1.0,
        byokUsageInference = null,
        requests = 1,
        promptTokens = null,
        completionTokens = null,
        reasoningTokens = null
    )

    // --- requestFor: the one invariant that matters most ------------------------------------

    @Test
    @DisplayName(
        "requestFor's time_range agrees with byModel's own inclusive-both-ends window, " +
            "for every period"
    )
    fun `requestFor agrees with byModel's window`() {
        ActivityAggregator.Period.values().forEach { period ->
            val earliestIncluded = today.minusDays(period.days.toLong() - 1)
            val dayBeforeEarliest = earliestIncluded.minusDays(1)

            // Read byModel's actual boundary off its real filtering behaviour, rather than
            // re-deriving it by hand - if a later edit changes byModel's own arithmetic, this
            // test (not just AnalyticsBreakdownTest's assumptions) moves with it.
            val boundaryResult = ActivityAggregator.byModel(
                listOf(
                    activityRow(earliestIncluded.toString(), "in"),
                    activityRow(dayBeforeEarliest.toString(), "out")
                ),
                period,
                today
            )
            assertEquals(
                listOf("in"),
                boundaryResult.map { it.model },
                "sanity check on byModel itself for $period - if this fails, the test's own " +
                    "premise about byModel's boundary is wrong"
            )

            val request = AnalyticsBreakdown.requestFor(period, today)
            val start = OffsetDateTime.parse(request.timeRange.start).toLocalDate()
            // The end bound is EXCLUSIVE (a half-open range), so the last calendar day it
            // actually covers is one day before it.
            val lastCoveredDay = OffsetDateTime.parse(request.timeRange.end).toLocalDate().minusDays(1)

            assertEquals(
                earliestIncluded,
                start,
                "for $period, requestFor's start ($start) must be the same earliest day " +
                    "byModel includes ($earliestIncluded) - a mismatch here means a provisioning-key " +
                    "user and a degraded-mode user asking for the same period see different windows"
            )
            assertEquals(
                today,
                lastCoveredDay,
                "for $period, requestFor's end must cover through today ($today), " +
                    "found it covering only through $lastCoveredDay"
            )
        }
    }

    @Test
    @DisplayName("a 24-hour period (DAY) covers only today, start and end on the same calendar day")
    fun `DAY period covers only today`() {
        val request = AnalyticsBreakdown.requestFor(ActivityAggregator.Period.DAY, today)

        val start = OffsetDateTime.parse(request.timeRange.start).toLocalDate()
        val lastCoveredDay = OffsetDateTime.parse(request.timeRange.end).toLocalDate().minusDays(1)

        assertEquals(today, start)
        assertEquals(today, lastCoveredDay)
    }

    @Test
    @DisplayName(
        "the request omits granularity for every period, since the fixture shows the server " +
            "buckets on granularity ALONE, independent of dimensions"
    )
    fun `the request omits granularity for every period`() {
        ActivityAggregator.Period.values().forEach { period ->
            assertEquals(
                null,
                AnalyticsBreakdown.requestFor(period, today).granularity,
                "for $period, a non-null granularity risks the server returning one row per " +
                    "model PER TIME BUCKET instead of one row per model for the whole window, " +
                    "which toModelSpend would then have to (and does) re-aggregate - but the " +
                    "request itself must not ask for that shape in the first place"
            )
        }
    }

    @Test
    @DisplayName("the request groups by model and orders by usage descending, matching byModel's own order")
    fun `the request groups by model and orders by usage descending`() {
        val request = AnalyticsBreakdown.requestFor(ActivityAggregator.Period.WEEK, today)

        assertEquals(listOf("model"), request.dimensions)
        assertEquals(listOf("total_usage", "request_count"), request.metrics)
        assertEquals("total_usage", request.orderBy?.field)
        assertEquals("desc", request.orderBy?.direction)
    }

    // --- Dimension (G6): requestFor/toModelSpend thread a dimension rather than acquiring a -----
    // --- second, parallel query-building path ---------------------------------------------------

    @Test
    @DisplayName("requestFor defaults to the model dimension when none is passed, unchanged from before G6")
    fun `requestFor defaults to the model dimension`() {
        val request = AnalyticsBreakdown.requestFor(ActivityAggregator.Period.WEEK, today)

        assertEquals(listOf("model"), request.dimensions)
    }

    @Test
    @DisplayName("requestFor asks for api_key_id, not model, when the KEY dimension is selected")
    fun `requestFor asks for api_key_id for the KEY dimension`() {
        val request = AnalyticsBreakdown.requestFor(
            ActivityAggregator.Period.WEEK,
            today,
            AnalyticsBreakdown.Dimension.KEY
        )

        assertEquals(listOf("api_key_id"), request.dimensions)
    }

    @Test
    @DisplayName(
        "requestFor's window is identical for both dimensions, for every period - the model and " +
            "key breakdowns must never silently cover different windows for the same period, the " +
            "same rule requestFor already keeps against byModel"
    )
    fun `requestFor's window agrees across dimensions`() {
        ActivityAggregator.Period.values().forEach { period ->
            val modelRequest = AnalyticsBreakdown.requestFor(period, today, AnalyticsBreakdown.Dimension.MODEL)
            val keyRequest = AnalyticsBreakdown.requestFor(period, today, AnalyticsBreakdown.Dimension.KEY)

            assertEquals(
                modelRequest.timeRange,
                keyRequest.timeRange,
                "for $period, the MODEL and KEY dimension queries must share the exact same " +
                    "time_range - found ${modelRequest.timeRange} vs ${keyRequest.timeRange}"
            )
        }
    }

    @Test
    @DisplayName(
        "toModelSpend reads api_key_id (already human-readable) for the KEY dimension, with a quoted request_count"
    )
    fun `toModelSpend reads api_key_id for the KEY dimension`() {
        // The live shape confirmed against a real query with dimensions: ["api_key_id"]: a plain
        // display name (not a hash/UUID), and request_count quoted exactly like the model query.
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("api_key_id" to "n8n", "total_usage" to 0.42, "request_count" to "5")),
            AnalyticsBreakdown.Dimension.KEY
        )

        assertEquals(1, result.size)
        assertEquals("n8n", result[0].model)
        assertEquals(0.42, result[0].usage, 1e-9)
        assertEquals(5L, result[0].requests)
    }

    @Test
    @DisplayName(
        "a row missing api_key_id is dropped for the KEY dimension, even if it carries an unrelated model field"
    )
    fun `a row missing api_key_id is dropped for the KEY dimension`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("model" to "anthropic/claude-sonnet-4.5", "total_usage" to 1.0, "request_count" to 1.0)),
            AnalyticsBreakdown.Dimension.KEY
        )

        assertTrue(
            result.isEmpty(),
            "a row with no api_key_id field must be dropped for the KEY dimension, not read " +
                "under its unrelated model field"
        )
    }

    @Test
    @DisplayName("KEY-dimension rows are sorted descending by spend, same as the model dimension")
    fun `KEY dimension rows sort descending by spend`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("api_key_id" to "cheap-key", "total_usage" to 0.01, "request_count" to "1"),
                mapOf("api_key_id" to "expensive-key", "total_usage" to 5.0, "request_count" to "1")
            ),
            AnalyticsBreakdown.Dimension.KEY
        )

        assertEquals(listOf("expensive-key", "cheap-key"), result.map { it.model })
    }

    @Test
    @DisplayName("a non-numeric total_usage drops the row for the KEY dimension too, rather than counting it as zero")
    fun `a non-numeric total_usage drops the row for the KEY dimension`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("api_key_id" to "corrupt", "total_usage" to "not-a-number", "request_count" to "1"),
                mapOf("api_key_id" to "good", "total_usage" to 1.0, "request_count" to "1")
            ),
            AnalyticsBreakdown.Dimension.KEY
        )

        assertEquals(listOf("good"), result.map { it.model })
    }

    @Test
    @DisplayName("an empty row list yields an empty result for the KEY dimension too")
    fun `an empty row list yields an empty result for the KEY dimension`() {
        assertTrue(AnalyticsBreakdown.toModelSpend(emptyList(), AnalyticsBreakdown.Dimension.KEY).isEmpty())
    }

    // --- toModelSpend -------------------------------------------------------------------------

    @Test
    @DisplayName("rows map to ModelSpend, converting Double-typed request_count to Long")
    fun `rows map to ModelSpend`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "anthropic/claude-sonnet-4.5", "total_usage" to 0.5123, "request_count" to 31.0)
            )
        )

        assertEquals(1, result.size)
        assertEquals("anthropic/claude-sonnet-4.5", result[0].model)
        assertEquals(0.5123, result[0].usage, 1e-9)
        assertEquals(31L, result[0].requests)
    }

    @Test
    @DisplayName("results are ordered by usage descending")
    fun `results are ordered by usage descending`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "cheap", "total_usage" to 0.01, "request_count" to 1.0),
                mapOf("model" to "expensive", "total_usage" to 5.0, "request_count" to 1.0),
                mapOf("model" to "middling", "total_usage" to 0.50, "request_count" to 1.0)
            )
        )

        assertEquals(listOf("expensive", "middling", "cheap"), result.map { it.model })
    }

    @Test
    @DisplayName("a row missing the model dimension is dropped, not counted under an empty name")
    fun `a row missing the model dimension is dropped`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("total_usage" to 1.0, "request_count" to 1.0),
                mapOf("model" to "real", "total_usage" to 1.0, "request_count" to 1.0)
            )
        )

        assertEquals(listOf("real"), result.map { it.model })
    }

    @Test
    @DisplayName("a blank model dimension is dropped, not rendered as an empty row")
    fun `a blank model dimension is dropped`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("model" to "  ", "total_usage" to 1.0, "request_count" to 1.0))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("a non-numeric total_usage drops the whole row rather than counting it as zero")
    fun `a non-numeric total_usage drops the row`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "corrupt", "total_usage" to "not-a-number", "request_count" to 1.0),
                mapOf("model" to "good", "total_usage" to 1.0, "request_count" to 1.0)
            )
        )

        assertEquals(listOf("good"), result.map { it.model })
    }

    @Test
    @DisplayName("a non-numeric request_count drops the whole row rather than counting it as zero")
    fun `a non-numeric request_count drops the row`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "corrupt", "total_usage" to 1.0, "request_count" to "many"),
                mapOf("model" to "good", "total_usage" to 1.0, "request_count" to 1.0)
            )
        )

        assertEquals(listOf("good"), result.map { it.model })
    }

    @Test
    @DisplayName(
        "the live bug: a QUOTED numeric request_count (the real server's actual shape) still " +
            "produces a non-empty breakdown with the right counts, instead of dropping every row"
    )
    fun `a quoted numeric request_count still parses`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "anthropic/claude-sonnet-4.5", "total_usage" to 0.5123, "request_count" to "31"),
                mapOf("model" to "openai/gpt-4o-mini", "total_usage" to 0.0221, "request_count" to "12")
            )
        )

        // Before the fix, numberOrZero's strict `as? Number` rejects a String outright - every
        // row above is dropped and this comes back empty, exactly the live "No activity in this
        // period" bug. It must come back with both rows, and the quoted count must convert to
        // the SAME Long a typed 31.0/12.0 would - not zero, not the row dropped.
        assertEquals(2, result.size)
        assertEquals("anthropic/claude-sonnet-4.5", result[0].model)
        assertEquals(31L, result[0].requests)
        assertEquals("openai/gpt-4o-mini", result[1].model)
        assertEquals(12L, result[1].requests)
    }

    @Test
    @DisplayName("a quoted numeric total_usage (server not consistent about which metric it quotes) still parses")
    fun `a quoted numeric total_usage still parses`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("model" to "m", "total_usage" to "0.75", "request_count" to 4.0))
        )

        assertEquals(1, result.size)
        assertEquals(0.75, result[0].usage, 1e-9)
        assertEquals(4L, result[0].requests)
    }

    @Test
    @DisplayName("a missing total_usage key counts as zero rather than dropping the row")
    fun `a missing total_usage key counts as zero`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("model" to "m", "request_count" to 4.0))
        )

        assertEquals(1, result.size)
        assertEquals(0.0, result[0].usage, 1e-9)
        assertEquals(4L, result[0].requests)
    }

    @Test
    @DisplayName("a missing request_count key counts as zero rather than dropping the row")
    fun `a missing request_count key counts as zero`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(mapOf("model" to "m", "total_usage" to 2.5))
        )

        assertEquals(1, result.size)
        assertEquals(2.5, result[0].usage, 1e-9)
        assertEquals(0L, result[0].requests)
    }

    @Test
    @DisplayName("an empty row list yields an empty result")
    fun `an empty row list yields an empty result`() {
        assertTrue(AnalyticsBreakdown.toModelSpend(emptyList()).isEmpty())
    }

    @Test
    @DisplayName(
        "rows are read against the server's own field names, and per-model rows across time " +
            "buckets are summed into one - the defensive shape toModelSpend's own KDoc describes " +
            "in case a granularity is ever reintroduced (the checked-in fixture itself is now " +
            "one row per model, matching requestFor's actual granularity-less query)"
    )
    fun `rows are read against the server's field names and grouped by model`() {
        // A synthetic multi-bucket-per-model shape - NOT what the checked-in fixture carries any
        // more (that now mirrors the real, granularity-less per-model query exactly: one row per
        // model, no date__day key). Kept here to pin the defensive re-grouping toModelSpend's own
        // KDoc still promises if a granularity/server-side bucketing default ever comes back: two
        // rows for the SAME model on two different date__day buckets, plus one row for a second
        // model.
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf(
                    "date__day" to "2026-09-18T00:00:00.000Z",
                    "model" to "anthropic/claude-sonnet-4.5",
                    "total_usage" to 0.5123,
                    "request_count" to 31.0
                ),
                mapOf(
                    "date__day" to "2026-09-18T00:00:00.000Z",
                    "model" to "openai/gpt-4o-mini",
                    "total_usage" to 0.0221,
                    "request_count" to 12.0
                ),
                mapOf(
                    "date__day" to "2026-09-19T00:00:00.000Z",
                    "model" to "anthropic/claude-sonnet-4.5",
                    "total_usage" to 0.3010,
                    "request_count" to 18.0
                )
            )
        )

        // Three rows in, but the two same-model rows are one bucketed model spent across two
        // days - not two separate list entries. If grouping were missing, this would assert
        // 3, not 2, and the sonnet row's usage would be 0.5123 or 0.3010 instead of their sum.
        assertEquals(2, result.size)
        assertEquals("anthropic/claude-sonnet-4.5", result[0].model)
        assertEquals(0.8133, result[0].usage, 1e-9)
        assertEquals(49L, result[0].requests)
        assertEquals("openai/gpt-4o-mini", result[1].model)
        assertEquals(0.0221, result[1].usage, 1e-9)
        assertEquals(12L, result[1].requests)
    }

    @Test
    @DisplayName("two rows for the same model, from different time buckets, are summed into one entry")
    fun `two rows for the same model are summed`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "m", "total_usage" to 0.30, "request_count" to 3.0),
                mapOf("model" to "m", "total_usage" to 0.21, "request_count" to 2.0)
            )
        )

        assertEquals(1, result.size)
        assertEquals(0.51, result[0].usage, 1e-9)
        assertEquals(5L, result[0].requests)
    }

    // --- spendSeriesRequestFor: Task 13's own window invariant --------------------------------

    @Test
    @DisplayName(
        "spendSeriesRequestFor's time_range agrees with byModel's own inclusive-both-ends window, " +
            "for every period - the top-line sparkline must never cover a different window than " +
            "the per-model breakdown for the same period"
    )
    fun `spendSeriesRequestFor agrees with byModel's window`() {
        ActivityAggregator.Period.values().forEach { period ->
            val earliestIncluded = today.minusDays(period.days.toLong() - 1)
            val dayBeforeEarliest = earliestIncluded.minusDays(1)

            // Read byModel's actual boundary off its real filtering behaviour, exactly as
            // "requestFor agrees with byModel's window" does above.
            val boundaryResult = ActivityAggregator.byModel(
                listOf(
                    activityRow(earliestIncluded.toString(), "in"),
                    activityRow(dayBeforeEarliest.toString(), "out")
                ),
                period,
                today
            )
            assertEquals(
                listOf("in"),
                boundaryResult.map { it.model },
                "sanity check on byModel itself for $period - if this fails, the test's own " +
                    "premise about byModel's boundary is wrong"
            )

            val request = AnalyticsBreakdown.spendSeriesRequestFor(period, today)
            val start = OffsetDateTime.parse(request.timeRange.start).toLocalDate()
            val lastCoveredDay = OffsetDateTime.parse(request.timeRange.end).toLocalDate().minusDays(1)

            assertEquals(
                earliestIncluded,
                start,
                "for $period, spendSeriesRequestFor's start ($start) must be the same earliest " +
                    "day byModel includes ($earliestIncluded) - a mismatch here means the " +
                    "sparkline and the breakdown disagree about what '7 days' covers"
            )
            assertEquals(
                today,
                lastCoveredDay,
                "for $period, spendSeriesRequestFor's end must cover through today ($today), " +
                    "found it covering only through $lastCoveredDay"
            )
        }
    }

    @Test
    @DisplayName(
        "the spend-series request asks for total_usage only, with no model dimension and " +
            "day granularity - a different shape from requestFor's per-model query"
    )
    fun `the spend-series request asks for the account-wide daily total`() {
        val request = AnalyticsBreakdown.spendSeriesRequestFor(ActivityAggregator.Period.WEEK, today)

        assertEquals(listOf("total_usage"), request.metrics)
        assertEquals(
            null,
            request.dimensions,
            "no model dimension - this is an account-wide total, not a breakdown"
        )
        assertEquals(
            "day",
            request.granularity,
            "a time series needs per-bucket rows, unlike requestFor's omitted granularity"
        )
    }

    @Test
    @DisplayName(
        "fix round 2, finding 3: DAY asks for hour granularity, not day - a single-day window " +
            "sliced into day-sized buckets would always return exactly one (still-filling) " +
            "bucket, which draws nothing on the sparkline"
    )
    fun `the DAY period asks for hour granularity`() {
        val request = AnalyticsBreakdown.spendSeriesRequestFor(ActivityAggregator.Period.DAY, today)

        assertEquals(
            "hour",
            request.granularity,
            "DAY's window is a single calendar day - 'day' granularity there would always yield " +
                "one bucket, and SparklineGeometry.plot draws nothing through a single point"
        )
    }

    @Test
    @DisplayName("WEEK and MONTH both still ask for day granularity, unlike DAY")
    fun `WEEK and MONTH ask for day granularity`() {
        listOf(ActivityAggregator.Period.WEEK, ActivityAggregator.Period.MONTH).forEach { period ->
            assertEquals(
                "day",
                AnalyticsBreakdown.spendSeriesRequestFor(period, today).granularity,
                "for $period, only DAY should ever ask for hour granularity"
            )
        }
    }

    // --- toSpendSeries --------------------------------------------------------------------------

    @Test
    @DisplayName("rows are ordered oldest first regardless of the input order, and same-bucket rows are summed")
    fun `toSpendSeries orders oldest first and sums within a bucket`() {
        // Deliberately supplied NEWEST first, and with the 18th split across two rows - if
        // toSpendSeries merely preserved input order, this would come back as
        // [0.30, 0.20, 0.30] (unsummed, input order); if it summed but sorted the wrong way, as
        // [0.30, 0.50] (newest first) instead of the correct oldest-first [0.50, 0.30].
        // assertEquals on a List<Double> compares bit-for-bit, so this uses an explicit
        // tolerance rather than relying on 0.20 + 0.30 happening to round-trip exactly under
        // IEEE-754 (fix round 2, finding 7).
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__day" to "2026-09-19T00:00:00.000Z", "total_usage" to 0.30),
                mapOf("date__day" to "2026-09-18T00:00:00.000Z", "total_usage" to 0.20),
                mapOf("date__day" to "2026-09-18T00:00:00.000Z", "total_usage" to 0.30)
            )
        )

        assertEquals(2, result.size)
        assertEquals(0.50, result[0], 1e-9)
        assertEquals(0.30, result[1], 1e-9)
    }

    @Test
    @DisplayName(
        "the bucket key is found by its date__ prefix, not hard-coded as date__day - a " +
            "different granularity's bucket name is still read correctly"
    )
    fun `toSpendSeries finds the bucket key by its date__ prefix`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__week" to "2026-09-14T00:00:00.000Z", "total_usage" to 1.0),
                mapOf("date__week" to "2026-09-21T00:00:00.000Z", "total_usage" to 2.0)
            )
        )

        assertEquals(listOf(1.0, 2.0), result)
    }

    @Test
    @DisplayName(
        "fix round 2, finding 3: DAY's hour-granularity response buckets under date__hour, and " +
            "toSpendSeries reads it correctly rather than this being left to inference"
    )
    fun `toSpendSeries reads the date__hour bucket key DAY's hour granularity produces`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__hour" to "2026-09-19T08:00:00.000Z", "total_usage" to 0.40),
                mapOf("date__hour" to "2026-09-19T09:00:00.000Z", "total_usage" to 0.60)
            )
        )

        assertEquals(2, result.size)
        assertEquals(0.40, result[0], 1e-9)
        assertEquals(0.60, result[1], 1e-9)
    }

    @Test
    @DisplayName(
        "a response with no date__-prefixed key on any row yields an empty series, not a guess " +
            "at which column was meant - the honest answer to the server changing shape under us"
    )
    fun `toSpendSeries returns an empty series when no bucket key is found`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(mapOf("bucket_start" to "2026-09-18T00:00:00.000Z", "total_usage" to 1.0))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("an empty row list yields an empty series")
    fun `toSpendSeries with no rows yields an empty series`() {
        assertTrue(AnalyticsBreakdown.toSpendSeries(emptyList()).isEmpty())
    }

    @Test
    @DisplayName("a row missing the bucket key is dropped, not counted under a blank bucket")
    fun `toSpendSeries drops a row missing the bucket key`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("total_usage" to 1.0),
                mapOf("date__day" to "2026-09-18T00:00:00.000Z", "total_usage" to 2.0)
            )
        )

        assertEquals(listOf(2.0), result)
    }

    @Test
    @DisplayName("a non-numeric total_usage drops that row rather than emptying the whole series")
    fun `toSpendSeries drops a row with a non-numeric total_usage`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__day" to "2026-09-18T00:00:00.000Z", "total_usage" to "not-a-number"),
                mapOf("date__day" to "2026-09-19T00:00:00.000Z", "total_usage" to 3.0)
            )
        )

        assertEquals(listOf(3.0), result)
    }

    @Test
    @DisplayName("a bucket with the key present but no total_usage field counts as zero, not a dropped row")
    fun `toSpendSeries treats a missing total_usage key as zero`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(mapOf("date__day" to "2026-09-18T00:00:00.000Z"))
        )

        assertEquals(listOf(0.0), result)
    }

    @Test
    @DisplayName(
        "a QUOTED numeric total_usage still parses into that bucket's reading, instead of " +
            "being dropped - the server is not consistent about which metric it quotes"
    )
    fun `toSpendSeries reads a quoted numeric total_usage`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(mapOf("date__day" to "2026-09-18T00:00:00.000Z", "total_usage" to "1.5"))
        )

        assertEquals(listOf(1.5), result)
    }

    // --- burnRatePerDay (fix round 2, findings 1 and 2) --------------------------------------

    @Test
    @DisplayName(
        "the still-filling final bucket is dropped from the sum, and the denominator is " +
            "period.days - 1 (completed days), never series.size"
    )
    fun `burnRatePerDay drops the last bucket and divides by completed days`() {
        // WEEK has 7 calendar days in its window; the LAST entry (today, still filling) must be
        // dropped from the sum, leaving [1.0, 2.0, 3.0, 4.0, 5.0, 6.0] (sum 21.0) divided by
        // 6 completed days (period.days - 1) = 3.5. If the still-filling bucket were wrongly
        // included, this would be 21.0 (with the 7.0 dropped) or (21.0 + 7.0) / 7 = 4.0 - either
        // way a different, wrong number.
        val series = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)

        val result = AnalyticsBreakdown.burnRatePerDay(series, ActivityAggregator.Period.WEEK)

        assertEquals(3.5, result!!, 1e-9)
    }

    @Test
    @DisplayName(
        "a server that omits zero-spend days must not inflate the rate - dividing by the KNOWN " +
            "period length, not the series' own (shorter) size, treats an omitted day as the " +
            "zero spend it was"
    )
    fun `burnRatePerDay divides by the period's own day count, not the series length`() {
        // A 30-day period where only ONE completed day (plus today, dropped) has a row at all -
        // the shape the project's own fixture shows is possible (a model/day combination with no
        // usage is simply absent, not a zero-valued row). If the denominator were series.size
        // (2, after today is dropped: [30.0] has size 1) instead of period.days - 1 (29), this
        // would come back as 30.0 (or 30.0 / 1) instead of the true rate spread over the whole
        // period, roughly 1.03.
        val series = listOf(30.0, 5.0) // one completed day at $30, then today's (dropped) $5
        val expected = 30.0 / (ActivityAggregator.Period.MONTH.days - 1)

        val result = AnalyticsBreakdown.burnRatePerDay(series, ActivityAggregator.Period.MONTH)

        assertEquals(expected, result!!, 1e-9)
    }

    @Test
    @DisplayName(
        "DAY has zero completed days by construction (period.days - 1 == 0) and always returns " +
            "null, regardless of what the series contains - never a rate computed from nothing " +
            "but still-filling data, and never a division by zero"
    )
    fun `burnRatePerDay returns null for DAY regardless of series content`() {
        assertEquals(null, AnalyticsBreakdown.burnRatePerDay(listOf(1.0, 2.0, 3.0), ActivityAggregator.Period.DAY))
        assertEquals(null, AnalyticsBreakdown.burnRatePerDay(emptyList(), ActivityAggregator.Period.DAY))
    }

    @Test
    @DisplayName(
        "a series with only the dropped still-filling bucket legitimately averages to 0.0, not " +
            "null - the server's silence about the completed days IS the answer 'nothing spent then'"
    )
    fun `burnRatePerDay is zero when only the still-filling bucket has data`() {
        val result = AnalyticsBreakdown.burnRatePerDay(listOf(9.0), ActivityAggregator.Period.WEEK)

        assertEquals(0.0, result!!, 1e-9)
    }

    @Test
    @DisplayName("an empty series has no known burn rate - null, never a confident zero")
    fun `burnRatePerDay returns null for an empty series`() {
        assertEquals(null, AnalyticsBreakdown.burnRatePerDay(emptyList(), ActivityAggregator.Period.WEEK))
    }

    @Test
    @DisplayName("a metric that is neither a number nor a string (an object, a list, a bool) drops the whole row")
    fun `a metric of a wholly non-numeric type drops the row`() {
        val result = AnalyticsBreakdown.toModelSpend(
            listOf(
                mapOf("model" to "listy", "total_usage" to listOf(1.0), "request_count" to 1.0),
                mapOf("model" to "objecty", "total_usage" to 1.0, "request_count" to mapOf("n" to 1)),
                mapOf("model" to "booly", "total_usage" to true, "request_count" to 1.0),
                mapOf("model" to "good", "total_usage" to 1.0, "request_count" to 1.0)
            )
        )

        assertEquals(listOf("good"), result.map { it.model })
    }

    @Test
    @DisplayName("toSpendSeries drops a row whose bucket value is not a string")
    fun `toSpendSeries drops a row with a non-string bucket`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__day" to "2026-09-18", "total_usage" to 1.0),
                mapOf("date__day" to 20260919, "total_usage" to 99.0)
            )
        )

        assertEquals(listOf(1.0), result)
    }

    @Test
    @DisplayName("toSpendSeries drops a row whose bucket value is blank")
    fun `toSpendSeries drops a row with a blank bucket`() {
        val result = AnalyticsBreakdown.toSpendSeries(
            listOf(
                mapOf("date__day" to "2026-09-18", "total_usage" to 1.0),
                mapOf("date__day" to "   ", "total_usage" to 99.0)
            )
        )

        assertEquals(listOf(1.0), result)
    }
}
