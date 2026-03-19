package org.zhavoronkov.openrouter.settings

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Manages the IntelliJ IDEA Plugin API key lifecycle.
 *
 * All network operations are performed on background threads using coroutines
 * to avoid EDT (Event Dispatch Thread) violations.
 */
@Suppress("TooManyFunctions")
class IntellijApiKeyManager(
    private val settingsService: OpenRouterSettingsService,
    private val openRouterService: OpenRouterService
) : Disposable {

    // Coroutine scope for background operations - uses IO dispatcher to avoid EDT violations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"

        // Singleton flag to prevent concurrent API key creation across all instances
        @Volatile
        private var isCreatingApiKey = false

        // Lock object for synchronization
        private val creationLock = Any()
    }

    fun ensureIntellijApiKeyExists(currentApiKeys: List<ApiKeyInfo>) {
        // Use synchronized block to prevent concurrent key operations
        synchronized(creationLock) {
            if (isCreatingApiKey) {
                PluginLogger.Settings.debug("Already creating API key, skipping ensureIntellijApiKeyExists")
                return
            }

            val storedApiKey = settingsService.getApiKey()
            // Find ALL keys with the IntelliJ name, not just the first one
            val existingIntellijApiKeys = currentApiKeys.filter { it.name == INTELLIJ_API_KEY_NAME }

            PluginLogger.Settings.debug(
                "ensureIntellijApiKeyExists: storedApiKey.length=${storedApiKey.length}, " +
                    "storedApiKey.isEmpty=${storedApiKey.isEmpty()}"
            )
            PluginLogger.Settings.debug(
                "ensureIntellijApiKeyExists: found ${existingIntellijApiKeys.size} IntelliJ API key(s)"
            )

            // Check if stored key matches ANY of the existing keys
            val matchingKey = findMatchingKey(existingIntellijApiKeys, storedApiKey)

            if (matchingKey != null && storedApiKey.isNotEmpty()) {
                // We have a valid key that matches - check if there are duplicates to clean up
                val duplicateCount = existingIntellijApiKeys.size - 1
                if (duplicateCount > 0) {
                    PluginLogger.Settings.warn(
                        "Found $duplicateCount duplicate IntelliJ API key(s) - cleaning up"
                    )
                    cleanupDuplicateKeys(existingIntellijApiKeys, matchingKey)
                } else {
                    PluginLogger.Settings.debug(
                        "IntelliJ API key exists and stored key matches - no action needed"
                    )
                }
                return
            }

            handleApiKeyMismatchOrMissing(existingIntellijApiKeys, storedApiKey, matchingKey)
        }
    }

    /**
     * Find a key from the list that matches the stored API key prefix.
     * Returns the first matching key or null if no match found.
     */
    @Suppress("ReturnCount")
    private fun findMatchingKey(keys: List<ApiKeyInfo>, storedApiKey: String): ApiKeyInfo? {
        if (storedApiKey.isEmpty()) return null

        for (key in keys) {
            val keyLabel = key.label
            // Label format is like "sk-or-v1-abc...xyz" - extract the prefix to compare
            val labelPrefix = if (keyLabel.contains("...")) {
                keyLabel.substringBefore("...")
            } else {
                keyLabel
            }
            val storedKeyPrefix = storedApiKey.take(labelPrefix.length)
            if (storedKeyPrefix == labelPrefix) {
                PluginLogger.Settings.debug(
                    "findMatchingKey: found match - keyLabel=$keyLabel, storedKeyPrefix=$storedKeyPrefix"
                )
                return key
            }
        }

        PluginLogger.Settings.debug("findMatchingKey: no matching key found for stored API key")
        return null
    }

    /**
     * Clean up duplicate keys, keeping only the matching one
     */
    private fun cleanupDuplicateKeys(allKeys: List<ApiKeyInfo>, keepKey: ApiKeyInfo) {
        val keysToDelete = allKeys.filter { it.hash != keepKey.hash }
        PluginLogger.Settings.info("Cleaning up ${keysToDelete.size} duplicate IntelliJ API key(s)")

        for (key in keysToDelete) {
            PluginLogger.Settings.debug("Deleting duplicate key: ${key.label}")
            deleteIntellijApiKeySilently(key.hash)
        }
    }

    private fun handleApiKeyMismatchOrMissing(
        existingIntellijApiKeys: List<ApiKeyInfo>,
        storedApiKey: String,
        matchingKey: ApiKeyInfo?
    ) {
        when {
            // Case 1: Keys exist but none match stored key - delete all and recreate
            existingIntellijApiKeys.isNotEmpty() && storedApiKey.isNotEmpty() && matchingKey == null -> {
                val keyCount = existingIntellijApiKeys.size
                PluginLogger.Settings.warn(
                    "Stored API key does not match any of $keyCount remote IntelliJ API key(s) - " +
                        "deleting all and regenerating"
                )
                if (!isCreatingApiKey) {
                    recreateIntellijApiKeySilently()
                }
            }
            // Case 2: No keys exist - create one
            existingIntellijApiKeys.isEmpty() && !isCreatingApiKey -> {
                PluginLogger.Settings.info("IntelliJ API key not found, creating automatically")
                createIntellijApiKeyOnce()
            }
            // Case 3: Keys exist but no stored key - delete all and recreate
            existingIntellijApiKeys.isNotEmpty() && storedApiKey.isEmpty() && !isCreatingApiKey -> {
                PluginLogger.Settings.info(
                    "IntelliJ API key(s) exist remotely but not stored locally - " +
                        "deleting ${existingIntellijApiKeys.size} key(s) and regenerating"
                )
                recreateIntellijApiKeySilently()
            }
        }
    }

    fun createIntellijApiKeyOnce() {
        synchronized(creationLock) {
            if (isCreatingApiKey) {
                PluginLogger.Settings.debug("Already creating API key, skipping createIntellijApiKeyOnce")
                return
            }
            isCreatingApiKey = true
        }

        // Use coroutine scope to avoid EDT violations
        scope.launch {
            try {
                val result = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME)
                when (result) {
                    is ApiResult.Success -> {
                        PluginLogger.Settings.info(
                            "Successfully created IntelliJ API key automatically"
                        )
                        saveAndVerifyApiKey(result.data.key)
                    }
                    is ApiResult.Error -> {
                        PluginLogger.Settings.error(
                            "Failed to create IntelliJ API key automatically: ${result.message}"
                        )
                    }
                }
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state creating IntelliJ API key: ${e.message}", e)
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error creating IntelliJ API key: ${e.message}", e)
            } catch (e: JsonSyntaxException) {
                PluginLogger.Settings.error("JSON parsing error creating IntelliJ API key: ${e.message}", e)
            } finally {
                isCreatingApiKey = false
            }
        }
    }

    fun recreateIntellijApiKey() {
        manageIntellijApiKeySilently()
        createIntellijApiKeyOnce()
    }

    private fun recreateIntellijApiKeySilently() {
        synchronized(creationLock) {
            if (isCreatingApiKey) {
                PluginLogger.Settings.debug("Already creating API key, skipping silent regeneration")
                return
            }
            isCreatingApiKey = true
        }

        PluginLogger.Settings.info("Starting silent regeneration of IntelliJ API key")

        try {
            manageIntellijApiKeySilently()
        } finally {
            isCreatingApiKey = false
        }
    }

    private fun deleteIntellijApiKeySilently(keyHash: String) {
        scope.launch {
            try {
                PluginLogger.Settings.debug("Deleting existing remote IntelliJ API key")
                val deleteResult = openRouterService.deleteApiKey(keyHash)
                when (deleteResult) {
                    is ApiResult.Success -> {
                        if (!deleteResult.data.deleted) {
                            PluginLogger.Settings.error(
                                "Failed to delete existing IntelliJ API key during silent regeneration"
                            )
                            return@launch
                        }
                        PluginLogger.Settings.debug("Successfully deleted existing remote IntelliJ API key")
                    }
                    is ApiResult.Error -> {
                        PluginLogger.Settings.error(
                            "Failed to delete existing IntelliJ API key: ${deleteResult.message}"
                        )
                        return@launch
                    }
                }
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error(
                    "Invalid state deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                    e
                )
            } catch (e: IOException) {
                val errorMessage = e.message ?: "Unknown error"
                PluginLogger.Settings.error(
                    "Network error deleting existing IntelliJ API key during silent regeneration: $errorMessage",
                    e
                )
            } catch (e: JsonSyntaxException) {
                PluginLogger.Settings.error(
                    "JSON parsing error deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                    e
                )
            }
        }
    }

    private suspend fun createIntellijApiKeySilently() {
        try {
            PluginLogger.Settings.debug("Creating new IntelliJ API key")
            val result = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME)

            when (result) {
                is ApiResult.Success -> {
                    val apiKey = result.data
                    saveAndVerifyApiKey(apiKey.key)
                    refreshStatusBarWidget()
                }
                is ApiResult.Error -> {
                    PluginLogger.Settings.error(
                        "Failed to create new IntelliJ API key during silent regeneration: ${result.message}"
                    )
                }
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error(
                "Invalid state creating new IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        } catch (e: IOException) {
            PluginLogger.Settings.error(
                "Network error creating new IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        } catch (e: JsonSyntaxException) {
            PluginLogger.Settings.error(
                "JSON parsing error creating new IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        }
    }

    private fun manageIntellijApiKeySilently() {
        scope.launch {
            try {
                val keysResult = openRouterService.getApiKeysList()
                if (keysResult is ApiResult.Success) {
                    // Find ALL keys with the IntelliJ name and delete them all
                    val intellijKeys = keysResult.data.data.filter { it.name == INTELLIJ_API_KEY_NAME }
                    if (intellijKeys.isNotEmpty()) {
                        PluginLogger.Settings.info(
                            "Deleting ${intellijKeys.size} existing IntelliJ API key(s) before creating new one"
                        )
                        for (key in intellijKeys) {
                            PluginLogger.Settings.debug("Deleting key: ${key.label}")
                            deleteIntellijApiKeyAsync(key.hash)
                        }
                    }
                }
                createIntellijApiKeySilently()
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state managing IntelliJ API key: ${e.message}", e)
            } catch (e: IOException) {
                PluginLogger.Settings.error("Network error managing IntelliJ API key: ${e.message}", e)
            } catch (e: JsonSyntaxException) {
                PluginLogger.Settings.error("JSON parsing error managing IntelliJ API key: ${e.message}", e)
            }
        }
    }

    /**
     * Deletes an API key asynchronously within a coroutine context.
     */
    private suspend fun deleteIntellijApiKeyAsync(keyHash: String) {
        try {
            PluginLogger.Settings.debug("Deleting existing remote IntelliJ API key")
            val deleteResult = openRouterService.deleteApiKey(keyHash)
            when (deleteResult) {
                is ApiResult.Success -> {
                    if (!deleteResult.data.deleted) {
                        PluginLogger.Settings.error(
                            "Failed to delete existing IntelliJ API key during silent regeneration"
                        )
                        return
                    }
                    PluginLogger.Settings.debug("Successfully deleted existing remote IntelliJ API key")
                }
                is ApiResult.Error -> {
                    PluginLogger.Settings.error(
                        "Failed to delete existing IntelliJ API key: ${deleteResult.message}"
                    )
                    return
                }
            }
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error(
                "Invalid state deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        } catch (e: IOException) {
            val errorMessage = e.message ?: "Unknown error"
            PluginLogger.Settings.error(
                "Network error deleting existing IntelliJ API key during silent regeneration: $errorMessage",
                e
            )
        } catch (e: JsonSyntaxException) {
            PluginLogger.Settings.error(
                "JSON parsing error deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        }
    }

    private fun saveAndVerifyApiKey(key: String) {
        PluginLogger.Settings.info("About to save new API key to settings...")
        settingsService.apiKeyManager.setApiKey(key)

        // Verify the key was saved
        val retrievedKey = settingsService.getApiKey()
        PluginLogger.Settings.info(
            "Verified saved API key length: ${retrievedKey.length}, matches=${retrievedKey == key}"
        )

        if (retrievedKey != key) {
            PluginLogger.Settings.error("CRITICAL: API key was not saved correctly!")
        }
    }

    private fun refreshStatusBarWidget() {
        ApplicationManager.getApplication().invokeLater {
            val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
            project?.let {
                val statusBarWidget = com.intellij.openapi.wm.WindowManager.getInstance()
                    .getStatusBar(it)
                    ?.getWidget(OpenRouterStatusBarWidget.ID) as? OpenRouterStatusBarWidget
                statusBarWidget?.updateQuotaInfo()
            }
        }
    }

    /**
     * Disposes of resources and cancels any pending coroutine operations.
     */
    override fun dispose() {
        scope.cancel()
    }
}
