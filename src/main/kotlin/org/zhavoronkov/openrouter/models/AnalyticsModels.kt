package org.zhavoronkov.openrouter.models

import com.google.gson.annotations.SerializedName

/**
 * Types for OpenRouter's analytics API — the same aggregation their own
 * dashboard is drawn from, reached with a management (provisioning) key.
 *
 * Rows come back as loose maps keyed by the server's own names, not as a fixed
 * data class, because the documentation states the metric and dimension set
 * grows over time and should be discovered through /analytics/meta rather than
 * hard-coded. Binding rows to a fixed shape would silently drop anything new.
 */
data class AnalyticsTimeRange(
    val start: String,
    val end: String
)

data class AnalyticsOrderBy(
    val field: String,
    val direction: String
)

data class AnalyticsQueryRequest(
    val metrics: List<String>,
    val dimensions: List<String>? = null,
    val granularity: String? = null,
    @SerializedName("time_range") val timeRange: AnalyticsTimeRange,
    val limit: Int? = null,
    @SerializedName("order_by") val orderBy: AnalyticsOrderBy? = null
)

data class AnalyticsMetadata(
    @SerializedName("query_time_ms") val queryTimeMs: Long?,
    @SerializedName("row_count") val rowCount: Int?,
    val truncated: Boolean?
)

data class AnalyticsQueryPayload(
    val data: List<Map<String, Any?>>,
    val metadata: AnalyticsMetadata?
)

data class AnalyticsQueryResponse(
    val data: AnalyticsQueryPayload
)

data class AnalyticsMetric(
    val name: String,
    @SerializedName("display_label") val displayLabel: String?,
    @SerializedName("display_format") val displayFormat: String?
)

data class AnalyticsDimension(
    val name: String,
    @SerializedName("display_label") val displayLabel: String?
)

data class AnalyticsMeta(
    val metrics: List<AnalyticsMetric> = emptyList(),
    val dimensions: List<AnalyticsDimension> = emptyList(),
    val granularities: List<String> = emptyList()
)

data class AnalyticsMetaResponse(
    val data: AnalyticsMeta
)
