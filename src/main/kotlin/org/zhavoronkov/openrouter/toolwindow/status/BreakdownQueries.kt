package org.zhavoronkov.openrouter.toolwindow.status

import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.AnalyticsService
import java.time.LocalDate

/**
 * Runs the Status tab's two analytics queries and answers with *data*, never with rendering.
 *
 * Split out of [StatusTabPanel] because issuing a query, mapping its rows and deciding what a
 * fruitless answer actually means is a different job from painting a panel - and because that
 * panel had reached detekt's `TooManyFunctions` ceiling doing both. Shaving functions to fit was
 * rejected; so was moving the breakdown block in here, which would only have relocated the
 * coupling: the block is driven by every state render and both selectors, not just by queries.
 *
 * This type therefore takes **only** an [AnalyticsService]. It holds no Swing reference, no cache
 * and no panel state, which is also what makes it testable under the fast headless `test` task
 * against a `MockWebServer` rather than needing a platform runner.
 */
class BreakdownQueries(private val analyticsService: AnalyticsService) {

    /**
     * What one breakdown query produced. [Empty] and [MissingNames] are deliberately distinct:
     * "the server checked and you spent nothing" and "this build asked for a metric the server no
     * longer reports" are different facts, and only one of them is about the user's spending.
     */
    sealed interface Breakdown {
        /** Rows came back. [truncated] mirrors the server's own `metadata.truncated`. */
        data class Rows(
            val rows: List<ActivityAggregator.ModelSpend>,
            val truncated: Boolean
        ) : Breakdown

        /** The query succeeded and genuinely reported nothing for the period. */
        data class Empty(val truncated: Boolean) : Breakdown

        /** Names this build hard-codes that `/analytics/meta` no longer lists. */
        data class MissingNames(val names: List<String>) : Breakdown

        /** The query failed and the metadata check could not explain why. */
        data object Failed : Breakdown
    }

    /** One spend-series query's output: the series itself and the rate derived from it. */
    data class SpendSeries(val series: List<Double>, val perDay: Double?)

    /**
     * Queries the breakdown for [period] grouped by [dimension].
     *
     * The metadata check runs **only** when nothing useful came back - rows are themselves proof
     * the hard-coded names are still valid, so checking first would put a second round trip on the
     * critical path of every successful load. Both the empty and the failed branch consult it,
     * because a server asked for a retired metric may answer either way.
     */
    suspend fun breakdown(
        period: ActivityAggregator.Period,
        dimension: AnalyticsBreakdown.Dimension,
        today: LocalDate
    ): Breakdown {
        val result = analyticsService.query(AnalyticsBreakdown.requestFor(period, today, dimension))

        if (result is ApiResult.Success) {
            val rows = AnalyticsBreakdown.toModelSpend(result.data.data, dimension)
            if (rows.isNotEmpty()) {
                return Breakdown.Rows(rows, result.data.metadata?.truncated == true)
            }
        }

        val metaCheck = when (val meta = analyticsService.meta()) {
            is ApiResult.Success -> AnalyticsMetaCheck.run(meta.data)
            is ApiResult.Error -> AnalyticsMetaCheck.Result.CouldNotCheck
        }
        if (metaCheck is AnalyticsMetaCheck.Result.Missing) {
            return Breakdown.MissingNames(metaCheck.missingNames)
        }

        return when (result) {
            is ApiResult.Success -> Breakdown.Empty(result.data.metadata?.truncated == true)
            is ApiResult.Error -> Breakdown.Failed
        }
    }

    /**
     * Queries the account-wide spend series for [period], returning `null` when the query fails.
     *
     * A null is "we learned nothing", not "spend was zero": the caller leaves whatever it already
     * had on screen rather than replacing a real series with an invented flat one.
     */
    suspend fun spendSeries(period: ActivityAggregator.Period, today: LocalDate): SpendSeries? =
        when (val result = analyticsService.query(AnalyticsBreakdown.spendSeriesRequestFor(period, today))) {
            is ApiResult.Success -> {
                val series = AnalyticsBreakdown.toSpendSeries(result.data.data)
                SpendSeries(series, AnalyticsBreakdown.burnRatePerDay(series, period))
            }

            is ApiResult.Error -> null
        }
}
