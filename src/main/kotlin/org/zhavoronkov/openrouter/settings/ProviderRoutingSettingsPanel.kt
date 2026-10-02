package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings panel for managing provider routing preferences.
 * Allows users to configure global defaults for provider order, fallbacks, sorting, etc.
 * These preferences are injected into proxy requests when enabled and absent from the request.
 *
 * The routing itself is edited in a [ProviderRoutingForm], the same form a preset's routing uses; this page
 * adds the master switch and the global fallback models, which only the global defaults have,
 * between the form's preference and filter groups where they have always been.
 */
class ProviderRoutingSettingsPanel(
    private val routing: ProviderRoutingManager = OpenRouterSettingsService.getInstance().providerRoutingManager
) : Disposable {

    private val enabledCheckbox = JBCheckBox("Inject default provider routing into proxy requests")
    internal val form = ProviderRoutingForm()

    private val fallbackModelsModel = DefaultListModel<String>()
    private val fallbackModelsList = JBList(fallbackModelsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredSize = Dimension(0, ProviderRoutingForm.LIST_PREFERRED_HEIGHT)
    }

    // What was shown last, for isModified
    private var initialEnabled = false
    private var initialRouting = ProviderRoutingPreferences()
    private var initialFallbackModels: List<String> = emptyList()

    /**
     * Create the main panel using UI DSL v2
     */
    fun createPanel(): JPanel {
        loadSettings()

        return panel {
            row {
                cell(enabledCheckbox)
            }.topGap(TopGap.MEDIUM)

            form.addPreferenceGroups(this)

            group("Global Fallback Models") {
                row {
                    comment("These models are used as fallbacks when a request doesn't specify a models[] array:")
                }

                row {
                    val decorator = ToolbarDecorator.createDecorator(fallbackModelsList)
                        .setAddAction { addFallbackModel() }
                        .setRemoveAction { ProviderRoutingForm.removeSelected(fallbackModelsList) }
                        .setMoveUpAction { ProviderRoutingForm.move(fallbackModelsList, -1) }
                        .setMoveDownAction { ProviderRoutingForm.move(fallbackModelsList, +1) }
                    cell(decorator.createPanel()).align(Align.FILL)
                }.resizableRow()
            }

            form.addFilterGroups(this)

            row {
                comment(
                    "These preferences are injected only when a request omits provider and models[]. " +
                        "Clients (like this plugin's sidebar chat) can override per-conversation."
                )
            }.topGap(TopGap.MEDIUM)
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

    private fun loadSettings() {
        enabledCheckbox.isSelected = routing.enabled
        initialEnabled = routing.enabled

        form.show(routing.toPreferences())
        initialRouting = form.value()

        fallbackModelsModel.clear()
        routing.fallbackModels.forEach { fallbackModelsModel.addElement(it) }
        initialFallbackModels = routing.fallbackModels.toList()
    }

    fun isModified(): Boolean {
        return enabledCheckbox.isSelected != initialEnabled ||
            form.value() != initialRouting ||
            fallbackModelsModel.elements().toList() != initialFallbackModels
    }

    fun apply() {
        routing.enabled = enabledCheckbox.isSelected
        routing.update(form.value())
        routing.fallbackModels = fallbackModelsModel.elements().toList().toMutableList()
        loadSettings()
    }

    fun reset() {
        loadSettings()
    }

    override fun dispose() {
        // No special cleanup needed
    }
}
