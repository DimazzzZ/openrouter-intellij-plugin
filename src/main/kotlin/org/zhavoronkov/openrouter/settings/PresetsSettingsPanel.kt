package org.zhavoronkov.openrouter.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.Preset
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.PresetsManager
import org.zhavoronkov.openrouter.settings.presets.PresetsPageState
import org.zhavoronkov.openrouter.settings.presets.PresetsPageState.EmptyState
import org.zhavoronkov.openrouter.settings.presets.PresetsPageState.WellKnownKey
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Settings panel for OpenRouter Presets (Tools -> OpenRouter -> Presets).
 *
 * A thin Swing adapter over the platform-free [PresetsPageState]: the view-model
 * owns all page logic, the panel renders from it and forwards user actions.
 * Presets are server-owned; this panel lists the user's fetched presets, shows a
 * selected preset's config read-only, and stages an editable copy that Save
 * submits through [OpenRouterService.createOrUpdatePreset] (a new version when the
 * slug already exists). There is no delete action: OpenRouter has no delete
 * endpoint, so deletion is web-UI only.
 */
@Suppress("TooManyFunctions")
class PresetsSettingsPanel(
    private val serviceProvider: () -> OpenRouterService = { OpenRouterService.getInstance() },
    private val isConfigured: () -> Boolean = { OpenRouterSettingsService.getInstance().isConfigured() },
    private val state: PresetsPageState = PresetsPageState(),
    private val autoLoad: Boolean = true,
) : Disposable {

    private companion object {
        const val PANEL_BORDER = 10
        const val LIST_PREFERRED_WIDTH = 560
        const val LIST_PREFERRED_HEIGHT = 160
        const val DETAIL_PREFERRED_WIDTH = 560
        const val DETAIL_PREFERRED_HEIGHT = 180
        const val PROMPT_ROWS = 6
        const val PROMPT_COLUMNS = 60
        const val CELL_PADDING_HORIZONTAL = 8
        const val CELL_PADDING_VERTICAL = 4
        const val WHOLE_NUMBER_EPSILON = 1.0
        const val MODEL_KEY = "model"
        const val TOOLS_KEY = "tools"
        const val MISSING_KEY_MESSAGE =
            "To manage presets, add your API key in Tools -> OpenRouter -> Settings."
        const val BUILT_IN_COMMENT =
            "These presets are always available and don't need to be configured:"
        const val DELETE_NOTE =
            "Presets cannot be deleted here: OpenRouter has no delete endpoint, so deletion is web-UI only."
        const val TOOLS_NOTE =
            "The tools array (if any) is preserved on save but not shown field-by-field here."
        const val SAVE_HINT =
            "Saving an existing slug creates a NEW VERSION of that preset (the old version is kept)."
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this)

    private val presetsListModel = DefaultListModel<Preset>()
    private val presetsList = JBList(presetsListModel)

    private val detailArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        name = "presetDetailArea"
    }

    private val modelField = JBTextField()
    private val temperatureField = JBTextField()
    private val topPField = JBTextField()
    private val maxTokensField = JBTextField()
    private val systemPromptArea = JBTextArea(PROMPT_ROWS, PROMPT_COLUMNS).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private var suppressSelectionEvents = false

    // Visible status/error line — surfaces load errors even when the list is non-empty (behaviour 2).
    private val statusLabel = JBLabel()

    fun createPanel(): JPanel {
        state.isConfigured = isConfigured()
        state.onChanged = ::render
        setupList()

        val content = panel {
            builtInGroup()
            yourPresetsGroup()
            detailGroup()
            editorGroup()
        }
        content.border = JBUI.Borders.empty(PANEL_BORDER)
        loadingPanel.add(content, BorderLayout.CENTER)

        render()
        if (state.isConfigured && autoLoad) loadPresets()
        return loadingPanel
    }

    private fun Panel.builtInGroup() {
        group("Built-in Presets") {
            row { comment(BUILT_IN_COMMENT) }
            PresetsManager.BUILT_IN_PRESETS.forEach { preset ->
                row {
                    label(preset.id).bold()
                    label(" - ${preset.description}")
                }.topGap(TopGap.NONE)
            }
        }
    }

    private fun Panel.yourPresetsGroup() {
        group("Your Presets") {
            if (!state.isConfigured) {
                row {
                    icon(AllIcons.General.Warning).gap(RightGap.SMALL)
                    label(MISSING_KEY_MESSAGE)
                    button("Open Settings") { openMainSettings() }
                }.topGap(TopGap.NONE)
            }
            row {
                button("Refresh") { loadPresets() }
                button("New Preset…") { promptNewPreset() }
            }.topGap(TopGap.NONE).visible(state.isConfigured)
            row {
                cell(statusLabel).align(Align.FILL).resizableColumn()
            }.topGap(TopGap.NONE).visible(state.isConfigured)
            row {
                cell(ScrollPaneFactory.createScrollPane(presetsList)).apply {
                    component.preferredSize = JBDimension(LIST_PREFERRED_WIDTH, LIST_PREFERRED_HEIGHT)
                }.align(Align.FILL).resizableColumn()
            }.resizableRow().topGap(TopGap.SMALL).visible(state.isConfigured)
            row { comment(DELETE_NOTE) }.topGap(TopGap.SMALL)
        }.resizableRow()
    }

    private fun Panel.detailGroup() {
        group("Selected Preset (read-only)") {
            row {
                cell(ScrollPaneFactory.createScrollPane(detailArea)).apply {
                    component.preferredSize = JBDimension(DETAIL_PREFERRED_WIDTH, DETAIL_PREFERRED_HEIGHT)
                }.align(Align.FILL).resizableColumn()
            }.resizableRow()
            row { comment(TOOLS_NOTE) }.topGap(TopGap.NONE)
        }
    }

    private fun Panel.editorGroup() {
        group("Edit / Save Preset") {
            row("Model:") { cell(modelField).align(AlignX.FILL).resizableColumn() }
            row("Temperature:") { cell(temperatureField).align(AlignX.FILL).resizableColumn() }
            row("Top P:") { cell(topPField).align(AlignX.FILL).resizableColumn() }
            row("Max tokens:") { cell(maxTokensField).align(AlignX.FILL).resizableColumn() }
            row("System prompt:") {
                cell(ScrollPaneFactory.createScrollPane(systemPromptArea)).align(Align.FILL).resizableColumn()
            }.resizableRow()
            row {
                button("Save") { savePreset() }
                button("Cancel") { state.cancelEdit() }
            }.topGap(TopGap.SMALL)
            row { comment(SAVE_HINT) }.topGap(TopGap.NONE)
        }
    }

    private fun setupList() {
        presetsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        presetsList.cellRenderer = PresetsListCellRenderer()
        presetsList.addListSelectionListener { event ->
            if (event.valueIsAdjusting || suppressSelectionEvents) return@addListSelectionListener
            val selected = presetsList.selectedValue
            if (selected == null) state.selectedSlug = null else state.beginEdit(selected.slug)
        }
    }

    // --- rendering -------------------------------------------------------------------------

    private fun render() {
        syncListModel()
        syncSelection()
        detailArea.text = detailTextFor(state.selectedPreset())
        detailArea.caretPosition = 0
        renderEditor()
        renderEmptyText()
        statusLabel.text = state.statusText()
    }

    private fun syncListModel() {
        suppressSelectionEvents = true
        try {
            presetsListModel.clear()
            state.visiblePresets().forEach { presetsListModel.addElement(it) }
        } finally {
            suppressSelectionEvents = false
        }
    }

    private fun syncSelection() {
        val slug = state.selectedSlug
        if (slug == null) {
            suppressSelectionEvents = true
            presetsList.clearSelection()
            suppressSelectionEvents = false
            return
        }
        val index = (0 until presetsListModel.size()).firstOrNull { presetsListModel.get(it).slug == slug } ?: -1
        suppressSelectionEvents = true
        if (index >= 0) presetsList.selectedIndex = index else presetsList.clearSelection()
        suppressSelectionEvents = false
    }

    private fun renderEditor() {
        val editor = state.editor
        val enabled = editor != null
        modelField.isEnabled = enabled
        temperatureField.isEnabled = enabled
        topPField.isEnabled = enabled
        maxTokensField.isEnabled = enabled
        systemPromptArea.isEnabled = enabled
        modelField.text = valueText(editor?.wellKnown?.get(WellKnownKey.MODEL))
        temperatureField.text = valueText(editor?.wellKnown?.get(WellKnownKey.TEMPERATURE))
        topPField.text = valueText(editor?.wellKnown?.get(WellKnownKey.TOP_P))
        maxTokensField.text = valueText(editor?.wellKnown?.get(WellKnownKey.MAX_TOKENS))
        systemPromptArea.text = editor?.systemPrompt.orEmpty()
    }

    private fun renderEmptyText() {
        val link = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        val empty = presetsList.emptyText.clear()
        when (state.emptyState()) {
            EmptyState.NONE -> Unit
            EmptyState.NOT_CONFIGURED -> empty.appendText("Add an API key to load your presets.")
            EmptyState.LOAD_FAILED -> empty.appendText("Failed to load presets.")
                .appendSecondaryText("Retry", link) { loadPresets() }
            EmptyState.NO_PRESETS -> empty.appendText("No presets found.")
        }
    }

    private fun detailTextFor(preset: Preset?): String {
        if (preset == null) return "Select a preset to view its configuration."
        val version = preset.designatedVersion
        val config = version?.config ?: emptyMap()
        val builder = StringBuilder()
        builder.append("name: ").append(preset.name).append('\n')
        builder.append("slug: ").append(preset.slug).append('\n')
        builder.append("model: ").append(valueText(config[MODEL_KEY]).ifBlank { "(none)" }).append('\n')
        builder.append("system_prompt: ").append(version?.systemPrompt?.ifBlank { null } ?: "(none)").append('\n')
        builder.append("params:").append('\n')
        val paramKeys = config.keys.filter { it != MODEL_KEY && it != TOOLS_KEY }.sorted()
        if (paramKeys.isEmpty()) {
            builder.append("  (none)").append('\n')
        } else {
            paramKeys.forEach { key ->
                builder.append("  ").append(key).append(": ").append(valueText(config[key])).append('\n')
            }
        }
        builder.append(TOOLS_NOTE)
        return builder.toString()
    }

    // --- user actions ----------------------------------------------------------------------

    private fun loadPresets() {
        if (!state.isConfigured) return
        loadingPanel.startLoading()
        coroutineScope.launch {
            val result = serviceProvider().getPresets()
            loadingPanel.stopLoading()
            when (result) {
                is ApiResult.Success -> {
                    state.setPresets(result.data.data)
                    // Keep the model-selector wiring working: ChatPanel and ModelsServlet build the
                    // @preset/<slug> list from PresetsManager.getCustomPresets(), so mirror the
                    // fetched slugs into it (AC6).
                    OpenRouterSettingsService.getInstance()
                        .presetsManager.setCustomPresets(result.data.data.map { it.slug })
                }
                is ApiResult.Error -> {
                    PluginLogger.Settings.warn("Failed to load presets: ${result.message}")
                    state.setLoadError(result.message)
                }
            }
        }
    }

    private fun savePreset() {
        savePresetInternal()
    }

    /** Prompt for a new slug and stage a blank editor for it (spec behaviour 4, create half). */
    private fun promptNewPreset() {
        val raw = Messages.showInputDialog(
            "Enter a preset slug (letters, numbers, hyphens), e.g. email-copywriter:",
            "New Preset",
            Messages.getQuestionIcon()
        ) ?: return
        val slug = raw.trim().lowercase()
            .removePrefix(PresetsManager.PRESET_PREFIX)
            .replace(Regex("[^a-z0-9-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        if (slug.isBlank()) {
            Messages.showWarningDialog("Please enter a valid slug (letters, numbers, hyphens).", "New Preset")
            return
        }
        if (state.visiblePresets().any { it.slug == slug }) {
            state.beginEdit(slug)
        } else {
            state.beginCreate(slug)
        }
    }

    private fun savePresetInternal() {
        if (state.editor == null) return
        commitEditorFields()
        val committed = state.editor ?: return
        if (state.isNewVersionOfExisting()) {
            val choice = Messages.showYesNoDialog(
                "Preset '${committed.slug}' already exists. Saving creates a NEW VERSION of it. Continue?",
                "Save Preset",
                Messages.getQuestionIcon()
            )
            if (choice != Messages.YES) return
        }
        val slug = committed.slug
        val config = committed.toConfig()
        val systemPrompt = committed.systemPrompt
        loadingPanel.startLoading()
        coroutineScope.launch {
            val result = serviceProvider().createOrUpdatePreset(slug, config, systemPrompt)
            when (result) {
                is ApiResult.Success -> {
                    state.cancelEdit()
                    loadPresets()
                }
                is ApiResult.Error -> {
                    loadingPanel.stopLoading()
                    PluginLogger.Settings.warn("Failed to save preset $slug: ${result.message}")
                    Messages.showErrorDialog("Failed to save preset: ${result.message}", "Save Preset")
                }
            }
        }
    }

    /** Push the current field text into the view-model before submitting. */
    private fun commitEditorFields() {
        state.updateWellKnown(WellKnownKey.MODEL, modelField.text.trim().ifBlank { null })
        state.updateWellKnown(WellKnownKey.TEMPERATURE, temperatureField.text.trim().toDoubleOrNull())
        state.updateWellKnown(WellKnownKey.TOP_P, topPField.text.trim().toDoubleOrNull())
        state.updateWellKnown(WellKnownKey.MAX_TOKENS, maxTokensField.text.trim().toIntOrNull())
        state.updateSystemPrompt(systemPromptArea.text)
    }

    private fun openMainSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, OpenRouterConfigurable::class.java)
    }

    private fun valueText(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value % WHOLE_NUMBER_EPSILON == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    // --- Configurable contract -------------------------------------------------------------

    fun isModified(): Boolean = state.editor != null

    fun apply() {
        // Presets are server-owned; edits are submitted explicitly via Save, so
        // there is nothing to persist locally here.
    }

    fun reset() {
        state.cancelEdit()
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    /** Renders each fetched preset as "name (slug) - model". */
    private inner class PresetsListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val preset = value as? Preset
            if (preset != null) {
                val model = valueText(preset.designatedVersion?.config?.get(MODEL_KEY))
                text = if (model.isBlank()) {
                    "${preset.name} (${preset.slug})"
                } else {
                    "${preset.name} (${preset.slug}) - $model"
                }
                toolTipText = "@preset/${preset.slug}"
            }
            border = JBUI.Borders.empty(CELL_PADDING_VERTICAL, CELL_PADDING_HORIZONTAL)
            return this
        }
    }
}
