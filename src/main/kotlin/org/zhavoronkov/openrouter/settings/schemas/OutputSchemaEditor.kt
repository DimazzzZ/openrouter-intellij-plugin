package org.zhavoronkov.openrouter.settings.schemas

import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.PLAIN_DOCUMENT
import java.awt.Font
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The fields of one Output Schema - a name, a strict flag and the schema itself - with the schema
 * checked as it is typed.
 *
 * Kept apart from [OutputSchemaDialog] so that everything it decides can be driven and read in a
 * test without a modal dialog. [takenNames] are the names of the other saved schemas, which this
 * one may not reuse.
 */
class OutputSchemaEditor(initial: OutputSchema?, private val takenNames: List<String>) {

    internal val name = JBTextField(initial?.name.orEmpty())
    internal val strict = JBCheckBox(STRICT_TEXT, initial?.strict ?: true)
    internal val body = JBTextArea(initial?.schema ?: TEMPLATE, BODY_ROWS, BODY_COLUMNS).apply {
        font = JBUI.Fonts.create(Font.MONOSPACED, font.size)
        tabSize = 2
    }

    /**
     * What stops the schema being saved, or that the schema is valid - the name's problem first,
     * then the schema's - updated on every keystroke in either field.
     */
    internal val status = JBLabel()

    /** Set by the caller; invoked after every edit to either field, once [status] is current. */
    var onChanged: () -> Unit = {}

    val component: JComponent = panel {
        row("Name:") {
            cell(name).align(AlignX.FILL)
                .comment("Listed in the chat's Output mode control, and sent to OpenRouter as the schema's name.")
        }
        row { cell(strict) }
        row("Schema:") {}
        row {
            cell(ScrollPaneFactory.createScrollPane(body)).align(Align.FILL).applyToComponent {
                preferredSize = JBDimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)
            }
        }.resizableRow()
        row { cell(status) }
    }

    init {
        val listener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = changed()
            override fun removeUpdate(e: DocumentEvent) = changed()

            @ExcludeFromCoverage(PLAIN_DOCUMENT)
            override fun changedUpdate(e: DocumentEvent) = changed()
        }
        name.document.addDocumentListener(listener)
        body.document.addDocumentListener(listener)
        refreshStatus()
    }

    private fun changed() {
        refreshStatus()
        onChanged()
    }

    /** What stops the schema being saved: the field to point at, and what is wrong with it. */
    data class Problem(val field: JComponent, val message: String)

    /** Why the schema cannot be saved yet, or null when it can. */
    fun problem(): Problem? {
        OutputSchema.nameProblem(name.text, takenNames)?.let { return Problem(name, it) }
        val check = OutputSchema.validateBody(body.text)
        return if (check is OutputSchema.BodyCheck.Invalid) Problem(body, check.message) else null
    }

    /** The schema as the fields describe it; only meaningful when [problem] is null. */
    fun result(): OutputSchema = OutputSchema(name = name.text.trim(), strict = strict.isSelected, schema = body.text)

    private fun refreshStatus() {
        val problem = problem()
        status.text = problem?.message ?: OutputSchema.validateBody(body.text).message
        status.foreground = if (problem == null) UIUtil.getContextHelpForeground() else UIUtil.getErrorForeground()
    }

    internal companion object {
        const val STRICT_TEXT = "Strict: the model must follow the schema exactly"
        const val BODY_ROWS = 14
        const val BODY_COLUMNS = 60
        const val PREFERRED_WIDTH = 520
        const val PREFERRED_HEIGHT = 280

        /** What a new schema starts from: the smallest object schema, ready to be filled in. */
        const val TEMPLATE = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "  },\n" +
            "  \"required\": [],\n" +
            "  \"additionalProperties\": false\n" +
            "}"
    }
}
