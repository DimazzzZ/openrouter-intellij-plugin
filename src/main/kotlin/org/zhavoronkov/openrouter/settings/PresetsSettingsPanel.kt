package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.PresetsManager
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings panel for managing OpenRouter Presets
 * This appears as a sub-page under Tools -> OpenRouter -> Presets
 */
@Suppress("TooManyFunctions")
class PresetsSettingsPanel : Disposable {

    companion object {
        private const val MIN_PANEL_WIDTH = 600
        private const val MIN_PANEL_HEIGHT = 400
        private const val LIST_PREFERRED_HEIGHT = 200
        private const val LIST_WIDTH_OFFSET = 50
        private const val PANEL_BORDER_SIZE = 10
        private const val CELL_PADDING_HORIZONTAL = 8
        private const val CELL_PADDING_VERTICAL = 4
    }

    private val settingsService = OpenRouterSettingsService.getInstance()

    // UI Components
    private val presetsListModel = DefaultListModel<String>()
    private val presetsList = JBList(presetsListModel)
    private lateinit var presetSlugTextField: JBTextField

    // Track initial state for isModified check
    private var initialPresets: List<String> = emptyList()

    init {
        setupPresetsList()
    }

    private fun setupPresetsList() {
        presetsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetsList.cellRenderer = PresetsListCellRenderer()
    }

    /**
     * Create the main panel using UI DSL v2
     */
    fun createPanel(): JPanel {
        // Load current presets from settings
        loadPresets()

        val panel = panel {
            group("Built-in Presets") {
                row {
                    comment(
                        "These presets are always available and don't need to be configured:"
                    )
                }

                PresetsManager.BUILT_IN_PRESETS.forEach { preset ->
                    row {
                        label(preset.id).bold()
                        label(" - ${preset.description}")
                    }.topGap(TopGap.NONE)
                }
            }

            group("Custom Presets") {
                row {
                    text(
                        "<icon src='AllIcons.General.Information'/>&nbsp;<b>Note:</b> " +
                            "OpenRouter doesn't provide a public API for listing presets. " +
                            "You need to manually add your preset slugs from your " +
                            "<a href='https://openrouter.ai/presets'>OpenRouter presets page</a>."
                    )
                }

                row {
                    comment(
                        "Presets appear in the model selector with @preset/ prefix."
                    )
                }.topGap(TopGap.NONE)

                row("Add preset slug:") {
                    presetSlugTextField = textField()
                        .applyToComponent {
                            toolTipText = "Enter preset slug (e.g., 'email-copywriter'). " +
                                "Find slugs at openrouter.ai/presets - it's the part after '/presets/' in URL."
                        }
                        .component
                    button("Add") { addPreset() }
                }.layout(RowLayout.PARENT_GRID).topGap(TopGap.SMALL)

                row {
                    val decorator = ToolbarDecorator.createDecorator(presetsList)
                        .disableAddAction() // We have our own Add button
                        .setRemoveAction { removeSelectedPreset() }
                        .disableUpDownActions()
                        .setPreferredSize(
                            Dimension(MIN_PANEL_WIDTH - LIST_WIDTH_OFFSET, LIST_PREFERRED_HEIGHT)
                        )

                    cell(decorator.createPanel())
                        .align(Align.FILL)
                        .resizableColumn()
                }.resizableRow().topGap(TopGap.NONE)
            }
        }

        panel.minimumSize = Dimension(MIN_PANEL_WIDTH, MIN_PANEL_HEIGHT)
        panel.border = JBUI.Borders.empty(PANEL_BORDER_SIZE)

        return panel
    }

    /**
     * Load presets from settings service
     */
    private fun loadPresets() {
        val presets = settingsService.presetsManager.getCustomPresets()
        initialPresets = presets.toList()

        presetsListModel.clear()
        presets.forEach { presetsListModel.addElement(it) }
    }

    /**
     * Add a preset from the text field
     */
    private fun addPreset() {
        if (!::presetSlugTextField.isInitialized) return

        val slug = presetSlugTextField.text.trim()
        val validationResult = validateAndNormalizeSlug(slug)

        when (validationResult) {
            is SlugValidationResult.Empty -> {
                Messages.showWarningDialog(
                    "Please enter a preset slug (e.g., 'email-copywriter')",
                    "Empty Preset Slug"
                )
            }
            is SlugValidationResult.Invalid -> {
                Messages.showWarningDialog(
                    "Invalid preset slug. Use only letters, numbers, and hyphens.",
                    "Invalid Preset Slug"
                )
            }
            is SlugValidationResult.Duplicate -> {
                Messages.showInfoMessage(
                    "Preset '${validationResult.slug}' is already added.",
                    "Preset Already Exists"
                )
            }
            is SlugValidationResult.Valid -> {
                presetsListModel.addElement(validationResult.slug)
                presetSlugTextField.text = ""
                PluginLogger.Settings.info("Added preset: ${validationResult.slug}")
            }
        }
    }

    private fun validateAndNormalizeSlug(slug: String): SlugValidationResult = when {
        slug.isBlank() -> SlugValidationResult.Empty
        normalizeSlug(slug).isBlank() -> SlugValidationResult.Invalid
        getCurrentPresets().contains(normalizeSlug(slug)) -> SlugValidationResult.Duplicate(normalizeSlug(slug))
        else -> SlugValidationResult.Valid(normalizeSlug(slug))
    }

    private sealed class SlugValidationResult {
        data object Empty : SlugValidationResult()
        data object Invalid : SlugValidationResult()
        data class Duplicate(val slug: String) : SlugValidationResult()
        data class Valid(val slug: String) : SlugValidationResult()
    }

    /**
     * Remove selected preset from the list
     */
    private fun removeSelectedPreset() {
        val selectedIndex = presetsList.selectedIndex
        if (selectedIndex >= 0) {
            val removed = presetsListModel.get(selectedIndex)
            presetsListModel.remove(selectedIndex)
            PluginLogger.Settings.info("Removed preset: $removed")
        }
    }

    /**
     * Normalize a preset slug
     */
    private fun normalizeSlug(slug: String): String {
        return slug
            .removePrefix(PresetsManager.PRESET_PREFIX)
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    /**
     * Get current presets from the list model
     */
    private fun getCurrentPresets(): List<String> {
        val presets = mutableListOf<String>()
        for (i in 0 until presetsListModel.size()) {
            presetsListModel.get(i)?.let { presets.add(it) }
        }
        return presets
    }

    /**
     * Check if settings have been modified
     */
    fun isModified(): Boolean {
        return getCurrentPresets() != initialPresets
    }

    /**
     * Apply changes to settings
     */
    fun apply() {
        val presets = getCurrentPresets()
        settingsService.presetsManager.setCustomPresets(presets)
        initialPresets = presets.toList()
        PluginLogger.Settings.info("Applied ${presets.size} custom presets")
    }

    /**
     * Reset to initial state
     */
    fun reset() {
        loadPresets()
    }

    override fun dispose() {
        // No resources to clean up
    }

    /**
     * Custom cell renderer for presets list
     */
    private inner class PresetsListCellRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            val preset = value as? String
            if (preset != null) {
                text = "@preset/$preset"
                toolTipText = "Preset slug: $preset"
            }

            border = JBUI.Borders.empty(CELL_PADDING_VERTICAL, CELL_PADDING_HORIZONTAL)

            return this
        }
    }
}
