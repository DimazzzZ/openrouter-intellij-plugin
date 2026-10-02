package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.models.AnalyticsMeta
import org.zhavoronkov.openrouter.models.AnalyticsMetaResponse
import org.zhavoronkov.openrouter.models.AnalyticsQueryPayload
import org.zhavoronkov.openrouter.models.AnalyticsQueryRequest
import org.zhavoronkov.openrouter.models.AnalyticsQueryResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.map
import org.zhavoronkov.openrouter.models.onSuccess
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.await
import org.zhavoronkov.openrouter.utils.toApiResult
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Access to OpenRouter's analytics API.
 *
 * Kept OUT of OpenRouterStatsCache deliberately. That cache holds one snapshot
 * per field — credits, activity, keys — and is refreshed on a timer because the
 * status bar needs it constantly. Analytics queries are parameterised by period,
 * metric and dimension, and are wanted only while the Status tab is open at a
 * chosen period. Folding them in would force the cache to become a keyed store
 * and would make the status bar fetch data nothing displays.
 *
 * This does not reintroduce the divergence the redesign removes: the rule is one
 * source per number, and analytics is data no other surface reads.
 */
class AnalyticsService internal constructor(
    private val baseUrlOverride: String?,
    private val provisioningKeyProvider: () -> String,
    private val baseUrlProvider: () -> String = { OpenRouterSettingsService.getInstance().getApiBaseUrl() },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(OpenRouterConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(OpenRouterConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(OpenRouterConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
) {

    constructor() : this(
        baseUrlOverride = null,
        provisioningKeyProvider = { OpenRouterSettingsService.getInstance().getProvisioningKey() }
    )

    private val gson = Gson()

    // ConcurrentHashMap, not a synchronized block: query() reads it, .onSuccess writes
    // it and invalidate() clears it, all on Dispatchers.IO, and the Status tab is
    // specified to fire a period query while a refresh is in flight. A benign put/put
    // race that recomputes one query is acceptable; a corrupted map is not. Same
    // precedent as OpenRouterProxyService's activeTasks map.
    private val queryCache = ConcurrentHashMap<AnalyticsQueryRequest, AnalyticsQueryPayload>()

    // Deliberately separate from queryCache and NOT cleared by invalidate() - see meta()'s and
    // invalidate()'s own KDocs. There is exactly one meta() call shape (no parameters), so a
    // single nullable field is the whole cache; a Map would imply a key that does not exist.
    // @Volatile, not a lock: the same benign put/put race queryCache's own KDoc accepts - two
    // concurrent misses both fetch and both write the same server answer, which costs one extra
    // request, never a corrupted read.
    @Volatile
    private var metaCache: AnalyticsMeta? = null

    /**
     * Resolved from the selected data region - analytics follow the region like everything else.
     */
    private fun getBaseUrl(): String = baseUrlOverride ?: baseUrlProvider()
    private fun getAnalyticsQueryEndpoint() = "${getBaseUrl()}/analytics/query"
    private fun getAnalyticsMetaEndpoint() = "${getBaseUrl()}/analytics/meta"

    /**
     * Whether a provisioning (management) key is configured. `query()` and `meta()`
     * both require this to be true and short-circuit before touching the network
     * when it is not.
     */
    fun isAvailable(): Boolean = provisioningKeyProvider().isNotBlank()

    /**
     * Clears the query cache only. Called when the Status tab refreshes deliberately,
     * so a stale answer for a previously-seen period is never served silently.
     *
     * [metaCache] is deliberately NOT cleared here: [meta] is fetched once per SESSION, not once
     * per refresh (this method is called on every deliberate refresh - see its callers) - the
     * names an API build advertises do not change while the IDE is running, and re-fetching them
     * on a timer-free tab that already minds its query budget (analytics queries spend the user's
     * quota) would be spending it on a question already answered.
     */
    fun invalidate() {
        queryCache.clear()
    }

    /**
     * Runs an analytics query, aggregated by whatever metrics/dimensions/granularity
     * the caller asked for. Results for an identical request are served from cache.
     */
    suspend fun query(request: AnalyticsQueryRequest): ApiResult<AnalyticsQueryPayload> =
        withContext(Dispatchers.IO) {
            val provisioningKey = provisioningKeyProvider()
            if (provisioningKey.isBlank()) {
                PluginLogger.Service.warn("No provisioning key available for analytics query")
                return@withContext ApiResult.Error("No management key configured")
            }

            queryCache[request]?.let { cached ->
                return@withContext ApiResult.Success(cached, HttpURLConnection.HTTP_OK)
            }

            try {
                val httpRequest = OpenRouterRequestBuilder.buildPostRequest(
                    url = getAnalyticsQueryEndpoint(),
                    jsonBody = gson.toJson(request),
                    authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                    authToken = provisioningKey
                )

                val response = client.newCall(httpRequest).await()
                response.toApiResult<AnalyticsQueryResponse>(gson)
                    .map { it.data }
                    .onSuccess { payload -> queryCache[request] = payload }
            } catch (e: IOException) {
                PluginLogger.Service.warn("Error querying analytics: ${e.message}")
                ApiResult.Error(message = e.message ?: "Network error", throwable = e)
            }
        }

    /**
     * Discovers the metrics, dimensions and granularities the analytics query
     * endpoint currently supports.
     *
     * Consumed in production by `StatusTabPanel`'s meta-validation check (spec correction C3,
     * scoped down from D6's literal "build queries from what the server discovers" reading - see
     * `AnalyticsMetaCheck`'s own KDoc): the hard-coded names in `AnalyticsBreakdown` are checked
     * against this response, not replaced by it. This method builds no query of its own from what
     * it returns.
     *
     * Cached for the life of this instance after the first successful call - a real per-SESSION
     * cache, unlike [queryCache], which [invalidate] clears on every deliberate refresh. See
     * [invalidate]'s own KDoc for why a name check must not repeat on a timer-free tab that
     * already minds its analytics query budget. Only a SUCCESS is cached; a failed call is
     * retried the next time [meta] is called, exactly like a query cache miss.
     */
    suspend fun meta(): ApiResult<AnalyticsMeta> = withContext(Dispatchers.IO) {
        val provisioningKey = provisioningKeyProvider()
        if (provisioningKey.isBlank()) {
            PluginLogger.Service.warn("No provisioning key available for analytics meta")
            return@withContext ApiResult.Error("No management key configured")
        }

        metaCache?.let { cached -> return@withContext ApiResult.Success(cached, HttpURLConnection.HTTP_OK) }

        try {
            val httpRequest = OpenRouterRequestBuilder.buildGetRequest(
                url = getAnalyticsMetaEndpoint(),
                authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                authToken = provisioningKey
            )

            val response = client.newCall(httpRequest).await()
            response.toApiResult<AnalyticsMetaResponse>(gson).map { it.data }
                .onSuccess { meta -> metaCache = meta }
        } catch (e: IOException) {
            PluginLogger.Service.warn("Error fetching analytics meta: ${e.message}")
            ApiResult.Error(message = e.message ?: "Network error", throwable = e)
        }
    }
}
