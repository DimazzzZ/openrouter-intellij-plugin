package org.zhavoronkov.openrouter.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.awt.BorderLayout
import java.awt.Dimension
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
            0 -> key.label
            1 -> key.name
            2 -> String.format("$%.6f", key.usage)
            3 -> key.limit?.let { String.format("$%.2f", it) } ?: "Unlimited"
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
    }
    
    init {
        provisioningKeyField = JBPasswordField()
        provisioningKeyField.columns = 40

        defaultModelField = JBTextField("openai/gpt-4o")
        defaultModelField.columns = 30
        
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

        val helpLabel = JBLabel("<html><small><b>Required for quota monitoring.</b> Get your provisioning key from <a href='https://openrouter.ai/settings/provisioning-keys'>openrouter.ai/settings/provisioning-keys</a></small></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createApiKeyTablePanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Configure table
        apiKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiKeyTable.preferredScrollableViewportSize = Dimension(600, 150)

        // Create toolbar with add/remove buttons
        val tablePanel = ToolbarDecorator.createDecorator(apiKeyTable)
            .setAddAction { addApiKey() }
            .setRemoveAction { removeApiKey() }
            .setAddActionName("Add API Key")
            .setRemoveActionName("Remove API Key")
            .createPanel()

        panel.add(tablePanel, BorderLayout.CENTER)

        val helpLabel = JBLabel("<html><small>Manage your API keys. Automatically loads when Provisioning Key is configured.</small></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

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
                        "API Key created successfully!\nKey: ${response.data.key}\n\nPlease save this key securely - it won't be shown again.",
                        "API Key Created"
                    )
                    refreshApiKeys()
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
            "Are you sure you want to delete the API key '${apiKey.label}' (${apiKey.name})?\nThis action cannot be undone.",
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
                        refreshApiKeys()
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

                    // Check if IntelliJ IDEA Plugin API key exists
                    val intellijApiKey = apiKeys.find { it.name == INTELLIJ_API_KEY_NAME }
                    if (intellijApiKey != null) {
                        // API key exists, we're good to go
                        // Note: We use provisioning key for quota monitoring, not the API key
                        logger.info("Found existing IntelliJ IDEA Plugin API key: ${intellijApiKey.name}")
                    } else {
                        // Create the IntelliJ IDEA Plugin API key automatically
                        createIntellijApiKey()
                    }
                } else {
                    logger.warn("Failed to fetch API keys from OpenRouter")
                    apiKeyTableModel.setApiKeys(emptyList())
                }
            }
        }
    }

    private fun createIntellijApiKey() {
        logger.info("Attempting to create IntelliJ IDEA Plugin API key")
        openRouterService.createApiKey(INTELLIJ_API_KEY_NAME, null).thenAccept { response ->
            ApplicationManager.getApplication().invokeLater {
                if (response != null) {
                    logger.info("Successfully created IntelliJ IDEA Plugin API key: ${response.data.name}")

                    // Refresh the table to show the new key
                    refreshApiKeys()

                    Messages.showInfoMessage(
                        "Automatically created '$INTELLIJ_API_KEY_NAME' API key for plugin use.",
                        "API Key Created"
                    )
                } else {
                    logger.warn("Failed to create IntelliJ IDEA Plugin API key")
                    Messages.showWarningDialog(
                        "Failed to automatically create '$INTELLIJ_API_KEY_NAME' API key. Please check your provisioning key and try again.",
                        "Auto-creation Failed"
                    )
                }
            }
        }
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
