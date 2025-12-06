package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

class ModelProviderUtilsTest {

    @Test
    fun `extractProvider should extract OpenAI provider`() {
        val provider = ModelProviderUtils.extractProvider("openai/gpt-4o")
        assertEquals("OpenAI", provider)
    }

    @Test
    fun `extractProvider should extract Anthropic provider`() {
        val provider = ModelProviderUtils.extractProvider("anthropic/claude-3.5-sonnet")
        assertEquals("Anthropic", provider)
    }

    @Test
    fun `extractProvider should extract Google provider`() {
        val provider = ModelProviderUtils.extractProvider("google/gemini-pro-1.5")
        assertEquals("Google", provider)
    }

    @Test
    fun `extractProvider should extract Meta provider from meta-llama`() {
        val provider = ModelProviderUtils.extractProvider("meta-llama/llama-3.1-70b-instruct")
        assertEquals("Meta", provider)
    }

    @Test
    fun `extractProvider should extract Mistral provider from mistralai`() {
        val provider = ModelProviderUtils.extractProvider("mistralai/mistral-large")
        assertEquals("Mistral", provider)
    }

    @Test
    fun `extractProvider should return Other for unknown provider`() {
        val provider = ModelProviderUtils.extractProvider("unknown/model")
        assertEquals("Unknown", provider)
    }

    @Test
    fun `extractProvider should return Other for model without separator`() {
        val provider = ModelProviderUtils.extractProvider("invalid-model-id")
        assertEquals("Other", provider)
    }

    @Test
    fun `getUniqueProviders should return sorted unique providers`() {
        val models = listOf(
            createModel("openai/gpt-4o"),
            createModel("openai/gpt-4o-mini"),
            createModel("anthropic/claude-3.5-sonnet"),
            createModel("google/gemini-pro-1.5")
        )

        val providers = ModelProviderUtils.getUniqueProviders(models)
        assertEquals(listOf("Anthropic", "Google", "OpenAI"), providers)
    }

    @Test
    fun `hasCapability should return true for models with vision`() {
        val model = createModel(
            "openai/gpt-4o",
            architecture = ModelArchitecture(inputModalities = listOf("text", "image"))
        )

        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.VISION))
    }

    @Test
    fun `hasCapability should return false for models without vision`() {
        val model = createModel(
            "openai/gpt-4o",
            architecture = ModelArchitecture(inputModalities = listOf("text"))
        )

        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.VISION))
    }

    @Test
    fun `hasCapability should return true for models with audio`() {
        val model = createModel(
            "openai/gpt-4o",
            architecture = ModelArchitecture(inputModalities = listOf("text", "audio"))
        )

        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.AUDIO))
    }

    @Test
    fun `hasCapability should return true for models with tools`() {
        val model = createModel(
            "openai/gpt-4o",
            supportedParameters = listOf("temperature", "tools", "max_tokens")
        )

        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.TOOLS))
    }

    @Test
    fun `hasCapability should return true for models with functions`() {
        val model = createModel(
            "openai/gpt-3.5-turbo",
            supportedParameters = listOf("temperature", "functions", "max_tokens")
        )

        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.TOOLS))
    }

    @Test
    fun `hasCapability should return true for models with image generation`() {
        val model = createModel(
            "dall-e/3",
            architecture = ModelArchitecture(outputModalities = listOf("image"))
        )

        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.IMAGE_GENERATION))
    }

    @Test
    fun `getCapabilities should return all capabilities`() {
        val model = createModel(
            "openai/gpt-4o",
            architecture = ModelArchitecture(
                inputModalities = listOf("text", "image", "audio"),
                outputModalities = listOf("text", "image")
            ),
            supportedParameters = listOf("tools")
        )

        val capabilities = ModelProviderUtils.getCapabilities(model)
        assertTrue(capabilities.contains("Vision"))
        assertTrue(capabilities.contains("Audio"))
        assertTrue(capabilities.contains("Tools"))
        assertTrue(capabilities.contains("Image Gen"))
    }

    @Test
    fun `matchesContextRange should match SMALL range`() {
        val model = createModel("openai/gpt-3.5-turbo", contextLength = 16000)
        assertTrue(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.SMALL))
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.MEDIUM))
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.LARGE))
    }

    @Test
    fun `matchesContextRange should match MEDIUM range`() {
        val model = createModel("openai/gpt-4o", contextLength = 128000)
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.SMALL))
        assertTrue(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.MEDIUM))
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.LARGE))
    }

    @Test
    fun `matchesContextRange should match LARGE range`() {
        val model = createModel("google/gemini-pro-1.5", contextLength = 1000000)
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.SMALL))
        assertFalse(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.MEDIUM))
        assertTrue(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.LARGE))
    }

    @Test
    fun `matchesContextRange should match ANY range for all models`() {
        val model = createModel("openai/gpt-4o", contextLength = 128000)
        assertTrue(ModelProviderUtils.matchesContextRange(model, ModelProviderUtils.ContextRange.ANY))
    }

    @Test
    fun `formatContextLength should format large numbers`() {
        assertEquals("1M", ModelProviderUtils.formatContextLength(1000000))
        assertEquals("128K", ModelProviderUtils.formatContextLength(128000))
        assertEquals("32K", ModelProviderUtils.formatContextLength(32000))
        assertEquals("500", ModelProviderUtils.formatContextLength(500))
        assertEquals("—", ModelProviderUtils.formatContextLength(null))
    }

    @Test
    fun `filterByProvider should filter models by provider`() {
        val models = listOf(
            createModel("openai/gpt-4o"),
            createModel("openai/gpt-4o-mini"),
            createModel("anthropic/claude-3.5-sonnet")
        )

        val filtered = ModelProviderUtils.filterByProvider(models, "OpenAI")
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.id.startsWith("openai/") })
    }

    @Test
    fun `filterByProvider should return all models for All Providers`() {
        val models = listOf(
            createModel("openai/gpt-4o"),
            createModel("anthropic/claude-3.5-sonnet")
        )

        val filtered = ModelProviderUtils.filterByProvider(models, "All Providers")
        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterByCapabilities should filter models with vision`() {
        val models = listOf(
            createModel(
                "openai/gpt-4o",
                architecture = ModelArchitecture(inputModalities = listOf("text", "image"))
            ),
            createModel(
                "openai/gpt-4o-mini",
                architecture = ModelArchitecture(inputModalities = listOf("text"))
            )
        )

        val filtered = ModelProviderUtils.filterByCapabilities(models, requireVision = true)
        assertEquals(1, filtered.size)
        assertEquals("openai/gpt-4o", filtered[0].id)
    }

    @Test
    fun `filterByCapabilities should filter models with multiple capabilities`() {
        val models = listOf(
            createModel(
                "openai/gpt-4o",
                architecture = ModelArchitecture(inputModalities = listOf("text", "image")),
                supportedParameters = listOf("tools")
            ),
            createModel(
                "openai/gpt-4o-mini",
                architecture = ModelArchitecture(inputModalities = listOf("text", "image"))
            )
        )

        val filtered = ModelProviderUtils.filterByCapabilities(
            models,
            requireVision = true,
            requireTools = true
        )
        assertEquals(1, filtered.size)
        assertEquals("openai/gpt-4o", filtered[0].id)
    }

    @Test
    fun `applyFilters should combine all filters`() {
        val models = listOf(
            createModel(
                "openai/gpt-4o",
                architecture = ModelArchitecture(inputModalities = listOf("text", "image")),
                supportedParameters = listOf("tools"),
                contextLength = 128000
            ),
            createModel(
                "openai/gpt-4o-mini",
                architecture = ModelArchitecture(inputModalities = listOf("text")),
                contextLength = 128000
            ),
            createModel(
                "anthropic/claude-3.5-sonnet",
                architecture = ModelArchitecture(inputModalities = listOf("text", "image")),
                supportedParameters = listOf("tools"),
                contextLength = 200000
            )
        )

        val criteria = ModelProviderUtils.FilterCriteria(
            provider = "OpenAI",
            contextRange = ModelProviderUtils.ContextRange.MEDIUM,
            requireVision = true,
            requireTools = true
        )
        val filtered = ModelProviderUtils.applyFilters(models, criteria)

        assertEquals(1, filtered.size)
        assertEquals("openai/gpt-4o", filtered[0].id)
    }

    @Test
    fun `applyFilters should apply search text filter`() {
        val models = listOf(
            createModel("openai/gpt-4o"),
            createModel("openai/gpt-4o-mini"),
            createModel("anthropic/claude-3.5-sonnet")
        )

        val criteria = ModelProviderUtils.FilterCriteria(searchText = "mini")
        val filtered = ModelProviderUtils.applyFilters(models, criteria)

        assertEquals(1, filtered.size)
        assertEquals("openai/gpt-4o-mini", filtered[0].id)
    }

    // Helper function to create test models
    private fun createModel(
        id: String,
        architecture: ModelArchitecture? = null,
        supportedParameters: List<String>? = null,
        contextLength: Int? = null
    ): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = id,
            name = id,
            created = 0L,
            architecture = architecture,
            supportedParameters = supportedParameters,
            contextLength = contextLength
        )
    }
}
