package org.zhavoronkov.openrouter.settings.schemas

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.MODAL_DIALOG
import java.awt.Component
import javax.swing.JComponent

/**
 * The modal editor for one Output Schema. OK is disabled while the name or the schema has a
 * problem, re-decided on every keystroke, and the editor's status line says what the problem is.
 *
 * The dialog decides OK itself rather than through the platform's continuous validation, whose
 * balloon keeps the first message it showed while the fields change under it. [doValidate] still
 * runs when OK is pressed, as the last word.
 */
class OutputSchemaDialog(
    parent: Component,
    initial: OutputSchema?,
    takenNames: List<String>
) : DialogWrapper(parent, true) {

    internal val editor = OutputSchemaEditor(initial, takenNames)

    init {
        title = if (initial == null) "New Output Schema" else "Edit Output Schema"
        init()
        editor.onChanged = ::refreshOk
        refreshOk()
    }

    private fun refreshOk() {
        isOKActionEnabled = editor.problem() == null
    }

    override fun createCenterPanel(): JComponent = editor.component

    override fun getPreferredFocusedComponent(): JComponent = editor.name

    public override fun doValidate(): ValidationInfo? =
        editor.problem()?.let { ValidationInfo(it.message, it.field) }

    companion object {
        /** Shows the dialog and returns the schema it describes, or null when it was cancelled. */
        @ExcludeFromCoverage(MODAL_DIALOG)
        fun edit(parent: Component, initial: OutputSchema?, takenNames: List<String>): OutputSchema? {
            val dialog = OutputSchemaDialog(parent, initial, takenNames)
            return if (dialog.showAndGet()) dialog.editor.result() else null
        }
    }
}
