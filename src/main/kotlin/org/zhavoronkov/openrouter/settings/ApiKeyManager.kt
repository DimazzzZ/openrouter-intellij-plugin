package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException
import javax.swing.JTable

/**
 * Handles API key management operations for OpenRouter settings
 */
@Suppress("TooManyFunctions")
class ApiKeyManager(
    private val settingsService: OpenRouterSettingsService,
    private val openRouterService: OpenRouterService,
    private val apiKeyTable: JTable,
    private val apiKeyTableModel: ApiKeyTableModel
) {

    companion object {
        private const val API_KEY_LABEL_PREFIX = "IntelliJ Plugin"
        private const val CACHE_DURATION_MS = 60000L // 1 minute cache for API keys
    }

    private val dialogManager = ApiKeyDialogManager()
    private val intellijKeyManager = IntellijApiKeyManager(settingsService, openRouterService)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Cache for API keys to avoid redundant API calls during settings dialog initialization
    private var cachedApiKeys: List<ApiKeyInfo>? = null
    private var cacheTimestamp: Long = 0L

    fun addApiKey() {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        val label = Messages.showInputDialog(
            "Enter a label for this API key:",
            "Create API Key",
            null,
            API_KEY_LABEL_PREFIX,
            null
        ) ?: return

        if (label.isNotBlank()) {
            createApiKeyAsync(label)
        } else {
            Messages.showErrorDialog("Label cannot be empty.", "Invalid Input")
        }
    }

    private fun createApiKeyAsync(label: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = openRouterService.createApiKey(label)
                ApplicationManager.getApplication().invokeLater({
                    when (result) {
                        is ApiResult.Success -> {
                            PluginLogger.Settings.info("Successfully created API key with label: $label")
                            dialogManager.showApiKeyDialog(result.data.key, label)
                            refreshApiKeys()
                        }
                        is ApiResult.Error -> {
                            PluginLogger.Settings.error("API call failed during key creation: ${result.message}")
                            Messages.showErrorDialog(
                                "Failed to create API key: ${result.message}",
                                "API Key Creation Failed"
                            )
                        }
                    }
                }, ModalityState.any())
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state during key creation: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to create API key due to invalid state. Please try again.",
                        "API Key Creation Failed"
                    )
                }, ModalityState.any())
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error during key creation: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to create API key due to network error. Please try again.",
                        "API Key Creation Failed"
                    )
                }, ModalityState.any())
            } catch (expectedError: Exception) {
                PluginLogger.Settings.error("Exception during key creation: ${expectedError.message}", expectedError)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to create API key due to error. Please try again.",
                        "API Key Creation Failed"
                    )
                }, ModalityState.any())
            }
        }
    }

    fun removeApiKey() {
        val selectedRow = apiKeyTable.selectedRow
        if (selectedRow < 0) {
            Messages.showInfoMessage("Please select an API key to remove.", "No Selection")
            return
        }

        val apiKey = apiKeyTableModel.getApiKeyAt(selectedRow)
        if (apiKey == null) {
            Messages.showErrorDialog("Failed to get selected API key.", "Error")
            return
        }

        val result = Messages.showYesNoDialog(
            "Are you sure you want to delete the API key '${apiKey.name}'?\n\n" +
                "This action cannot be undone and will invalidate the key immediately.",
            "Confirm API Key Deletion",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            performApiKeyDeletion(apiKey, selectedRow)
        }
    }

    private fun performApiKeyDeletion(apiKey: ApiKeyInfo, selectedRow: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val deleteResult = openRouterService.deleteApiKey(apiKey.hash)
                ApplicationManager.getApplication().invokeLater({
                    when (deleteResult) {
                        is ApiResult.Success -> {
                            if (deleteResult.data.deleted) {
                                PluginLogger.Settings.info("Successfully deleted API key: ${apiKey.name}")
                                apiKeyTableModel.removeApiKey(selectedRow)
                                Messages.showInfoMessage("API key deleted successfully.", "Success")
                            } else {
                                Messages.showErrorDialog(
                                    "Failed to delete API key. Please try again.",
                                    "Deletion Failed"
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            PluginLogger.Settings.error("API call failed during key deletion: ${deleteResult.message}")
                            Messages.showErrorDialog(
                                "Failed to delete API key: ${deleteResult.message}",
                                "Deletion Failed"
                            )
                        }
                    }
                }, ModalityState.any())
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state during key deletion: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to delete API key due to error. Please try again.",
                        "Deletion Failed"
                    )
                }, ModalityState.any())
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error during key deletion: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Failed to delete API key due to error. Please try again.",
                        "Deletion Failed"
                    )
                }, ModalityState.any())
            }
        }
    }

    fun refreshApiKeys(forceRefresh: Boolean = true) {
        PluginLogger.Settings.info("========================================")
        PluginLogger.Settings.info("REFRESH BUTTON CLICKED - refreshApiKeys() called (forceRefresh: $forceRefresh)")
        PluginLogger.Settings.info("========================================")

        loadApiKeysAsync(forceRefresh)
    }

    /**
     * Loads API keys asynchronously on a background thread.
     * Updates the table model on EDT after fetching.
     */
    private fun loadApiKeysAsync(forceRefresh: Boolean) {
        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("Not configured, clearing API keys table and cache")
            apiKeyTableModel.setApiKeys(emptyList())
            clearCache()
            return
        }

        // Check cache first (unless force refresh)
        if (!forceRefresh && useCachedApiKeysIfValid()) {
            return
        }

        if (forceRefresh) {
            PluginLogger.Settings.debug("Force refresh requested, bypassing cache")
        }

        // Fetch from API on background thread
        coroutineScope.launch(Dispatchers.IO) {
            fetchAndUpdateApiKeys()
        }
    }

    /**
     * Checks if cache is valid and uses it if so.
     * @return true if cache was used, false if fetch is needed
     */
    private fun useCachedApiKeysIfValid(): Boolean {
        val now = System.currentTimeMillis()
        val cacheAge = now - cacheTimestamp
        val isCacheValid = cachedApiKeys != null && cacheAge < CACHE_DURATION_MS

        if (isCacheValid) {
            PluginLogger.Settings.debug("Using cached API keys (${cachedApiKeys?.size} keys, age: ${cacheAge}ms)")
            apiKeyTableModel.setApiKeys(cachedApiKeys!!)
            return true
        } else if (cachedApiKeys != null) {
            PluginLogger.Settings.debug(
                "Cache expired (age: ${cacheAge}ms > ${CACHE_DURATION_MS}ms), fetching fresh data"
            )
        }
        return false
    }

    /**
     * Fetches API keys from OpenRouter and updates the UI.
     * Must be called from a background thread.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchAndUpdateApiKeys() {
        try {
            val result = openRouterService.getApiKeysList(settingsService.getProvisioningKey())
            ApplicationManager.getApplication().invokeLater({
                handleApiKeysResult(result)
            }, ModalityState.any())
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error("Invalid state while refreshing API keys: ${e.message}", e)
            clearApiKeysOnEdt()
        } catch (e: IOException) {
            PluginLogger.Settings.error("Network error while refreshing API keys: ${e.message}", e)
            clearApiKeysOnEdt()
        } catch (expectedError: Exception) {
            PluginLogger.Settings.error("Failed to refresh API keys: ${expectedError.message}", expectedError)
            clearApiKeysOnEdt()
        }
    }

    /**
     * Handles the result of fetching API keys.
     * Must be called on EDT.
     */
    private fun handleApiKeysResult(result: ApiResult<*>) {
        when (result) {
            is ApiResult.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                val apiKeys = (result.data as? org.zhavoronkov.openrouter.models.ApiKeysListResponse)?.data
                    ?: emptyList()
                PluginLogger.Settings.info("Loaded ${apiKeys.size} API keys from OpenRouter")

                // Update cache
                cachedApiKeys = apiKeys
                cacheTimestamp = System.currentTimeMillis()
                PluginLogger.Settings.debug("Updated API keys cache (${apiKeys.size} keys)")

                apiKeyTableModel.setApiKeys(apiKeys)

                // Ensure IntelliJ API key exists (runs async)
                coroutineScope.launch(Dispatchers.IO) {
                    intellijKeyManager.ensureIntellijApiKeyExists(apiKeys)
                }
            }
            is ApiResult.Error -> {
                PluginLogger.Settings.warn("Failed to load API keys: ${result.message}")
                apiKeyTableModel.setApiKeys(emptyList())
                clearCache()
            }
        }
    }

    /**
     * Clears API keys table on EDT.
     */
    private fun clearApiKeysOnEdt() {
        ApplicationManager.getApplication().invokeLater({
            apiKeyTableModel.setApiKeys(emptyList())
            clearCache()
        }, ModalityState.any())
    }

    fun clearCache() {
        PluginLogger.Settings.debug("Clearing API keys cache")
        cachedApiKeys = null
        cacheTimestamp = 0L
    }

    fun loadApiKeysWithoutAutoCreate() {
        PluginLogger.Settings.debug("Loading API keys without auto-creation (will use cache if available)")
        // Use cached data if available (don't force refresh)
        loadApiKeysAsync(forceRefresh = false)
    }
}
