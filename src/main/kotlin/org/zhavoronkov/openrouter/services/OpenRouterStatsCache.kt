package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.api.BalanceData
import org.zhavoronkov.openrouter.listeners.OpenRouterStatsListener
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.KeyData
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.applicationServiceOrNull
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared cache service for OpenRouter statistics data.
 *
 * This service provides a single source of truth for stats data (credits, activity)
 * that can be consumed by multiple UI components (status bar widget, stats popup, etc.).
 *
 * When data is refreshed, all listeners subscribed to [OpenRouterStatsListener.TOPIC]
 * will be notified, ensuring UI consistency across all components.
 */
/**
 * @param settingsServiceOverride injected collaborator; when absent the application service is
 *  resolved on demand, exactly as before. Mirrors [FavoriteModelsService], which carries the same
 *  kind of override for the same reason.
 * @param openRouterServiceOverride as [settingsServiceOverride], for the API client.
 * @param scope where [refresh] launches its work. Injectable so a caller can await the refresh
 *  instead of polling [isLoading].
 *
 * Every parameter defaults to what this class used to build or resolve itself, so the
 * no-argument construction the platform does when instantiating the service is unchanged.
 */
@Service(Service.Level.APP)
@Suppress("TooManyFunctions")
class OpenRouterStatsCache(
    private val settingsServiceOverride: OpenRouterSettingsService? = null,
    private val openRouterServiceOverride: OpenRouterService? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Disposable {

    companion object {
        /** What [activity] reports spent on [today], in USD, or null when it reports nothing for it. */
        internal fun todayUsage(activity: List<ActivityData>?, today: LocalDate): Double? {
            val spent = activity.orEmpty().filter { it.date == today.toString() }.sumOf { it.usage ?: 0.0 }
            return spent.takeIf { it > 0 }
        }

        fun getInstance(): OpenRouterStatsCache {
            return ApplicationManager.getApplication().getService(OpenRouterStatsCache::class.java)
        }
    }

    // Cached data
    @Volatile
    private var cachedCredits: CreditsData? = null

    @Volatile
    private var cachedActivity: List<ActivityData>? = null

    @Volatile
    private var cachedApiKeys: ApiKeysListResponse? = null

    @Volatile
    private var lastError: String? = null

    /**
     * Why there is nothing to fetch, when that is a property of the configuration rather than a
     * failure. Distinct from [lastError] because the two mean opposite things to a user: one says
     * something went wrong, the other says this setup does less - see [notifyUnavailable].
     */
    @Volatile
    private var unavailableReason: String? = null

    /**
     * What `GET /api/v1/key` says about the key currently configured, or null until it is read.
     *
     * Kept here rather than fetched by whoever needs it, for the same reason everything else in
     * this class is: the status bar, the stats popup and the Status tab must never show different
     * numbers for the same thing.
     */
    @Volatile
    private var currentKeyInfo: KeyData? = null

    @Volatile
    private var lastUpdateTimestamp: Long = 0

    private val isLoading = AtomicBoolean(false)

    /** Returns the cached credits data, or null if not yet loaded. */
    fun getCachedCredits(): CreditsData? = cachedCredits

    /** Returns the cached activity data, or null if not yet loaded. */
    fun getCachedActivity(): List<ActivityData>? = cachedActivity

    /** Returns the cached API keys response, or null if not yet loaded. */
    fun getCachedApiKeys(): ApiKeysListResponse? = cachedApiKeys

    /** Returns the last error message, or null if no error. */
    fun getLastError(): String? = lastError

    /** Why account data is unavailable in this configuration, or null when it is available. */
    fun getUnavailableReason(): String? = unavailableReason

    /** The configured key's own usage and spend cap, or null until [refreshCurrentKey] answers. */
    fun getCurrentKeyInfo(): KeyData? = currentKeyInfo

    /**
     * Reads the configured key's own usage and spend cap.
     *
     * Separate from [refresh] because it needs no Management Key: `GET /api/v1/key` describes the
     * key making the request, so an ordinary API key can read its own cap and spend. `GET /keys` -
     * the whole account's key list - is the Management-Key-only one, and conflating the two is why
     * the Status tab told people their own key's spend cap "Needs a Management Key".
     */
    fun refreshCurrentKey(): Job? {
        val settingsService = getSettingsServiceSafely() ?: return null
        val apiKey = settingsService.getApiKey().ifBlank { settingsService.getProvisioningKey() }
        if (apiKey.isBlank()) return null

        val service = getOpenRouterServiceSafely() ?: return null
        return scope.launch {
            when (val result = service.fetchKeyInfo(apiKey)) {
                is ApiResult.Success -> {
                    currentKeyInfo = result.data.data
                    PluginLogger.Service.debug("Stats cache: current key info refreshed")
                }
                is ApiResult.Error -> PluginLogger.Service.debug(
                    "Stats cache: could not read the current key: ${result.message}"
                )
            }
        }
    }

    /** Returns the timestamp of the last successful update. */
    fun getLastUpdateTimestamp(): Long = lastUpdateTimestamp

    /** Returns whether data is currently being loaded. */
    fun isLoading(): Boolean = isLoading.get()

    /** Checks if there is valid cached data available. */
    fun hasCachedData(): Boolean = cachedCredits != null

    /**
     * Refreshes the stats data from the OpenRouter API.
     * Notifies all listeners when data is available.
     */
    @Suppress("ReturnCount")
    fun refresh(): Job? {
        PluginLogger.Service.info("Stats cache: refresh() called")

        val unavailable = unavailableInThisConfiguration()
        if (unavailable != null) {
            PluginLogger.Service.debug("Stats cache: nothing to fetch - $unavailable")
            notifyUnavailable(unavailable)
            return null
        }

        val validationResult = validateRefreshPreconditions()
        if (validationResult != null) {
            PluginLogger.Service.warn("Stats cache: Validation failed: $validationResult")
            notifyError(validationResult)
            return null
        }

        // Prevent concurrent refreshes
        if (!isLoading.compareAndSet(false, true)) {
            PluginLogger.Service.debug("Stats cache: Already loading, skipping refresh")
            return null
        }

        PluginLogger.Service.info("Stats cache: Starting refresh - launching coroutine")
        notifyLoading()

        return scope.launch {
            PluginLogger.Service.info("Stats cache: Coroutine started")
            executeRefresh()
            PluginLogger.Service.info("Stats cache: Coroutine completed")
        }
    }

    /**
     * Guards [refresh] on configuration alone, not on capability.
     *
     * Measured against the live API (2026-09-21): `/credits` answers for an ordinary API key,
     * so refusing to refresh at all without a management key would withhold a balance the key
     * in hand can actually read. `isConfigured()` (an API key is present) is the only precondition
     * now; which of credits/activity/keys a refresh can actually fetch is decided per-endpoint by
     * [fetchAndProcessData]/[processResults] below, each of which already treats its own result as
     * optional rather than failing the whole refresh.
     */
    @Suppress("ReturnCount")
    private fun validateRefreshPreconditions(): String? {
        val settingsService = getSettingsServiceSafely()
            ?: return "Settings service not available"

        if (!settingsService.isConfigured()) {
            PluginLogger.Service.debug("Stats cache: Not configured, skipping refresh")
            lastError = "Not configured"
            return "Not configured"
        }

        return null
    }

    private fun getSettingsServiceSafely(): OpenRouterSettingsService? =
        settingsServiceOverride ?: applicationServiceOrNull(OpenRouterSettingsService::class.java)

    private fun getOpenRouterServiceSafely(): OpenRouterService? =
        openRouterServiceOverride ?: applicationServiceOrNull(OpenRouterService::class.java)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRefresh() {
        PluginLogger.Service.info("Stats cache: executeRefresh() started")
        val openRouterService = getOpenRouterServiceSafely()
        if (openRouterService == null) {
            PluginLogger.Service.warn("Stats cache: OpenRouter service is null")
            isLoading.set(false)
            notifyError("OpenRouter service not available")
            return
        }

        try {
            PluginLogger.Service.info("Stats cache: calling fetchAndProcessData")
            fetchAndProcessData(openRouterService)
            PluginLogger.Service.info("Stats cache: fetchAndProcessData completed")
        } catch (e: IOException) {
            PluginLogger.Service.error("Stats cache: IOException during refresh", e)
            handleRefreshError("Network error: ${e.message}")
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Stats cache: IllegalStateException during refresh", e)
            handleRefreshError("Error: ${e.message}")
        } catch (e: IllegalArgumentException) {
            PluginLogger.Service.error("Stats cache: IllegalArgumentException during refresh", e)
            handleRefreshError("Invalid argument: ${e.message}")
        } catch (e: RuntimeException) {
            // Catch remaining runtime exceptions (including NPE) to prevent crashes
            PluginLogger.Service.error("Stats cache: RuntimeException during refresh", e)
            handleRefreshError("Unexpected error: ${e.message}")
        } finally {
            isLoading.set(false)
            PluginLogger.Service.info("Stats cache: executeRefresh() finished")
        }
    }

    private suspend fun fetchAndProcessData(openRouterService: OpenRouterService) {
        // Fetch data in parallel, as children of THIS coroutine rather than of [scope]: cancelling
        // the job `refresh()` handed back must cancel the three requests it started, and children
        // attached to the outer scope would outlive it and keep writing into the cache.
        val (creditsResult, activityResult, apiKeysResult) = coroutineScope {
            val creditsDeferred = async { openRouterService.getCredits() }
            val activityDeferred = async { openRouterService.getActivity() }
            val apiKeysDeferred = async { openRouterService.getApiKeysList() }

            Triple(creditsDeferred.await(), activityDeferred.await(), apiKeysDeferred.await())
        }

        processResults(creditsResult, activityResult, apiKeysResult)
    }

    private fun processResults(
        creditsResult: ApiResult<*>,
        activityResult: ApiResult<*>,
        apiKeysResult: ApiResult<*>
    ) {
        when (creditsResult) {
            is ApiResult.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val creditsResponse = creditsResult.data as org.zhavoronkov.openrouter.models.CreditsResponse
                cachedCredits = creditsResponse.data
                lastError = null
                lastUpdateTimestamp = System.currentTimeMillis()

                processOptionalResults(activityResult, apiKeysResult)

                PluginLogger.Service.debug("Stats cache: Refresh successful")
                notifySuccess(cachedCredits!!, cachedActivity)
            }
            is ApiResult.Error -> {
                lastError = creditsResult.message
                PluginLogger.Service.warn("Stats cache: Failed to load credits: ${creditsResult.message}")
                notifyError(creditsResult.message)
            }
        }
    }

    private fun processOptionalResults(activityResult: ApiResult<*>, apiKeysResult: ApiResult<*>) {
        // Activity is optional
        cachedActivity = when (activityResult) {
            is ApiResult.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val activityResponse = activityResult.data as org.zhavoronkov.openrouter.models.ActivityResponse
                activityResponse.data
            }
            is ApiResult.Error -> {
                PluginLogger.Service.warn("Stats cache: Failed to load activity: ${activityResult.message}")
                null
            }
        }

        // API keys are optional
        cachedApiKeys = when (apiKeysResult) {
            is ApiResult.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                apiKeysResult.data as ApiKeysListResponse
            }
            is ApiResult.Error -> {
                PluginLogger.Service.warn("Stats cache: Failed to load API keys: ${apiKeysResult.message}")
                null
            }
        }
    }

    private fun handleRefreshError(message: String) {
        lastError = message
        PluginLogger.Service.warn("Stats cache: $message")
        notifyError(message)
    }

    /** Clears the cached data. */
    fun clearCache() {
        cachedCredits = null
        cachedActivity = null
        cachedApiKeys = null
        lastError = null
        lastUpdateTimestamp = 0
        PluginLogger.Service.debug("Stats cache: Cleared")
    }

    /**
     * Updates the cache with data loaded by the popup dialog.
     * This allows the popup to share its loaded data with other listeners
     * (like the status bar widget) without making duplicate API calls.
     */
    fun updateFromPopup(
        creditsResponse: org.zhavoronkov.openrouter.models.CreditsResponse,
        activityResponse: org.zhavoronkov.openrouter.models.ActivityResponse?,
        apiKeysResponse: ApiKeysListResponse
    ) {
        cachedCredits = creditsResponse.data
        cachedActivity = activityResponse?.data
        cachedApiKeys = apiKeysResponse
        lastError = null
        lastUpdateTimestamp = System.currentTimeMillis()

        PluginLogger.Service.debug("Stats cache: Updated from popup")
        // Only notify if ApplicationManager is available (not in unit tests)
        if (ApplicationManager.getApplication() != null) {
            notifySuccess(creditsResponse.data, activityResponse?.data)
        }
    }

    private fun notifyLoading() {
        val application = ApplicationManager.getApplication()
        application?.invokeLater {
            application.messageBus
                .syncPublisher(OpenRouterStatsListener.TOPIC)
                .onStatsLoading()
        }

        // Also notify balance providers
        toBalanceProviders("loading") { it.notifyLoading() }
    }

    private fun notifySuccess(credits: CreditsData, activity: List<ActivityData>?) {
        val application = ApplicationManager.getApplication()
        application?.invokeLater {
            application.messageBus
                .syncPublisher(OpenRouterStatsListener.TOPIC)
                .onStatsUpdated(credits, activity)
        }

        // Push to extension point providers (balance sharing with other plugins)
        pushToBalanceProviders(credits, activity)
    }

    /**
     * Pushes balance data to registered BalanceProvider implementations.
     *
     * This allows other plugins (like TokenPulse) to receive real-time balance updates
     * without needing direct access to OpenRouter's internal services.
     *
     * The push happens on a background thread to avoid blocking the UI,
     * and exceptions from individual providers are isolated.
     */
    private fun pushToBalanceProviders(credits: CreditsData, activity: List<ActivityData>?) =
        toBalanceProviders("update") { notifier ->
            notifier.notifyBalanceUpdated(
                BalanceData(
                    totalCredits = credits.totalCredits,
                    totalUsage = credits.totalUsage,
                    remainingCredits = credits.totalCredits - credits.totalUsage,
                    timestamp = System.currentTimeMillis(),
                    todayUsage = todayUsage(activity, LocalDate.now())
                )
            )
        }

    /**
     * Hands [notify] the balance providers' notifier, when there is one, and never lets a
     * failure reach the cache. The notifier already isolates each provider and its own lookup,
     * so this is a second guard behind it.
     */
    @ExcludeFromCoverage("a second guard behind BalanceProviderNotifier's own, which nothing gets past")
    @Suppress("TooGenericExceptionCaught") // Intentional: isolate provider failures from main plugin
    private fun toBalanceProviders(what: String, notify: (BalanceProviderNotifier) -> Unit) {
        try {
            BalanceProviderNotifier.getInstanceOrNull()?.let(notify)
        } catch (e: Throwable) {
            PluginLogger.Service.debug("Failed to notify balance providers ($what): ${e.message}")
        }
    }

    /**
     * Reports that this configuration has no account data to show, which is not a failure.
     *
     * Everything this cache fetches - credits, activity, the key list - is Management-Key-only, so
     * an ordinary API key has nothing to go and get. That is a supported way to run the plugin:
     * chat and the proxy work, only monitoring does not. Sending it down the error channel put
     * "Status: Error" in the status bar for a setup that works, directly above the same menu's
     * own, correct "Monitoring Disabled".
     */
    private fun notifyUnavailable(reason: String) {
        unavailableReason = reason
        lastError = null

        val application = ApplicationManager.getApplication()
        application?.invokeLater {
            application.messageBus
                .syncPublisher(OpenRouterStatsListener.TOPIC)
                .onStatsUnavailable(reason)
        }
    }

    /**
     * Why account data cannot be fetched in this configuration, or null when it can.
     *
     * Separate from [validateRefreshPreconditions] because the answers differ in kind: that one
     * reports things that are wrong, this one reports what this setup simply does not include.
     */
    private fun unavailableInThisConfiguration(): String? {
        val settingsService = getSettingsServiceSafely() ?: return null
        if (!settingsService.isConfigured()) return null

        // Firing three requests that can only answer 403 would spend the user's rate limit to
        // learn what the missing key already told us.
        return if (settingsService.getProvisioningKey().isBlank()) "Management Key required" else null
    }

    private fun notifyError(message: String) {
        val application = ApplicationManager.getApplication()
        application?.invokeLater {
            application.messageBus
                .syncPublisher(OpenRouterStatsListener.TOPIC)
                .onStatsError(message)
        }

        // Also notify balance providers
        toBalanceProviders("error") { it.notifyError(message) }
    }

    override fun dispose() {
        clearCache()
        PluginLogger.Service.info("OpenRouterStatsCache disposed")
    }
}
