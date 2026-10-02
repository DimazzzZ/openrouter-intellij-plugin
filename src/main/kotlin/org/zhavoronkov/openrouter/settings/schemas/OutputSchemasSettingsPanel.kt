package org.zhavoronkov.openrouter.settings.schemas

import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.OutputSchemasManager
import org.zhavoronkov.openrouter.settings.SettingsPage
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Settings page for Output Schemas: a table of the saved schemas with add, edit and delete, each
 * edited in [OutputSchemaDialog].
 *
 * Edits are staged in the table and stored only on apply, like every other settings page, so
 * Cancel on the Settings dialog discards them.
 *
 * [openEditor] shows the editor and returns what it produced, or null when it was cancelled. It is
 * a parameter so a test can stand in for the modal dialog, which cannot be shown in one; its
 * arguments are the schema being edited (null for a new one) and the names already taken by the
 * other schemas.
 */
class OutputSchemasSettingsPanel(
    private val manager: OutputSchemasManager = OpenRouterSettingsService.getInstance().outputSchemasManager,
    private val openEditor: (Component, OutputSchema?, List<String>) -> OutputSchema? = OutputSchemaDialog::edit
) : SettingsPage {

    internal val model = ListTableModel<OutputSchema>(NAME_COLUMN, STRICT_COLUMN, FIELDS_COLUMN)
    internal val table = TableView(model).apply {
        setShowGrid(false)
        emptyText.text = "No schemas yet. Add one to ask for replies in its shape."
    }

    override fun createPanel(): JPanel {
        reset()
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                editSelected()
                return true
            }
        }.installOn(table)
        val decorated = ToolbarDecorator.createDecorator(table)
            .setAddAction { add() }
            .setEditAction { editSelected() }
            .setRemoveAction { removeSelected() }
            .disableUpDownActions()
            .createPanel()
        return panel {
            row {
                comment(
                    "Named JSON Schemas a reply can be asked to follow, from the chat or by a Consumer " +
                        "naming one in a json_schema response format. A schema's name is what OpenRouter " +
                        "receives, so name it for what it produces."
                )
            }.topGap(TopGap.MEDIUM)
            row { cell(decorated).align(Align.FILL) }.resizableRow()
        }
    }

    internal fun add() {
        val created = openEditor(table, null, names()) ?: return
        model.addRow(created)
        table.selection = listOf(created)
    }

    internal fun editSelected() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val current = model.getItem(row)
        val edited = openEditor(table, current, names().filterIndexed { index, _ -> index != row }) ?: return
        model.removeRow(row)
        model.insertRow(row, edited)
        table.selection = listOf(edited)
    }

    internal fun removeSelected() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        model.removeRow(row)
    }

    private fun names(): List<String> = model.items.map { it.name }

    override fun isModified(): Boolean = model.items != manager.all()

    override fun apply() = manager.replaceAll(model.items)

    override fun reset() {
        model.items = manager.all()
    }

    private companion object {
        // Unreachable code: the three getters below are never called - the class reads these
        // fields of its own private companion directly - so each declaration line keeps one
        // instruction no test can run
        val NAME_COLUMN = object : ColumnInfo<OutputSchema, String>("Name") {
            override fun valueOf(item: OutputSchema): String = item.name
        }

        // Unreachable code: see NAME_COLUMN
        val STRICT_COLUMN = object : ColumnInfo<OutputSchema, String>("Strict") {
            override fun valueOf(item: OutputSchema): String = if (item.strict) "Yes" else "No"
        }

        // Unreachable code: see NAME_COLUMN
        val FIELDS_COLUMN = object : ColumnInfo<OutputSchema, String>("Fields") {
            override fun valueOf(item: OutputSchema): String =
                when (val check = OutputSchema.validateBody(item.schema)) {
                    is OutputSchema.BodyCheck.Valid -> check.fields.toString()
                    is OutputSchema.BodyCheck.Invalid -> "invalid"
                }
        }
    }
}
