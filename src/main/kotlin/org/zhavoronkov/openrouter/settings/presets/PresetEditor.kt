package org.zhavoronkov.openrouter.settings.presets

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.models.RequestChoices
import org.zhavoronkov.openrouter.toolwindow.chat.CHAT_WARNING_FOREGROUND
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.PLAIN_DOCUMENT
import org.zhavoronkov.openrouter.utils.asObjectOrNull
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/**
 * The fields of one preset: its slug, then every setting a preset can have, each on a row of its
 * own and each starting empty - "Not set", or an empty field - which leaves it out of the preset.
 * What the preset sets is whatever is not empty, and nothing is hidden behind a menu. Kept apart
 * from [PresetDialog] so that a test can drive it without a modal dialog.
 *
 * [editRouting] edits a `provider` block and gives back the new one, or null when cancelled - the
 * Provider Routing form in a dialog of its own, since the whole form in a row is what made a
 * preset's editor scroll sideways.
 */
class PresetEditor(
    internal val draft: PresetDraft,
    private val isNew: Boolean,
    private val takenSlugs: List<String>,
    private val schemas: List<OutputSchema>,
    private val editRouting: (ProviderRoutingPreferences) -> ProviderRoutingPreferences?
) {
    internal val slug = JBTextField(draft.slug).apply { isEditable = isNew }

    internal val model = textField(PresetSetting.MODEL)
    internal val webSearch = choice(
        listOf(NOT_SET, ALLOWED),
        if (draft.has(PresetSetting.WEB_SEARCH)) ALLOWED else NOT_SET
    )

    /** The preset's own schema when the plugin has not saved one of its name, kept to be chosen again. */
    private val unsavedSchemaName: String? =
        draft.outputSchemaName?.takeIf { name -> schemas.none { it.name.equals(name, true) } }

    // Unreachable branch: unsavedSchemaName is read from the OUTPUT value, so that value is never null in the let block
    private val unsavedSchemaOutput = unsavedSchemaName?.let { draft[PresetSetting.OUTPUT]?.deepCopy() }

    private val outputChoices: List<String> =
        listOf(NOT_SET, PLAIN_JSON) + schemas.map { it.name } + listOfNotNull(unsavedSchemaName)
    internal val output = choice(
        outputChoices,
        when {
            !draft.has(PresetSetting.OUTPUT) -> NOT_SET
            draft.plainJson -> PLAIN_JSON
            else -> draft.outputSchemaName ?: NOT_SET
        }
    )
    internal val reasoning = choice(
        listOf(NOT_SET) + RequestChoices.REASONING_EFFORTS.keys,
        draft.reasoningLabel ?: NOT_SET
    )
    internal val verbosity = choice(listOf(NOT_SET) + RequestChoices.VERBOSITIES, draft.verbosityLabel ?: NOT_SET)

    internal val routingSummary = JBLabel()
    internal val editRoutingLink = ActionLink("Edit…") { editRoutingBlock() }
    internal val clearRoutingLink = ActionLink("Clear") { clearRouting() }

    internal val temperature = textField(PresetSetting.TEMPERATURE)
    internal val topP = textField(PresetSetting.TOP_P)
    internal val maxTokens = textField(PresetSetting.MAX_TOKENS)
    internal val systemPrompt = JBTextArea(draft.systemPrompt.orEmpty(), PROMPT_ROWS, 0).apply {
        lineWrap = true
        wrapStyleWord = true
    }

    /** What stops the preset being saved, what OpenRouter will drop, or what it sets. */
    internal val status = JBLabel()

    /** Offers to copy a saved schema that changed since this preset copied it. */
    internal val updateSchema = ActionLink("") { copyStaleSchema() }

    /** Set by the caller; invoked after every change that can alter [problem]. */
    var onChanged: () -> Unit = {}

    val component: JComponent = panel {
        row("Slug:") { cell(slug).align(AlignX.FILL) }
        row("Model:") { cell(model).align(AlignX.FILL).comment("Empty: the model a pair names") }
        row("Web search:") { cell(webSearch) }
        row("Output:") { cell(output) }
        row("Reasoning:") { cell(reasoning) }
        row("Verbosity:") { cell(verbosity) }
        row("Provider routing:") { cell(routingRow()).align(AlignX.FILL).resizableColumn() }
        row("Temperature:") { cell(temperature).columns(NUMBER_COLUMNS) }
        row("Top P:") { cell(topP).columns(NUMBER_COLUMNS) }
        row("Max tokens:") { cell(maxTokens).columns(NUMBER_COLUMNS) }
        row("System prompt:") { cell(JBScrollPane(systemPrompt)).align(AlignX.FILL) }
        row { cell(status) }
        row { cell(updateSchema) }
    }

    init {
        slug.onText {
            draft.slug = slug.text.trim()
            changed()
        }
        model.onText { setOrRemove(PresetSetting.MODEL, model.text.trim()) }
        systemPrompt.onText { setOrRemove(PresetSetting.SYSTEM_PROMPT, systemPrompt.text.takeIf { it.isNotBlank() }) }
        listOf(temperature, topP, maxTokens).forEach { it.onText(::changed) }
        webSearch.addActionListener {
            if (webSearch.selectedItem == ALLOWED) {
                draft.add(PresetSetting.WEB_SEARCH)
            } else {
                draft.remove(PresetSetting.WEB_SEARCH)
            }
            changed()
        }
        output.addActionListener {
            // Unreachable branch: every item of the combo is a String and nothing clears its selection
            when (val chosen = output.selectedItem as? String) {
                NOT_SET -> draft.remove(PresetSetting.OUTPUT)
                PLAIN_JSON -> draft.setPlainJson()
                // Unreachable branch: unsavedSchemaOutput is null only if unsavedSchemaName is; chosen is never null
                unsavedSchemaName -> unsavedSchemaOutput?.let { draft[PresetSetting.OUTPUT] = it.deepCopy() }
                // Unreachable branch: the else arm only sees a name listed from schemas, so it is always found
                else -> schemas.firstOrNull { it.name == chosen }?.let(draft::setSchema)
            }
            changed()
        }
        reasoning.addActionListener {
            // Unreachable branch: every item of the combo is a String and nothing clears its selection
            val chosen = reasoning.selectedItem as? String
            if (chosen == NOT_SET) draft.remove(PresetSetting.REASONING) else draft.reasoningLabel = chosen
            changed()
        }
        verbosity.addActionListener {
            // Unreachable branch: every item of the combo is a String and nothing clears its selection
            val chosen = verbosity.selectedItem as? String
            if (chosen == NOT_SET) draft.remove(PresetSetting.VERBOSITY) else draft.verbosityLabel = chosen
            changed()
        }
        refreshStatus()
    }

    /** The preset as the fields describe it; only meaningful when [problem] is null. */
    fun result(): PresetDraft {
        commitNumbers()
        return draft
    }

    /** Why the preset cannot be saved, or null when it can. */
    fun problem(): String? = numberProblem() ?: draft.problem(isNew, takenSlugs)

    private fun routingRow() = JPanel(BorderLayout(JBUI.scale(GAP), 0)).apply {
        isOpaque = false
        add(routingSummary, BorderLayout.CENTER)
        add(
            JPanel(BorderLayout(JBUI.scale(GAP), 0)).apply {
                isOpaque = false
                add(editRoutingLink, BorderLayout.WEST)
                add(clearRoutingLink, BorderLayout.EAST)
            },
            BorderLayout.EAST
        )
    }

    private fun setOrRemove(setting: PresetSetting, text: String?) {
        if (text.isNullOrEmpty()) draft.remove(setting) else draft[setting] = JsonPrimitive(text)
        changed()
    }

    private fun editRoutingBlock() {
        val block = routingBlock()
        val edited = editRouting(PresetRouting.preferencesOf(block)) ?: return
        val merged = PresetRouting.merged(block, edited)
        if (merged.size() == 0) {
            draft.remove(PresetSetting.PROVIDER_ROUTING)
        } else {
            draft[PresetSetting.PROVIDER_ROUTING] = merged
        }
        changed()
    }

    private fun clearRouting() {
        draft.remove(PresetSetting.PROVIDER_ROUTING)
        changed()
    }

    private fun routingBlock(): JsonObject =
        draft[PresetSetting.PROVIDER_ROUTING]?.asObjectOrNull() ?: JsonObject()

    private fun copyStaleSchema() {
        // Unreachable branch: updateSchema is visible only while staleSchema is non-null, and every change re-checks it
        draft.staleSchema(schemas)?.let(draft::setSchema)
        changed()
    }

    private fun changed() {
        refreshStatus()
        onChanged()
    }

    private fun refreshStatus() {
        val routed = draft.has(PresetSetting.PROVIDER_ROUTING)
        routingSummary.text = if (routed) PresetRouting.summary(routingBlock()) else NOT_SET
        routingSummary.toolTipText = routingSummary.text
        routingSummary.foreground = if (routed) UIUtil.getLabelForeground() else UIUtil.getContextHelpForeground()
        clearRoutingLink.isVisible = routed
        val problem = problem()
        val warning = draft.webSearchWarning
        status.text = problem ?: warning ?: "Sets: ${draft.summary}"
        status.toolTipText = status.text
        status.foreground = when {
            problem != null -> UIUtil.getErrorForeground()
            warning != null -> CHAT_WARNING_FOREGROUND
            else -> UIUtil.getContextHelpForeground()
        }
        val stale = draft.staleSchema(schemas)
        updateSchema.isVisible = stale != null
        updateSchema.text = stale?.let { "Update from saved schema '${it.name}'" }.orEmpty()
    }

    /** A number field left empty sets nothing; one holding anything but a number stops the save. */
    private fun numberProblem(): String? = listOf(
        Triple(PresetSetting.TEMPERATURE, temperature, false),
        Triple(PresetSetting.TOP_P, topP, false),
        Triple(PresetSetting.MAX_TOKENS, maxTokens, true)
    ).firstNotNullOfOrNull { (setting, field, whole) ->
        val text = field.text.trim()
        val ok = text.isEmpty() || if (whole) text.toIntOrNull() != null else text.toDoubleOrNull() != null
        "${setting.title} must be a ${if (whole) "whole number" else "number"}".takeUnless { ok }
    }

    private fun commitNumbers() {
        commitNumber(PresetSetting.TEMPERATURE, temperature, String::toDoubleOrNull)
        commitNumber(PresetSetting.TOP_P, topP, String::toDoubleOrNull)
        commitNumber(PresetSetting.MAX_TOKENS, maxTokens, String::toIntOrNull)
    }

    /** An unchanged number keeps the JSON it came as, so a `1` does not come back as `1.0`. */
    private fun commitNumber(setting: PresetSetting, field: JBTextField, parse: (String) -> Number?) {
        val text = field.text.trim()
        if (draft[setting]?.takeIf { it.isJsonPrimitive }?.asString == text) return
        val value = parse(text)
        if (value == null) draft.remove(setting) else draft[setting] = JsonPrimitive(value)
    }

    private fun textField(setting: PresetSetting) = JBTextField(
        draft[setting]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    )

    private fun choice(items: List<String>, selected: String) =
        ComboBox(DefaultComboBoxModel(items.toTypedArray())).apply { selectedItem = selected }

    private fun JTextComponent.onText(action: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = action()
            override fun removeUpdate(e: DocumentEvent) = action()

            @ExcludeFromCoverage(PLAIN_DOCUMENT)
            override fun changedUpdate(e: DocumentEvent) = action()
        })
    }

    internal companion object {
        const val NOT_SET = "Not set"
        const val ALLOWED = "Allowed"
        const val PLAIN_JSON = "Plain JSON"
        const val PROMPT_ROWS = 4
        const val NUMBER_COLUMNS = 8
        const val GAP = 6
    }
}
