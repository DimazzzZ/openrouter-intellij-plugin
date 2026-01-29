package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for ModelSuggestions utility object
 */
@DisplayName("ModelSuggestions Tests")
class ModelSuggestionsTest {

    // ========== Model List Tests ==========

    @Test
    @DisplayName("Should contain expected vision models")
    fun testVisionModels() {
        val expectedModels = listOf(
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "anthropic/claude-4.5-sonnet",
            "google/gemini-2.5-pro"
        )

        assertEquals(expectedModels.size, ModelSuggestions.VISION_MODELS.size)
        expectedModels.forEach { model ->
            assertTrue(ModelSuggestions.VISION_MODELS.contains(model), "VISION_MODELS should contain: $model")
        }
    }

    @Test
    @DisplayName("Should contain expected audio models")
    fun testAudioModels() {
        val expectedModels = listOf(
            "openai/gpt-4o-audio-preview",
            "google/gemini-2.0-flash-exp",
            "google/gemini-2.5-pro"
        )

        assertEquals(expectedModels.size, ModelSuggestions.AUDIO_MODELS.size)
        expectedModels.forEach { model ->
            assertTrue(ModelSuggestions.AUDIO_MODELS.contains(model), "AUDIO_MODELS should contain: $model")
        }
    }

    @Test
    @DisplayName("Should contain expected video models")
    fun testVideoModels() {
        val expectedModels = listOf(
            "google/gemini-2.0-flash-exp",
            "google/gemini-2.5-pro",
            "openai/gpt-4o (for video frames)"
        )

        assertEquals(expectedModels.size, ModelSuggestions.VIDEO_MODELS.size)
        expectedModels.forEach { model ->
            assertTrue(ModelSuggestions.VIDEO_MODELS.contains(model), "VIDEO_MODELS should contain: $model")
        }
    }

    @Test
    @DisplayName("Should contain expected file models")
    fun testFileModels() {
        val expectedModels = listOf(
            "google/gemini-2.5-pro",
            "anthropic/claude-4.5-sonnet",
            "openai/gpt-4o"
        )

        assertEquals(expectedModels.size, ModelSuggestions.FILE_MODELS.size)
        expectedModels.forEach { model ->
            assertTrue(ModelSuggestions.FILE_MODELS.contains(model), "FILE_MODELS should contain: $model")
        }
    }

    @Test
    @DisplayName("Should contain expected general models")
    fun testGeneralModels() {
        val expectedModels = listOf(
            "openai/gpt-4o-mini (fast, affordable)",
            "anthropic/claude-3.5-sonnet (high quality)",
            "google/gemini-pro-1.5 (large context)"
        )

        assertEquals(expectedModels.size, ModelSuggestions.GENERAL_MODELS.size)
        expectedModels.forEach { model ->
            assertTrue(ModelSuggestions.GENERAL_MODELS.contains(model), "GENERAL_MODELS should contain: $model")
        }
    }

    @Test
    @DisplayName("Model lists should not be empty")
    fun testModelListsNotEmpty() {
        assertTrue(ModelSuggestions.VISION_MODELS.isNotEmpty(), "VISION_MODELS should not be empty")
        assertTrue(ModelSuggestions.AUDIO_MODELS.isNotEmpty(), "AUDIO_MODELS should not be empty")
        assertTrue(ModelSuggestions.VIDEO_MODELS.isNotEmpty(), "VIDEO_MODELS should not be empty")
        assertTrue(ModelSuggestions.FILE_MODELS.isNotEmpty(), "FILE_MODELS should not be empty")
        assertTrue(ModelSuggestions.GENERAL_MODELS.isNotEmpty(), "GENERAL_MODELS should not be empty")
    }

    // ========== formatModelList Tests ==========

    @Test
    @DisplayName("Should format empty list as empty string")
    fun testFormatModelListWithEmptyList() {
        val result = ModelSuggestions.formatModelList(emptyList())
        assertEquals("", result)
    }

    @Test
    @DisplayName("Should format single model correctly")
    fun testFormatModelListWithSingleModel() {
        val models = listOf("openai/gpt-4o")
        val result = ModelSuggestions.formatModelList(models)
        assertEquals("- openai/gpt-4o", result)
    }

    @Test
    @DisplayName("Should format multiple models with line breaks")
    fun testFormatModelListWithMultipleModels() {
        val models = listOf(
            "openai/gpt-4o",
            "anthropic/claude-3.5-sonnet",
            "google/gemini-pro"
        )
        val result = ModelSuggestions.formatModelList(models)
        val expected = """- openai/gpt-4o
- anthropic/claude-3.5-sonnet
- google/gemini-pro"""
        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should format model names with descriptions")
    fun testFormatModelListWithDescriptions() {
        val models = listOf(
            "openai/gpt-4o-mini (fast, affordable)",
            "anthropic/claude-3.5-sonnet (high quality)"
        )
        val result = ModelSuggestions.formatModelList(models)
        val expected = """- openai/gpt-4o-mini (fast, affordable)
- anthropic/claude-3.5-sonnet (high quality)"""
        assertEquals(expected, result)
    }

    // ========== createSuggestionSection Tests ==========

    @Test
    @DisplayName("Should create suggestion section with header and models")
    fun testCreateSuggestionSection() {
        val header = "Recommended Vision Models:"
        val models = listOf("openai/gpt-4o", "google/gemini-2.5-pro")

        val result = ModelSuggestions.createSuggestionSection(header, models)

        val expected = """Recommended Vision Models:
- openai/gpt-4o
- google/gemini-2.5-pro"""

        assertEquals(expected, result)
    }

    @Test
    @DisplayName("Should create suggestion section with empty model list")
    fun testCreateSuggestionSectionWithEmptyList() {
        val header = "No models available:"
        val models = emptyList<String>()

        val result = ModelSuggestions.createSuggestionSection(header, models)

        assertEquals("No models available:\n", result)
    }

    @Test
    @DisplayName("Should create suggestion section with single model")
    fun testCreateSuggestionSectionWithSingleModel() {
        val header = "Best Option:"
        val models = listOf("openai/gpt-4o")

        val result = ModelSuggestions.createSuggestionSection(header, models)

        assertEquals("Best Option:\n- openai/gpt-4o", result)
    }

    @Test
    @DisplayName("Should handle multiline headers")
    fun testCreateSuggestionSectionWithMultilineHeader() {
        val header = "Available Models:\n(Select one)"
        val models = listOf("model-1", "model-2")

        val result = ModelSuggestions.createSuggestionSection(header, models)

        val expected = """Available Models:
(Select one)
- model-1
- model-2"""

        assertEquals(expected, result)
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should create complete vision model suggestion")
    fun testVisionModelSuggestion() {
        val header = "Try these vision-capable models instead:"
        val result = ModelSuggestions.createSuggestionSection(header, ModelSuggestions.VISION_MODELS)

        assertTrue(result.contains(header))
        assertTrue(result.contains("openai/gpt-4o"))
        assertTrue(result.contains("google/gemini-2.5-pro"))
        assertTrue(result.startsWith(header))
    }

    @Test
    @DisplayName("Should create complete audio model suggestion")
    fun testAudioModelSuggestion() {
        val header = "These models support audio input:"
        val result = ModelSuggestions.createSuggestionSection(header, ModelSuggestions.AUDIO_MODELS)

        assertTrue(result.contains(header))
        assertTrue(result.contains("openai/gpt-4o-audio-preview"))
        assertTrue(result.startsWith(header))
    }

    @Test
    @DisplayName("Vision and file models should have overlap for common multimodal models")
    fun testVisionAndFileModelsOverlap() {
        // Gemini models support both vision and files
        val commonModels = ModelSuggestions.VISION_MODELS.intersect(ModelSuggestions.FILE_MODELS.toSet())
        assertTrue(commonModels.isNotEmpty(), "There should be models supporting both vision and files")
        assertTrue(commonModels.contains("google/gemini-2.5-pro"), "Gemini should support both")
    }
}
