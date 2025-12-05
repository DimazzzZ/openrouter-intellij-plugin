package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
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
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private const val API_KEY_LABEL_PREFIX = "IntelliJ Plugin"
        private const val API_KEY_PREVIEW_LENGTH = 10
        private const val CACHE_DURATION_MS = 60000L // 1 minute cache for API keys
    }

    private var isCreatingApiKey = false

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

        if (label.isBlank()) {
            Messages.showErrorDialog("Label cannot be empty.", "Invalid Input")
            return
        }

        try {
            val apiKey = openRouterService.createApiKey(label).get()
            if (apiKey != null) {
                PluginLogger.Settings.info("Successfully created API key with label: $label")
                showApiKeyDialog(apiKey.key, label)
                refreshApiKeys()
            } else {
                Messages.showErrorDialog(
                    "Failed to create API key. Please check your Provisioning Key.",
                    "API Key Creation Failed"
                )
            }
        } catch (e: java.util.concurrent.ExecutionException) {
            PluginLogger.Settings.error("API call failed during key creation: ${e.message}", e)
            Messages.showErrorDialog(
                "Failed to create API key due to network or API error. Please try again.",
                "API Key Creation Failed"
            )
        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Settings.error("Timeout occurred while creating API key: ${e.message}", e)
            Messages.showErrorDialog(
                "Request timed out while creating API key. Please try again.",
                "API Key Creation Failed"
            )
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error("Invalid state during API key creation: ${e.message}", e)
            Messages.showErrorDialog(
                "Invalid state occurred during API key creation. Please try again.",
                "API Key Creation Failed"
            )
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
            try {
                val deleteResponse = openRouterService.deleteApiKey(apiKey.hash).get()
                if (deleteResponse?.deleted == true) {
                    PluginLogger.Settings.info("Successfully deleted API key: ${apiKey.name}")
                    apiKeyTableModel.removeApiKey(selectedRow)
                    Messages.showInfoMessage("API key deleted successfully.", "Success")
                } else {
                    Messages.showErrorDialog(
                        "Failed to delete API key. Please try again.",
                        "Deletion Failed"
                    )
                }
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Settings.error("API call failed during key deletion: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to delete API key due to network or API error. Please try again.",
                    "Deletion Failed"
                )
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Settings.error("Timeout occurred while deleting API key: ${e.message}", e)
                Messages.showErrorDialog(
                    "Request timed out while deleting API key. Please try again.",
                    "Deletion Failed"
                )
            } catch (e: IllegalStateException) {
                PluginLogger.Settings.error("Invalid state during API key deletion: ${e.message}", e)
                Messages.showErrorDialog(
                    "Invalid state occurred during API key deletion. Please try again.",
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
        try {
            val response = openRouterService.getApiKeysList(settingsService.getProvisioningKey()).get()
            val apiKeys = response?.data
            if (apiKeys != null) {
                PluginLogger.Settings.info("Loaded ${apiKeys.size} API keys from OpenRouter")

                // Update cache
                cachedApiKeys = apiKeys
                cacheTimestamp = System.currentTimeMillis()
                PluginLogger.Settings.debug("Updated API keys cache (${apiKeys.size} keys)")

                apiKeyTableModel.setApiKeys(apiKeys)
                ensureIntellijApiKeyExists(apiKeys)
            } else {
                PluginLogger.Settings.warn("Failed to load API keys - received null response")
                apiKeyTableModel.setApiKeys(emptyList())
                clearCache()
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to refresh API keys: ${e.message}", e)
            apiKeyTableModel.setApiKeys(emptyList())
            clearCache()
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

        try {
            val apiKey = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME).get()
            if (apiKey != null) {
                PluginLogger.Settings.info(
                    "Successfully created IntelliJ API key automatically: ${apiKey.key.take(20)}..."
                )
                PluginLogger.Settings.info("About to save new API key to settings (automatic creation)...")

                settingsService.setApiKey(apiKey.key)

                // Verify the key was saved
                val savedKey = settingsService.getApiKey()
                PluginLogger.Settings.info(
                    "Verification (automatic): saved key length=${savedKey.length}, matches=${savedKey == apiKey.key}"
                )

                if (savedKey != apiKey.key) {
                    PluginLogger.Settings.error("CRITICAL: API key was not saved correctly during automatic creation!")
                    PluginLogger.Settings.error("Expected: ${apiKey.key.take(20)}...")
                    PluginLogger.Settings.error("Got: ${savedKey.take(20)}...")
                }

                refreshApiKeys()
            } else {
                PluginLogger.Settings.error("Failed to create IntelliJ API key automatically")
                // No longer showing dialog - just log the error
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Exception creating IntelliJ API key: ${e.message}", e)
            // No longer showing dialog - just log the error
        } finally {
            isCreatingApiKey = false
        }
    }

    fun recreateIntellijApiKey() {
        val currentApiKeys = apiKeyTableModel.getApiKeys()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null) {
            try {
                val deleteResponse = openRouterService.deleteApiKey(existingIntellijApiKey.hash).get()
                if (deleteResponse?.deleted != true) {
                    PluginLogger.Settings.warn("Failed to delete existing IntelliJ API key")
                }
            } catch (e: Exception) {
                PluginLogger.Settings.error("Exception deleting existing IntelliJ API key: ${e.message}", e)
            }
        }

        createIntellijApiKeyOnce()
    }

    /**
     * Silently regenerate the IntelliJ API key without showing any dialogs.
     * This is used when the key exists remotely but is not stored locally.
     */
    private fun recreateIntellijApiKeySilently() {
        if (isCreatingApiKey) {
            PluginLogger.Settings.debug("Already creating API key, skipping silent regeneration")
            return
        }

        isCreatingApiKey = true
        PluginLogger.Settings.info("Starting silent regeneration of IntelliJ API key")

        try {
            deleteExistingIntellijApiKeySilently()
            createNewIntellijApiKeySilently()
        } finally {
            isCreatingApiKey = false
        }
    }

    /**
     * Delete existing IntelliJ API key silently if it exists
     */
    private fun deleteExistingIntellijApiKeySilently() {
        val currentApiKeys = apiKeyTableModel.getApiKeys()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey == null) {
            return
        }

        try {
            PluginLogger.Settings.debug("Deleting existing remote IntelliJ API key")
            val deleteResponse = openRouterService.deleteApiKey(existingIntellijApiKey.hash).get()
            if (deleteResponse?.deleted != true) {
                PluginLogger.Settings.error("Failed to delete existing IntelliJ API key during silent regeneration")
                return
            }
            PluginLogger.Settings.debug("Successfully deleted existing remote IntelliJ API key")
        } catch (e: Exception) {
            PluginLogger.Settings.error(
                "Exception deleting existing IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        }
    }

    /**
     * Create new IntelliJ API key silently
     */
    private fun createNewIntellijApiKeySilently() {
        try {
            PluginLogger.Settings.debug("Creating new IntelliJ API key")
            val apiKey = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME).get()

            if (apiKey == null) {
                PluginLogger.Settings.error("Failed to create new IntelliJ API key during silent regeneration")
                return
            }

            saveAndVerifyApiKey(apiKey.key)
            refreshStatusBarWidget()
            refreshApiKeys()
        } catch (e: Exception) {
            PluginLogger.Settings.error(
                "Exception creating new IntelliJ API key during silent regeneration: ${e.message}",
                e
            )
        }
    }

    /**
     * Save API key and verify it was saved correctly
     */
    private fun saveAndVerifyApiKey(key: String) {
        PluginLogger.Settings.info(
            "Successfully created new IntelliJ API key during silent regeneration: ${key.take(20)}..."
        )
        PluginLogger.Settings.info("About to save new API key to settings (silent regeneration)...")

        settingsService.setApiKey(key)

        // Verify the key was saved
        val retrievedKey = settingsService.getApiKey()
        PluginLogger.Settings.info(
            "Verified saved API key length: ${retrievedKey.length}, matches=${retrievedKey == key}"
        )

        if (retrievedKey != key) {
            PluginLogger.Settings.error("CRITICAL: API key was not saved correctly during silent regeneration!")
            PluginLogger.Settings.error("Expected: ${key.take(20)}...")
            PluginLogger.Settings.error("Got: ${retrievedKey.take(20)}...")
        }
    }

    private fun recreateIntellijApiKeyWithResult() {
        val currentApiKeys = apiKeyTableModel.getApiKeys()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        // Delete existing key if found
        if (existingIntellijApiKey != null) {
            try {
                val deleteResponse = openRouterService.deleteApiKey(existingIntellijApiKey.hash).get()
                if (deleteResponse?.deleted != true) {
                    showErrorDialog("Failed to delete the existing API key. Please try again.")
                    return
                }
            } catch (e: Exception) {
                PluginLogger.Settings.error("Exception deleting existing IntelliJ API key: ${e.message}", e)
                showErrorDialog("Error deleting existing key: ${e.message}")
                return
            }
        }

        // Create new key
        try {
            val apiKey = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME).get()
            if (apiKey != null) {
                PluginLogger.Settings.info("Successfully recreated IntelliJ API key: ${apiKey.key.take(20)}...")
                PluginLogger.Settings.info("About to save new API key to settings...")

                settingsService.setApiKey(apiKey.key)

                // Verify the key was saved correctly
                val savedKey = settingsService.getApiKey()
                PluginLogger.Settings.info(
                    "Verification: saved key length=${savedKey.length}, matches=${savedKey == apiKey.key}"
                )

                if (savedKey != apiKey.key) {
                    PluginLogger.Settings.error("CRITICAL: API key was not saved correctly!")
                    PluginLogger.Settings.error("Expected: ${apiKey.key.take(20)}...")
                    PluginLogger.Settings.error("Got: ${savedKey.take(20)}...")
                }

                showNewApiKeyDialog(apiKey.key)
                refreshApiKeys()
            } else {
                showErrorDialog("Failed to create new API key. Please check your Provisioning Key.")
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Exception creating IntelliJ API key: ${e.message}", e)
            showErrorDialog("Error creating new key: ${e.message}")
        }
    }

    private fun showErrorDialog(message: String) {
        Messages.showErrorDialog(message, "API Key Error")
    }

    fun resetApiKeyCreationFlag() {
        isCreatingApiKey = false
        PluginLogger.Settings.debug("Reset API key creation flag")
    }

    private fun showApiKeyDialog(apiKey: String, label: String) {
        PluginLogger.Settings.debug("Showing API key dialog for label: $label, key length: ${apiKey.length}")
        val dialog = object : DialogWrapper(true) {
            init {
                title = "API Key Created"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.preferredSize = Dimension(500, 200)

                val infoLabel = JBLabel(
                    "<html><b>API Key created successfully!</b><br><br>" +
                        "Label: $label<br>" +
                        "Key: ${apiKey.take(API_KEY_PREVIEW_LENGTH)}...<br><br>" +
                        "<b>Important:</b> Copy this key now as it won't be shown again.</html>"
                )

                val keyField = JBTextField(apiKey)
                keyField.isEditable = false

                val copyButton = JButton("Copy to Clipboard")
                copyButton.addActionListener {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(apiKey), null)
                    Messages.showInfoMessage("API key copied to clipboard!", "Copied")
                }

                panel.add(infoLabel, BorderLayout.NORTH)
                panel.add(keyField, BorderLayout.CENTER)
                panel.add(copyButton, BorderLayout.SOUTH)

                return panel
            }

            override fun createActions(): Array<Action> {
                return arrayOf(okAction)
            }
        }
        dialog.show()
    }

    private fun showNewApiKeyDialog(apiKey: String) {
        PluginLogger.Settings.debug("Showing new API key dialog, key length: ${apiKey.length}")
        val dialog = object : DialogWrapper(true) {
            private val keyField = com.intellij.ui.components.JBPasswordField().apply {
                text = apiKey
                isEditable = false
                columns = 40
                addFocusListener(object : java.awt.event.FocusAdapter() {
                    override fun focusGained(e: java.awt.event.FocusEvent?) {
                        selectAll()
                    }
                })
            }

            init {
                title = "New API Key Created"
                init()
            }

            override fun createCenterPanel(): JComponent {
                return panel {
                    // Success message
                    row {
                        label(
                            "<html><b>New API Key Created Successfully!</b><br>" +
                                "Label: $INTELLIJ_API_KEY_NAME<br>" +
                                "Preview: ${apiKey.take(API_KEY_PREVIEW_LENGTH)}...</html>"
                        )
                    }.bottomGap(BottomGap.SMALL)

                    // API Key field with Copy button
                    row("API Key:") {
                        cell(keyField)
                            .align(AlignX.FILL)
                            .resizableColumn()

                        button("Copy") {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(apiKey), null)

                            // Visual feedback
                            val btn = it.source as? JButton
                            btn?.let { button ->
                                val originalText = button.text
                                button.text = "Copied!"
                                javax.swing.Timer(2000) {
                                    button.text = originalText
                                }.apply {
                                    isRepeats = false
                                    start()
                                }
                            }
                        }
                    }.bottomGap(BottomGap.SMALL)

                    // Warning notice
                    row {
                        label(
                            "<html>⚠ <b>Important:</b> This key is shown only once. Copy and store it securely.</html>"
                        )
                            .applyToComponent {
                                foreground = com.intellij.ui.JBColor.namedColor(
                                    "Label.infoForeground",
                                    com.intellij.ui.JBColor(0x808080, 0x8C8C8C)
                                )
                            }
                    }
                }
            }

            override fun createActions(): Array<Action> {
                val closeAction = object : DialogWrapperAction("Close") {
                    init {
                        putValue(DEFAULT_ACTION, true)
                    }

                    override fun doAction(e: java.awt.event.ActionEvent) {
                        close(DialogWrapper.OK_EXIT_CODE)
                    }
                }
                return arrayOf(closeAction)
            }
        }
        dialog.show()
    }

    // Removed complex dialog - now using silent regeneration

    private fun refreshStatusBarWidget() {
        ApplicationManager.getApplication().invokeLater({
            PluginLogger.Settings.debug("Refreshing status bar widget after API key update")
        }, com.intellij.openapi.application.ModalityState.any())
    }
}
