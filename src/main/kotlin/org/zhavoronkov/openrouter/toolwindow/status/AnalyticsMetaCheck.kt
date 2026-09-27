package org.zhavoronkov.openrouter.toolwindow.status

import org.zhavoronkov.openrouter.models.AnalyticsMeta

/**
 * Checks [AnalyticsBreakdown]'s hard-coded metric/dimension/granularity names against the
 * server's own `/analytics/meta` response - the assumption-validation half of spec correction
 * C3, deliberately scoped down from D6's literal "build queries from what the server discovers"
 * reading. Nobody has asked for a configurable metric, and constructing queries from whatever
 * the server advertises is a feature with its own design questions (what does a newly-discovered
 * metric even mean, how is a dimension the UI has no column for rendered, ...) - not a safety net.
 * This IS a safety net: fetch once, compare against what we already hard-code, and if a name is
 * gone, say so plainly instead of letting [AnalyticsBreakdown]'s query come back silently empty.
 *
 * Exactly three states, never conflated:
 * - [Result.Validated] - every required name is present. Renders nothing different.
 * - [Result.Missing] - the server answered and at least one required name is gone. This is the
 *   ONLY state allowed to change what the breakdown shows.
 * - [Result.CouldNotCheck] - the check itself could not be performed (`meta()` failed, or came
 *   back so empty it cannot be told apart from a parse failure - see [run]'s own KDoc). This must
 *   be treated exactly like [Result.Validated] by every caller: a diagnostic that cannot run must
 *   never be read as "the names are wrong". [run] never returns this for an [AnalyticsMeta] that
 *   parsed at all - callers derive it themselves from a failed `meta()` call - see
 *   [StatusTabPanel]'s own wiring - the type exists here so both callers share one vocabulary for
 *   "unable to check" instead of each inventing its own.
 *
 * Zero `com.intellij.*`/`javax.swing.*`/`java.awt.*` imports - runs in the fast headless `test`
 * task and stays OUT of the Kover exclusion list, same as [AnalyticsBreakdown] itself.
 */
object AnalyticsMetaCheck {

    sealed class Result {
        /** Every required metric, dimension and granularity name is present in the metadata. */
        object Validated : Result()

        /**
         * The metadata was read successfully but could not be told apart from "nothing parsed" -
         * see [run]'s own KDoc for why a wholly empty [AnalyticsMeta] means this, not "every name
         * removed". Callers must render exactly as if [Validated] had come back.
         */
        object CouldNotCheck : Result()

        /** [missingNames] are hard-coded names [AnalyticsBreakdown] relies on that the server's
         * metadata no longer lists, in the order metrics, then dimensions, then granularities. */
        data class Missing(val missingNames: List<String>) : Result()
    }

    /**
     * Checks [requiredMetrics]/[requiredDimensions]/[requiredGranularities] - defaulted to
     * [AnalyticsBreakdown]'s own [AnalyticsBreakdown.REQUIRED_METRICS]/
     * [AnalyticsBreakdown.REQUIRED_DIMENSIONS]/[AnalyticsBreakdown.REQUIRED_GRANULARITIES], so
     * production call sites never re-type the names themselves - against [meta]'s three lists,
     * each checked against the ONE list it belongs to.
     *
     * [Gson][com.google.gson.Gson] writes each of [AnalyticsMeta]'s three list fields as its
     * declared `emptyList()` default when the server's JSON omits the key entirely, which is
     * indistinguishable, by the time this function sees it, from "the server genuinely listed
     * zero metrics, zero dimensions AND zero granularities" - a response no real analytics API
     * would ever send. Treating that wholly-empty shape as [Result.Missing] with every hard-coded
     * name inside it would report a total wipeout the day `meta()`'s response merely fails to
     * parse as expected (a reshaped envelope, an unexpected `null` at the top level, ...) - exactly
     * the false alarm this check exists to prevent, not cause. [Result.CouldNotCheck] is returned
     * instead, for this case only: any [meta] with even ONE entry in any of the three lists is
     * treated as a real answer, and a name absent from ITS OWN list is then genuinely missing.
     */
    fun run(
        meta: AnalyticsMeta,
        requiredMetrics: Set<String> = AnalyticsBreakdown.REQUIRED_METRICS,
        requiredDimensions: Set<String> = AnalyticsBreakdown.REQUIRED_DIMENSIONS,
        requiredGranularities: Set<String> = AnalyticsBreakdown.REQUIRED_GRANULARITIES
    ): Result {
        if (meta.metrics.isEmpty() && meta.dimensions.isEmpty() && meta.granularities.isEmpty()) {
            return Result.CouldNotCheck
        }

        val knownMetrics = meta.metrics.map { it.name }.toSet()
        val knownDimensions = meta.dimensions.map { it.name }.toSet()
        val knownGranularities = meta.granularities.toSet()

        val missing = requiredMetrics.filterNot { it in knownMetrics } +
            requiredDimensions.filterNot { it in knownDimensions } +
            requiredGranularities.filterNot { it in knownGranularities }

        return if (missing.isEmpty()) Result.Validated else Result.Missing(missing)
    }
}
