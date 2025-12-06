package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val result = openRouterService.createApiKey(label)
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
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state during key creation: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to create API key due to invalid state. Please try again.",
                    "API Key Creation Failed"
                )
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error during key creation: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to create API key due to network error. Please try again.",
                    "API Key Creation Failed"
                )
            } catch (expectedError: Exception) {
                PluginLogger.Settings.error("Exception during key creation: ${expectedError.message}", expectedError)
                Messages.showErrorDialog(
                    "Failed to create API key due to error. Please try again.",
                    "API Key Creation Failed"
                )
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
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            try {
                val deleteResult = openRouterService.deleteApiKey(apiKey.hash)
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
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state during key deletion: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to delete API key due to error. Please try again.",
                    "Deletion Failed"
                )
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error during key deletion: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to delete API key due to error. Please try again.",
                    "Deletion Failed"
                )
            }
        }
    }

    fun refreshApiKeys(forceRefresh: Boolean = true) {
        PluginLogger.Settings.info("========================================")
        PluginLogger.Settings.info("REFRESH BUTTON CLICKED - refreshApiKeys() called (forceRefresh: $forceRefresh)")
        PluginLogger.Settings.info("========================================")
        println("========================================")
        println("REFRESH BUTTON CLICKED - refreshApiKeys() called (forceRefresh: $forceRefresh)")
        println("========================================")

        loadApiKeysInternal(forceRefresh)
    }

    private fun loadApiKeysInternal(forceRefresh: Boolean) {
        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("Not configured, clearing API keys table and cache")
            apiKeyTableModel.setApiKeys(emptyList())
            clearCache()
            return
        }

        // Check cache first (unless force refresh)
        if (!forceRefresh) {
            val now = System.currentTimeMillis()
            val cacheAge = now - cacheTimestamp
            val isCacheValid = cachedApiKeys != null && cacheAge < CACHE_DURATION_MS

            if (isCacheValid) {
                PluginLogger.Settings.debug("Using cached API keys (${cachedApiKeys?.size} keys, age: ${cacheAge}ms)")
                apiKeyTableModel.setApiKeys(cachedApiKeys!!)
                return
            } else if (cachedApiKeys != null) {
                PluginLogger.Settings.debug(
                    "Cache expired (age: ${cacheAge}ms > ${CACHE_DURATION_MS}ms), fetching fresh data"
                )
            }
        } else {
            PluginLogger.Settings.debug("Force refresh requested, bypassing cache")
        }

        // Fetch from API
        runBlocking {
            try {
                val result = openRouterService.getApiKeysList(settingsService.getProvisioningKey())
                when (result) {
                    is ApiResult.Success -> {
                        val apiKeys = result.data.data
                        PluginLogger.Settings.info("Loaded ${apiKeys.size} API keys from OpenRouter")

                        // Update cache
                        cachedApiKeys = apiKeys
                        cacheTimestamp = System.currentTimeMillis()
                        PluginLogger.Settings.debug("Updated API keys cache (${apiKeys.size} keys)")

                        apiKeyTableModel.setApiKeys(apiKeys)
                        intellijKeyManager.ensureIntellijApiKeyExists(apiKeys)
                    }
                    is ApiResult.Error -> {
                        PluginLogger.Settings.warn("Failed to load API keys: ${result.message}")
                        apiKeyTableModel.setApiKeys(emptyList())
                        clearCache()
                    }
                }
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state while refreshing API keys: ${e.message}", e)
                apiKeyTableModel.setApiKeys(emptyList())
                clearCache()
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error while refreshing API keys: ${e.message}", e)
                apiKeyTableModel.setApiKeys(emptyList())
                clearCache()
            } catch (expectedError: Exception) {
                PluginLogger.Settings.error("Failed to refresh API keys: ${expectedError.message}", expectedError)
                apiKeyTableModel.setApiKeys(emptyList())
                clearCache()
            }
        }
    }

    fun clearCache() {
        PluginLogger.Settings.debug("Clearing API keys cache")
        cachedApiKeys = null
        cacheTimestamp = 0L
    }

    fun loadApiKeysWithoutAutoCreate() {
        PluginLogger.Settings.debug("Loading API keys without auto-creation (will use cache if available)")
        // Use cached data if available (don't force refresh)
        loadApiKeysInternal(forceRefresh = false)
    }

    fun getIntellijKeyManager(): IntellijApiKeyManager = intellijKeyManager
}
