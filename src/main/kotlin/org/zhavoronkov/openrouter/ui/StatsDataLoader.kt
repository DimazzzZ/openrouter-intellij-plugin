package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Handles loading of statistics data from OpenRouter API
 */
class StatsDataLoader(
    private val settingsService: OpenRouterSettingsService?,
    private val openRouterService: OpenRouterService?
) {

    companion object {
        private const val ERROR_MESSAGE = "Failed to load data"
    }

    data class StatsData(
        val apiKeysResponse: ApiKeysListResponse,
        val creditsResponse: CreditsResponse,
        val activityResponse: ActivityResponse?
    )

    sealed class LoadResult {
        data class Success(val data: StatsData) : LoadResult()
        data class Error(val message: String) : LoadResult()
        object NotConfigured : LoadResult()
        object ProvisioningKeyMissing : LoadResult()
    }

    fun loadData(onResult: (LoadResult) -> Unit) {
        PluginLogger.Service.debug("StatsDataLoader.loadData() called")
        when {
            settingsService == null || openRouterService == null -> {
                PluginLogger.Service.warn("Services are null: settings=$settingsService, router=$openRouterService")
                onResult(LoadResult.Error(ERROR_MESSAGE))
            }
            !settingsService.isConfigured() -> {
                PluginLogger.Service.warn("Settings not configured")
                onResult(LoadResult.NotConfigured)
            }
            settingsService.getProvisioningKey().isBlank() -> {
                PluginLogger.Service.warn("Provisioning key is blank")
                onResult(LoadResult.ProvisioningKeyMissing)
            }
            else -> {
                PluginLogger.Service.debug("Starting async data load")
                loadDataAsync(openRouterService, onResult)
            }
        }
    }

    private fun loadDataAsync(routerService: OpenRouterService, onResult: (LoadResult) -> Unit) {
        PluginLogger.Service.debug("loadDataAsync() starting")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                PluginLogger.Service.debug("Launching async API calls")
                // Fetch API keys, credits, and activity concurrently
                val apiKeysDeferred = async { routerService.getApiKeysList() }
                val creditsDeferred = async { routerService.getCredits() }
                val activityDeferred = async { routerService.getActivity() }

                PluginLogger.Service.debug("Waiting for API results")
                val apiKeysResult = apiKeysDeferred.await()
                PluginLogger.Service.debug("API keys result: ${apiKeysResult::class.simpleName}")
                val creditsResult = creditsDeferred.await()
                PluginLogger.Service.debug("Credits result: ${creditsResult::class.simpleName}")
                val activityResult = activityDeferred.await()
                PluginLogger.Service.debug("Activity result: ${activityResult::class.simpleName}")

                PluginLogger.Service.debug("Invoking UI update on EDT")
                invokeOnEdtOrDirect {
                    handleApiResults(apiKeysResult, creditsResult, activityResult, onResult)
                }
            } catch (e: TimeoutCancellationException) {
                logLoadFailure("Quota info loading timeout", e)
                invokeErrorOnEdt(onResult)
            } catch (e: IOException) {
                logLoadFailure("Quota info network error", e)
                invokeErrorOnEdt(onResult)
            } catch (expectedError: Exception) {
                logLoadFailure("Failed to load quota info", expectedError)
                invokeErrorOnEdt(onResult)
            }
        }
    }

    private fun handleApiResults(
        apiKeysResult: ApiResult<ApiKeysListResponse>,
        creditsResult: ApiResult<CreditsResponse>,
        activityResult: ApiResult<ActivityResponse>,
        onResult: (LoadResult) -> Unit
    ) {
        PluginLogger.Service.debug("EDT callback executing")
        when {
            apiKeysResult is ApiResult.Success && creditsResult is ApiResult.Success -> {
                PluginLogger.Service.debug("Both API keys and credits succeeded")
                val activityData = if (activityResult is ApiResult.Success) {
                    activityResult.data
                } else {
                    null
                }
                onResult(
                    LoadResult.Success(
                        StatsData(
                            apiKeysResponse = apiKeysResult.data,
                            creditsResponse = creditsResult.data,
                            activityResponse = activityData
                        )
                    )
                )
            }
            apiKeysResult is ApiResult.Error -> {
                PluginLogger.Service.error("Failed to load API keys: ${apiKeysResult.message}")
                onResult(LoadResult.Error("Failed to load API keys: ${apiKeysResult.message}"))
            }
            creditsResult is ApiResult.Error -> {
                PluginLogger.Service.error("Failed to load credits: ${creditsResult.message}")
                onResult(LoadResult.Error("Failed to load credits: ${creditsResult.message}"))
            }
            else -> {
                PluginLogger.Service.error("Unknown error loading stats data")
                onResult(LoadResult.Error(ERROR_MESSAGE))
            }
        }
    }

    private fun invokeErrorOnEdt(onResult: (LoadResult) -> Unit) {
        invokeOnEdtOrDirect {
            onResult(LoadResult.Error(ERROR_MESSAGE))
        }
    }

    private fun invokeOnEdtOrDirect(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            application.invokeLater(action, ModalityState.any())
        } else {
            PluginLogger.Service.debug(
                "Application not available; invoking stats handler directly"
            )
            action()
        }
    }

    private fun logLoadFailure(message: String, throwable: Throwable) {
        if (ApplicationManager.getApplication() == null) {
            PluginLogger.Service.warn(message, throwable)
        } else {
            PluginLogger.Service.error(message, throwable)
        }
    }
}
