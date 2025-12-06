package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModelPresetsTest {

    @Test
    fun `POPULAR_MODELS should contain expected models`() {
        assertTrue(ModelPresets.POPULAR_MODELS.contains("openai/gpt-4o"))
        assertTrue(ModelPresets.POPULAR_MODELS.contains("anthropic/claude-3.5-sonnet"))
        assertTrue(ModelPresets.POPULAR_MODELS.contains("google/gemini-pro-1.5"))
        assertTrue(ModelPresets.POPULAR_MODELS.isNotEmpty())
    }

    @Test
    fun `OPENAI_MODELS should only contain OpenAI models`() {
        assertTrue(ModelPresets.OPENAI_MODELS.all { it.startsWith("openai/") })
        assertTrue(ModelPresets.OPENAI_MODELS.contains("openai/gpt-4o"))
        assertTrue(ModelPresets.OPENAI_MODELS.contains("openai/gpt-4o-mini"))
    }

    @Test
    fun `ANTHROPIC_MODELS should only contain Anthropic models`() {
        assertTrue(ModelPresets.ANTHROPIC_MODELS.all { it.startsWith("anthropic/") })
        assertTrue(ModelPresets.ANTHROPIC_MODELS.contains("anthropic/claude-3.5-sonnet"))
        assertTrue(ModelPresets.ANTHROPIC_MODELS.contains("anthropic/claude-3-haiku"))
    }

    @Test
    fun `GOOGLE_MODELS should only contain Google models`() {
        assertTrue(ModelPresets.GOOGLE_MODELS.all { it.startsWith("google/") })
        assertTrue(ModelPresets.GOOGLE_MODELS.contains("google/gemini-pro-1.5"))
        assertTrue(ModelPresets.GOOGLE_MODELS.contains("google/gemini-flash-1.5"))
    }

    @Test
    fun `META_MODELS should only contain Meta models`() {
        assertTrue(ModelPresets.META_MODELS.all { it.startsWith("meta-llama/") })
        assertTrue(ModelPresets.META_MODELS.contains("meta-llama/llama-3.1-70b-instruct"))
    }

    @Test
    fun `COST_EFFECTIVE_MODELS should contain budget-friendly models`() {
        assertTrue(ModelPresets.COST_EFFECTIVE_MODELS.contains("openai/gpt-4o-mini"))
        assertTrue(ModelPresets.COST_EFFECTIVE_MODELS.contains("anthropic/claude-3-haiku"))
        assertTrue(ModelPresets.COST_EFFECTIVE_MODELS.contains("google/gemini-flash-1.5"))
    }

    @Test
    fun `MULTIMODAL_MODELS should contain models with vision capabilities`() {
        assertTrue(ModelPresets.MULTIMODAL_MODELS.contains("openai/gpt-4o"))
        assertTrue(ModelPresets.MULTIMODAL_MODELS.contains("anthropic/claude-3.5-sonnet"))
        assertTrue(ModelPresets.MULTIMODAL_MODELS.contains("google/gemini-pro-1.5"))
    }

    @Test
    fun `CODING_MODELS should contain coding-focused models`() {
        assertTrue(ModelPresets.CODING_MODELS.contains("openai/gpt-4o"))
        assertTrue(ModelPresets.CODING_MODELS.contains("anthropic/claude-3.5-sonnet"))
    }

    @Test
    fun `ALL_PRESETS should contain all preset definitions`() {
        assertEquals(8, ModelPresets.ALL_PRESETS.size)

        val presetNames = ModelPresets.ALL_PRESETS.map { it.name }
        assertTrue(presetNames.contains("Popular Models"))
        assertTrue(presetNames.contains("OpenAI"))
        assertTrue(presetNames.contains("Anthropic"))
        assertTrue(presetNames.contains("Google"))
        assertTrue(presetNames.contains("Meta"))
        assertTrue(presetNames.contains("Cost-Effective"))
        assertTrue(presetNames.contains("Multimodal"))
        assertTrue(presetNames.contains("Coding"))
    }

    @Test
    fun `getPreset should return correct preset by name`() {
        val preset = ModelPresets.getPreset("Popular Models")
        assertNotNull(preset)
        assertEquals("Popular Models", preset?.name)
        assertEquals(ModelPresets.POPULAR_MODELS, preset?.modelIds)
    }

    @Test
    fun `getPreset should return null for unknown preset`() {
        val preset = ModelPresets.getPreset("Unknown Preset")
        assertNull(preset)
    }

    @Test
    fun `getModelIds should return model IDs for valid preset`() {
        val modelIds = ModelPresets.getModelIds("OpenAI")
        assertEquals(ModelPresets.OPENAI_MODELS, modelIds)
    }

    @Test
    fun `getModelIds should return empty list for unknown preset`() {
        val modelIds = ModelPresets.getModelIds("Unknown Preset")
        assertTrue(modelIds.isEmpty())
    }

    @Test
    fun `all presets should have descriptions`() {
        ModelPresets.ALL_PRESETS.forEach { preset ->
            assertNotNull(preset.description)
            assertTrue(preset.description.isNotBlank())
        }
    }

    @Test
    fun `all presets should have non-empty model lists`() {
        ModelPresets.ALL_PRESETS.forEach { preset ->
            assertTrue(preset.modelIds.isNotEmpty(), "Preset ${preset.name} has empty model list")
        }
    }

    @Test
    fun `preset model IDs should not contain duplicates`() {
        ModelPresets.ALL_PRESETS.forEach { preset ->
            val uniqueIds = preset.modelIds.distinct()
            assertEquals(
                uniqueIds.size,
                preset.modelIds.size,
                "Preset ${preset.name} contains duplicate model IDs"
            )
        }
    }
}
