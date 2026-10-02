package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/**
 * The fields of one set of OpenRouter provider routing preferences - provider order, fallback
 * behaviour, quantizations and provider filters - as one form, so the Provider Routing page and a
 * preset's routing are edited the same way.
 *
 * [show] fills the form from preferences and [value] reads it back. A field left at OpenRouter's
 * own default is read as not set, so [value] only carries what the user chose.
 */
class ProviderRoutingForm(private val choose: (String, String, List<String>) -> String? = ::chooseFromList) {

    internal val order = DefaultListModel<String>()
    internal val only = DefaultListModel<String>()
    internal val ignore = DefaultListModel<String>()
    internal val allowFallbacks = JBCheckBox("Allow fallbacks", true)
    internal val requireParameters = JBCheckBox("Require parameters")
    internal val sort = ComboBox(DefaultComboBoxModel(SORT_OPTIONS.toTypedArray()))
    internal val dataCollection = ComboBox(DefaultComboBoxModel(DATA_COLLECTION_OPTIONS.toTypedArray()))
    internal val quantizations = QUANTIZATIONS.associateWith { JBCheckBox(it) }

    private val orderList = JBList(order).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredSize = Dimension(0, LIST_PREFERRED_HEIGHT)
    }
    private val onlyList = JBList(only)
    private val ignoreList = JBList(ignore)

    /** Adds every group of the form to [panel]: [addPreferenceGroups], then [addFilterGroups]. */
    fun addTo(panel: Panel) {
        addPreferenceGroups(panel)
        addFilterGroups(panel)
    }

    /**
     * Adds provider order, fallback behaviour and quantizations to [panel]. Split from
     * [addFilterGroups] so the Provider Routing page can keep its own groups between the two.
     */
    fun addPreferenceGroups(panel: Panel) = with(panel) {
        group("Provider Order") {
            row { comment("Providers are tried in this order. Leave empty to use OpenRouter's default.") }
            row {
                val decorated = ToolbarDecorator.createDecorator(orderList)
                    .setAddAction {
                        addProvider(order, "Select a provider to add:", "Add Provider", warnWhenFull = true)
                    }
                    .setRemoveAction { removeSelected(orderList) }
                    .setMoveUpAction { move(orderList, -1) }
                    .setMoveDownAction { move(orderList, +1) }
                    .createPanel()
                cell(decorated).align(Align.FILL)
            }.resizableRow()
        }
        group("Fallback Behavior") {
            row { cell(allowFallbacks) }
            row {
                label("Sort by:")
                cell(sort)
            }
            row { cell(requireParameters) }
            row {
                label("Data collection:")
                cell(dataCollection)
            }
        }
        group("Quantizations") {
            row { comment("Restrict to models with these quantization formats (leave unchecked for any):") }
            QUANTIZATIONS.chunked(QUANTIZATIONS_PER_ROW).forEach { chunk ->
                row { chunk.forEach { cell(quantizations.getValue(it)) } }
            }
        }
    }

    /** Adds the only and ignore provider filters to [panel]. */
    fun addFilterGroups(panel: Panel) = with(panel) {
        group("Provider Filters (Advanced)") {
            row {
                comment(
                    "Restrict routing to specific providers (only) or exclude providers (ignore). " +
                        "Leave both empty for no filtering."
                )
            }
            row { label("Only these providers:") }
            row {
                val decorated = ToolbarDecorator.createDecorator(onlyList)
                    .setAddAction { addProvider(only, "Select a provider to restrict to:", "Add 'Only' Provider") }
                    .setRemoveAction { removeSelected(onlyList) }
                    .createPanel()
                cell(decorated).align(Align.FILL)
            }.resizableRow()
            row { label("Ignore these providers:") }
            row {
                val decorated = ToolbarDecorator.createDecorator(ignoreList)
                    .setAddAction { addProvider(ignore, "Select a provider to exclude:", "Add 'Ignore' Provider") }
                    .setRemoveAction { removeSelected(ignoreList) }
                    .createPanel()
                cell(decorated).align(Align.FILL)
            }.resizableRow()
        }
    }

    /** Fills the form from [preferences]; null, or a field left null, shows OpenRouter's default. */
    fun show(preferences: ProviderRoutingPreferences?) {
        fill(order, preferences?.order)
        fill(only, preferences?.only)
        fill(ignore, preferences?.ignore)
        allowFallbacks.isSelected = preferences?.allowFallbacks != false
        requireParameters.isSelected = preferences?.requireParameters == true
        sort.selectedItem = preferences?.sort.orEmpty()
        dataCollection.selectedItem = preferences?.dataCollection.orEmpty()
        val chosen = preferences?.quantizations.orEmpty().toSet()
        quantizations.forEach { (name, box) -> box.isSelected = name in chosen }
    }

    /** What the form says, with every field still at OpenRouter's default left out. */
    fun value(): ProviderRoutingPreferences = ProviderRoutingPreferences(
        order = items(order),
        allowFallbacks = false.takeUnless { allowFallbacks.isSelected },
        sort = (sort.selectedItem as? String)?.ifBlank { null },
        requireParameters = true.takeIf { requireParameters.isSelected },
        dataCollection = (dataCollection.selectedItem as? String)?.ifBlank { null },
        quantizations = QUANTIZATIONS.filter { quantizations.getValue(it).isSelected }.ifEmpty { null },
        only = items(only),
        ignore = items(ignore)
    )

    /** Offers the providers [model] does not list yet; only the order list says so when there are none. */
    private fun addProvider(
        model: DefaultListModel<String>,
        message: String,
        title: String,
        warnWhenFull: Boolean = false
    ) {
        val used = model.elements().toList().toSet()
        val available = ModelProviderUtils.KNOWN_PROVIDERS.values.filter { it !in used }
        if (available.isEmpty()) {
            if (warnWhenFull) Messages.showWarningDialog("All providers are already in the list.", "No More Providers")
            return
        }
        choose(message, title, available)?.let(model::addElement)
    }

    companion object {
        /** How tall a reorderable list of this form - or of the page around it - starts. */
        internal const val LIST_PREFERRED_HEIGHT = 150
        private const val QUANTIZATIONS_PER_ROW = 3
        private val SORT_OPTIONS = listOf("", "price", "throughput", "latency")
        private val DATA_COLLECTION_OPTIONS = listOf("", "allow", "deny")
        private val QUANTIZATIONS = listOf("int4", "int8", "fp8", "fp16", "bf16", "fp32")

        private fun items(model: DefaultListModel<String>): List<String>? = model.elements().toList().ifEmpty { null }

        private fun fill(model: DefaultListModel<String>, items: List<String>?) {
            model.clear()
            items.orEmpty().forEach(model::addElement)
        }

        /** Removes [list]'s selected entry, if any. */
        internal fun removeSelected(list: JBList<String>) {
            val index = list.selectedIndex
            if (index >= 0) (list.model as DefaultListModel<String>).removeElementAt(index)
        }

        /** Moves [list]'s selected entry [by] places, keeping it selected, when there is room. */
        internal fun move(list: JBList<String>, by: Int) {
            val model = list.model as DefaultListModel<String>
            val index = list.selectedIndex
            val target = index + by
            if (index < 0 || target !in 0 until model.size()) return
            val item = model.getElementAt(index)
            model.removeElementAt(index)
            model.insertElementAt(item, target)
            list.selectedIndex = target
        }

        /**
         * Modal single-choice picker used by the "add provider" actions.
         *
         * Replaces the deprecated Messages.showChooseDialog overload, which has no drop-in
         * replacement reachable from the 2025.3 compile target (the HtmlChunk-based and
         * dialog-builder variants only landed in 2026.x). Returns the selected item, or
         * null on cancel.
         */
        fun chooseFromList(message: String, title: String, options: List<String>): String? {
            if (options.isEmpty()) return null
            val dialog = ProviderChooserDialog(message, title, options)
            return if (dialog.showAndGet()) dialog.selected else null
        }
    }
}

/**
 * Small modal picker used to replace the deprecated Messages.showChooseDialog.
 *
 * A plain ComboBox inside a DialogWrapper; the initial selection defaults to the
 * first entry, matching the previous initialValue = available[0] behaviour.
 */
private class ProviderChooserDialog(
    private val message: String,
    dialogTitle: String,
    options: List<String>
) : DialogWrapper(null, true) {

    private val combo = ComboBox(DefaultComboBoxModel(options.toTypedArray())).apply {
        if (options.isNotEmpty()) selectedIndex = 0
    }

    init {
        title = dialogTitle
        init()
    }

    val selected: String? get() = combo.selectedItem as? String

    override fun createCenterPanel(): JComponent = panel {
        row { label(message) }
        row { cell(combo).align(Align.FILL) }
    }

    override fun getPreferredFocusedComponent(): JComponent = combo
}
