package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Builds the breakdown row's tooltip text: the full, un-ellipsised model id plus its request
 * count for the row's period.
 *
 * [BreakdownBlock.ModelNameLabel] already sets its own `toolTipText` to the full model id, so a
 * middle-ellipsised label (see [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis])
 * stays readable in full. This EXTENDS that tooltip rather than replacing it or adding a second,
 * competing one on the same row: [forRow]'s result always starts with [modelId] verbatim, so
 * anything that could recover the full id from the old tooltip can still recover it from the new
 * one by reading up to the separator.
 *
 * The period itself is deliberately NOT restated per row: [BreakdownBlock]'s period selector
 * already names it once for the whole list ("24 hours" / "7 days" / "30 days"), and every row
 * shown under it is already scoped to that same query - repeating it here would only echo what
 * the block already states once.
 *
 * Pure: only the Kotlin stdlib, so this runs in the fast headless `test` task rather than needing
 * a platform runner - same precedent as [KeyLimit] and [AnalyticsBreakdown].
 */
object BreakdownRowTooltip {

    /**
     * @param modelId the row's full, un-ellipsised model id.
     * @param requests [ActivityAggregator.ModelSpend.requests] for the row's period. Singular
     *   "request" for exactly `1`, plural "requests" for every other count including `0` - a bare
     *   number beside a model id ("31") reads as ambiguous, so this always names the unit.
     */
    fun forRow(modelId: String, requests: Long): String {
        val noun = if (requests == 1L) "request" else "requests"
        return "$modelId — $requests $noun"
    }
}
