package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

/**
 * Tests for Favorite Models table models and helpers
 */
@DisplayName("Favorite Models Table Models Tests")
class FavoriteModelsTableModelsTest {

    @Nested
    @DisplayName("Available Model Display")
    inner class AvailableModelDisplayTests {

        @Test
        fun `should extract provider from model ID`() {
            val model = createTestModel("openai/gpt-4")
            val display = AvailableModelDisplay.from(model)

            assertEquals("openai", display.provider, "Should extract provider correctly")
        }

        @Test
        fun `should handle model ID without provider`() {
            val model = createTestModel("gpt-4")
            val display = AvailableModelDisplay.from(model)

            assertEquals("—", display.provider, "Should show unknown for missing provider")
        }

        @Test
        fun `should format context window correctly`() {
            val model = createTestModel("openai/gpt-4", contextLength = 128000)
            val display = AvailableModelDisplay.from(model)

            assertEquals("128K", display.contextWindow, "Should format context window")
        }

        @Test
        fun `should handle missing context window`() {
            val model = createTestModel("openai/gpt-4", contextLength = null)
            val display = AvailableModelDisplay.from(model)

            assertEquals("—", display.contextWindow, "Should show unknown for missing context")
        }

        @Test
        fun `should detect vision capability`() {
            val model = createTestModel(
                "openai/gpt-4-vision",
                architecture = ModelArchitecture(
                    inputModalities = listOf("text", "image"),
                    outputModalities = null,
                    tokenizer = null,
                    instructType = null
                )
            )
            val display = AvailableModelDisplay.from(model)

            assertTrue(display.capabilities.contains("Vision"), "Should detect vision capability")
        }

        @Test
        fun `should detect tools capability`() {
            val model = createTestModel(
                "openai/gpt-4",
                supportedParameters = listOf("temperature", "tools", "max_tokens")
            )
            val display = AvailableModelDisplay.from(model)

            assertTrue(display.capabilities.contains("Tools"), "Should detect tools capability")
        }

        @Test
        fun `should show unknown for no capabilities`() {
            val model = createTestModel("openai/gpt-4")
            val display = AvailableModelDisplay.from(model)

            assertEquals("—", display.capabilities, "Should show unknown for no capabilities")
        }
    }

    @Nested
    @DisplayName("Favorite Model Display")
    inner class FavoriteModelDisplayTests {

        @Test
        fun `should mark model as available when in available list`() {
            val model = createTestModel("openai/gpt-4")
            val availableModels = listOf(model)

            val display = FavoriteModelDisplay.from(model, availableModels)

            assertTrue(display.isAvailable, "Model should be marked as available")
        }

        @Test
        fun `should mark model as unavailable when not in available list`() {
            val model = createTestModel("openai/gpt-4")
            val availableModels = listOf(createTestModel("anthropic/claude-3"))

            val display = FavoriteModelDisplay.from(model, availableModels)

            assertFalse(display.isAvailable, "Model should be marked as unavailable")
        }
    }

    @Nested
    @DisplayName("Table Helper Functions")
    inner class TableHelperTests {

        @Test
        fun `should convert models to available display items`() {
            val models = listOf(
                createTestModel("openai/gpt-4"),
                createTestModel("anthropic/claude-3")
            )

            val displays = FavoriteModelsTableHelper.toAvailableDisplayItems(models)

            assertEquals(2, displays.size, "Should convert all models")
            assertEquals("openai/gpt-4", displays[0].model.id, "First model should be gpt-4")
            assertEquals("anthropic/claude-3", displays[1].model.id, "Second model should be claude-3")
        }

        @Test
        fun `should filter models by search text`() {
            val models = listOf(
                AvailableModelDisplay.from(createTestModel("openai/gpt-4")),
                AvailableModelDisplay.from(createTestModel("anthropic/claude-3")),
                AvailableModelDisplay.from(createTestModel("google/gemini-pro"))
            )

            val filtered = FavoriteModelsTableHelper.filterModels(models, "openai")

            assertEquals(1, filtered.size, "Should filter to matching models")
            assertEquals("openai/gpt-4", filtered[0].model.id, "Should match openai model")
        }

        @Test
        fun `should return all models when search is blank`() {
            val models = listOf(
                AvailableModelDisplay.from(createTestModel("openai/gpt-4")),
                AvailableModelDisplay.from(createTestModel("anthropic/claude-3"))
            )

            val filtered = FavoriteModelsTableHelper.filterModels(models, "")

            assertEquals(2, filtered.size, "Should return all models for blank search")
        }

        @Test
        fun `should filter case-insensitively`() {
            val models = listOf(
                AvailableModelDisplay.from(createTestModel("openai/gpt-4")),
                AvailableModelDisplay.from(createTestModel("anthropic/claude-3"))
            )

            val filtered = FavoriteModelsTableHelper.filterModels(models, "OPENAI")

            assertEquals(1, filtered.size, "Should filter case-insensitively")
            assertEquals("openai/gpt-4", filtered[0].model.id, "Should match regardless of case")
        }

        @Test
        fun `should exclude favorites from available list`() {
            val available = listOf(
                AvailableModelDisplay.from(createTestModel("openai/gpt-4")),
                AvailableModelDisplay.from(createTestModel("anthropic/claude-3")),
                AvailableModelDisplay.from(createTestModel("google/gemini-pro"))
            )

            val favorites = listOf(
                FavoriteModelDisplay.from(createTestModel("openai/gpt-4"), emptyList())
            )

            val excluded = FavoriteModelsTableHelper.excludeFavorites(available, favorites)

            assertEquals(2, excluded.size, "Should exclude favorite from available")
            assertFalse(
                excluded.any { it.model.id == "openai/gpt-4" },
                "Should not contain favorited model"
            )
        }
    }

    @Nested
    @DisplayName("Column Definitions")
    inner class ColumnDefinitionsTests {

        @Test
        fun `should create available models table model`() {
            val tableModel = AvailableModelsColumns.createTableModel()

            assertEquals(4, tableModel.columnCount, "Should have 4 columns")
            assertEquals("Model ID", tableModel.getColumnName(0), "First column should be Model ID")
            assertEquals("Provider", tableModel.getColumnName(1), "Second column should be Provider")
            assertEquals("Context", tableModel.getColumnName(2), "Third column should be Context")
            assertEquals("Capabilities", tableModel.getColumnName(3), "Fourth column should be Capabilities")
        }

        @Test
        fun `should create favorite models table model`() {
            val tableModel = FavoriteModelsColumns.createTableModel()

            assertEquals(2, tableModel.columnCount, "Should have 2 columns")
            assertEquals("Model ID", tableModel.getColumnName(0), "First column should be Model ID")
            assertEquals("Status", tableModel.getColumnName(1), "Second column should be Status")
        }

        @Test
        fun `should get correct values from available model display`() {
            val model = createTestModel("openai/gpt-4", contextLength = 8192)
            val display = AvailableModelDisplay.from(model)

            assertEquals("openai/gpt-4", AvailableModelsColumns.MODEL_ID.valueOf(display))
            assertEquals("openai", AvailableModelsColumns.PROVIDER.valueOf(display))
            assertEquals("8K", AvailableModelsColumns.CONTEXT.valueOf(display))
        }

        @Test
        fun `should get correct status from favorite model display`() {
            val model = createTestModel("openai/gpt-4")
            val availableDisplay = FavoriteModelDisplay.from(model, listOf(model))
            val unavailableDisplay = FavoriteModelDisplay.from(model, emptyList())

            assertEquals("Available", FavoriteModelsColumns.STATUS.valueOf(availableDisplay))
            assertEquals("Unavailable", FavoriteModelsColumns.STATUS.valueOf(unavailableDisplay))
        }
    }

    private fun createTestModel(
        id: String,
        contextLength: Int? = null,
        architecture: ModelArchitecture? = null,
        supportedParameters: List<String>? = null
    ): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = id,
            name = id,
            created = System.currentTimeMillis() / 1000,
            description = "Test model",
            architecture = architecture,
            topProvider = null,
            pricing = null,
            contextLength = contextLength,
            perRequestLimits = null,
            supportedParameters = supportedParameters
        )
    }
}
