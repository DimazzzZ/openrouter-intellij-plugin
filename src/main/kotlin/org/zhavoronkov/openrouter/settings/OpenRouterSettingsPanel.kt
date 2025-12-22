
package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.SetupWizardDialog
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.AbstractTableModel

/**
 * Table model for API keys
 */
class ApiKeyTableModel : AbstractTableModel() {
    companion object {
        // Table column indices
        private const val COLUMN_LABEL = 0
        private const val COLUMN_NAME = 1
        private const val COLUMN_USAGE = 2
        private const val COLUMN_LIMIT = 3
        private const val COLUMN_STATUS = 4
    }

    private val columnNames = arrayOf("Label", "Name", "Usage", "Limit", "Status")
    private val apiKeys = mutableListOf<ApiKeyInfo>()

    override fun getRowCount(): Int = apiKeys.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val key = apiKeys[rowIndex]
        return when (columnIndex) {
            COLUMN_LABEL -> key.label ?: ""
            COLUMN_NAME -> key.name ?: ""
            COLUMN_USAGE -> formatUsage(key.usage)
            COLUMN_LIMIT -> formatLimit(key.limit)
            COLUMN_STATUS -> if (!key.disabled) "Active" else "Inactive"
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

    private lateinit var panel: JPanel
    private val provisioningKeyField: JBPasswordField
    private val apiKeyField: JBPasswordField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    private val defaultMaxTokensSpinner: JSpinner
    private val enableDefaultMaxTokensCheckBox: JBCheckBox
    private val apiKeyTableModel = ApiKeyTableModel()
    private val apiKeyTable = JBTable(apiKeyTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val proxyService = OpenRouterProxyService.getInstance()

    // API Keys panel
    private val apiKeysPanel = JPanel(BorderLayout())

    // Helper classes for managing different aspects
    private val apiKeyManager: ApiKeyManager

    // Proxy server status components
    private lateinit var statusLabel: JLabel
    private lateinit var startServerButton: JButton
    private lateinit var stopServerButton: JButton
    private lateinit var copyUrlButton: JButton

    // Proxy configuration components
    private lateinit var proxyAutoStartCheckBox: JBCheckBox
    private lateinit var useSpecificPortCheckBox: JBCheckBox
    private lateinit var proxyPortSpinner: JSpinner
    private lateinit var proxyPortRangeStartSpinner: JSpinner
    private lateinit var proxyPortRangeEndSpinner: JSpinner

    // UI state tracking
    private var currentUiAuthScope: AuthScope = AuthScope.EXTENDED
    private lateinit var regularRadioButton: javax.swing.JRadioButton
    private lateinit var extendedRadioButton: javax.swing.JRadioButton

    // Authentication status labels
    private lateinit var authScopeLabel: javax.swing.JLabel
    private lateinit var authDescriptionLabel: javax.swing.JEditorPane

    // Scope predicates for visibility management
    private val uiPredicates = mutableListOf<UpdatablePredicate>()

    private abstract inner class UpdatablePredicate : com.intellij.ui.layout.ComponentPredicate() {
        private val listeners = mutableListOf<(Boolean) -> Unit>()
        override fun addListener(listener: (Boolean) -> Unit) {
            listeners.add(listener)
            listener(invoke())
        }
        fun update() {
            val newValue = invoke()
            listeners.forEach { it(newValue) }
        }
    }

    companion object {
        private const val DEFAULT_REFRESH_INTERVAL = 300
        private const val MIN_REFRESH_INTERVAL = 60
        private const val MAX_REFRESH_INTERVAL = 300
        private const val REFRESH_INTERVAL_STEP = 10
        private const val DEFAULT_MAX_TOKENS = 8000
        private const val MIN_MAX_TOKENS = 1
        private const val MAX_MAX_TOKENS = 128000
        private const val MAX_TOKENS_STEP = 1000

        // Proxy configuration constants
        private const val MIN_PORT = 1024
        private const val MAX_PORT = 65535
        private const val DEFAULT_PROXY_PORT = 8880

        // UI dimensions
        private const val PASSWORD_FIELD_COLUMNS = 32

        // Proxy port range defaults
        private const val DEFAULT_PROXY_PORT_RANGE_START = 8880
        private const val DEFAULT_PROXY_PORT_RANGE_END = 8899

        // Status update delay
        private const val STATUS_UPDATE_DELAY_SECONDS = 5
    }

    init {
        // Initialize logging configuration
        PluginLogger.logConfiguration()

        val isHeadless = java.awt.GraphicsEnvironment.isHeadless()
        PluginLogger.Settings.debug("OpenRouter Settings Panel INITIALIZED (headless: $isHeadless)")

        // Initialize UI state from settings
        currentUiAuthScope = settingsService.apiKeyManager.authScope

        // Initialize components
        provisioningKeyField = JBPasswordField().apply {
            columns = PASSWORD_FIELD_COLUMNS
        }

        apiKeyField = JBPasswordField().apply {
            columns = PASSWORD_FIELD_COLUMNS
        }

        autoRefreshCheckBox = JBCheckBox("Auto-refresh quota information")
        showCostsCheckBox = JBCheckBox("Show costs in status bar")

        refreshIntervalSpinner = JSpinner(
            SpinnerNumberModel(
                DEFAULT_REFRESH_INTERVAL,
                MIN_REFRESH_INTERVAL,
                MAX_REFRESH_INTERVAL,
                REFRESH_INTERVAL_STEP
            )
        )

        enableDefaultMaxTokensCheckBox = JBCheckBox("Enable default max tokens for proxy requests")
        defaultMaxTokensSpinner = JSpinner(
            SpinnerNumberModel(
                DEFAULT_MAX_TOKENS, // Default value
                MIN_MAX_TOKENS, // Minimum value
                MAX_MAX_TOKENS, // Maximum value
                MAX_TOKENS_STEP // Step
            )
        ).apply {
            isEnabled = false // Disabled by default since feature is off by default
        }

        // Initialize proxy configuration components
        proxyAutoStartCheckBox = JBCheckBox("Auto-start proxy server on IDEA startup")
        useSpecificPortCheckBox = JBCheckBox("Use specific port")

        proxyPortSpinner = JSpinner(
            SpinnerNumberModel(
                DEFAULT_PROXY_PORT, // Default value
                MIN_PORT, // Minimum value
                MAX_PORT, // Maximum value
                1 // Step
            )
        ).apply {
            isEnabled = false // Disabled by default since "Use specific port" is unchecked
        }

        proxyPortRangeStartSpinner = JSpinner(
            SpinnerNumberModel(
                DEFAULT_PROXY_PORT_RANGE_START, // Default start of range
                MIN_PORT,
                MAX_PORT,
                1
            )
        )

        proxyPortRangeEndSpinner = JSpinner(
            SpinnerNumberModel(
                DEFAULT_PROXY_PORT_RANGE_END, // Default end of range
                MIN_PORT,
                MAX_PORT,
                1
            )
        )

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
        try {
            panel = panel {
                // Run Setup Wizard button
                row {
                    button("Run Setup Wizard") {
                        try {
                            PluginLogger.Settings.info("Run Setup Wizard button clicked")
                            val project = ProjectManager.getInstance().openProjects.firstOrNull()
                                ?: ProjectManager.getInstance().defaultProject
                            PluginLogger.Settings.info("Showing setup wizard for project: ${project?.name ?: "default"}")
                            val result = SetupWizardDialog.show(project)
                            PluginLogger.Settings.info("Setup wizard completed with result: $result")
                            if (result) {
                                refreshUI()
                            }
                        } catch (e: Exception) {
                            PluginLogger.Settings.error("Failed to show setup wizard", e)
                            Messages.showErrorDialog(
                                "Failed to show setup wizard: ${e.message}",
                                "Setup Wizard Error"
                            )
                        }
                    }
                    comment("Re-run the initial setup wizard to configure the plugin.")
                }

                // Authentication Status group
                group("Authentication") {
                    row("Current Scope:") {
                        authScopeLabel = label("").applyToComponent {
                            text = if (!settingsService.setupStateManager.hasCompletedSetup()) {
                                "Not Configured"
                            } else if (settingsService.apiKeyManager.authScope == AuthScope.REGULAR) {
                                "Regular API Key"
                            } else {
                                "Extended (Provisioning Key)"
                            }
                        }.bold().component
                    }
                    row {
                        authDescriptionLabel = text("").applyToComponent {
                            text = if (!settingsService.setupStateManager.hasCompletedSetup()) {
                                "Please run the Setup Wizard to configure authentication."
                            } else if (settingsService.apiKeyManager.authScope == AuthScope.REGULAR) {
                                "Minimal permissions. Quota tracking and usage monitoring are disabled."
                            } else {
                                "Full functionality. The plugin can manage API keys and monitor usage."
                            }
                        }.component
                    }
                    row {
                        text("To change your authentication settings, please use the <b>Run Setup Wizard</b> button above.")
                    }
                }

                // General Settings group
                group("General Settings") {
                    row("Refresh interval (seconds):") {
                        cell(refreshIntervalSpinner)
                    }.layout(RowLayout.PARENT_GRID)

                    row {
                        cell(enableDefaultMaxTokensCheckBox)
                    }.layout(RowLayout.PARENT_GRID)

                    row("Maximum tokens:") {
                        cell(defaultMaxTokensSpinner)
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
                }.visibleIf(isExtendedScope())

                // Proxy Server group
                group("Proxy Server") {
                    // Auto-start configuration
                    row {
                        cell(proxyAutoStartCheckBox)
                    }.layout(RowLayout.PARENT_GRID)

                    // Port configuration
                    row {
                        cell(useSpecificPortCheckBox)
                    }.layout(RowLayout.PARENT_GRID)

                    row("Specific port:") {
                        cell(proxyPortSpinner)
                    }.layout(RowLayout.PARENT_GRID)

                    row("Port range start:") {
                        cell(proxyPortRangeStartSpinner)
                    }.layout(RowLayout.PARENT_GRID)

                    row("Port range end:") {
                        cell(proxyPortRangeEndSpinner)
                    }.layout(RowLayout.PARENT_GRID)

                    row {
                        comment(
                            "When 'Use specific port' is unchecked, the proxy will " +
                                "auto-select from the specified range."
                        )
                    }

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
        } catch (e: Exception) {
            PluginLogger.Settings.error("Failed to create settings panel", e)
            panel = JPanel(BorderLayout()).apply {
                add(JLabel("Error creating settings panel: ${e.message}"), BorderLayout.NORTH)
                val scrollPane = JScrollPane(javax.swing.JTextArea(e.stackTraceToString()))
                add(scrollPane, BorderLayout.CENTER)
            }
        }

        // Set up listeners
        autoRefreshCheckBox.addActionListener {
            refreshIntervalSpinner.isEnabled = autoRefreshCheckBox.isSelected
        }

        enableDefaultMaxTokensCheckBox.addActionListener {
            defaultMaxTokensSpinner.isEnabled = enableDefaultMaxTokensCheckBox.isSelected
        }

        // Proxy configuration listeners
        useSpecificPortCheckBox.addActionListener {
            proxyPortSpinner.isEnabled = useSpecificPortCheckBox.isSelected
            proxyPortRangeStartSpinner.isEnabled = !useSpecificPortCheckBox.isSelected
            proxyPortRangeEndSpinner.isEnabled = !useSpecificPortCheckBox.isSelected
        }

        // Validate port range when values change
        proxyPortRangeStartSpinner.addChangeListener {
            validatePortRange()
        }

        proxyPortRangeEndSpinner.addChangeListener {
            validatePortRange()
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

    private fun pasteToField(field: JBPasswordField) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            field.text = data.trim()
        } catch (e: java.awt.HeadlessException) {
            PluginLogger.Settings.warn("Clipboard not available in headless environment", e)
        } catch (e: java.awt.datatransfer.UnsupportedFlavorException) {
            PluginLogger.Settings.warn("Clipboard data format not supported", e)
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.warn("Invalid state accessing clipboard", e)
        } catch (expectedError: Exception) {
            PluginLogger.Settings.warn("Failed to paste from clipboard: ${expectedError.message}")
        }
    }

    private fun isExtendedScope(): com.intellij.ui.layout.ComponentPredicate {
        val predicate = object : UpdatablePredicate() {
            override fun invoke() = settingsService.apiKeyManager.authScope == AuthScope.EXTENDED
        }
        uiPredicates.add(predicate)
        return predicate
    }

    private fun notifyScopeChanged() {
        uiPredicates.forEach { it.update() }
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

        // Apply current proxy settings from UI before starting server
        // This ensures the proxy uses the current UI values without requiring Apply/OK
        applyCurrentProxySettings()

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

    fun updateProxyStatus() {
        // Safe check for lateinit properties
        if (!::statusLabel.isInitialized || !::startServerButton.isInitialized ||
            !::stopServerButton.isInitialized || !::copyUrlButton.isInitialized
        ) {
            return
        }

        val status = proxyService.getServerStatus()
        val isConfigured = settingsService.isConfigured()

        // Update status label with icon
        if (status.isRunning && status.url != null) {
            statusLabel.icon = AllIcons.General.InspectionsOK
            statusLabel.text = "Running - ${status.url}"
            statusLabel.iconTextGap = STATUS_UPDATE_DELAY_SECONDS
        } else {
            statusLabel.icon = AllIcons.General.BalloonInformation
            statusLabel.text = "Stopped"
            statusLabel.iconTextGap = STATUS_UPDATE_DELAY_SECONDS
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

    fun getAuthScope(): AuthScope = currentUiAuthScope

    fun setAuthScope(scope: AuthScope) {
        currentUiAuthScope = scope
        if (::regularRadioButton.isInitialized && ::extendedRadioButton.isInitialized) {
            regularRadioButton.isSelected = scope == AuthScope.REGULAR
            extendedRadioButton.isSelected = scope == AuthScope.EXTENDED
        }
        notifyScopeChanged()
    }

    fun getApiKey(): String = String(apiKeyField.password)

    fun setApiKey(apiKey: String) {
        apiKeyField.text = apiKey
    }

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
        // Load API keys without triggering validation dialogs when setting provisioning key
        if (provisioningKey.isNotBlank()) {
            apiKeyManager.loadApiKeysWithoutAutoCreate()
        }
        // Update proxy status to reflect current server state
        updateProxyStatus()
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

    fun isDefaultMaxTokensEnabled(): Boolean = enableDefaultMaxTokensCheckBox.isSelected

    fun setDefaultMaxTokensEnabled(enabled: Boolean) {
        enableDefaultMaxTokensCheckBox.isSelected = enabled
        defaultMaxTokensSpinner.isEnabled = enabled
    }

    fun getDefaultMaxTokens(): Int = defaultMaxTokensSpinner.value as Int

    fun setDefaultMaxTokens(maxTokens: Int) {
        defaultMaxTokensSpinner.value = maxTokens
    }

    // Proxy configuration methods

    fun getProxyAutoStart(): Boolean = proxyAutoStartCheckBox.isSelected

    fun setProxyAutoStart(enabled: Boolean) {
        proxyAutoStartCheckBox.isSelected = enabled
    }

    fun getUseSpecificPort(): Boolean = useSpecificPortCheckBox.isSelected

    fun setUseSpecificPort(useSpecific: Boolean) {
        useSpecificPortCheckBox.isSelected = useSpecific
        proxyPortSpinner.isEnabled = useSpecific
        proxyPortRangeStartSpinner.isEnabled = !useSpecific
        proxyPortRangeEndSpinner.isEnabled = !useSpecific
    }

    fun getProxyPort(): Int = proxyPortSpinner.value as Int

    fun setProxyPort(port: Int) {
        proxyPortSpinner.value = port
    }

    fun getProxyPortRangeStart(): Int = proxyPortRangeStartSpinner.value as Int

    fun setProxyPortRangeStart(port: Int) {
        proxyPortRangeStartSpinner.value = port
        validatePortRange()
    }

    fun getProxyPortRangeEnd(): Int = proxyPortRangeEndSpinner.value as Int

    fun setProxyPortRangeEnd(port: Int) {
        proxyPortRangeEndSpinner.value = port
        validatePortRange()
    }

    /**
     * Applies current proxy settings from UI to settings service.
     * This ensures immediate proxy server startup uses current UI values
     * without requiring Apply/OK button click.
     */
    private fun applyCurrentProxySettings() {
        try {
            settingsService.proxyManager.setProxyAutoStart(getProxyAutoStart())

            val port = if (getUseSpecificPort()) {
                getProxyPort()
            } else {
                0 // 0 means auto-select from range
            }
            settingsService.proxyManager.setProxyPort(port)

            settingsService.proxyManager.setProxyPortRange(
                getProxyPortRangeStart(),
                getProxyPortRangeEnd()
            )

            val rangeStr = "${getProxyPortRangeStart()}-${getProxyPortRangeEnd()}"
            PluginLogger.Settings.debug(
                "Applied current proxy settings: autoStart=${getProxyAutoStart()}, port=$port, range=$rangeStr"
            )
        } catch (e: NumberFormatException) {
            PluginLogger.Settings.error("Invalid port number format: ${e.message}", e)
        } catch (e: IllegalStateException) {
            PluginLogger.Settings.error("Invalid state applying proxy settings: ${e.message}", e)
        } catch (expectedError: Exception) {
            PluginLogger.Settings.error(
                "Failed to apply current proxy settings: ${expectedError.message}",
                expectedError
            )
        }
    }

    /**
     * Validates that port range start <= end
     */
    private fun validatePortRange() {
        val startPort = proxyPortRangeStartSpinner.value as Int
        val endPort = proxyPortRangeEndSpinner.value as Int

        if (startPort > endPort) {
            // Automatically adjust the end port to be at least equal to start port
            proxyPortRangeEndSpinner.value = startPort
        }
    }

    /**
     * Refreshes the UI with the latest values from the settings service.
     * This is called after the Setup Wizard completes to ensure the settings page reflects the changes.
     */
    private fun refreshUI() {
        ApplicationManager.getApplication().invokeLater {
            // Refresh Authentication Scope
            val newScope = settingsService.apiKeyManager.authScope
            setAuthScope(newScope)

            // Update status labels
            if (::authScopeLabel.isInitialized) {
                authScopeLabel.text = if (!settingsService.setupStateManager.hasCompletedSetup()) {
                    "Not Configured"
                } else if (newScope == AuthScope.REGULAR) {
                    "Regular API Key"
                } else {
                    "Extended (Provisioning Key)"
                }
            }
            if (::authDescriptionLabel.isInitialized) {
                authDescriptionLabel.text = if (!settingsService.setupStateManager.hasCompletedSetup()) {
                    "Please run the Setup Wizard to configure authentication."
                } else if (newScope == AuthScope.REGULAR) {
                    "Minimal permissions. Quota tracking and usage monitoring are disabled."
                } else {
                    "Full functionality. The plugin can manage API keys and monitor usage."
                }
            }

            // Refresh Keys
            if (newScope == AuthScope.REGULAR) {
                setApiKey(settingsService.apiKeyManager.getApiKey())
            } else {
                setProvisioningKey(settingsService.apiKeyManager.getProvisioningKey())
            }

            // Refresh Proxy Settings
            setProxyAutoStart(settingsService.proxyManager.isProxyAutoStartEnabled())
            setUseSpecificPort(settingsService.proxyManager.getProxyPort() != 0)
            if (getUseSpecificPort()) {
                setProxyPort(settingsService.proxyManager.getProxyPort())
            }

            // Refresh Proxy Status
            updateProxyStatus()

            PluginLogger.Settings.info("Settings panel UI refreshed from service state")
        }
    }
}
