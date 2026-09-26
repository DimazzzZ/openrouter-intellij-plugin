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
    private val provisioningKeyProvider: () -> String
) {

    constructor() : this(
        baseUrlOverride = null,
        provisioningKeyProvider = { OpenRouterSettingsService.getInstance().getProvisioningKey() }
    )

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(OpenRouterConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(OpenRouterConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(OpenRouterConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // ConcurrentHashMap, not a synchronized block: query() reads it, .onSuccess writes
    // it and invalidate() clears it, all on Dispatchers.IO, and the Status tab is
    // specified to fire a period query while a refresh is in flight. A benign put/put
    // race that recomputes one query is acceptable; a corrupted map is not. Same
    // precedent as OpenRouterProxyService's activeTasks map.
    private val queryCache = ConcurrentHashMap<AnalyticsQueryRequest, AnalyticsQueryPayload>()

    private fun getBaseUrl(): String = baseUrlOverride ?: OpenRouterConstants.BASE_URL
    private fun getAnalyticsQueryEndpoint() = "${getBaseUrl()}/analytics/query"
    private fun getAnalyticsMetaEndpoint() = "${getBaseUrl()}/analytics/meta"

    /**
     * Whether a provisioning (management) key is configured. `query()` and `meta()`
     * both require this to be true and short-circuit before touching the network
     * when it is not.
     */
    fun isAvailable(): Boolean = provisioningKeyProvider().isNotBlank()

    /**
     * Clears the query cache. Called when the Status tab refreshes deliberately,
     * so a stale answer for a previously-seen period is never served silently.
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
                return@withContext ApiResult.Error("No provisioning key configured")
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
     */
    suspend fun meta(): ApiResult<AnalyticsMeta> = withContext(Dispatchers.IO) {
        val provisioningKey = provisioningKeyProvider()
        if (provisioningKey.isBlank()) {
            PluginLogger.Service.warn("No provisioning key available for analytics meta")
            return@withContext ApiResult.Error("No provisioning key configured")
        }

        try {
            val httpRequest = OpenRouterRequestBuilder.buildGetRequest(
                url = getAnalyticsMetaEndpoint(),
                authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                authToken = provisioningKey
            )

            val response = client.newCall(httpRequest).await()
            response.toApiResult<AnalyticsMetaResponse>(gson).map { it.data }
        } catch (e: IOException) {
            PluginLogger.Service.warn("Error fetching analytics meta: ${e.message}")
            ApiResult.Error(message = e.message ?: "Network error", throwable = e)
        }
    }
}
