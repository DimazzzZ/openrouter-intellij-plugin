package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

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
                showApiKeyDialog(apiKey.key, label)
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
            val response = openRouterService.getApiKeysList(settingsService.getProvisioningKey()).get()
            val apiKeys = response?.data
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
            val response = openRouterService.getApiKeysList(settingsService.getProvisioningKey()).get()
            val apiKeys = response?.data
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
                settingsService.setApiKey(apiKey.key)
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
                PluginLogger.Settings.info("Successfully recreated IntelliJ API key")
                settingsService.setApiKey(apiKey.key)
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
            init {
                title = "New API Key Created"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.preferredSize = Dimension(550, 220)

                // Top message
                val infoLabel = JBLabel(
                    "<html><b>New API Key Created Successfully!</b><br><br>" +
                            "Label: $INTELLIJ_API_KEY_NAME<br>" +
                            "Preview: ${apiKey.take(API_KEY_PREVIEW_LENGTH)}...<br><br></html>"
                )
                panel.add(infoLabel, BorderLayout.NORTH)

                // Center panel with password field and copy button
                val centerPanel = JPanel(BorderLayout())
                centerPanel.border = JBUI.Borders.empty(10, 0)

                val keyField = com.intellij.ui.components.JBPasswordField()
                keyField.text = apiKey
                keyField.isEditable = false
                keyField.addFocusListener(object : java.awt.event.FocusAdapter() {
                    override fun focusGained(e: java.awt.event.FocusEvent?) {
                        keyField.selectAll()
                    }
                })

                val copyButton = JButton("Copy")
                copyButton.addActionListener {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(apiKey), null)
                    copyButton.text = "Copied!"
                    javax.swing.Timer(2000) { copyButton.text = "Copy" }.apply {
                        isRepeats = false
                        start()
                    }
                }

                centerPanel.add(keyField, BorderLayout.CENTER)
                centerPanel.add(copyButton, BorderLayout.EAST)
                panel.add(centerPanel, BorderLayout.CENTER)

                // Bottom warning
                val warningLabel = JBLabel(
                    "<html><b>⚠ Important:</b> This key is shown only once. Copy and store it securely.</html>"
                )
                warningLabel.foreground = com.intellij.ui.JBColor.ORANGE
                panel.add(warningLabel, BorderLayout.SOUTH)

                return panel
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

    private fun showRecreateApiKeyDialog() {
        val dialog = object : DialogWrapper(true) {
            private val regenerateRadio = JRadioButton("Regenerate (delete the old key and create a new one automatically)", true)
            private val manualRadio = JRadioButton("Enter key manually")
            private val manualKeyField = com.intellij.ui.components.JBPasswordField()
            private val manualKeyPanel = JPanel(BorderLayout())
            private val errorLabel = JBLabel("")

            init {
                title = "API Key Already Exists"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.preferredSize = Dimension(550, 250)

                // Message at top
                val message = JBLabel(
                    "<html><b>The 'IntelliJ IDEA Plugin' API key already exists</b><br><br>" +
                            "The API key exists on OpenRouter but is not stored locally.<br>" +
                            "Choose how you would like to proceed:</html>"
                )
                panel.add(message, BorderLayout.NORTH)

                // Radio buttons and manual entry panel
                val centerPanel = JPanel()
                centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)
                centerPanel.border = JBUI.Borders.empty(10, 0)

                // Group radio buttons
                val buttonGroup = ButtonGroup()
                buttonGroup.add(regenerateRadio)
                buttonGroup.add(manualRadio)

                centerPanel.add(regenerateRadio)
                centerPanel.add(Box.createVerticalStrut(10))
                centerPanel.add(manualRadio)
                centerPanel.add(Box.createVerticalStrut(10))

                // Manual entry panel (initially hidden)
                manualKeyPanel.border = JBUI.Borders.emptyLeft(25)
                val manualLabel = JBLabel("API Key:")
                manualKeyField.emptyText.text = "sk-or-v1-..."
                manualKeyPanel.add(manualLabel, BorderLayout.WEST)
                manualKeyPanel.add(manualKeyField, BorderLayout.CENTER)
                manualKeyPanel.isVisible = false
                centerPanel.add(manualKeyPanel)

                // Error label
                errorLabel.foreground = com.intellij.ui.JBColor.RED
                centerPanel.add(Box.createVerticalStrut(5))
                centerPanel.add(errorLabel)

                // Radio button listeners
                regenerateRadio.addActionListener {
                    manualKeyPanel.isVisible = false
                    errorLabel.text = ""
                }
                manualRadio.addActionListener {
                    manualKeyPanel.isVisible = true
                    errorLabel.text = ""
                }

                panel.add(centerPanel, BorderLayout.CENTER)
                return panel
            }

            override fun createActions(): Array<Action> {
                val continueAction = object : DialogWrapperAction("Continue") {
                    init {
                        putValue(DEFAULT_ACTION, true)
                    }

                    override fun doAction(e: java.awt.event.ActionEvent) {
                        if (regenerateRadio.isSelected) {
                            close(DialogWrapper.OK_EXIT_CODE)
                        } else {
                            // Validate manual entry
                            val apiKey = String(manualKeyField.password)
                            if (apiKey.isBlank()) {
                                errorLabel.text = "Please enter an API key"
                                return
                            }
                            if (!apiKey.startsWith("sk-or-v1-") || apiKey.length < 20) {
                                errorLabel.text = "Invalid API key format. Must start with 'sk-or-v1-' and be at least 20 characters."
                                return
                            }
                            close(2) // Manual entry exit code
                        }
                    }
                }

                return arrayOf(continueAction, cancelAction)
            }

            fun getManualApiKey(): String = String(manualKeyField.password)
        }

        if (dialog.showAndGet()) {
            val exitCode = dialog.exitCode
            when (exitCode) {
                DialogWrapper.OK_EXIT_CODE -> recreateIntellijApiKeyWithResult()
                2 -> {
                    val apiKey = dialog.getManualApiKey()
                    settingsService.setApiKey(apiKey)
                    PluginLogger.Settings.info("API key entered manually and saved")
                    refreshStatusBarWidget()
                }
            }
        }
    }



    private fun refreshStatusBarWidget() {
        ApplicationManager.getApplication().invokeLater({
            PluginLogger.Settings.debug("Refreshing status bar widget after API key update")
        }, com.intellij.openapi.application.ModalityState.any())
    }
}
