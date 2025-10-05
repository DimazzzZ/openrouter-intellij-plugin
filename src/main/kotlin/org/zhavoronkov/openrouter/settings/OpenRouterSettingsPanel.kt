package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.GotItTooltip
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import com.intellij.ui.table.TableView
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.ColumnInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.swing.*
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
            0 -> key.label ?: ""
            1 -> key.name ?: ""
            2 -> formatUsage(key.usage)
            3 -> formatLimit(key.limit)
            4 -> if (!key.disabled) "Active" else "Inactive"
            else -> ""
        }
    }

    private fun formatUsage(usage: Double?): String {
        return if (usage != null) {
            "$%.4f".format(usage)
        } else {
            "N/A"
        }
    }

    private fun formatLimit(limit: Double?): String {
        return if (limit != null) {
            "$%.2f".format(limit)
        } else {
            "Unlimited"
        }
    }

    fun setApiKeys(keys: List<ApiKeyInfo>) {
        apiKeys.clear()
        apiKeys.addAll(keys)
        fireTableDataChanged()
    }

    fun getApiKeyAt(index: Int): ApiKeyInfo? {
        return if (index >= 0 && index < apiKeys.size) apiKeys[index] else null
    }

    fun removeApiKey(index: Int) {
        if (index >= 0 && index < apiKeys.size) {
            apiKeys.removeAt(index)
            fireTableRowsDeleted(index, index)
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

    // API Keys panel
    private val apiKeysPanel = JPanel(BorderLayout())

    // State tracking
    private var isCreatingApiKey = false

    // Helper classes for managing different aspects
    private val apiKeyManager: ApiKeyManager

    // Proxy server status components
    private lateinit var statusLabel: JLabel
    private lateinit var startServerButton: JButton
    private lateinit var stopServerButton: JButton
    private lateinit var copyUrlButton: JButton

    companion object {
        private const val DEFAULT_REFRESH_INTERVAL = 300
        private const val MIN_REFRESH_INTERVAL = 60
        private const val MAX_REFRESH_INTERVAL = 300
        private const val REFRESH_INTERVAL_STEP = 10
        private const val LABEL_COLUMN_WIDTH = 170
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()

        val isHeadless = java.awt.GraphicsEnvironment.isHeadless()
        PluginLogger.Settings.debug("OpenRouter Settings Panel INITIALIZED (headless: $isHeadless)")

        // Initialize components
        provisioningKeyField = JBPasswordField().apply {
            columns = 32
        }

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")
        showCostsCheckBox = JBCheckBox("Show costs in status bar")

        refreshIntervalSpinner = JSpinner(SpinnerNumberModel(
            DEFAULT_REFRESH_INTERVAL,
            MIN_REFRESH_INTERVAL,
            MAX_REFRESH_INTERVAL,
            REFRESH_INTERVAL_STEP
        ))

        // Configure API key table
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.fillsViewportHeight = true

        // Setup API Keys panel
        setupApiKeysPanel()

        // Initialize API key manager
        apiKeyManager = ApiKeyManager(
            settingsService,
            openRouterService,
            apiKeyTable,
            apiKeyTableModel
        )

        // Initialize proxy status components
        statusLabel = JLabel("Status: Stopped")

        // Create the main panel using UI DSL v2
        panel = panel {
            // Provisioning group
            group("Provisioning") {
                row("Provisioning Key:") {
                    cell(provisioningKeyField)
                        .resizableColumn()
                        .columns(32)
                    button("Paste") { pasteFromClipboard() }
                }.layout(RowLayout.PARENT_GRID)

                row {
                    comment("Required for quota monitoring and API keys management.")
                }

                row {
                    link("Get your key from OpenRouter Provisioning Keys") {
                        BrowserUtil.browse("https://openrouter.ai/settings/provisioning-keys")
                    }
                }
            }

            // General Settings group
            group("General Settings") {
                row("Refresh interval (seconds):") {
                    cell(refreshIntervalSpinner)
                }.layout(RowLayout.PARENT_GRID)

                row {
                    cell(autoRefreshCheckBox)
                    cell(showCostsCheckBox)
                }.layout(RowLayout.PARENT_GRID)
            }

            // API Keys group
            group("API Keys") {
                row {
                    comment("Keys load automatically when Provisioning Key is configured.")
                }

                row {
                    cell(apiKeysPanel)
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow()
            }

            // Proxy Server group
            group("Proxy Server") {
                // Proxy server controls
                row("Status:") {
                    cell(statusLabel)
                }.layout(RowLayout.PARENT_GRID)

                row {
                    startServerButton = button("Start Server") { startProxyServer() }.component
                    stopServerButton = button("Stop Server") { stopProxyServer() }.component
                    copyUrlButton = button("Copy Proxy URL") { copyProxyUrlToClipboard() }.component
                }.layout(RowLayout.PARENT_GRID)
            }
        }

        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }

        // Initialize status
        updateProxyStatus()

        // Show GotIt tooltips for first-time users
        showGotItTooltips()
    }

    /**
     * Show GotIt tooltips for first-time users
     * Note: Tooltips are shown asynchronously to avoid blocking UI initialization
     */
    private fun showGotItTooltips() {
        // GotIt tooltips are disabled for now to avoid memory leak issues
        // They will be re-enabled in a future version with proper disposable management
        // See: https://jetbrains.org/intellij/sdk/docs/basics/disposers.html
    }

    private fun setupApiKeysPanel() {
        // North: Toolbar with Add/Remove on left and Refresh on right
        val toolbar = createApiKeysToolbarWithRefresh()
        apiKeysPanel.add(toolbar, BorderLayout.NORTH)

        // Center: API Keys table in scroll pane
        val scrollPane = JScrollPane(apiKeyTable)
        apiKeysPanel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun createApiKeysToolbarWithRefresh(): JPanel {
        // Create the main toolbar with Add/Remove
        val decorator = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeSelectedApiKey() }
            .disableUpDownActions()

        val toolbarPanel = decorator.createPanel()

        // Create a wrapper panel to add Refresh button on the right
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(toolbarPanel, BorderLayout.CENTER)

        // Add Refresh button on the right
        val refreshButtonPanel = JPanel(BorderLayout())
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refreshApiKeysWithValidation() }
        refreshButton.accessibleContext.accessibleName = "Refresh API Keys"
        refreshButtonPanel.add(refreshButton, BorderLayout.EAST)
        wrapperPanel.add(refreshButtonPanel, BorderLayout.EAST)

        return wrapperPanel
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            provisioningKeyField.text = data.trim()
        } catch (e: Exception) {
            PluginLogger.Settings.warn("Failed to paste from clipboard: ${e.message}")
        }
    }

    private fun addApiKey() {
        apiKeyManager.addApiKey()
    }

    private fun removeSelectedApiKey() {
        apiKeyManager.removeApiKey()
    }

    private fun refreshApiKeysWithValidation() {
        // User clicked "Refresh" button - force refresh to bypass cache
        apiKeyManager.refreshApiKeys(forceRefresh = true)
    }

    fun refreshApiKeys(forceRefresh: Boolean = true) {
        // Called from OpenRouterConfigurable - allow specifying forceRefresh
        apiKeyManager.refreshApiKeys(forceRefresh)
    }

    private fun startProxyServer() {
        if (!settingsService.isConfigured()) {
            Messages.showErrorDialog(
                "Please configure your Provisioning Key first.",
                "Configuration Required"
            )
            return
        }

        startServerButton.isEnabled = false
        startServerButton.text = "Starting..."

        proxyService.startServer().thenAccept { success ->
            ApplicationManager.getApplication().invokeLater({
                if (success) {
                    PluginLogger.Settings.info("Proxy server started successfully")
                } else {
                    PluginLogger.Settings.error("Failed to start proxy server")
                    Messages.showErrorDialog(
                        "Failed to start proxy server. Check logs for details.",
                        "Proxy Start Failed"
                    )
                }
                updateProxyStatus()
            }, com.intellij.openapi.application.ModalityState.any())
        }.exceptionally { throwable ->
            ApplicationManager.getApplication().invokeLater({
                PluginLogger.Settings.error("Exception starting proxy server: ${throwable.message}", throwable)
                Messages.showErrorDialog(
                    "Failed to start proxy server: ${throwable.message}",
                    "Proxy Start Failed"
                )
                updateProxyStatus()
            }, com.intellij.openapi.application.ModalityState.any())
            null
        }
    }

    private fun stopProxyServer() {
        stopServerButton.isEnabled = false
        stopServerButton.text = "Stopping..."

        proxyService.stopServer().thenAccept { success ->
            ApplicationManager.getApplication().invokeLater({
                if (success) {
                    PluginLogger.Settings.info("Proxy server stopped successfully")
                } else {
                    PluginLogger.Settings.warn("Failed to stop proxy server")
                }
                updateProxyStatus()
            }, com.intellij.openapi.application.ModalityState.any())
        }.exceptionally { throwable ->
            ApplicationManager.getApplication().invokeLater({
                PluginLogger.Settings.error("Exception stopping proxy server: ${throwable.message}", throwable)
                updateProxyStatus()
            }, com.intellij.openapi.application.ModalityState.any())
            null
        }
    }

    /**
     * Copy proxy URL to clipboard with improved messaging
     */
    private fun copyProxyUrlToClipboard() {
        val status = proxyService.getServerStatus()
        val url = if (status.isRunning && status.url != null) {
            status.url
        } else {
            "Server not running"
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(url), null)

        if (status.isRunning) {
            Messages.showInfoMessage(
                "Proxy URL copied to clipboard:\n\n$url\n\nPaste this as the Base URL in AI Assistant settings.",
                "Proxy URL Copied"
            )
        } else {
            Messages.showWarningDialog(
                "Proxy server is not running. Start the server first to get the URL.",
                "Server Not Running"
            )
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    private fun copyProxyUrl() {
        copyProxyUrlToClipboard()
    }

    private fun updateProxyStatus() {
        val status = proxyService.getServerStatus()
        val isConfigured = settingsService.isConfigured()

        // Update status label with icon
        if (status.isRunning && status.url != null) {
            statusLabel.icon = AllIcons.General.InspectionsOK
            statusLabel.text = "Running - ${status.url}"
            statusLabel.iconTextGap = 5
        } else {
            statusLabel.icon = AllIcons.General.BalloonInformation
            statusLabel.text = "Stopped"
            statusLabel.iconTextGap = 5
        }

        // Update button states
        startServerButton.isEnabled = !status.isRunning && isConfigured
        stopServerButton.isEnabled = status.isRunning
        copyUrlButton.isEnabled = status.isRunning

        // Update button text
        if (!isConfigured) {
            startServerButton.text = "Start Proxy Server (Configure First)"
        } else {
            startServerButton.text = "Start Proxy Server"
        }
        stopServerButton.text = "Stop Proxy Server"

        // Force UI update
        statusLabel.repaint()
        statusLabel.revalidate()
        panel.repaint()
    }

    // Public API methods
    fun getPanel(): JPanel = panel

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
        // Load API keys without triggering validation dialogs when setting provisioning key
        if (provisioningKey.isNotBlank()) {
            apiKeyManager.loadApiKeysWithoutAutoCreate()
        }
    }

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
