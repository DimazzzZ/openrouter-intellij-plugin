package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings panel for managing provider routing preferences (Phase 2).
 * Allows users to configure global defaults for provider order, fallbacks, sorting, etc.
 * These preferences are injected into proxy requests when enabled and absent from the request.
 */
@Suppress("TooManyFunctions")
class ProviderRoutingSettingsPanel : Disposable {

    companion object {
        private const val MIN_PANEL_WIDTH = 600
        private const val LIST_PREFERRED_HEIGHT = 150
        private val SORT_OPTIONS = listOf("", "price", "throughput", "latency")
        private val DATA_COLLECTION_OPTIONS = listOf("", "allow", "deny")
        private val QUANTIZATIONS = listOf("int4", "int8", "fp8", "fp16", "bf16", "fp32")
    }

    private val settingsService = OpenRouterSettingsService.getInstance()

    // Master switch
    private val enabledCheckbox = JBCheckBox("Inject default provider routing into proxy requests")

    // Provider order list
    private val providerOrderModel = DefaultListModel<String>()
    private val providerOrderList = JBList(providerOrderModel)

    // Checkboxes and combos
    private val allowFallbacksCheckbox = JBCheckBox("Allow fallbacks", true)
    private val requireParametersCheckbox = JBCheckBox("Require parameters")
    private val sortCombo = ComboBox(DefaultComboBoxModel(SORT_OPTIONS.toTypedArray()))
    private val dataCollectionCombo = ComboBox(DefaultComboBoxModel(DATA_COLLECTION_OPTIONS.toTypedArray()))

    // Quantizations checkboxes
    private val quantizationCheckboxes = QUANTIZATIONS.associateWith { JBCheckBox(it) }

    // Fallback models list
    private val fallbackModelsModel = DefaultListModel<String>()
    private val fallbackModelsList = JBList(fallbackModelsModel)

    // Only/Ignore provider filter lists
    private val onlyProvidersModel = DefaultListModel<String>()
    private val onlyProvidersList = JBList(onlyProvidersModel)
    private val ignoreProvidersModel = DefaultListModel<String>()
    private val ignoreProvidersList = JBList(ignoreProvidersModel)

    // Track initial state for isModified check
    private var initialEnabled: Boolean = false
    private var initialOrder: List<String> = emptyList()
    private var initialAllowFallbacks: Boolean = true
    private var initialSort: String = ""
    private var initialRequireParameters: Boolean = false
    private var initialDataCollection: String = ""
    private var initialQuantizations: Set<String> = emptySet()
    private var initialFallbackModels: List<String> = emptyList()
    private var initialOnlyProviders: List<String> = emptyList()
    private var initialIgnoreProviders: List<String> = emptyList()

    init {
        setupLists()
    }

    private fun setupLists() {
        providerOrderList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        providerOrderList.preferredSize = Dimension(0, LIST_PREFERRED_HEIGHT)

        fallbackModelsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fallbackModelsList.preferredSize = Dimension(0, LIST_PREFERRED_HEIGHT)
    }

    /**
     * Create the main panel using UI DSL v2
     */
    fun createPanel(): JPanel {
        loadSettings()

        return panel {
            row {
                cell(enabledCheckbox)
            }.topGap(TopGap.MEDIUM)

            group("Provider Order") {
                row {
                    comment("Providers are tried in this order. Leave empty to use OpenRouter's default.")
                }

                row {
                    val decorator = ToolbarDecorator.createDecorator(providerOrderList)
                        .setAddAction { addProvider() }
                        .setRemoveAction { removeProvider() }
                        .setMoveUpAction { moveProviderUp() }
                        .setMoveDownAction { moveProviderDown() }
                    cell(decorator.createPanel()).align(Align.FILL)
                }.resizableRow()
            }

            group("Fallback Behavior") {
                row {
                    cell(allowFallbacksCheckbox)
                }
                row {
                    label("Sort by:")
                    cell(sortCombo)
                }
                row {
                    cell(requireParametersCheckbox)
                }
                row {
                    label("Data collection:")
                    cell(dataCollectionCombo)
                }
            }

            group("Quantizations") {
                row {
                    comment("Restrict to models with these quantization formats (leave unchecked for any):")
                }
                QUANTIZATIONS.chunked(3).forEach { chunk ->
                    row {
                        chunk.forEach { q ->
                            cell(quantizationCheckboxes[q]!!)
                        }
                    }
                }
            }

            group("Global Fallback Models") {
                row {
                    comment("These models are used as fallbacks when a request doesn't specify a models[] array:")
                }

                row {
                    val decorator = ToolbarDecorator.createDecorator(fallbackModelsList)
                        .setAddAction { addFallbackModel() }
                        .setRemoveAction { removeFallbackModel() }
                        .setMoveUpAction { moveFallbackModelUp() }
                        .setMoveDownAction { moveFallbackModelDown() }
                    cell(decorator.createPanel()).align(Align.FILL)
                }.resizableRow()
            }

            group("Provider Filters (Advanced)") {
                row {
                    comment("Restrict routing to specific providers (only) or exclude providers (ignore). " +
                        "Leave both empty for no filtering.")
                }

                row {
                    label("Only these providers:")
                }
                row {
                    val onlyDecorator = ToolbarDecorator.createDecorator(onlyProvidersList)
                        .setAddAction { addOnlyProvider() }
                        .setRemoveAction { removeOnlyProvider() }
                    cell(onlyDecorator.createPanel()).align(Align.FILL)
                }.resizableRow()

                row {
                    label("Ignore these providers:")
                }
                row {
                    val ignoreDecorator = ToolbarDecorator.createDecorator(ignoreProvidersList)
                        .setAddAction { addIgnoreProvider() }
                        .setRemoveAction { removeIgnoreProvider() }
                    cell(ignoreDecorator.createPanel()).align(Align.FILL)
                }.resizableRow()
            }

            row {
                comment("These preferences are injected only when a request omits provider and models[]. " +
                    "Clients (like this plugin's sidebar chat) can override per-conversation.")
            }.topGap(TopGap.MEDIUM)
        }
    }

    private fun addProvider() {
        val availableProviders = ModelProviderUtils.KNOWN_PROVIDERS.values.toList()
        val used = providerOrderModel.elements().toList().toSet()
        val available = availableProviders.filter { it !in used }

        if (available.isEmpty()) {
            Messages.showWarningDialog("All providers are already in the list.", "No More Providers")
            return
        }

        val selected = Messages.showChooseDialog(
            "Select a provider to add:",
            "Add Provider",
            available.toTypedArray(),
            available[0],
            null
        )

        if (selected in 0 until available.size) {
            providerOrderModel.addElement(available[selected])
        }
    }

    private fun removeProvider() {
        val index = providerOrderList.selectedIndex
        if (index >= 0) {
            providerOrderModel.removeElementAt(index)
        }
    }

    private fun moveProviderUp() {
        val index = providerOrderList.selectedIndex
        if (index > 0) {
            val item = providerOrderModel.getElementAt(index)
            providerOrderModel.removeElementAt(index)
            providerOrderModel.insertElementAt(item, index - 1)
            providerOrderList.selectedIndex = index - 1
        }
    }

    private fun moveProviderDown() {
        val index = providerOrderList.selectedIndex
        if (index >= 0 && index < providerOrderModel.size() - 1) {
            val item = providerOrderModel.getElementAt(index)
            providerOrderModel.removeElementAt(index)
            providerOrderModel.insertElementAt(item, index + 1)
            providerOrderList.selectedIndex = index + 1
        }
    }

    private fun addFallbackModel() {
        val modelId = Messages.showInputDialog(
            "Enter model ID (e.g., openai/gpt-4o, anthropic/claude-3.5-sonnet):",
            "Add Fallback Model",
            null,
            "",
            object : InputValidator {
                override fun checkInput(input: String?): Boolean {
                    return !input.isNullOrBlank() && input.contains("/")
                }

                override fun canClose(input: String?): Boolean {
                    return checkInput(input)
                }
            }
        )

        if (modelId != null && modelId.isNotBlank()) {
            fallbackModelsModel.addElement(modelId)
        }
    }

    private fun removeFallbackModel() {
        val index = fallbackModelsList.selectedIndex
        if (index >= 0) {
            fallbackModelsModel.removeElementAt(index)
        }
    }

    private fun moveFallbackModelUp() {
        val index = fallbackModelsList.selectedIndex
        if (index > 0) {
            val item = fallbackModelsModel.getElementAt(index)
            fallbackModelsModel.removeElementAt(index)
            fallbackModelsModel.insertElementAt(item, index - 1)
            fallbackModelsList.selectedIndex = index - 1
        }
    }

    private fun moveFallbackModelDown() {
        val index = fallbackModelsList.selectedIndex
        if (index >= 0 && index < fallbackModelsModel.size() - 1) {
            val item = fallbackModelsModel.getElementAt(index)
            fallbackModelsModel.removeElementAt(index)
            fallbackModelsModel.insertElementAt(item, index + 1)
            fallbackModelsList.selectedIndex = index + 1
        }
    }

    private fun addOnlyProvider() {
        val allProviders = ModelProviderUtils.KNOWN_PROVIDERS.values.toList()
        val used = onlyProvidersModel.elements().toList().toSet()
        val available = allProviders.filter { it !in used }
        if (available.isEmpty()) return

        val selected = Messages.showChooseDialog(
            "Select a provider to restrict to:",
            "Add 'Only' Provider",
            available.toTypedArray(),
            available[0],
            null
        )
        if (selected in 0 until available.size) {
            onlyProvidersModel.addElement(available[selected])
        }
    }

    private fun removeOnlyProvider() {
        val index = onlyProvidersList.selectedIndex
        if (index >= 0) onlyProvidersModel.removeElementAt(index)
    }

    private fun addIgnoreProvider() {
        val allProviders = ModelProviderUtils.KNOWN_PROVIDERS.values.toList()
        val used = ignoreProvidersModel.elements().toList().toSet()
        val available = allProviders.filter { it !in used }
        if (available.isEmpty()) return

        val selected = Messages.showChooseDialog(
            "Select a provider to exclude:",
            "Add 'Ignore' Provider",
            available.toTypedArray(),
            available[0],
            null
        )
        if (selected in 0 until available.size) {
            ignoreProvidersModel.addElement(available[selected])
        }
    }

    private fun removeIgnoreProvider() {
        val index = ignoreProvidersList.selectedIndex
        if (index >= 0) ignoreProvidersModel.removeElementAt(index)
    }

    private fun loadSettings() {
        val routing = settingsService.providerRoutingManager

        enabledCheckbox.isSelected = routing.enabled
        initialEnabled = routing.enabled

        providerOrderModel.clear()
        routing.order.forEach { providerOrderModel.addElement(it) }
        initialOrder = routing.order.toList()

        allowFallbacksCheckbox.isSelected = routing.allowFallbacks
        initialAllowFallbacks = routing.allowFallbacks

        sortCombo.selectedItem = routing.sort
        initialSort = routing.sort

        requireParametersCheckbox.isSelected = routing.requireParameters
        initialRequireParameters = routing.requireParameters

        dataCollectionCombo.selectedItem = routing.dataCollection
        initialDataCollection = routing.dataCollection

        quantizationCheckboxes.forEach { (q, cb) ->
            cb.isSelected = q in routing.quantizations
        }
        initialQuantizations = routing.quantizations.toSet()

        fallbackModelsModel.clear()
        routing.fallbackModels.forEach { fallbackModelsModel.addElement(it) }
        initialFallbackModels = routing.fallbackModels.toList()

        onlyProvidersModel.clear()
        routing.only.forEach { onlyProvidersModel.addElement(it) }
        initialOnlyProviders = routing.only.toList()

        ignoreProvidersModel.clear()
        routing.ignore.forEach { ignoreProvidersModel.addElement(it) }
        initialIgnoreProviders = routing.ignore.toList()
    }

    fun isModified(): Boolean {
        return enabledCheckbox.isSelected != initialEnabled ||
            providerOrderModel.elements().toList() != initialOrder ||
            allowFallbacksCheckbox.isSelected != initialAllowFallbacks ||
            sortCombo.selectedItem != initialSort ||
            requireParametersCheckbox.isSelected != initialRequireParameters ||
            dataCollectionCombo.selectedItem != initialDataCollection ||
            quantizationCheckboxes.filter { (_, cb) -> cb.isSelected }.keys != initialQuantizations ||
            fallbackModelsModel.elements().toList() != initialFallbackModels ||
            onlyProvidersModel.elements().toList() != initialOnlyProviders ||
            ignoreProvidersModel.elements().toList() != initialIgnoreProviders
    }

    fun apply() {
        val routing = settingsService.providerRoutingManager

        routing.enabled = enabledCheckbox.isSelected
        routing.order = providerOrderModel.elements().toList().toMutableList()
        routing.allowFallbacks = allowFallbacksCheckbox.isSelected
        routing.sort = sortCombo.selectedItem as? String ?: ""
        routing.requireParameters = requireParametersCheckbox.isSelected
        routing.dataCollection = dataCollectionCombo.selectedItem as? String ?: ""
        routing.quantizations = quantizationCheckboxes
            .filter { (_, cb) -> cb.isSelected }
            .keys.toMutableList()
        routing.fallbackModels = fallbackModelsModel.elements().toList().toMutableList()
        routing.only = onlyProvidersModel.elements().toList().toMutableList()
        routing.ignore = ignoreProvidersModel.elements().toList().toMutableList()
    }

    fun reset() {
        loadSettings()
    }

    override fun dispose() {
        // No special cleanup needed
    }
}
