package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Locale
import javax.swing.JPanel
import javax.swing.JSpinner
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
    private val apiKeyTable = JBTable(apiKeyTableModel)
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val logger = Logger.getInstance(OpenRouterSettingsPanel::class.java)

    companion object {
        private const val INTELLIJ_API_KEY_NAME = "IntelliJ IDEA Plugin"
        private var isCreatingApiKey = false // Flag to prevent multiple creation attempts
    }

    init {
        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = 30

        defaultModelField = JBTextField("openai/gpt-4o")
        defaultModelField.columns = 25

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
        apiKeyTable.autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
        apiKeyTable.preferredScrollableViewportSize = Dimension(500, 120)

        // Set column widths to be more responsive
        apiKeyTable.columnModel.getColumn(0).preferredWidth = 120  // Label
        apiKeyTable.columnModel.getColumn(1).preferredWidth = 150  // Name
        apiKeyTable.columnModel.getColumn(2).preferredWidth = 80   // Usage
        apiKeyTable.columnModel.getColumn(3).preferredWidth = 80   // Limit
        apiKeyTable.columnModel.getColumn(4).preferredWidth = 70   // Status

        // Create toolbar with add/remove buttons
        val tablePanel = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeApiKey() }
            .setAddActionName("Add API Key")
            .setRemoveActionName("Remove API Key")
            .setPreferredSize(Dimension(500, 150))
            .createPanel()

        panel.add(tablePanel, BorderLayout.CENTER)

        val helpLabel = JBLabel(
            "<html><small>Manage your API keys. Automatically loads when Provisioning Key is configured.</small></html>"
        )
        panel.add(helpLabel, BorderLayout.SOUTH)

        // Reset the creation flag when panel is created
        resetApiKeyCreationFlag()

        // Auto-load table when panel is created
        refreshApiKeys()

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
        if (!settingsService.isConfigured()) {
            logger.info("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        logger.info("Fetching API keys from OpenRouter")
        openRouterService.getApiKeysList().thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                if (response != null) {
                    val apiKeys = response.data
                    logger.info("Received ${apiKeys.size} API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(apiKeys)

                    // Check if IntelliJ IDEA Plugin API key exists and handle creation
                    ensureIntellijApiKeyExists(apiKeys)
                } else {
                    logger.warn("Failed to fetch API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(emptyList())
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
        if (!settingsService.isConfigured()) {
            logger.info("Provisioning key not configured, clearing table")
            apiKeyTableModel.setApiKeys(emptyList())
            return
        }

        openRouterService.getApiKeysList().thenAccept { response ->
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

    fun getPanel(): JPanel = panel

    fun getProvisioningKey(): String = String(provisioningKeyField.password)

    fun setProvisioningKey(provisioningKey: String) {
        provisioningKeyField.text = provisioningKey
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
