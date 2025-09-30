package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
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
    }

    private var isCreatingApiKey = false

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
                showApiKeyDialog(apiKey, label)
                refreshApiKeys()
            } else {
                Messages.showErrorDialog(
                    "Failed to create API key. Please check your Provisioning Key.",
                    "API Key Creation Failed"
                )
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to create API key: ${e.message}", e)
            Messages.showErrorDialog(
                "Failed to create API key: ${e.message}",
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
                val success = openRouterService.deleteApiKey(apiKey.id).get()
                if (success) {
                    PluginLogger.Settings.info("Successfully deleted API key: ${apiKey.name}")
                    apiKeyTableModel.removeApiKey(selectedRow)
                    Messages.showInfoMessage("API key deleted successfully.", "Success")
                } else {
                    Messages.showErrorDialog(
                        "Failed to delete API key. Please try again.",
                        "Deletion Failed"
                    )
                }
            } catch (e: Exception) {
                PluginLogger.Settings.error("Failed to delete API key: ${e.message}", e)
                Messages.showErrorDialog(
                    "Failed to delete API key: ${e.message}",
                    "Deletion Failed"
                )
            }
        }
    }

    fun refreshApiKeys() {
        PluginLogger.Settings.debug("Refreshing API keys table")

        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("Not configured, clearing API keys table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        try {
            val apiKeys = openRouterService.getApiKeys().get()
            if (apiKeys != null) {
                PluginLogger.Settings.debug("Loaded ${apiKeys.size} API keys from OpenRouter")
                apiKeyTableModel.setApiKeys(apiKeys)
                ensureIntellijApiKeyExists(apiKeys)
            } else {
                PluginLogger.Settings.warn("Failed to load API keys - received null response")
                apiKeyTableModel.setApiKeys(emptyList())
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to refresh API keys: ${e.message}", e)
            apiKeyTableModel.setApiKeys(emptyList())
        }
    }

    fun loadApiKeysWithoutAutoCreate() {
        PluginLogger.Settings.debug("Loading API keys without auto-creation")

        if (!settingsService.isConfigured()) {
            PluginLogger.Settings.debug("Not configured, clearing API keys table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        try {
            val apiKeys = openRouterService.getApiKeys().get()
            if (apiKeys != null) {
                PluginLogger.Settings.debug("Loaded ${apiKeys.size} API keys from OpenRouter (no auto-create)")
                apiKeyTableModel.setApiKeys(apiKeys)
            } else {
                PluginLogger.Settings.warn("Failed to load API keys - received null response")
                apiKeyTableModel.setApiKeys(emptyList())
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to load API keys: ${e.message}", e)
            apiKeyTableModel.setApiKeys(emptyList())
        }
    }

    fun ensureIntellijApiKeyExists(currentApiKeys: List<ApiKeyInfo>) {
        val storedApiKey = settingsService.getApiKey()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null && storedApiKey.isNotEmpty()) {
            PluginLogger.Settings.debug("IntelliJ API key exists and is stored locally")
            return
        }

        if (existingIntellijApiKey == null && !isCreatingApiKey) {
            PluginLogger.Settings.info("IntelliJ API key not found, creating automatically")
            createIntellijApiKeyOnce()
        } else if (existingIntellijApiKey != null && storedApiKey.isEmpty()) {
            PluginLogger.Settings.warn("IntelliJ API key exists remotely but not stored locally")
            showRecreateApiKeyDialog()
        }
    }

    fun createIntellijApiKeyOnce() {
        isCreatingApiKey = true

        try {
            val apiKey = openRouterService.createApiKey(INTELLIJ_API_KEY_NAME).get()
            if (apiKey != null) {
                PluginLogger.Settings.info("Successfully created IntelliJ API key automatically")
                settingsService.setApiKey(apiKey)
                refreshApiKeys()
            } else {
                PluginLogger.Settings.error("Failed to create IntelliJ API key automatically")
                showRecreateApiKeyDialog()
            }
        } catch (e: Exception) {
            PluginLogger.Settings.error("Exception creating IntelliJ API key: ${e.message}", e)
            showRecreateApiKeyDialog()
        } finally {
            isCreatingApiKey = false
        }
    }

    fun recreateIntellijApiKey() {
        val currentApiKeys = apiKeyTableModel.getApiKeys()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null) {
            try {
                val deleteSuccess = openRouterService.deleteApiKey(existingIntellijApiKey.id).get()
                if (!deleteSuccess) {
                    PluginLogger.Settings.warn("Failed to delete existing IntelliJ API key")
                }
            } catch (e: Exception) {
                PluginLogger.Settings.error("Exception deleting existing IntelliJ API key: ${e.message}", e)
            }
        }

        createIntellijApiKeyOnce()
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

    private fun showRecreateApiKeyDialog() {
        val dialog = object : DialogWrapper(true) {
            init {
                title = "API Key Configuration"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.preferredSize = Dimension(500, 150)

                val message = JBLabel(
                    "<html><b>IntelliJ API Key Issue</b><br><br>" +
                            "The 'IntelliJ IDEA Plugin' API key exists but is not stored locally.<br>" +
                            "Would you like to recreate it or enter it manually?</html>"
                )

                panel.add(message, BorderLayout.CENTER)
                return panel
            }

            override fun createActions(): Array<Action> {
                val recreateAction = object : DialogWrapperAction("Recreate") {
                    override fun doAction(e: java.awt.event.ActionEvent) {
                        close(1)
                    }
                }

                val enterManuallyAction = object : DialogWrapperAction("Enter Manually") {
                    override fun doAction(e: java.awt.event.ActionEvent) {
                        close(2)
                    }
                }

                return arrayOf(recreateAction, enterManuallyAction, cancelAction)
            }
        }

        val result = dialog.showAndGet()
        when (result) {
            1 -> recreateIntellijApiKey()
            2 -> showManualApiKeyEntryDialog()
        }
    }

    private fun showManualApiKeyEntryDialog(): Boolean {
        val apiKey = Messages.showInputDialog(
            "Enter your OpenRouter API key for the 'IntelliJ IDEA Plugin':\n\n" +
                    "You can find this in your OpenRouter dashboard under API Keys.",
            "Enter API Key",
            null,
            "",
            null
        )

        if (!apiKey.isNullOrBlank()) {
            settingsService.setApiKey(apiKey)
            PluginLogger.Settings.info("API key entered manually and saved")
            refreshStatusBarWidget()
            return true
        }

        return false
    }

    private fun refreshStatusBarWidget() {
        ApplicationManager.getApplication().invokeLater({
            PluginLogger.Settings.debug("Refreshing status bar widget after API key update")
        }, com.intellij.openapi.application.ModalityState.any())
    }
}
