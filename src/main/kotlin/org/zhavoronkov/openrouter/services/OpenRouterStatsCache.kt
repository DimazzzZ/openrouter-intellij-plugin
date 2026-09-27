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

        // Everything this cache fetches - credits, activity, the key list - is Management-Key-only,
        // so without one there is nothing to go and get. Refusing here is not a restriction, it is
        // the honest answer: firing three requests that can only answer 403 would spend the user's
        // rate limit to learn what the missing key already told us.
        if (settingsService.getProvisioningKey().isBlank()) {
            PluginLogger.Service.debug("Stats cache: No Management Key, skipping refresh")
            lastError = "Management Key required"
            return "Management Key required"
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
        @Suppress("TooGenericExceptionCaught") // Intentional: isolate provider failures
        try {
            BalanceProviderNotifier.getInstanceOrNull()?.notifyLoading()
        } catch (e: Throwable) {
            PluginLogger.Service.debug("Failed to notify balance providers of loading: ${e.message}")
        }
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
    @Suppress("TooGenericExceptionCaught") // Intentional: isolate provider failures from main plugin
    private fun pushToBalanceProviders(credits: CreditsData, activity: List<ActivityData>?) {
        try {
            val notifier = BalanceProviderNotifier.getInstanceOrNull() ?: return

            val balanceData = BalanceData(
                totalCredits = credits.totalCredits,
                totalUsage = credits.totalUsage,
                remainingCredits = credits.totalCredits - credits.totalUsage,
                timestamp = System.currentTimeMillis(),
                todayUsage = calculateTodayUsage(activity)
            )

            notifier.notifyBalanceUpdated(balanceData)
        } catch (e: Throwable) {
            // Don't let balance provider failures affect the main plugin
            PluginLogger.Service.debug("Failed to notify balance providers: ${e.message}")
        }
    }

    /**
     * Calculates today's usage from activity data.
     *
     * @param activity The activity data list, may be null
     * @return Today's usage in USD, or null if not available
     */
    private fun calculateTodayUsage(activity: List<ActivityData>?): Double? {
        if (activity.isNullOrEmpty()) return null

        val today = LocalDate.now().toString() // Format: YYYY-MM-DD

        val todayUsage = activity
            .filter { it.date == today }
            .sumOf { it.usage ?: 0.0 }

        return if (todayUsage > 0) todayUsage else null
    }

    private fun notifyError(message: String) {
        val application = ApplicationManager.getApplication()
        application?.invokeLater {
            application.messageBus
                .syncPublisher(OpenRouterStatsListener.TOPIC)
                .onStatsError(message)
        }

        // Also notify balance providers
        @Suppress("TooGenericExceptionCaught") // Intentional: isolate provider failures
        try {
            BalanceProviderNotifier.getInstanceOrNull()?.notifyError(message)
        } catch (e: Throwable) {
            PluginLogger.Service.debug("Failed to notify balance providers of error: ${e.message}")
        }
    }

    override fun dispose() {
        clearCache()
        PluginLogger.Service.info("OpenRouterStatsCache disposed")
    }
}
