package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AnalyticsDimension
import org.zhavoronkov.openrouter.models.AnalyticsMeta
import org.zhavoronkov.openrouter.models.AnalyticsMetric

@DisplayName("AnalyticsMetaCheck")
class AnalyticsMetaCheckTest {

    private fun completeMeta() = AnalyticsMeta(
        metrics = listOf(AnalyticsMetric("total_usage", null, null), AnalyticsMetric("request_count", null, null)),
        dimensions = listOf(AnalyticsDimension("model", null)),
        granularities = listOf("hour", "day")
    )

    // --- Derived from AnalyticsBreakdown's own constants, not re-typed here --------------------

    @Test
    @DisplayName("a live capture carrying every hard-coded name validates, using the real, production required sets")
    fun `every hard-coded name present validates`() {
        val result = AnalyticsMetaCheck.run(completeMeta())

        assertEquals(AnalyticsMetaCheck.Result.Validated, result)
    }

    // --- The failure this check exists to catch: a name removed from the metadata ---------------

    @Test
    @DisplayName(
        "a metric removed from the metadata is named in Missing, checked against AnalyticsBreakdown's own metric name"
    )
    fun `a removed metric is reported missing`() {
        val meta = completeMeta().copy(metrics = listOf(AnalyticsMetric("request_count", null, null)))

        val result = AnalyticsMetaCheck.run(meta)

        assertEquals(
            AnalyticsMetaCheck.Result.Missing(listOf("total_usage")),
            result,
            "must name exactly the removed metric - AnalyticsBreakdown's own METRIC_USAGE constant, " +
                "surfaced through its public REQUIRED_METRICS set, not a re-typed copy of the string"
        )
    }

    @Test
    @DisplayName("a dimension removed from the metadata is named in Missing")
    fun `a removed dimension is reported missing`() {
        val meta = completeMeta().copy(dimensions = emptyList())

        val result = AnalyticsMetaCheck.run(meta)

        assertEquals(AnalyticsMetaCheck.Result.Missing(listOf("model")), result)
    }

    @Test
    @DisplayName("a granularity removed from the metadata is named in Missing")
    fun `a removed granularity is reported missing`() {
        val meta = completeMeta().copy(granularities = listOf("day"))

        val result = AnalyticsMetaCheck.run(meta)

        assertEquals(AnalyticsMetaCheck.Result.Missing(listOf("hour")), result)
    }

    @Test
    @DisplayName(
        "multiple missing names across all three categories are all reported, metrics then dimensions then granularities"
    )
    fun `multiple missing names across categories are all reported`() {
        val meta = AnalyticsMeta(
            metrics = listOf(AnalyticsMetric("request_count", null, null)),
            dimensions = emptyList(),
            granularities = listOf("day")
        )

        val result = AnalyticsMetaCheck.run(meta)

        assertEquals(
            AnalyticsMetaCheck.Result.Missing(listOf("total_usage", "model", "hour")),
            result
        )
    }

    // --- The ruling that matters most: an unreadable response is "could not check", never "all gone" ---

    @Test
    @DisplayName(
        "a wholly empty AnalyticsMeta (Gson's null-past-Kotlin default) is CouldNotCheck, not Missing with every name"
    )
    fun `a wholly empty meta is could-not-check not a total wipeout`() {
        val result = AnalyticsMetaCheck.run(AnalyticsMeta())

        assertEquals(
            AnalyticsMetaCheck.Result.CouldNotCheck,
            result,
            "a default-constructed AnalyticsMeta must never be read as 'every metric, dimension " +
                "and granularity was removed' - it means the response could not be told apart from " +
                "a parse failure, which is a DIFFERENT fact from 'the server took the names away'"
        )
    }

    @Test
    @DisplayName("a real 'metric-only' server response is still checked, not treated as could-not-check")
    fun `a response with only one non-empty list is checked as real data`() {
        // Real signal, not a parse failure: at least one list is non-empty, so this IS an answer -
        // the empty dimensions/granularities lists are read as "the server has none", and the
        // hard-coded 'model' dimension and 'hour'/'day' granularities are genuinely missing.
        val meta = AnalyticsMeta(
            metrics = listOf(AnalyticsMetric("total_usage", null, null), AnalyticsMetric("request_count", null, null)),
            dimensions = emptyList(),
            granularities = emptyList()
        )

        val result = AnalyticsMetaCheck.run(meta)

        assertTrue(
            result is AnalyticsMetaCheck.Result.Missing,
            "a non-empty metrics list makes this a real response, not a parse failure - the " +
                "absent dimension/granularity names must be reported, got $result"
        )
        assertEquals(listOf("model", "day", "hour"), (result as AnalyticsMetaCheck.Result.Missing).missingNames)
    }

    // --- Explicit parameters are honoured, proving this is not secretly hard-wired -------------

    @Test
    @DisplayName("explicit required-name sets are honoured instead of AnalyticsBreakdown's defaults")
    fun `explicit required sets override the defaults`() {
        val meta = AnalyticsMeta(
            metrics = listOf(AnalyticsMetric("some_other_metric", null, null)),
            dimensions = emptyList(),
            granularities = emptyList()
        )

        val result = AnalyticsMetaCheck.run(
            meta,
            requiredMetrics = setOf("some_other_metric"),
            requiredDimensions = emptySet(),
            requiredGranularities = emptySet()
        )

        assertEquals(AnalyticsMetaCheck.Result.Validated, result)
    }
}
