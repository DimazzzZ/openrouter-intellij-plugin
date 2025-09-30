package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.JButton
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
 * Settings panel for OpenRouter configuration using IntelliJ UI DSL v2
 */
class OpenRouterSettingsPanel {

    private val panel: JPanel
    private val provisioningKeyField: JBPasswordField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    private val apiKeyTableModel = ApiKeyTableModel()
    private val apiKeyTable = JBTable(apiKeyTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val proxyService = OpenRouterProxyService.getInstance()

    // State tracking
    private var isCreatingApiKey = false

    // Helper classes for managing different aspects
    private val apiKeyManager: ApiKeyManager
    private val proxyServerManager: ProxyServerManager

    // Status components for AI Assistant Integration
    private val statusLabel = JBLabel()

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private const val DEFAULT_REFRESH_INTERVAL = 30
        private const val MIN_REFRESH_INTERVAL = 10
        private const val MAX_REFRESH_INTERVAL = 300
        private const val REFRESH_INTERVAL_STEP = 10
        private const val LABEL_COLUMN_WIDTH = 170
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()
        PluginLogger.Settings.debug("Initializing OpenRouter Settings Panel")
        println("[OpenRouter] Settings panel initializing...") // Immediate console output

        // Initialize components
        provisioningKeyField = JBPasswordField().apply {
            columns = 32
        }

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")

        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(
            DEFAULT_REFRESH_INTERVAL,
            MIN_REFRESH_INTERVAL,
            MAX_REFRESH_INTERVAL,
            REFRESH_INTERVAL_STEP
        ))

        showCostsCheckBox = JBCheckBox("Show costs in status bar")

        // Configure API key table
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.fillsViewportHeight = true

        // Initialize helper classes
        apiKeyManager = ApiKeyManager(settingsService, openRouterService, apiKeyTable, apiKeyTableModel)
        proxyServerManager = ProxyServerManager(proxyService, settingsService)

        // Create the main panel using UI DSL v2
        panel = panel {
            // Provisioning group
            group("Provisioning") {
                row("Provisioning Key:") {
                    cell(provisioningKeyField)
                        .resizableColumn()
                    button("Paste") { pasteFromClipboard() }
                }.layout(RowLayout.PARENT_GRID)

                row {
                    comment("Required for quota monitoring.")
                }

                row {
                    comment("Get your key from <a href=\"https://openrouter.ai/settings/provisioning-keys\">OpenRouter Provisioning Keys</a>")
                        .component.addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                BrowserUtil.browse("https://openrouter.ai/settings/provisioning-keys")
                            }
                        })
                }
            }

            // General Settings group
            group("General Settings") {
                row("Refresh interval (seconds):") {
                    cell(refreshIntervalSpinner)
                }.layout(RowLayout.PARENT_GRID)

                row("") {
                    cell(autoRefreshCheckBox)
                    cell(showCostsCheckBox)
                }.layout(RowLayout.PARENT_GRID)
            }

            // API Keys group
            group("API Keys") {
                row("") {
                    val toolbar = createApiKeysToolbar()
                    cell(toolbar.createPanel())
                        .resizableColumn()
                    button("Refresh") { refreshApiKeysWithValidation() }
                }.layout(RowLayout.PARENT_GRID)

                row {
                    scrollCell(apiKeyTable)
                        .align(AlignX.FILL)
                        .align(AlignY.FILL)
                }.resizableRow()

                row {
                    comment("Keys load automatically when Provisioning Key is configured.")
                }
            }

            // Favorite Models group
            group("Favorite Models") {
                row {
                    cell(FavoriteModelsPanel())
                        .align(AlignX.FILL)
                        .align(AlignY.FILL)
                }.resizableRow()

                row {
                    comment("Manage your favorite models that will appear in AI Assistant. Only favorite models are shown to keep the list manageable.")
                }
            }

            // AI Assistant Integration group
            group("AI Assistant Integration") {
                row("Status:") {
                    cell(statusLabel)
                }.layout(RowLayout.PARENT_GRID)

                row("") {
                    button("Start Proxy Server") { startProxyServer() }
                    button("Stop Proxy Server") { stopProxyServer() }
                    button("Copy URL") { copyProxyUrl() }
                    button("Enter API Key Manually") { enterApiKeyManually() }
                }.layout(RowLayout.PARENT_GRID)

                row {
                    comment("Copy the URL above and paste it as the Base URL in AI Assistant settings:<br>" +
                            "<b>Tools > AI Assistant > Models > Add Model > Custom OpenAI-compatible</b>")
                }
            }
        }

        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }

        // Initialize status
        updateProxyStatus()
    }

    private fun createApiKeysToolbar(): ToolbarDecorator {
        return ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeSelectedApiKey() }
            .disableUpDownActions()
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            if (data != null) {
                provisioningKeyField.text = data.trim()
            }
        } catch (e: Exception) {
            PluginLogger.Settings.warn("Failed to paste from clipboard: ${e.message}")
        }
    }

    private fun updateProxyStatus() {
        proxyServerManager.updateProxyStatusLabel(statusLabel)
        // Force UI repaint to show changes immediately
        statusLabel.repaint()
        statusLabel.revalidate()
        panel.repaint()
    }

    private fun startProxyServer() {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val success = proxyService.startServer().get()
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    if (!success) {
                        Messages.showErrorDialog(
                            "Failed to start proxy server. Check logs for details.",
                            "Proxy Start Failed"
                        )
                    }
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    Messages.showErrorDialog(
                        "Failed to start proxy server: ${e.message}",
                        "Proxy Start Failed"
                    )
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    private fun stopProxyServer() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                proxyService.stopServer().get()
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                }, com.intellij.openapi.application.ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    updateProxyStatus()
                    Messages.showErrorDialog(
                        "Failed to stop proxy server: ${e.message}",
                        "Proxy Stop Failed"
                    )
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }

    private fun copyProxyUrl() {
        val status = proxyService.getServerStatus()
        if (status.isRunning && status.url != null) {
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val stringSelection = java.awt.datatransfer.StringSelection(status.url)
                clipboard.setContents(stringSelection, null)

                Messages.showInfoMessage(
                    "Proxy URL copied to clipboard: ${status.url}\n\n" +
                    "Paste this as the Base URL in AI Assistant settings:\n" +
                    "Tools > AI Assistant > Models > Add Model > Custom OpenAI-compatible",
                    "URL Copied"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "Failed to copy URL to clipboard: ${e.message}",
                    "Copy Failed"
                )
            }
        } else {
            Messages.showWarningDialog(
                "Proxy server is not running. Please start the proxy server first.",
                "Server Not Running"
            )
        }
    }

    private fun showConfigurationInstructions() {
        val instructions = proxyService.getAIAssistantConfigurationInstructions()
        Messages.showInfoMessage(instructions, "AI Assistant Configuration")
    }

    private fun addApiKey() {
        apiKeyManager.addApiKey()
    }

    private fun removeSelectedApiKey() {
        apiKeyManager.removeApiKey()
    }

    private fun enterApiKeyManually() {
        val apiKey = Messages.showInputDialog(
            "Enter your OpenRouter API key:\n\n" +
            "This should be the actual API key value (starting with 'sk-or-v1-').\n" +
            "You can find this in your OpenRouter dashboard.",
            "Enter API Key Manually",
            Messages.getQuestionIcon()
        )

        if (!apiKey.isNullOrBlank()) {
            // Validate the API key format
            if (apiKey.startsWith("sk-or-v1-") && apiKey.length > 20) {
                // Store the API key
                settingsService.setApiKey(apiKey)
                // Don't show success dialog or refresh table to avoid cascading dialogs
                // The user can manually refresh if needed
            } else {
                Messages.showErrorDialog(
                    "The entered API key doesn't appear to be valid. OpenRouter API keys should start with 'sk-or-v1-' and be longer than 20 characters.",
                    "Invalid API Key Format"
                )
            }
        }
    }

    fun refreshApiKeys() {
        // Use loadApiKeysWithoutAutoCreate to avoid triggering validation dialogs
        // Only the manual "Refresh" button should trigger full validation
        apiKeyManager.loadApiKeysWithoutAutoCreate()
    }

    fun refreshApiKeysWithValidation() {
        // This method is for the manual "Refresh" button that should trigger validation
        apiKeyManager.refreshApiKeys()
    }



    // Public interface methods for the configurable

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
        // Load API keys without triggering validation dialogs when setting provisioning key
        if (provisioningKey.isNotBlank()) {
            apiKeyManager.loadApiKeysWithoutAutoCreate()
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



