package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Dialog for variant-aware favorite model selection.
 * Shows base models in the left pane and their available variants in the right.
 * Users can toggle variants to include them in favorites.
 */
class VariantAwareFavoritesPickerDialog(
    private val availableModels: List<OpenRouterModelInfo>,
    private val currentFavorites: List<String>,
    private val onConfirm: (newFavorites: List<String>) -> Unit
) : DialogWrapper(true) {

    companion object {
        private const val DIALOG_WIDTH = 900
        private const val DIALOG_HEIGHT = 600
        private const val LIST_HEIGHT = 250
    }

    private val baseModelsModel = DefaultListModel<String>()
    private val baseModelsList = JBList(baseModelsModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        preferredSize = Dimension(0, LIST_HEIGHT)
    }

    private val variantCheckboxes = mutableMapOf<String, JBCheckBox>()
    private val selectedVariants = mutableSetOf<String>()

    /** Persistent container whose contents are swapped when the base selection changes. */
    private val variantContainer = JPanel(BorderLayout())

    init {
        title = "Select Favorite Models"
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT)
        init()
        loadModels()
    }

    override fun createCenterPanel(): JPanel {
        baseModelsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                updateVariantCheckboxes()
            }
        }

        return panel {
            row {
                label("Base Models").bold()
                label("Variants").bold()
            }
            row {
                cell(JBScrollPane(baseModelsList))
                    .align(Align.FILL)
                    .resizableColumn()

                cell(JBScrollPane(variantContainer))
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
        }
    }

    private fun createVariantPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
    }

    private fun loadModels() {
        val baseIds = VariantPickerLogic.baseModelIds(availableModels)

        baseModelsModel.clear()
        baseIds.forEach { baseModelsModel.addElement(it) }

        // Parse current favorites into selectedVariants
        selectedVariants.clear()
        selectedVariants.addAll(currentFavorites)
    }

    private fun updateVariantCheckboxes() {
        val selectedBase = baseModelsList.selectedValue
        variantContainer.removeAll()
        variantCheckboxes.clear()

        if (selectedBase == null) {
            variantContainer.revalidate()
            variantContainer.repaint()
            return
        }

        val variantsForBase = getVariantsForBase(selectedBase)
        val panel = createVariantPanel()

        variantsForBase.forEach { variant ->
            val modelId = VariantPickerLogic.toModelId(selectedBase, variant)
            val cb = JBCheckBox(
                if (variant == VariantPickerLogic.BASE_SENTINEL) "Base (no variant)" else "Variant: $variant",
                modelId in selectedVariants
            )
            cb.addActionListener {
                if (cb.isSelected) {
                    selectedVariants.add(modelId)
                } else {
                    selectedVariants.remove(modelId)
                }
            }
            variantCheckboxes[modelId] = cb
            panel.add(cb)
        }

        variantContainer.add(panel, BorderLayout.NORTH)
        variantContainer.revalidate()
        variantContainer.repaint()
    }

    private fun getVariantsForBase(baseId: String): List<String> =
        VariantPickerLogic.variantsForBase(availableModels, baseId)

    override fun doOKAction() {
        onConfirm(selectedVariants.toList())
        super.doOKAction()
    }
}
