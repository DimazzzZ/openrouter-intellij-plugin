package org.zhavoronkov.openrouter.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
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
import javax.swing.BoxLayout
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
    private val proxyService = OpenRouterProxyService.getInstance()

    // Helper classes for managing different aspects
    private val apiKeyManager: ApiKeyManager
    private val proxyServerManager: ProxyServerManager

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private const val DEFAULT_REFRESH_INTERVAL = 30
        private const val MIN_REFRESH_INTERVAL = 10
        private const val MAX_REFRESH_INTERVAL = 300
        private const val REFRESH_INTERVAL_STEP = 10
        private const val PROVISIONING_KEY_FIELD_COLUMNS = 30
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()
        PluginLogger.Settings.debug("Initializing OpenRouter Settings Panel")
        println("[OpenRouter] Settings panel initializing...") // Immediate console output

        // Initialize helper classes
        apiKeyManager = ApiKeyManager(settingsService, openRouterService, apiKeyTable, apiKeyTableModel)
        proxyServerManager = ProxyServerManager(proxyService, settingsService)

        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = PROVISIONING_KEY_FIELD_COLUMNS
        provisioningKeyField.preferredSize = Dimension(300, provisioningKeyField.preferredSize.height)

        // TODO: Future version - Default model selection
        // defaultModelField = JBTextField("openai/gpt-4o")
        // defaultModelField.columns = 18     // Reduced from 25 to be more responsive

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")

        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(
            DEFAULT_REFRESH_INTERVAL,
            MIN_REFRESH_INTERVAL,
            MAX_REFRESH_INTERVAL,
            REFRESH_INTERVAL_STEP
        ))

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
            .addVerticalGap(15)
            .addComponent(createAIAssistantIntegrationPanel(), 1)
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

    private fun createAIAssistantIntegrationPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Add section label
        val sectionLabel = JBLabel("AI Assistant Integration:")
        sectionLabel.border = JBUI.Borders.emptyBottom(10)
        panel.add(sectionLabel, BorderLayout.NORTH)

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

        // Status display
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val statusLabel = JBLabel("Proxy Server Status: ")
        val statusValueLabel = JBLabel()
        updateProxyStatusLabel(statusValueLabel)
        statusPanel.add(statusLabel)
        statusPanel.add(statusValueLabel)
        contentPanel.add(statusPanel)

        // Control buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val startButton = JButton("Start Proxy Server")
        val stopButton = JButton("Stop Proxy Server")
        val instructionsButton = JButton("Show Configuration Instructions")

        startButton.addActionListener { startProxyServer(statusValueLabel, startButton, stopButton) }
        stopButton.addActionListener { stopProxyServer(statusValueLabel, startButton, stopButton) }
        instructionsButton.addActionListener { showConfigurationInstructions() }

        buttonPanel.add(startButton)
        buttonPanel.add(stopButton)
        buttonPanel.add(instructionsButton)
        contentPanel.add(buttonPanel)

        // Update button states
        updateProxyButtons(startButton, stopButton)

        // Help text
        val helpLabel = JBLabel(
            "<html><small>The proxy server allows JetBrains AI Assistant to use OpenRouter models.<br/>" +
            "Start the server and configure AI Assistant to connect to the provided URL.</small></html>"
        )
        helpLabel.border = JBUI.Borders.emptyTop(10)
        contentPanel.add(helpLabel)

        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    private fun updateProxyStatusLabel(statusLabel: JBLabel) {
        val status = proxyService.getServerStatus()
        if (status.isRunning) {
            statusLabel.text = "Running on port ${status.port}"
            statusLabel.foreground = JBColor.GREEN
        } else {
            statusLabel.text = "Stopped"
            statusLabel.foreground = JBColor.RED
        }
    }

    private fun updateProxyButtons(startButton: JButton, stopButton: JButton) {
        val status = proxyService.getServerStatus()
        val isConfigured = settingsService.isConfigured()

        startButton.isEnabled = !status.isRunning && isConfigured
        stopButton.isEnabled = status.isRunning

        if (!isConfigured) {
            startButton.toolTipText = "Configure OpenRouter first"
        } else {
            startButton.toolTipText = null
        }
    }

    private fun startProxyServer(statusLabel: JBLabel, startButton: JButton, stopButton: JButton) {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        startButton.isEnabled = false
        startButton.text = "Starting..."

        proxyService.startServer().thenAccept { success ->
            ApplicationManager.getApplication().invokeLater {
                if (success) {
                    updateProxyStatusLabel(statusLabel)
                    updateProxyButtons(startButton, stopButton)
                    startButton.text = "Start Proxy Server"

                    val status = proxyService.getServerStatus()
                    Messages.showInfoMessage(
                        "Proxy server started successfully on port ${status.port}.\n\n" +
                        "You can now configure AI Assistant to use: ${status.url}",
                        "Proxy Server Started"
                    )
                } else {
                    startButton.isEnabled = true
                    startButton.text = "Start Proxy Server"
                    Messages.showErrorDialog(
                        "Failed to start proxy server. Please check the logs for details.",
                        "Proxy Server Error"
                    )
                }
            }
        }
    }

    private fun stopProxyServer(statusLabel: JBLabel, startButton: JButton, stopButton: JButton) {
        stopButton.isEnabled = false
        stopButton.text = "Stopping..."

        proxyService.stopServer().thenAccept { success ->
            ApplicationManager.getApplication().invokeLater {
                updateProxyStatusLabel(statusLabel)
                updateProxyButtons(startButton, stopButton)
                stopButton.text = "Stop Proxy Server"

                if (success) {
                    Messages.showInfoMessage(
                        "Proxy server stopped successfully.",
                        "Proxy Server Stopped"
                    )
                } else {
                    Messages.showErrorDialog(
                        "Failed to stop proxy server. Please check the logs for details.",
                        "Proxy Server Error"
                    )
                }
            }
        }
    }

    private fun showConfigurationInstructions() {
        val instructions = proxyService.getAIAssistantConfigurationInstructions()
        Messages.showInfoMessage(instructions, "AI Assistant Configuration")
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
        apiKeyManager.addApiKey()
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
        apiKeyManager.removeApiKey()
    }

    fun refreshApiKeys() {
        apiKeyManager.refreshApiKeys()
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

            // Show a dialog asking if the user wants to recreate the key or enter manually
            showRecreateApiKeyDialog()
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

                        // Refresh the status bar to show the new connection status
                        refreshStatusBarWidget()
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

    /**
     * Show a dialog with options to recreate API key or enter manually
     */
    private fun showRecreateApiKeyDialog() {
        val dialog = object : com.intellij.openapi.ui.DialogWrapper(true) {
            init {
                title = "Recreate IntelliJ API Key?"
                init()
            }

            override fun createCenterPanel(): javax.swing.JComponent {
                val panel = com.intellij.ui.components.JBPanel<com.intellij.ui.components.JBPanel<*>>(java.awt.BorderLayout())
                panel.preferredSize = java.awt.Dimension(500, 150)

                val messageLabel = com.intellij.ui.components.JBLabel(
                    "<html>The 'IntelliJ IDEA Plugin' API key exists on the server but is not available locally.<br/><br/>" +
                    "This means the credits endpoint cannot be used. You can:<br/>" +
                    "• <b>Recreate</b> - Delete the existing key and create a new one<br/>" +
                    "• <b>Enter Manually</b> - If you have the API key value, enter it manually<br/>" +
                    "• <b>Cancel</b> - Do nothing for now</html>"
                )
                messageLabel.border = com.intellij.util.ui.JBUI.Borders.empty(10)
                panel.add(messageLabel, java.awt.BorderLayout.CENTER)

                return panel
            }

            override fun createActions(): Array<javax.swing.Action> {
                val recreateAction = object : com.intellij.openapi.ui.DialogWrapper.DialogWrapperAction("Recreate") {
                    override fun doAction(e: java.awt.event.ActionEvent) {
                        close(1) // Custom exit code for recreate
                    }
                }

                val enterManuallyAction = object : com.intellij.openapi.ui.DialogWrapper.DialogWrapperAction("Enter Manually") {
                    override fun doAction(e: java.awt.event.ActionEvent) {
                        close(2) // Custom exit code for manual entry
                    }
                }

                return arrayOf(recreateAction, enterManuallyAction, cancelAction)
            }
        }

        val result = dialog.showAndGet()
        when (dialog.exitCode) {
            1 -> recreateIntellijApiKey() // Recreate
            2 -> {
                val success = showManualApiKeyEntryDialog() // Enter manually
                if (success) {
                    // Don't refresh the table - the user manually entered the key for the existing server key
                    // Just log that the manual entry was successful and refresh the status bar
                    PluginLogger.Settings.info("Manual API key entry completed successfully - no server operations needed")
                    refreshStatusBarWidget()
                }
            }
            // 0 or any other value = Cancel (do nothing)
        }
    }

    /**
     * Show a dialog for manual API key entry
     * @return true if API key was successfully entered and saved, false otherwise
     */
    private fun showManualApiKeyEntryDialog(): Boolean {
        val apiKey = com.intellij.openapi.ui.Messages.showInputDialog(
            "Enter your OpenRouter API key for the 'IntelliJ IDEA Plugin':\n\n" +
            "This should be the actual API key value (starting with 'sk-or-v1-').\n" +
            "You can find this in your OpenRouter dashboard if you saved it when the key was created.",
            "Enter API Key Manually",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )

        if (!apiKey.isNullOrBlank()) {
            // Validate the API key format
            if (apiKey.startsWith("sk-or-v1-") && apiKey.length > 20) {
                // Store the API key
                settingsService.setApiKey(apiKey)
                com.intellij.openapi.ui.Messages.showInfoMessage(
                    "API key has been saved successfully. The plugin can now access OpenRouter credits information.\n\n" +
                    "Note: The existing API key on the server has been preserved.",
                    "API Key Saved"
                )
                PluginLogger.Settings.info("API key entered manually and saved - existing server key preserved")
                return true
            } else {
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    "The entered API key doesn't appear to be valid. OpenRouter API keys should start with 'sk-or-v1-' and be longer than 20 characters.",
                    "Invalid API Key Format"
                )
                return false
            }
        }
        return false // User cancelled or entered empty key
    }

    /**
     * Refresh the status bar widget to reflect updated connection status
     */
    private fun refreshStatusBarWidget() {
        // Use invokeLater with proper modality state to avoid write-unsafe context errors
        ApplicationManager.getApplication().invokeLater({
            try {
                // Get all open projects and refresh their status bar widgets
                val projectManager = com.intellij.openapi.project.ProjectManager.getInstance()
                projectManager.openProjects.forEach { project ->
                    val statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project)
                    val widget = statusBar?.getWidget(org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget.ID)
                        as? org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget
                    widget?.updateQuotaInfo()
                    PluginLogger.Settings.debug("Refreshed status bar widget for project: ${project.name}")
                }
            } catch (e: Exception) {
                PluginLogger.Settings.error("Error refreshing status bar widget: ${e.message}", e)
            }
        }, com.intellij.openapi.application.ModalityState.defaultModalityState())
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
