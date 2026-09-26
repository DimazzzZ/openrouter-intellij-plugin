package org.zhavoronkov.openrouter.toolwindow.status

import org.zhavoronkov.openrouter.models.AnalyticsOrderBy
import org.zhavoronkov.openrouter.models.AnalyticsQueryRequest
import org.zhavoronkov.openrouter.models.AnalyticsTimeRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Turns a [ActivityAggregator.Period] into an analytics query, and an analytics
 * response's loose rows back into [ActivityAggregator.ModelSpend] - the two
 * decisions the task brief's own summary ("both produce `List<ModelSpend>`, so
 * `show()` does not care which path fed it") glosses over.
 *
 * Both directions matter because they are the other half of the single-source
 * rule this redesign exists to enforce: [requestFor] must ask the analytics
 * endpoint for EXACTLY the window [ActivityAggregator.byModel] would have used
 * on the degraded (no provisioning key) path, or the same user asking for "7
 * days" sees two different numbers depending only on which path answered -
 * the precise defect class this whole tab redesign exists to remove.
 *
 * Pure apart from `java.time` and the plugin's own model types, both plain JVM
 * classes, so this runs in the fast headless task - same precedent as
 * [ActivityAggregator] itself.
 *
 * Task 13 adds the account-wide daily spend series this file's own top-level KDoc left as a gap:
 * [requestFor]/[toModelSpend] answer "where does the money go" (per model, no time bucketing);
 * [spendSeriesRequestFor]/[toSpendSeries]/[burnRatePerDay] answer "how fast am I burning it" and
 * "how long does it last" (account-wide, WITH time bucketing) - a genuinely different query shape,
 * not a filter over the same rows. Kept in this object rather than a sibling one because the two
 * pairs share the exact same window arithmetic and [TIME_RANGE_FORMAT] - splitting them apart
 * would either duplicate that arithmetic or force an import back into a "pure query/mapping"
 * object anyway - and the naming already signals the relationship: [spendSeriesRequestFor] and
 * [toSpendSeries] are qualified with "spend series" precisely to read as a second, distinguishable
 * pair beside [requestFor]/[toModelSpend], not a replacement for them.
 */
object AnalyticsBreakdown {

    // Metric/dimension names taken from the fixture at
    // src/test/resources/fixtures/analytics-query-response.json and confirmed
    // against AnalyticsModelsFixtureTest / AnalyticsServiceTest, which serialise
    // and parse requests/responses using exactly these server-side names.
    private const val METRIC_USAGE = "total_usage"
    private const val METRIC_REQUESTS = "request_count"
    private const val DIMENSION_MODEL = "model"
    private const val ORDER_DIRECTION_DESC = "desc"
    private const val GRANULARITY_DAY = "day"
    private const val GRANULARITY_HOUR = "hour"

    // The fixture buckets rows under "date__day" for a "day" granularity - the server names the
    // bucket key after whatever granularity was requested, not a single fixed literal. Matching by
    // PREFIX rather than hard-coding "date__day" is what lets toSpendSeries keep working if this
    // object is ever asked for a different granularity, without a corresponding code change here.
    private const val BUCKET_KEY_PREFIX = "date__"

    // An unverified guess at a sane upper bound on model rows, not a value read off any
    // documented server default. With `granularity` omitted (see requestFor's KDoc) this is
    // expected to be one row per actively-used model, so it should no longer be load-bearing -
    // but AnalyticsQueryPayload.metadata.truncated is the actual safety net (StatusTabPanel
    // surfaces it to BreakdownBlock), not this number.
    private const val ROW_LIMIT = 1000

    // Formats a LocalDateTime as e.g. "2026-09-19T00:00:00Z" - the same shape
    // AnalyticsServiceTest and AnalyticsModelsFixtureTest use for time_range
    // bounds. The 'Z' here is a quoted literal, not an offset: these windows
    // are calendar-day boundaries, not readings off any particular instant.
    private val TIME_RANGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    /**
     * Builds the query for [period], anchored on [today].
     *
     * The window is `[earliest, today]`, inclusive at both ends, where
     * `earliest = today.minusDays(period.days - 1)` - the EXACT same
     * computation [ActivityAggregator.byModel] performs. Expressed as a
     * time_range this is the half-open instant range
     * `[earliest-at-midnight, (today + 1 day)-at-midnight)`: the exclusive
     * upper bound is the start of the day AFTER today, precisely so that all
     * of today's own activity - not merely up to its first instant - is
     * included. See AnalyticsBreakdownTest's
     * "requestFor agrees with byModel's window" for the test that pins this.
     *
     * `granularity` is deliberately left `null` (it is a nullable, optional field on
     * [AnalyticsQueryRequest]). The fixture at
     * src/test/resources/fixtures/analytics-query-response.json shows the server buckets rows by
     * `date__day` off `granularity` ALONE, independent of `dimensions` - so requesting
     * `"hour"`/`"day"` here, with `dimensions = ["model"]` only, would make the endpoint return
     * one row per model PER TIME BUCKET (up to 24x for a 24-hour window, 30x for a 30-day one)
     * against [ROW_LIMIT], risking truncation for an account with many actively-used models, for
     * no benefit: nothing here wants the per-bucket breakdown, only the per-model total. Omitting
     * it asks the server to aggregate the whole window into one row per model directly.
     * [toModelSpend]'s grouping step is kept regardless, in case a live account ever proves a
     * granularity is required after all and it has to come back.
     */
    fun requestFor(period: ActivityAggregator.Period, today: LocalDate): AnalyticsQueryRequest {
        val earliest = today.minusDays(period.days.toLong() - 1)
        val start = earliest.atStartOfDay().format(TIME_RANGE_FORMAT)
        val end = today.plusDays(1).atStartOfDay().format(TIME_RANGE_FORMAT)

        return AnalyticsQueryRequest(
            metrics = listOf(METRIC_USAGE, METRIC_REQUESTS),
            dimensions = listOf(DIMENSION_MODEL),
            granularity = null,
            timeRange = AnalyticsTimeRange(start = start, end = end),
            limit = ROW_LIMIT,
            orderBy = AnalyticsOrderBy(field = METRIC_USAGE, direction = ORDER_DIRECTION_DESC)
        )
    }

    /**
     * Maps an analytics query response's rows to [ActivityAggregator.ModelSpend],
     * grouped by model and summed, then sorted descending by usage - the same
     * shape [ActivityAggregator.byModel] produces, so both paths present
     * identically regardless of which fed [BreakdownBlock.show].
     *
     * The grouping step is kept as a defensive measure, not because [requestFor] currently asks
     * for a `granularity`: it deliberately omits one (see its KDoc), precisely so the endpoint
     * aggregates each model into a single row over the whole window. But if a `granularity` is
     * ever reintroduced - or a server-side default bucketing kicks in regardless - the endpoint
     * would return one row PER MODEL PER TIME BUCKET, exactly as the fixture at
     * src/test/resources/fixtures/analytics-query-response.json shows for
     * `anthropic/claude-sonnet-4.5` (one row for 2026-09-18, another for 2026-09-19). Grouping
     * here means that shape still renders as one summed entry per model instead of silently
     * fragmenting into duplicates, at no cost when the server already returns one row per model.
     *
     * A row missing its `model` dimension, or carrying a non-numeric value
     * where `total_usage`/`request_count` is present, is dropped rather than
     * crashing the whole breakdown or being silently counted as zero - the
     * same "one malformed row must not empty the whole breakdown" precedent
     * [ActivityAggregator.byModel] applies to an unparseable date. A metric key
     * that is simply ABSENT (as opposed to present-but-wrong-type) counts as
     * zero, matching [ActivityAggregator.byModel]'s own treatment of a null
     * `usage`/`requests` field.
     *
     * Every number in [rows] arrives as a [Double] under Gson's `Any?` decoding,
     * even a conceptually integral one like `request_count` - so this checks
     * `is Number` and converts, and never `is Int`, which would silently drop
     * every correctly-decoded row.
     */
    fun toModelSpend(rows: List<Map<String, Any?>>): List<ActivityAggregator.ModelSpend> =
        rows.mapNotNull(::toRowSpend)
            .groupBy { it.model }
            .map { (model, group) ->
                ActivityAggregator.ModelSpend(
                    model = model,
                    usage = group.sumOf { it.usage },
                    requests = group.sumOf { it.requests }
                )
            }
            .sortedByDescending { it.usage }

    /** One row's own model/usage/requests, before grouping - see [toModelSpend]. */
    private fun toRowSpend(row: Map<String, Any?>): ActivityAggregator.ModelSpend? {
        val model = (row[DIMENSION_MODEL] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val usage = numberOrZero(row, METRIC_USAGE) ?: return null
        val requests = numberOrZero(row, METRIC_REQUESTS) ?: return null

        return ActivityAggregator.ModelSpend(model = model, usage = usage, requests = requests.toLong())
    }

    /**
     * Reads a numeric field from a row: `0.0` when [key] is absent (matching
     * [ActivityAggregator.byModel]'s null-counts-as-zero rule), the converted
     * value when it is a [Number], or `null` - "skip this whole row" - when
     * [key] is present with a non-numeric value.
     */
    private fun numberOrZero(row: Map<String, Any?>, key: String): Double? {
        if (!row.containsKey(key)) return 0.0
        return (row[key] as? Number)?.toDouble()
    }

    /**
     * Builds the account-wide daily spend query for [period], anchored on [today] - the query the
     * READY/ERROR sparkline (and the burn rate/days-left estimate derived from it) is fed from,
     * closing the gap [requestFor] deliberately leaves open (see its own KDoc): that query omits
     * `dimensions`/`granularity` on purpose so the server returns ONE row per model for the whole
     * window. This query wants the opposite shape - the account's OWN total, bucketed per day -
     * so unlike [requestFor] it omits [DIMENSION_MODEL] entirely (a per-model breakdown is not
     * this query's job) and always asks for a granularity: a time series is exactly the one case
     * where per-bucket rows are the wanted shape, which is why [requestFor]'s own KDoc calls
     * omitting granularity there deliberate rather than an oversight.
     *
     * The granularity itself depends on [period] (fix round 2, finding 3): [ActivityAggregator.Period.DAY]
     * asks for [GRANULARITY_HOUR], every other period for [GRANULARITY_DAY]. A one-bucket series
     * cannot be drawn as a line at all ([SparklineGeometry.plot] through a single point draws
     * nothing), and DAY's window is a single calendar day, so `"day"` granularity there would
     * always return exactly one (still-filling) bucket - an empty-looking chart on the tab's own
     * default view, which is what this task exists to fix. Task 9's objection to a finer
     * granularity (row inflation against [ROW_LIMIT] from one row PER MODEL PER BUCKET) does not
     * apply here: this query carries no [DIMENSION_MODEL], so an hourly DAY query costs at most 24
     * rows, not 24 times the model count.
     *
     * The window is IDENTICAL to [requestFor]'s and to [ActivityAggregator.byModel]'s: `[earliest,
     * today]` inclusive at both ends, `earliest = today.minusDays(period.days - 1)`, expressed as
     * the same half-open `time_range` with an exclusive upper bound at the start of the day AFTER
     * [today]. See AnalyticsBreakdownTest's "spendSeriesRequestFor agrees with byModel's window"
     * for the test pinning this against [ActivityAggregator.byModel]'s own real filtering
     * behaviour, the same way [requestFor]'s own window-agreement test already does - a mismatch
     * here would mean the top-line sparkline and the per-model breakdown answer for "7 days"
     * silently cover different calendar windows. The granularity change above does not touch this
     * window arithmetic at all - only which bucket size the SAME window is sliced into.
     *
     * [ROW_LIMIT] is reused as the row cap even though this query can never return more than
     * [ActivityAggregator.Period.MONTH]'s 30 daily buckets, or DAY's 24 hourly ones - both far
     * below it - because there is no sharper, still-honest bound to state instead, and truncation
     * here would be a genuine server anomaly worth surfacing the same way it already is for
     * [requestFor].
     */
    fun spendSeriesRequestFor(period: ActivityAggregator.Period, today: LocalDate): AnalyticsQueryRequest {
        val earliest = today.minusDays(period.days.toLong() - 1)
        val start = earliest.atStartOfDay().format(TIME_RANGE_FORMAT)
        val end = today.plusDays(1).atStartOfDay().format(TIME_RANGE_FORMAT)
        val granularity = if (period == ActivityAggregator.Period.DAY) GRANULARITY_HOUR else GRANULARITY_DAY

        return AnalyticsQueryRequest(
            metrics = listOf(METRIC_USAGE),
            dimensions = null,
            granularity = granularity,
            timeRange = AnalyticsTimeRange(start = start, end = end),
            limit = ROW_LIMIT
        )
    }

    /**
     * Maps a [spendSeriesRequestFor] response's rows to one spend value per time bucket, ORDERED
     * OLDEST FIRST - [SparklineGeometry.plot]'s own documented input order, and the whole chart
     * reads backwards (a rising line drawn as falling, and vice versa) if this were reversed.
     *
     * The bucket key is discovered by its [BUCKET_KEY_PREFIX] rather than hard-coded as the
     * fixture's own `"date__day"`: the server names the bucket after whatever `granularity` was
     * requested - [spendSeriesRequestFor] asks for `"day"` for WEEK/MONTH (hence `date__day` in
     * the fixture) but `"hour"` for DAY (hence `date__hour` there instead; see
     * AnalyticsBreakdownTest's own test pinning that key rather than leaving it to inference) -
     * and the KEY ITSELF is not this object's contract to fix in stone regardless. The first row
     * that carries a key with this prefix decides which key every row is read against; a response that names its
     * bucket some OTHER way entirely (no `date__`-prefixed key on any row) yields an empty list -
     * a plotted-as-unknown sparkline is the honest answer to "the server changed shape under us",
     * never a guess at which column was meant.
     *
     * Values sharing a bucket are SUMMED (defensive, the same reasoning [toModelSpend] already
     * documents for a re-introduced per-model granularity: this metric alone, with no dimension,
     * should already be one row per bucket, but a duplicate must still combine rather than
     * silently pick one). Buckets are then sorted by their own key - the fixture's ISO-8601 UTC
     * bucket strings (`"2026-09-18T00:00:00.000Z"`) sort lexicographically in the same order as
     * chronologically, so a plain string sort is both correct and independent of the order the
     * server happened to return rows in.
     *
     * A row missing the bucket key, or carrying a non-numeric [METRIC_USAGE], is dropped rather
     * than emptying the whole series or being silently counted as zero and merged into a
     * neighbouring bucket - the same "one malformed row must not empty the whole answer" rule
     * [toModelSpend] and [ActivityAggregator.byModel] already apply. A row with the bucket key but
     * no `total_usage` key AT ALL counts as zero for that bucket, matching [numberOrZero]'s
     * existing absent-vs-wrong-type distinction.
     */
    fun toSpendSeries(rows: List<Map<String, Any?>>): List<Double> {
        val bucketKey = rows.firstNotNullOfOrNull { row -> row.keys.find { it.startsWith(BUCKET_KEY_PREFIX) } }
            ?: return emptyList()

        return rows.mapNotNull { row -> toBucketReading(row, bucketKey) }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .values
            .map { it.sum() }
    }

    /** One row's own bucket key and usage value, before grouping - see [toSpendSeries]. */
    private fun toBucketReading(row: Map<String, Any?>, bucketKey: String): Pair<String, Double>? {
        val bucket = (row[bucketKey] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val usage = numberOrZero(row, METRIC_USAGE) ?: return null
        return bucket to usage
    }

    /**
     * The burn rate [BalanceBlock] turns into a days-left estimate: the total of [series]'s
     * COMPLETED buckets, divided by [period]'s own count of completed calendar days -
     * `period.days - 1`, never [series]'s own length. Two decisions here, both fixes from fix
     * round 2 (findings 1 and 2) for the same underlying rule: **a rate must never be derived
     * from an incomplete bucket, and its denominator must not come from the series length.**
     *
     * 1. The LAST bucket in [series] (oldest-first, so the most recent one) is always today's -
     *    or, for [ActivityAggregator.Period.DAY]'s hourly buckets, the current hour's - and is
     *    still filling: the window's own end is "now", not "the end of the current bucket". A
     *    user checking at 09:00 having spent $1 of a normal $4/day habit must not be told "$1.00
     *    /day" - that number would also change every hour, on the tab's own DEFAULT view. It is
     *    dropped from the sum entirely, never averaged in.
     * 2. The denominator is `period.days - 1` (the window's calendar days MINUS the one still-
     *    filling day dropped above) - a value this function already KNOWS from [period], not one
     *    inferred from `series.size`. Inferring it from the series length would silently assume
     *    the server returns one row per calendar day; this project's own fixture disproves that
     *    (`analytics-query-response.json` has no row at all for a model/day combination with zero
     *    usage) - a server that omits zero-spend days would otherwise turn "$1 spent on one day
     *    of a 30-day period" into a reported "$30.00/day, 1 day left" instead of the true
     *    "$1.00/day, ~30 days left". Dividing by the KNOWN day count instead treats each omitted
     *    day as the zero spend it actually was, exactly as [toSpendSeries]'s own "absent counts as
     *    nothing recorded" contract already implies.
     *
     * For [ActivityAggregator.Period.DAY], `period.days - 1 == 0`: there are no completed days in
     * a 24-hour (or, since fix round 2, single-calendar-day) window at all, so this returns `null`
     * unconditionally - correctly hiding the days-left row rather than dividing by zero or
     * reporting a rate computed from nothing but still-filling data.
     *
     * `null`, never `0.0`, for an empty [series] - because no data at all (e.g. [toSpendSeries]
     * could not find a bucket key) is a genuinely unknown rate, not a confidently observed zero;
     * [BalanceBlock] already renders a `null` `perDay` as an em dash and hides the days-left row
     * for exactly this reason. A NON-empty [series] whose only entry is the dropped still-filling
     * bucket (nothing recorded on any completed day) legitimately averages to `0.0`, not `null` -
     * the server's own silence about the completed days IS the answer "nothing was spent then".
     */
    fun burnRatePerDay(series: List<Double>, period: ActivityAggregator.Period): Double? {
        val completedDays = period.days - 1
        if (completedDays <= 0 || series.isEmpty()) return null
        return series.dropLast(1).sum() / completedDays
    }
}
