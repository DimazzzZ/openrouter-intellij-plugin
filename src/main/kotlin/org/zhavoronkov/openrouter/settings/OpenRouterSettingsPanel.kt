package org.zhavoronkov.openrouter.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ProviderInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.AbstractTableModel

/**
 * Table model for API keys
 */
class ApiKeyTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Label", "Name", "Usage", "Limit", "Status")
    private val apiKeys = mutableListOf<ApiKeyInfo>()

    override fun getRowCount(): Int = apiKeys.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val key = apiKeys[rowIndex]
        return when (columnIndex) {
            0 -> key.label
            1 -> key.name
            2 -> String.format(Locale.US, "$%.6f", key.usage)
            3 -> key.limit?.let { String.format(Locale.US, "$%.2f", it) } ?: "Unlimited"
            4 -> if (key.disabled) "Disabled" else "Enabled"
            else -> ""
        }
    }

    fun setApiKeys(keys: List<ApiKeyInfo>) {
        PluginLogger.Settings.debug("ApiKeysTableModel.setApiKeys called with ${keys.size} keys")

        // Ensure we're on EDT for UI updates
        if (!ApplicationManager.getApplication().isDispatchThread) {
            PluginLogger.Settings.debug("Not on EDT, scheduling setApiKeys on EDT")
            ApplicationManager.getApplication().invokeLater({
                setApiKeys(keys)
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            return
        }

        PluginLogger.Settings.debug("On EDT, updating table model")
        apiKeys.clear()
        apiKeys.addAll(keys)
        PluginLogger.Settings.debug("ApiKeysTableModel internal list now has ${apiKeys.size} keys")
        fireTableDataChanged()
        PluginLogger.Settings.debug("fireTableDataChanged() called - table should now show ${apiKeys.size} rows")
        PluginLogger.Settings.debug("Table model getRowCount() returns: ${getRowCount()}")
        PluginLogger.Settings.debug("First key details: ${if (keys.isNotEmpty()) keys[0] else "No keys"}")
    }

    fun getApiKeyAt(rowIndex: Int): ApiKeyInfo? {
        return if (rowIndex >= 0 && rowIndex < apiKeys.size) apiKeys[rowIndex] else null
    }

    fun removeApiKey(rowIndex: Int) {
        if (rowIndex >= 0 && rowIndex < apiKeys.size) {
            apiKeys.removeAt(rowIndex)
            fireTableRowsDeleted(rowIndex, rowIndex)
        }
    }

    fun getApiKeys(): List<ApiKeyInfo> {
        return apiKeys.toList()
    }
}

/**
 * Settings panel for OpenRouter configuration
 */
class OpenRouterSettingsPanel {

    private val panel: JPanel
    private val provisioningKeyField: JBPasswordField
    // TODO: Future version - Default model selection
    // private val defaultModelField: JBTextField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    private val apiKeyTableModel = ApiKeyTableModel()
    private val apiKeyTable = JBTable(apiKeyTableModel)
    // TODO: Future version - Providers list
    // private val providersTableModel = ProvidersTableModel()
    // private val providersTable = JBTable(providersTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private var isCreatingApiKey = false // Flag to prevent multiple creation attempts
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()
        PluginLogger.Settings.debug("Initializing OpenRouter Settings Panel")
        println("[OpenRouter] Settings panel initializing...") // Immediate console output

        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = 30  // Reasonable width for provisioning keys
        provisioningKeyField.preferredSize = Dimension(300, provisioningKeyField.preferredSize.height)

        // TODO: Future version - Default model selection
        // defaultModelField = JBTextField("openai/gpt-4o")
        // defaultModelField.columns = 18     // Reduced from 25 to be more responsive

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")

        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(300, 60, 3600, 60))

        showCostsCheckBox = JBCheckBox("Show costs in status bar")

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("Provisioning Key:"),
                createProvisioningKeyPanel(),
                1,
                false
            )
            // TODO: Future version - Default model selection
            // .addLabeledComponent(
            //     JBLabel("Default Model:"),
            //     defaultModelField,
            //     1,
            //     false
            // )
            .addComponent(autoRefreshCheckBox, 1)
            .addLabeledComponent(
                JBLabel("Refresh Interval (seconds):"),
                refreshIntervalSpinner,
                1,
                false
            )
            .addComponent(showCostsCheckBox, 1)
            .addVerticalGap(15)
            .addComponent(createApiKeyTablePanel(), 1)
            // TODO: Future version - Providers list
            // .addVerticalGap(15)
            // .addComponent(createProvidersTablePanel(), 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.border = JBUI.Borders.empty(10)

        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }
    }

    private fun createProvisioningKeyPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(provisioningKeyField, BorderLayout.CENTER)

        val helpLabel = JBLabel(
            "<html><small><b>Required for quota monitoring.</b> Get your provisioning key from " +
                "<a href='https://openrouter.ai/settings/provisioning-keys'>" +
                "openrouter.ai/settings/provisioning-keys</a></small></html>"
        )
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createApiKeyTablePanel(): JPanel {
        val panel = JPanel(BorderLayout())

        PluginLogger.Settings.debug("Creating API key table panel with JBTable")

        // Add table label according to JetBrains guidelines
        val tableLabel = JBLabel("API Keys List:")
        tableLabel.border = JBUI.Borders.emptyBottom(5)
        panel.add(tableLabel, BorderLayout.NORTH)

        // Configure table for full-width layout
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.fillsViewportHeight = true

        // Set column widths for better proportions
        apiKeyTable.columnModel.getColumn(0).preferredWidth = 120  // Label
        apiKeyTable.columnModel.getColumn(1).preferredWidth = 150  // Name
        apiKeyTable.columnModel.getColumn(2).preferredWidth = 80   // Usage
        apiKeyTable.columnModel.getColumn(3).preferredWidth = 80   // Limit
        apiKeyTable.columnModel.getColumn(4).preferredWidth = 70   // Status

        PluginLogger.Settings.debug("API key table configured with model: ${apiKeyTable.model}")
        PluginLogger.Settings.debug("Initial table row count: ${apiKeyTable.rowCount}")
        PluginLogger.Settings.debug("Initial model row count: ${apiKeyTableModel.rowCount}")

        // Create toolbar with add/remove buttons - full width
        val tablePanel = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeApiKey() }
            .setAddActionName("Add API Key")
            .setRemoveActionName("Remove API Key")
            .setPreferredSize(Dimension(-1, 150))  // Full width, fixed height
            .createPanel()

        panel.add(tablePanel, BorderLayout.CENTER)

        // Add refresh button and help text
        val bottomPanel = JPanel(BorderLayout())

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val refreshButton = JButton("Refresh API Keys")
        refreshButton.addActionListener { refreshApiKeys() }
        buttonPanel.add(refreshButton)
        bottomPanel.add(buttonPanel, BorderLayout.NORTH)

        val helpLabel = JBLabel(
            "<html><small>Manage your API keys. Automatically loads when Provisioning Key is configured.</small></html>"
        )
        helpLabel.border = JBUI.Borders.emptyTop(5)
        bottomPanel.add(helpLabel, BorderLayout.SOUTH)

        panel.add(bottomPanel, BorderLayout.SOUTH)

        // Reset the creation flag when panel is created
        resetApiKeyCreationFlag()

        // Note: refreshApiKeys() will be called by the configurable after settings are loaded

        return panel
    }

    // TODO: Future version - Providers list
    // private fun createProvidersTablePanel(): JPanel {
    //     val panel = JPanel(BorderLayout())
    //
    //     // Add table label according to JetBrains guidelines
    //     val tableLabel = JBLabel("Available Providers List:")
    //     tableLabel.border = JBUI.Borders.emptyBottom(5)
    //     panel.add(tableLabel, BorderLayout.NORTH)
    //
    //     // Configure providers table for full-width layout
    //     providersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    //     providersTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    //     providersTable.fillsViewportHeight = true
    //
    //     // Set column widths for better proportions
    //     providersTable.columnModel.getColumn(0).preferredWidth = 150  // Provider
    //     providersTable.columnModel.getColumn(1).preferredWidth = 80   // Status
    //     providersTable.columnModel.getColumn(2).preferredWidth = 120  // Privacy Policy
    //     providersTable.columnModel.getColumn(3).preferredWidth = 120  // Terms of Service
    //
    //     // Add double-click listener to open links
    //     providersTable.addMouseListener(object : MouseAdapter() {
    //         override fun mouseClicked(e: MouseEvent) {
    //             if (e.clickCount == 2) {
    //                 val row = providersTable.rowAtPoint(e.point)
    //                 val col = providersTable.columnAtPoint(e.point)
    //                 if (row >= 0 && col >= 2) { // Privacy Policy or Terms columns
    //                     val provider = providersTableModel.getProvider(row)
    //                     if (provider != null) {
    //                         val url = when (col) {
    //                             2 -> provider.privacyPolicyUrl
    //                             3 -> provider.termsOfServiceUrl
    //                             else -> null
    //                         }
    //                         if (url != null) {
    //                             BrowserUtil.browse(url)
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     })
    //
    //     val scrollPane = JBScrollPane(providersTable)
    //     scrollPane.preferredSize = Dimension(-1, 180)  // Full width, fixed height
    //     panel.add(scrollPane, BorderLayout.CENTER)
    //
    //     // Add refresh button and help text
    //     val bottomPanel = JPanel(BorderLayout())
    //
    //     val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
    //     val refreshButton = JButton("Refresh Providers")
    //     refreshButton.addActionListener { loadProviders() }
    //     buttonPanel.add(refreshButton)
    //     bottomPanel.add(buttonPanel, BorderLayout.NORTH)
    //
    //     val helpLabel = JBLabel(
    //         "<html><small>List of available AI providers on OpenRouter. Double-click on Privacy Policy or Terms to open links.</small></html>"
    //     )
    //     helpLabel.border = JBUI.Borders.emptyTop(5)
    //     bottomPanel.add(helpLabel, BorderLayout.SOUTH)
    //
    //     panel.add(bottomPanel, BorderLayout.SOUTH)
    //
    //     // Auto-load providers when panel is created
    //     loadProviders()
    //
    //     return panel
    // }

    private fun addApiKey() {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        val label = Messages.showInputDialog(
            "Enter a label for the new API key:",
            "Create API Key",
            Messages.getQuestionIcon()
        )

        if (label.isNullOrBlank()) {
            return
        }

        val limitInput = Messages.showInputDialog(
            "Enter credit limit (leave empty for unlimited):",
            "Set Credit Limit",
            Messages.getQuestionIcon()
        )

        val limit = if (limitInput.isNullOrBlank()) null else limitInput.toDoubleOrNull()

        openRouterService.createApiKey(label, limit).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater({
                if (response != null) {
                    PluginLogger.Settings.debug("API key created successfully: ${response.data.label}")
                    // Show the API key in a copyable dialog
                    showApiKeyDialog(response.key, response.data.label)
                    // Refresh the table to show the new key
                    PluginLogger.Settings.debug("Refreshing table after API key creation")
                    loadApiKeysWithoutAutoCreate()
                } else {
                    PluginLogger.Settings.warn("Failed to create API key - response was null")
                    Messages.showErrorDialog(
                        "Failed to create API key. Please check your Provisioning Key and try again.",
                        "Creation Failed"
                    )
                }
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
        }
    }

    /**
     * Show a dialog with the newly created API key in a copyable format
     */
    private fun showApiKeyDialog(apiKey: String, label: String) {
        PluginLogger.Settings.debug("Showing API key dialog for label: $label, key length: ${apiKey.length}")
        val dialog = object : DialogWrapper(true) {
            init {
                title = "API Key Created Successfully"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JBPanel<JBPanel<*>>(BorderLayout())
                panel.preferredSize = Dimension(500, 200)

                // Header message
                val headerLabel = JBLabel("<html><b>Your new API key has been created!</b><br/>" +
                    "Label: $label<br/><br/>" +
                    "⚠️ <b>Important:</b> Copy this key now - it will not be shown again.</html>")
                headerLabel.border = JBUI.Borders.empty(10)
                panel.add(headerLabel, BorderLayout.NORTH)

                // API key text field (read-only but copyable)
                val keyField = JBTextField(apiKey)
                keyField.isEditable = false
                keyField.selectAll() // Pre-select the text for easy copying
                keyField.border = JBUI.Borders.compound(
                    JBUI.Borders.empty(10),
                    JBUI.Borders.customLine(JBColor.GRAY, 1)
                )
                keyField.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                panel.add(keyField, BorderLayout.CENTER)

                // Copy button
                val copyButton = JButton("Copy to Clipboard")
                copyButton.addActionListener {
                    try {
                        PluginLogger.Settings.debug("Copying API key to clipboard: ${apiKey.take(10)}...")
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        val stringSelection = StringSelection(apiKey)
                        clipboard.setContents(stringSelection, null)

                        // Show brief confirmation
                        copyButton.text = "Copied!"
                        val timer = Timer(1500) {
                            copyButton.text = "Copy to Clipboard"
                        }
                        timer.isRepeats = false
                        timer.start()
                        PluginLogger.Settings.debug("API key copied to clipboard successfully")
                    } catch (e: Exception) {
                        PluginLogger.Settings.error("Failed to copy API key to clipboard", e)
                        Messages.showErrorDialog(
                            "Failed to copy to clipboard: ${e.message}",
                            "Copy Error"
                        )
                    }
                }

                val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout())
                buttonPanel.add(copyButton)
                panel.add(buttonPanel, BorderLayout.SOUTH)

                return panel
            }

            override fun createActions(): Array<Action> {
                return arrayOf(okAction)
            }
        }

        dialog.show()
    }

    private fun removeApiKey() {
        val selectedRow = apiKeyTable.selectedRow
        if (selectedRow < 0) {
            Messages.showErrorDialog(
                "Please select an API key to remove.",
                "No Selection"
            )
            return
        }

        val apiKey = apiKeyTableModel.getApiKeyAt(selectedRow)
        if (apiKey == null) {
            return
        }

        val result = Messages.showYesNoDialog(
            "Are you sure you want to delete the API key '${apiKey.label}' (${apiKey.name})?\n" +
                "This action cannot be undone.",
            "Confirm Deletion",
            Messages.getWarningIcon()
        )

        if (result == Messages.YES) {
            PluginLogger.Settings.debug("User confirmed deletion of API key: ${apiKey.label}")
            openRouterService.deleteApiKey(apiKey.hash).thenAccept { response ->
                ApplicationManager.getApplication().invokeLater({
                    PluginLogger.Settings.debug("Delete API key callback executed - response: ${if (response != null) "not null" else "null"}")
                    if (response != null && response.deleted) {
                        PluginLogger.Settings.debug("API key deleted successfully, refreshing table")
                        Messages.showInfoMessage(
                            "API Key '${apiKey.label}' has been deleted successfully.",
                            "API Key Deleted"
                        )
                        loadApiKeysWithoutAutoCreate()
                    } else {
                        PluginLogger.Settings.warn("Failed to delete API key - response: $response")
                        Messages.showErrorDialog(
                            "Failed to delete API key. Please try again.",
                            "Deletion Failed"
                        )
                    }
                }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            }.exceptionally { throwable ->
                PluginLogger.Settings.error("Exception during API key deletion", throwable)
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        "Error occurred while deleting API key: ${throwable.message}",
                        "Deletion Error"
                    )
                }, com.intellij.openapi.application.ModalityState.defaultModalityState())
                null
            }
        }
    }

    fun refreshApiKeys() {
        PluginLogger.Settings.debug("Refreshing API keys table")

        // Check if provisioning key is available (either in panel or saved settings)
        val currentProvisioningKey = getProvisioningKey()
        val savedProvisioningKey = settingsService.getProvisioningKey()
        val provisioningKey = if (currentProvisioningKey.isNotBlank()) currentProvisioningKey else savedProvisioningKey

        PluginLogger.Settings.debug("Current provisioning key from panel: ${if (currentProvisioningKey.isNotBlank()) "[PRESENT]" else "[EMPTY]"}")
        PluginLogger.Settings.debug("Saved provisioning key from settings: ${if (savedProvisioningKey.isNotBlank()) "[PRESENT]" else "[EMPTY]"}")
        PluginLogger.Settings.debug("Using provisioning key: ${if (provisioningKey.isNotBlank()) "[PRESENT]" else "[EMPTY]"}")

        if (provisioningKey.isBlank()) {
            PluginLogger.Settings.debug("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        PluginLogger.Settings.debug("Fetching API keys from OpenRouter with provisioning key: ${provisioningKey.take(10)}...")
        openRouterService.getApiKeysList(provisioningKey).thenAccept { response ->
            PluginLogger.Settings.debug("thenAccept callback executed - response is ${if (response != null) "not null" else "null"}")
            PluginLogger.Settings.debug("About to call ApplicationManager.invokeLater")
            ApplicationManager.getApplication().invokeLater({
                PluginLogger.Settings.debug("invokeLater callback executed - starting UI update")
                if (response != null) {
                    val apiKeys = response.data
                    PluginLogger.Settings.info("Successfully received ${apiKeys.size} API keys from OpenRouter")
                    PluginLogger.Settings.debug("About to call apiKeyTableModel.setApiKeys with ${apiKeys.size} keys")
                    apiKeyTableModel.setApiKeys(apiKeys)
                    PluginLogger.Settings.debug("After setApiKeys - table row count: ${apiKeyTable.rowCount}")
                    PluginLogger.Settings.debug("After setApiKeys - model row count: ${apiKeyTableModel.rowCount}")

                    // Check if IntelliJ IDEA Plugin API key exists and handle creation
                    ensureIntellijApiKeyExists(apiKeys)
                } else {
                    PluginLogger.Settings.warn("Failed to fetch API keys from OpenRouter - response was null")
                    apiKeyTableModel.setApiKeys(emptyList())
                    // Show user-friendly error message
                    Messages.showWarningDialog(
                        "Failed to load API keys. Please check your Provisioning Key and internet connection.",
                        "Load Failed"
                    )
                }
            }, com.intellij.openapi.application.ModalityState.any())
        }.exceptionally { throwable ->
            PluginLogger.Settings.error("Exception in getApiKeysList thenAccept callback", throwable)
            ApplicationManager.getApplication().invokeLater({
                apiKeyTableModel.setApiKeys(emptyList())
                Messages.showErrorDialog(
                    "Error loading API keys: ${throwable.message}",
                    "Load Error"
                )
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            null
        }
    }

    /**
     * Ensures that the IntelliJ IDEA Plugin API key exists, creating it only if necessary
     */
    private fun ensureIntellijApiKeyExists(currentApiKeys: List<ApiKeyInfo>) {
        // First check: Do we have a stored API key that might be valid?
        val storedApiKey = settingsService.getApiKey()
        if (storedApiKey.isNotBlank()) {
            PluginLogger.Settings.debug("Stored API key exists, assuming it's valid")
            return
        }

        // Second check: Does the API key already exist on the server?
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null) {
            PluginLogger.Settings.debug("IntelliJ IDEA Plugin API key exists on server but not stored locally: ${existingIntellijApiKey.name}")
            PluginLogger.Settings.warn("IntelliJ IDEA Plugin API key exists on server but the actual key value is not available. You may need to delete and recreate it.")

            // Show a dialog asking if the user wants to recreate the key
            val result = Messages.showYesNoDialog(
                "The 'IntelliJ IDEA Plugin' API key exists on the server but is not available locally.\n\n" +
                "This means the credits endpoint cannot be used. Would you like to recreate the API key?\n\n" +
                "Note: This will delete the existing key and create a new one.",
                "Recreate IntelliJ API Key?",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                recreateIntellijApiKey()
            }
            return // API key exists on server but we can't get the actual key value
        }

        // Third check: Are we already in the process of creating one?
        if (isCreatingApiKey) {
            PluginLogger.Settings.debug("API key creation already in progress, skipping")
            return
        }

        // Only now create the API key
        PluginLogger.Settings.debug("No IntelliJ IDEA Plugin API key found, creating one...")
        createIntellijApiKeyOnce()
    }

    private fun loadApiKeysWithoutAutoCreate() {
        PluginLogger.Settings.debug("Loading API keys without auto-creation")

        // Check if provisioning key is available (either in panel or saved settings)
        val currentProvisioningKey = getProvisioningKey()
        val savedProvisioningKey = settingsService.getProvisioningKey()
        val provisioningKey = if (currentProvisioningKey.isNotBlank()) currentProvisioningKey else savedProvisioningKey

        if (provisioningKey.isBlank()) {
            PluginLogger.Settings.debug("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        openRouterService.getApiKeysList(provisioningKey).thenAccept { response ->
            PluginLogger.Settings.debug("refreshApiKeys thenAccept callback executed - response is ${if (response != null) "not null" else "null"}")
            ApplicationManager.getApplication().invokeLater({
                PluginLogger.Settings.debug("refreshApiKeys invokeLater callback executed")
                if (response != null) {
                    val apiKeys = response.data
                    PluginLogger.Settings.info("Received ${apiKeys.size} API keys from OpenRouter")
                    PluginLogger.Settings.debug("Setting API keys in table model: ${apiKeys.map { it.name }}")
                    apiKeyTableModel.setApiKeys(apiKeys)
                    PluginLogger.Settings.debug("Table model now has ${apiKeyTableModel.rowCount} rows")
                    PluginLogger.Settings.debug("Table component row count: ${apiKeyTable.rowCount}")
                } else {
                    PluginLogger.Settings.warn("Failed to fetch API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(emptyList())
                }
            }, com.intellij.openapi.application.ModalityState.any())
        }.exceptionally { throwable ->
            PluginLogger.Settings.error("Exception in refreshApiKeys thenAccept callback", throwable)
            ApplicationManager.getApplication().invokeLater({
                apiKeyTableModel.setApiKeys(emptyList())
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            null
        }
    }

    private fun createIntellijApiKeyOnce() {
        // Set the flag to prevent multiple creation attempts
        isCreatingApiKey = true

        PluginLogger.Settings.debug("Attempting to create IntelliJ IDEA Plugin API key (once)")

        val provisioningKey = getProvisioningKey()
        if (provisioningKey.isBlank()) {
            PluginLogger.Settings.warn("Cannot create API key: no provisioning key available")
            isCreatingApiKey = false
            return
        }

        openRouterService.createApiKey(INTELLIJ_API_KEY_NAME).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater({
                try {
                    if (response != null) {
                        PluginLogger.Settings.info("Successfully created IntelliJ IDEA Plugin API key: ${response.data.name}")

                        // Store the API key securely
                        settingsService.setApiKey(response.key)

                        // Show the API key creation dialog
                        showApiKeyDialog(response.key, INTELLIJ_API_KEY_NAME)

                        // Refresh the table to show the new key (but don't create another one)
                        loadApiKeysWithoutAutoCreate()
                    } else {
                        PluginLogger.Settings.warn("Failed to create IntelliJ IDEA Plugin API key")
                        Messages.showWarningDialog(
                            "Failed to automatically create '$INTELLIJ_API_KEY_NAME' API key. " +
                                "Please check your provisioning key and try again.",
                            "Auto-creation Failed"
                        )
                    }
                } finally {
                    // Always reset the flag when done
                    isCreatingApiKey = false
                }
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
        }.exceptionally { throwable ->
            ApplicationManager.getApplication().invokeLater({
                PluginLogger.Settings.error("Error creating IntelliJ IDEA Plugin API key: ${throwable.message}")
                isCreatingApiKey = false
            }, com.intellij.openapi.application.ModalityState.defaultModalityState())
            null
        }
    }

    /**
     * Recreates the IntelliJ IDEA Plugin API key by first deleting the existing one
     */
    private fun recreateIntellijApiKey() {
        val currentApiKeys = apiKeyTableModel.getApiKeys()
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null) {
            PluginLogger.Settings.debug("Deleting existing IntelliJ IDEA Plugin API key before recreating")

            val provisioningKey = getProvisioningKey()
            if (provisioningKey.isBlank()) {
                PluginLogger.Settings.warn("Cannot recreate API key: no provisioning key available")
                return
            }

            openRouterService.deleteApiKey(existingIntellijApiKey.hash)
                .thenAccept { deleteResponse ->
                    ApplicationManager.getApplication().invokeLater({
                        if (deleteResponse?.deleted == true) {
                            PluginLogger.Settings.debug("Existing IntelliJ IDEA Plugin API key deleted, now creating new one")
                            // Clear the stored API key
                            settingsService.setApiKey("")
                            // Create a new one
                            createIntellijApiKeyOnce()
                        } else {
                            PluginLogger.Settings.error("Failed to delete existing IntelliJ IDEA Plugin API key")
                            Messages.showErrorDialog(
                                "Failed to delete the existing 'IntelliJ IDEA Plugin' API key. Please try again.",
                                "Deletion Failed"
                            )
                        }
                    }, com.intellij.openapi.application.ModalityState.defaultModalityState())
                }
                .exceptionally { throwable ->
                    ApplicationManager.getApplication().invokeLater({
                        PluginLogger.Settings.error("Error deleting existing IntelliJ IDEA Plugin API key: ${throwable.message}")
                        Messages.showErrorDialog(
                            "Error occurred while deleting the existing API key: ${throwable.message}",
                            "Error"
                        )
                    }, com.intellij.openapi.application.ModalityState.defaultModalityState())
                    null
                }
        } else {
            // No existing key, just create a new one
            createIntellijApiKeyOnce()
        }
    }

    /**
     * Reset the API key creation flag - useful when settings panel is reopened
     */
    private fun resetApiKeyCreationFlag() {
        isCreatingApiKey = false
        PluginLogger.Settings.debug("Reset API key creation flag")
    }

    // TODO: Future version - Providers list
    // /**
    //  * Load providers list from OpenRouter
    //  */
    // private fun loadProviders() {
    //     PluginLogger.Settings.debug("Loading providers list from OpenRouter API")
    //
    //     openRouterService.getProviders().thenAccept { response ->
    //         ApplicationManager.getApplication().invokeLater({
    //             if (response != null && response.data.isNotEmpty()) {
    //                 PluginLogger.Settings.info("Successfully loaded ${response.data.size} providers from OpenRouter")
    //                 PluginLogger.Settings.debug("About to call providersTableModel.setProviders with ${response.data.size} providers")
    //                 providersTableModel.setProviders(response.data)
    //                 PluginLogger.Settings.debug("After setProviders - providers table row count: ${providersTable.rowCount}")
    //                 PluginLogger.Settings.debug("After setProviders - providers model row count: ${providersTableModel.rowCount}")
    //             } else {
    //                 PluginLogger.Settings.warn("Failed to load providers - response was null or empty")
    //                 providersTableModel.setProviders(emptyList())
    //                 Messages.showWarningDialog(
    //                     "Failed to load providers list. Please check your internet connection and try again.",
    //                     "Load Failed"
    //                 )
    //             }
    //         }, com.intellij.openapi.application.ModalityState.any())
    //     }.exceptionally { throwable ->
    //         ApplicationManager.getApplication().invokeLater({
    //             PluginLogger.Settings.error("Exception while loading providers", throwable)
    //             providersTableModel.setProviders(emptyList())
    //             Messages.showErrorDialog(
    //                 "Error loading providers: ${throwable.message}",
    //                 "Load Error"
    //             )
    //         }, com.intellij.openapi.application.ModalityState.any())
    //         null
    //     }
    // }

    fun getPanel(): JPanel = panel

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
        // Refresh tables when provisioning key is set
        if (provisioningKey.isNotBlank()) {
            refreshApiKeys()
        }
    }

    // TODO: Future version - Default model selection
    // fun getDefaultModel(): String = defaultModelField.text
    //
    // fun setDefaultModel(model: String) {
    //     defaultModelField.text = model
    // }

    fun isAutoRefreshEnabled(): Boolean = autoRefreshCheckBox.isSelected

    fun setAutoRefresh(enabled: Boolean) {
        autoRefreshCheckBox.isSelected = enabled
        refreshIntervalSpinner.isEnabled = enabled
    }

    fun getRefreshInterval(): Int = refreshIntervalSpinner.value as Int

    fun setRefreshInterval(interval: Int) {
        refreshIntervalSpinner.value = interval
    }

    fun shouldShowCosts(): Boolean = showCostsCheckBox.isSelected

    fun setShowCosts(show: Boolean) {
        showCostsCheckBox.isSelected = show
    }
}

// TODO: Future version - Providers list
// /**
//  * Table model for displaying OpenRouter providers
//  */
// class ProvidersTableModel : AbstractTableModel() {
//     private var providers: List<ProviderInfo> = emptyList()
//
//     private val columnNames = arrayOf("Provider", "Status", "Privacy Policy", "Terms of Service")
//
//     override fun getRowCount(): Int = providers.size
//
//     override fun getColumnCount(): Int = columnNames.size
//
//     override fun getColumnName(column: Int): String = columnNames[column]
//
//     override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
//         val provider = providers[rowIndex]
//         return when (columnIndex) {
//             0 -> provider.name
//             1 -> "Available" // All providers in the list are available
//             2 -> if (provider.privacyPolicyUrl != null) "View" else "-"
//             3 -> if (provider.termsOfServiceUrl != null) "View" else "-"
//             else -> ""
//         }
//     }
//
//     override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
//
//     fun setProviders(newProviders: List<ProviderInfo>) {
//         PluginLogger.Settings.debug("ProvidersTableModel.setProviders called with ${newProviders.size} providers")
//
//         // Ensure we're on EDT for UI updates
//         if (!ApplicationManager.getApplication().isDispatchThread) {
//             PluginLogger.Settings.debug("Not on EDT, scheduling setProviders on EDT")
//             ApplicationManager.getApplication().invokeLater({
//                 setProviders(newProviders)
//             }, com.intellij.openapi.application.ModalityState.defaultModalityState())
//             return
//         }
//
//         PluginLogger.Settings.debug("On EDT, updating providers table model")
//         providers = newProviders
//         PluginLogger.Settings.debug("ProvidersTableModel internal list now has ${providers.size} providers")
//         fireTableDataChanged()
//         PluginLogger.Settings.debug("fireTableDataChanged() called - providers table should now show ${providers.size} rows")
//         PluginLogger.Settings.debug("Providers table model getRowCount() returns: ${getRowCount()}")
//         PluginLogger.Settings.debug("First provider details: ${if (newProviders.isNotEmpty()) newProviders[0].name else "No providers"}")
//     }
//
//     fun getProvider(rowIndex: Int): ProviderInfo? {
//         return if (rowIndex >= 0 && rowIndex < providers.size) {
//             providers[rowIndex]
//         } else {
//             null
//         }
//     }
// }
