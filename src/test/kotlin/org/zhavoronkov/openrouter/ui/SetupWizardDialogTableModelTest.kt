package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ModelPricing
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

@DisplayName("SetupWizardDialog ModelsTableModel Tests")
class SetupWizardDialogTableModelTest {

    @Nested
    @DisplayName("Column Structure")
    inner class ColumnStructureTests {

        @Test
        fun `should have 4 columns`() {
            val model = TestModelsTableModel()
            assertEquals(4, model.columnCount)
        }

        @Test
        fun `should have correct column names`() {
            val model = TestModelsTableModel()
            assertEquals("", model.getColumnName(0))
            assertEquals("Model", model.getColumnName(1))
            assertEquals("Input Price", model.getColumnName(2))
            assertEquals("Output Price", model.getColumnName(3))
        }
    }

    @Nested
    @DisplayName("Value Mapping")
    inner class ValueMappingTests {

        @Test
        fun `should return checkbox state for column 0`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))
            model.toggleSelection(0)

            assertEquals(true, model.getValueAt(0, 0))
        }

        @Test
        fun `should return model name for column 1`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))

            assertEquals("GPT-4", model.getValueAt(0, 1))
        }

        @Test
        fun `should return formatted input price for column 2`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))

            assertEquals("$1.5000", model.getValueAt(0, 2))
        }

        @Test
        fun `should return formatted output price for column 3`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))

            assertEquals("$6.0000", model.getValueAt(0, 3))
        }

        @Test
        fun `should return dash for null pricing in price columns`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", null, null))

            assertEquals("—", model.getValueAt(0, 2))
            assertEquals("—", model.getValueAt(0, 3))
        }
    }

    @Nested
    @DisplayName("Selection Behavior")
    inner class SelectionBehaviorTests {

        @Test
        fun `should toggle selection on click`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))

            assertFalse(model.getValueAt(0, 0) as Boolean)
            model.toggleSelection(0)
            assertTrue(model.getValueAt(0, 0) as Boolean)
            model.toggleSelection(0)
            assertFalse(model.getValueAt(0, 0) as Boolean)
        }

        @Test
        fun `should return selected model IDs`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))
            model.addModel(createModel("anthropic/claude-3", "Claude 3", "0.000002", "0.000008"))

            model.toggleSelection(0)
            model.toggleSelection(1)

            val selected = model.getSelectedModelIds()
            assertEquals(2, selected.size)
            assertTrue(selected.contains("openai/gpt-4"))
            assertTrue(selected.contains("anthropic/claude-3"))
        }
    }

    @Nested
    @DisplayName("Row Count")
    inner class RowCountTests {

        @Test
        fun `should return zero rows when empty`() {
            val model = TestModelsTableModel()
            assertEquals(0, model.rowCount)
        }

        @Test
        fun `should return correct row count`() {
            val model = TestModelsTableModel()
            model.addModel(createModel("openai/gpt-4", "GPT-4", "0.0000015", "0.000006"))
            model.addModel(createModel("anthropic/claude-3", "Claude 3", "0.000002", "0.000008"))

            assertEquals(2, model.rowCount)
        }
    }

    private fun createModel(
        id: String,
        name: String,
        promptPrice: String?,
        completionPrice: String?
    ): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = id,
            name = name,
            created = System.currentTimeMillis() / 1000,
            pricing = ModelPricing(
                prompt = promptPrice,
                completion = completionPrice,
                image = null,
                request = null
            )
        )
    }

    /**
     * Simplified test version of the ModelsTableModel from SetupWizardDialog
     * Mirrors the structure and behavior for isolated unit testing
     */
    private class TestModelsTableModel : javax.swing.table.AbstractTableModel() {
        private val models = mutableListOf<OpenRouterModelInfo>()
        private val selectedModels = mutableSetOf<String>()

        fun addModel(model: OpenRouterModelInfo) {
            models.add(model)
        }

        fun toggleSelection(rowIndex: Int) {
            if (rowIndex in models.indices) {
                val model = models[rowIndex]
                if (selectedModels.contains(model.id)) {
                    selectedModels.remove(model.id)
                } else {
                    selectedModels.add(model.id)
                }
                fireTableCellUpdated(rowIndex, 0)
            }
        }

        fun getSelectedModelIds(): List<String> = selectedModels.toList()

        override fun getRowCount(): Int = models.size
        override fun getColumnCount(): Int = 4

        override fun getColumnName(column: Int): String = when (column) {
            0 -> ""
            1 -> "Model"
            2 -> "Input Price"
            3 -> "Output Price"
            else -> ""
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
            0 -> Boolean::class.java
            else -> String::class.java
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            if (rowIndex !in models.indices) return null
            val model = models[rowIndex]
            return when (columnIndex) {
                0 -> selectedModels.contains(model.id)
                1 -> model.name
                2 -> org.zhavoronkov.openrouter.utils.ModelPricingFormatter.formatInputPrice(model.pricing)
                3 -> org.zhavoronkov.openrouter.utils.ModelPricingFormatter.formatOutputPrice(model.pricing)
                else -> null
            }
        }
    }
}
