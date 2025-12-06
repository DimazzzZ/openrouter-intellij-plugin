package org.zhavoronkov.openrouter.settings

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.runBlocking
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Manages the IntelliJ IDEA Plugin API key lifecycle
 */
class IntellijApiKeyManager(
    private val settingsService: OpenRouterSettingsService,
    private val openRouterService: OpenRouterService
) {

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
    }

    private var isCreatingApiKey = false

    fun ensureIntellijApiKeyExists(currentApiKeys: List<ApiKeyInfo>) {
        val storedApiKey = settingsService.getApiKey()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        PluginLogger.Settings.debug(
            "ensureIntellijApiKeyExists: storedApiKey.length=${storedApiKey.length}, " +
                "storedApiKey.isEmpty=${storedApiKey.isEmpty()}"
        )
        PluginLogger.Settings.debug(
            "ensureIntellijApiKeyExists: existingIntellijApiKey=${existingIntellijApiKey?.name ?: "null"}"
        )

        if (existingIntellijApiKey != null && storedApiKey.isNotEmpty()) {
            PluginLogger.Settings.debug("IntelliJ API key exists and is stored locally - no action needed")
            return
        }

        if (existingIntellijApiKey == null && !isCreatingApiKey) {
            PluginLogger.Settings.info("IntelliJ API key not found, creating automatically")
            createIntellijApiKeyOnce()
        } else if (existingIntellijApiKey != null && storedApiKey.isEmpty() && !isCreatingApiKey) {
            PluginLogger.Settings.info(
                "IntelliJ API key exists remotely but not stored locally - regenerating silently"
            )
            recreateIntellijApiKeySilently()
        }
    }

    fun createIntellijApiKeyOnce() {
        isCreatingApiKey = true

        // Use runBlocking since this is called during settings initialization
        runBlocking {
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

    fun resetApiKeyCreationFlag() {
        isCreatingApiKey = false
        PluginLogger.Settings.info("Reset API key creation flag")
    }

    private fun recreateIntellijApiKeySilently() {
        if (isCreatingApiKey) {
            PluginLogger.Settings.debug("Already creating API key, skipping silent regeneration")
            return
        }

        isCreatingApiKey = true
        PluginLogger.Settings.info("Starting silent regeneration of IntelliJ API key")

        try {
            manageIntellijApiKeySilently()
        } finally {
            isCreatingApiKey = false
        }
    }

    private fun deleteIntellijApiKeySilently(keyHash: String) {
        runBlocking {
            try {
                PluginLogger.Settings.debug("Deleting existing remote IntelliJ API key")
                val deleteResult = openRouterService.deleteApiKey(keyHash)
                when (deleteResult) {
                    is ApiResult.Success -> {
                        if (!deleteResult.data.deleted) {
                            PluginLogger.Settings.error(
                                "Failed to delete existing IntelliJ API key during silent regeneration"
                            )
                            return@runBlocking
                        }
                        PluginLogger.Settings.debug("Successfully deleted existing remote IntelliJ API key")
                    }
                    is ApiResult.Error -> {
                        PluginLogger.Settings.error(
                            "Failed to delete existing IntelliJ API key: ${deleteResult.message}"
                        )
                        return@runBlocking
                    }
                }
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error(
                    "Invalid state deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                    e
                )
            } catch (e: IOException) {
                val errorMsg = "Network error deleting existing IntelliJ API key during silent regeneration: " +
                    "${e.message ?: "Unknown error"}"
                PluginLogger.Settings.error(errorMsg, e)
            } catch (e: JsonSyntaxException) {
                PluginLogger.Settings.error(
                    "JSON parsing error deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                    e
                )
            }
        }
    }

    private fun createIntellijApiKeySilently() {
        runBlocking {
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
    }

    private fun manageIntellijApiKeySilently() {
        runBlocking {
            try {
                val keysResult = openRouterService.getApiKeysList()
                if (keysResult is ApiResult.Success) {
                    val intellijKey = keysResult.data.data.find { it.name == INTELLIJ_API_KEY_NAME }
                    intellijKey?.let { deleteIntellijApiKeySilently(it.hash) }
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
}
