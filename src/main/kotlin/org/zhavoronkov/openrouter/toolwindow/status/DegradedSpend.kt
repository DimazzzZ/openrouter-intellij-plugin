package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Turns the plugin's own locally-recorded credit snapshots into the per-interval spend series
 * DEGRADED mode's sparkline draws, instead of the analytics API's server-side numbers.
 *
 * The only data that survives without a provisioning key is `CreditUsageHistoryService`'s history
 * of `(timestampUtc, totalUsed)` snapshots, taken periodically while the IDE runs (see its own
 * KDoc). `totalUsed` is CUMULATIVE - feeding it straight into a sparkline draws a line that only
 * ever rises, which reads as steadily growing spend no matter what actually happened. What the
 * chart needs is the per-interval DIFFERENCE between consecutive snapshots, which is what
 * [spendSeries] computes.
 *
 * Takes `(timestampUtc, totalUsed)` pairs rather than the service's own snapshot type so this
 * module stays free of platform and service types - the same reasoning as [SparklineGeometry] and
 * [KeyLimit] - and runs in the fast headless `test` task.
 */
object DegradedSpend {

    /**
     * @param snapshots `(timestampUtc, totalUsed)` pairs, in any order
     * @return the non-negative difference between each snapshot and the one before it, oldest
     *   first, or an empty list when there are fewer than two snapshots to difference. A credit
     *   top-up lowers `totalUsed`, which would otherwise render as negative spend; that interval is
     *   clamped to zero rather than reported as income. The input is sorted by timestamp first -
     *   the stored list is never assumed to already be in order.
     */
    fun spendSeries(snapshots: List<Pair<Long, Double>>): List<Double> {
        val sorted = snapshots.sortedBy { it.first }
        if (sorted.size < MINIMUM_SNAPSHOTS_TO_DIFFERENCE) return emptyList()

        return sorted.zipWithNext { previous, current ->
            (current.second - previous.second).coerceAtLeast(0.0)
        }
    }

    private const val MINIMUM_SNAPSHOTS_TO_DIFFERENCE = 2
}
