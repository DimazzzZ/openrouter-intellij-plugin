package org.zhavoronkov.openrouter.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ProviderInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
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
        apiKeys.clear()
        apiKeys.addAll(keys)
        fireTableDataChanged()
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
}

/**
 * Settings panel for OpenRouter configuration
 */
class OpenRouterSettingsPanel {

    private val panel: JPanel
    private val provisioningKeyField: JBPasswordField
    private val defaultModelField: JBTextField
    private val autoRefreshCheckBox: JBCheckBox
    private val refreshIntervalSpinner: JSpinner
    private val showCostsCheckBox: JBCheckBox
    private val apiKeyTableModel = ApiKeyTableModel()
    private val apiKeyTable = JTable(apiKeyTableModel)
    private val providersTableModel = ProvidersTableModel()
    private val providersTable = JTable(providersTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val logger = Logger.getInstance(OpenRouterSettingsPanel::class.java)

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private var isCreatingApiKey = false // Flag to prevent multiple creation attempts
    }

    init {
        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = 20  // Reduced from 30 to prevent horizontal scroll

        defaultModelField = JBTextField("openai/gpt-4o")
        defaultModelField.columns = 18     // Reduced from 25 to be more responsive

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
            .addLabeledComponent(
                JBLabel("API Keys:"),
                createApiKeyTablePanel(),
                1,
                false
            )
            .addLabeledComponent(
                JBLabel("Available Providers:"),
                createProvidersTablePanel(),
                1,
                false
            )
            .addLabeledComponent(
                JBLabel("Default Model:"),
                defaultModelField,
                1,
                false
            )
            .addComponent(autoRefreshCheckBox, 1)
            .addLabeledComponent(
                JBLabel("Refresh Interval (seconds):"),
                refreshIntervalSpinner,
                1,
                false
            )
            .addComponent(showCostsCheckBox, 1)
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

        // Configure table for responsive layout
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.preferredScrollableViewportSize = Dimension(450, 120)  // Reduced width

        // Set column widths to be more responsive
        apiKeyTable.columnModel.getColumn(0).preferredWidth = 100  // Label
        apiKeyTable.columnModel.getColumn(1).preferredWidth = 120  // Name
        apiKeyTable.columnModel.getColumn(2).preferredWidth = 70   // Usage
        apiKeyTable.columnModel.getColumn(3).preferredWidth = 70   // Limit
        apiKeyTable.columnModel.getColumn(4).preferredWidth = 60   // Status

        // Create toolbar with add/remove buttons
        val tablePanel = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeApiKey() }
            .setAddActionName("Add API Key")
            .setRemoveActionName("Remove API Key")
            .setPreferredSize(Dimension(450, 150))  // Reduced width for better fit
            .createPanel()

        panel.add(tablePanel, BorderLayout.CENTER)

        // Add refresh button for API keys
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val refreshButton = JButton("Refresh API Keys")
        refreshButton.addActionListener { refreshApiKeys() }
        buttonPanel.add(refreshButton)

        val helpLabel = JBLabel(
            "<html><small>Manage your API keys. Automatically loads when Provisioning Key is configured.</small></html>"
        )

        val southPanel = JPanel(BorderLayout())
        southPanel.add(buttonPanel, BorderLayout.NORTH)
        southPanel.add(helpLabel, BorderLayout.SOUTH)
        panel.add(southPanel, BorderLayout.SOUTH)

        // Reset the creation flag when panel is created
        resetApiKeyCreationFlag()

        // Note: refreshApiKeys() will be called by the configurable after settings are loaded

        return panel
    }

    private fun createProvidersTablePanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Configure providers table
        providersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        providersTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        providersTable.fillsViewportHeight = true

        // Set column widths to be more responsive
        providersTable.columnModel.getColumn(0).preferredWidth = 120  // Provider
        providersTable.columnModel.getColumn(1).preferredWidth = 70   // Status
        providersTable.columnModel.getColumn(2).preferredWidth = 90   // Privacy Policy
        providersTable.columnModel.getColumn(3).preferredWidth = 100  // Terms of Service

        // Add double-click listener to open links
        providersTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = providersTable.rowAtPoint(e.point)
                    val col = providersTable.columnAtPoint(e.point)
                    if (row >= 0 && col >= 2) { // Privacy Policy or Terms columns
                        val provider = providersTableModel.getProvider(row)
                        if (provider != null) {
                            val url = when (col) {
                                2 -> provider.privacyPolicyUrl
                                3 -> provider.termsOfServiceUrl
                                else -> null
                            }
                            if (url != null) {
                                BrowserUtil.browse(url)
                            }
                        }
                    }
                }
            }
        })

        val scrollPane = JBScrollPane(providersTable)
        scrollPane.preferredSize = Dimension(450, 180)  // Reduced size for better fit
        panel.add(scrollPane, BorderLayout.CENTER)

        // Add refresh button
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val refreshButton = JButton("Refresh Providers")
        refreshButton.addActionListener { loadProviders() }
        buttonPanel.add(refreshButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        // Auto-load providers when panel is created
        loadProviders()

        // Help text (moved to SOUTH for consistency with other sections)
        val helpLabel = JBLabel(
            "<html><small>List of available AI providers on OpenRouter. Double-click on Privacy Policy or Terms to open links.</small></html>"
        )
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

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
            ApplicationManager.getApplication().invokeLater {
                if (response != null) {
                    Messages.showInfoMessage(
                        "API Key created successfully!\nKey: ${response.data.key}\n\n" +
                            "Please save this key securely - it won't be shown again.",
                        "API Key Created"
                    )
                    loadApiKeysWithoutAutoCreate()
                } else {
                    Messages.showErrorDialog(
                        "Failed to create API key. Please check your Provisioning Key and try again.",
                        "Creation Failed"
                    )
                }
            }
        }
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
            openRouterService.deleteApiKey(apiKey.name).thenAccept { response ->
                ApplicationManager.getApplication().invokeLater {
                    if (response != null && response.data.deleted) {
                        Messages.showInfoMessage(
                            "API Key '${apiKey.label}' has been deleted successfully.",
                            "API Key Deleted"
                        )
                        loadApiKeysWithoutAutoCreate()
                    } else {
                        Messages.showErrorDialog(
                            "Failed to delete API key. Please try again.",
                            "Deletion Failed"
                        )
                    }
                }
            }
        }
    }

    fun refreshApiKeys() {
        logger.info("Refreshing API keys table")

        // Check if provisioning key is available (either in panel or saved settings)
        val currentProvisioningKey = getProvisioningKey()
        val savedProvisioningKey = settingsService.getProvisioningKey()
        val provisioningKey = if (currentProvisioningKey.isNotBlank()) currentProvisioningKey else savedProvisioningKey


        if (provisioningKey.isBlank()) {
            logger.info("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        logger.info("Fetching API keys from OpenRouter with provisioning key: ${provisioningKey.take(10)}...")
        openRouterService.getApiKeysList(provisioningKey).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                if (response != null) {
                    val apiKeys = response.data
                    logger.info("Successfully received ${apiKeys.size} API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(apiKeys)

                    // Check if IntelliJ IDEA Plugin API key exists and handle creation
                    ensureIntellijApiKeyExists(apiKeys)
                } else {
                    logger.warn("Failed to fetch API keys from OpenRouter - response was null")
                    apiKeyTableModel.setApiKeys(emptyList())
                    // Show user-friendly error message
                    Messages.showWarningDialog(
                        "Failed to load API keys. Please check your Provisioning Key and internet connection.",
                        "Load Failed"
                    )
                }
            }
        }
    }

    /**
     * Ensures that the IntelliJ IDEA Plugin API key exists, creating it only if necessary
     */
    private fun ensureIntellijApiKeyExists(currentApiKeys: List<ApiKeyInfo>) {
        // First check: Does the API key already exist on the server?
        val existingIntellijApiKey = currentApiKeys.find { it.name == INTELLIJ_API_KEY_NAME }

        if (existingIntellijApiKey != null) {
            logger.info("IntelliJ IDEA Plugin API key already exists: ${existingIntellijApiKey.name}")
            return // API key exists, nothing to do
        }

        // Second check: Are we already in the process of creating one?
        if (isCreatingApiKey) {
            logger.info("API key creation already in progress, skipping")
            return
        }

        // Third check: Do we have a stored API key that might be valid?
        val storedApiKey = settingsService.getApiKey()
        if (storedApiKey.isNotBlank()) {
            logger.info("Stored API key exists, assuming it's valid")
            return
        }

        // Only now create the API key
        logger.info("No IntelliJ IDEA Plugin API key found, creating one...")
        createIntellijApiKeyOnce()
    }

    private fun loadApiKeysWithoutAutoCreate() {
        logger.info("Loading API keys without auto-creation")

        // Check if provisioning key is available (either in panel or saved settings)
        val currentProvisioningKey = getProvisioningKey()
        val savedProvisioningKey = settingsService.getProvisioningKey()
        val provisioningKey = if (currentProvisioningKey.isNotBlank()) currentProvisioningKey else savedProvisioningKey

        if (provisioningKey.isBlank()) {
            logger.info("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        openRouterService.getApiKeysList(provisioningKey).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                if (response != null) {
                    val apiKeys = response.data
                    logger.info("Received ${apiKeys.size} API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(apiKeys)
                } else {
                    logger.warn("Failed to fetch API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(emptyList())
                }
            }
        }
    }

    private fun createIntellijApiKeyOnce() {
        // Set the flag to prevent multiple creation attempts
        isCreatingApiKey = true

        logger.info("Attempting to create IntelliJ IDEA Plugin API key (once)")
        openRouterService.createApiKey(INTELLIJ_API_KEY_NAME, null).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                try {
                    if (response != null) {
                        logger.info("Successfully created IntelliJ IDEA Plugin API key: ${response.data.name}")

                        // Store the API key securely
                        settingsService.setApiKey(response.data.key)

                        // Refresh the table to show the new key (but don't create another one)
                        loadApiKeysWithoutAutoCreate()

                        Messages.showInfoMessage(
                            "Automatically created '$INTELLIJ_API_KEY_NAME' API key for plugin use.",
                            "API Key Created"
                        )
                    } else {
                        logger.warn("Failed to create IntelliJ IDEA Plugin API key")
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
            }
        }
    }

    /**
     * Reset the API key creation flag - useful when settings panel is reopened
     */
    private fun resetApiKeyCreationFlag() {
        isCreatingApiKey = false
        logger.info("Reset API key creation flag")
    }

    /**
     * Load providers list from OpenRouter
     */
    private fun loadProviders() {
        logger.info("Loading providers list from OpenRouter API")

        openRouterService.getProviders().thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                if (response != null && response.data.isNotEmpty()) {
                    logger.info("Successfully loaded ${response.data.size} providers from OpenRouter")
                    providersTableModel.setProviders(response.data)
                } else {
                    logger.warn("Failed to load providers - response was null or empty")
                    providersTableModel.setProviders(emptyList())
                    Messages.showWarningDialog(
                        "Failed to load providers list. Please check your internet connection and try again.",
                        "Load Failed"
                    )
                }
            }
        }.exceptionally { throwable ->
            ApplicationManager.getApplication().invokeLater {
                logger.error("Exception while loading providers", throwable)
                providersTableModel.setProviders(emptyList())
                Messages.showErrorDialog(
                    "Error loading providers: ${throwable.message}",
                    "Load Error"
                )
            }
            null
        }
    }

    fun getPanel(): JPanel = panel

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
        // Refresh tables when provisioning key is set
        if (provisioningKey.isNotBlank()) {
            refreshApiKeys()
        }
    }

    fun getDefaultModel(): String = defaultModelField.text

    fun setDefaultModel(model: String) {
        defaultModelField.text = model
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

/**
 * Table model for displaying OpenRouter providers
 */
class ProvidersTableModel : AbstractTableModel() {
    private var providers: List<ProviderInfo> = emptyList()

    private val columnNames = arrayOf("Provider", "Status", "Privacy Policy", "Terms of Service")

    override fun getRowCount(): Int = providers.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val provider = providers[rowIndex]
        return when (columnIndex) {
            0 -> provider.name
            1 -> "Available" // All providers in the list are available
            2 -> if (provider.privacyPolicyUrl != null) "View" else "-"
            3 -> if (provider.termsOfServiceUrl != null) "View" else "-"
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    fun setProviders(newProviders: List<ProviderInfo>) {
        providers = newProviders
        fireTableDataChanged()
    }

    fun getProvider(rowIndex: Int): ProviderInfo? {
        return if (rowIndex >= 0 && rowIndex < providers.size) {
            providers[rowIndex]
        } else {
            null
        }
    }
}
