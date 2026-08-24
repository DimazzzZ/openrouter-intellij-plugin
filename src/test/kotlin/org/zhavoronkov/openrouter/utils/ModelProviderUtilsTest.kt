package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
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

    // --- Reasoning capability tests ---

    @Test
    fun `hasCapability REASONING returns true when supportedParameters contains reasoning`() {
        val model = createModel("openai/o3-mini", supportedParameters = listOf("reasoning", "tools"))
        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.REASONING))
    }

    @Test
    fun `hasCapability REASONING returns false when supportedParameters is null`() {
        val model = createModel("openai/gpt-4o", supportedParameters = null)
        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.REASONING))
    }

    @Test
    fun `hasCapability REASONING returns false when supportedParameters is empty`() {
        val model = createModel("openai/gpt-4o", supportedParameters = emptyList())
        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.REASONING))
    }

    @Test
    fun `hasCapability REASONING returns false when not in list`() {
        val model = createModel("openai/gpt-4o", supportedParameters = listOf("tools", "temperature"))
        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.REASONING))
    }

    @Test
    fun `hasCapability REASONING is case-insensitive`() {
        val model = createModel("test/model", supportedParameters = listOf("Reasoning"))
        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.REASONING))
    }

    // --- Verbosity capability tests ---

    @Test
    fun `hasCapability VERBOSITY returns true when supportedParameters contains verbosity`() {
        val model = createModel("anthropic/claude-4.6-sonnet", supportedParameters = listOf("verbosity", "reasoning"))
        assertTrue(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.VERBOSITY))
    }

    @Test
    fun `hasCapability VERBOSITY returns false when not in list`() {
        val model = createModel("openai/gpt-4o", supportedParameters = listOf("tools"))
        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.VERBOSITY))
    }

    @Test
    fun `hasCapability VERBOSITY returns false when supportedParameters is null`() {
        val model = createModel("openai/gpt-4o")
        assertFalse(ModelProviderUtils.hasCapability(model, ModelProviderUtils.Capability.VERBOSITY))
    }

    // --- getCapabilities includes/excludes reasoning and verbosity ---

    @Test
    fun `getCapabilities includes Reasoning when supported`() {
        val model = createModel("openai/o3", supportedParameters = listOf("reasoning"))
        val caps = ModelProviderUtils.getCapabilities(model)
        assertTrue(caps.contains("Reasoning"), "Capabilities should include Reasoning")
    }

    @Test
    fun `getCapabilities excludes Reasoning when not supported`() {
        val model = createModel("openai/gpt-4o", supportedParameters = listOf("tools"))
        val caps = ModelProviderUtils.getCapabilities(model)
        assertFalse(caps.contains("Reasoning"), "Capabilities should not include Reasoning")
    }

    @Test
    fun `getCapabilities includes Verbosity when supported`() {
        val model = createModel("anthropic/claude-4.6", supportedParameters = listOf("verbosity"))
        val caps = ModelProviderUtils.getCapabilities(model)
        assertTrue(caps.contains("Verbosity"), "Capabilities should include Verbosity")
    }

    @Test
    fun `getCapabilities excludes Verbosity when not supported`() {
        val model = createModel("openai/gpt-4o", supportedParameters = listOf("tools"))
        val caps = ModelProviderUtils.getCapabilities(model)
        assertFalse(caps.contains("Verbosity"), "Capabilities should not include Verbosity")
    }

    @Test
    fun `getCapabilities includes both Reasoning and Verbosity when both supported`() {
        val model = createModel("anthropic/claude-4.6", supportedParameters = listOf("reasoning", "verbosity", "tools"))
        val caps = ModelProviderUtils.getCapabilities(model)
        assertTrue(caps.contains("Reasoning"))
        assertTrue(caps.contains("Verbosity"))
    }

    // --- Model variant parsing tests ---

    @Test
    fun `parseModelId should parse standard model without variant`() {
        val result = ModelProviderUtils.parseModelId("openai/gpt-4o")
        assertEquals("OpenAI", result.provider)
        assertEquals("gpt-4o", result.baseName)
        assertNull(result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should parse model with free variant`() {
        val result = ModelProviderUtils.parseModelId("x-ai/grok-4-fast:free")
        assertEquals("xAI", result.provider)
        assertEquals("grok-4-fast", result.baseName)
        assertEquals(ModelProviderUtils.ModelVariant.FREE, result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should parse model with thinking variant`() {
        val result = ModelProviderUtils.parseModelId("meta-llama/llama-3.1-70b-instruct:thinking")
        assertEquals("Meta", result.provider)
        assertEquals("llama-3.1-70b-instruct", result.baseName)
        assertEquals(ModelProviderUtils.ModelVariant.THINKING, result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should parse model with nitro variant`() {
        val result = ModelProviderUtils.parseModelId("anthropic/claude-3.5-sonnet:nitro")
        assertEquals("Anthropic", result.provider)
        assertEquals("claude-3.5-sonnet", result.baseName)
        assertEquals(ModelProviderUtils.ModelVariant.NITRO, result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should capture unknown variant`() {
        val result = ModelProviderUtils.parseModelId("some/model:brand-new-variant")
        assertEquals("Some", result.provider)
        assertEquals("model", result.baseName)
        assertNull(result.variant)
        assertEquals(":brand-new-variant", result.unknownVariant)
    }

    @Test
    fun `parseModelId should handle preset slug`() {
        val result = ModelProviderUtils.parseModelId("@preset/email-copywriter")
        assertEquals("@preset", result.provider)
        assertEquals("email-copywriter", result.baseName)
        assertNull(result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should handle bare model name without slash`() {
        val result = ModelProviderUtils.parseModelId("gpt-4o")
        assertEquals("Other", result.provider)
        assertEquals("gpt-4o", result.baseName)
        assertNull(result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should handle blank input`() {
        val result = ModelProviderUtils.parseModelId("")
        assertEquals("Other", result.provider)
        assertEquals("", result.baseName)
        assertNull(result.variant)
        assertNull(result.unknownVariant)
    }

    @Test
    fun `parseModelId should handle all known variants`() {
        val variants = ModelProviderUtils.ModelVariant.entries
        for (variant in variants) {
            val result = ModelProviderUtils.parseModelId("openai/gpt-4o${variant.suffix}")
            assertEquals(variant, result.variant, "Failed to parse variant ${variant.suffix}")
            assertEquals("gpt-4o", result.baseName)
        }
    }

    // --- stripVariant tests ---

    @Test
    fun `stripVariant should remove free variant suffix`() {
        assertEquals("x-ai/grok-4-fast", ModelProviderUtils.stripVariant("x-ai/grok-4-fast:free"))
    }

    @Test
    fun `stripVariant should return unchanged ID without variant`() {
        assertEquals("openai/gpt-4o", ModelProviderUtils.stripVariant("openai/gpt-4o"))
    }

    @Test
    fun `stripVariant should remove unknown variant suffix`() {
        assertEquals("some/model", ModelProviderUtils.stripVariant("some/model:brand-new"))
    }

    @Test
    fun `stripVariant should handle preset slug with variant`() {
        assertEquals("@preset/email", ModelProviderUtils.stripVariant("@preset/email:thinking"))
    }

    // --- hasVariant tests ---

    @Test
    fun `hasVariant should return true for matching variant`() {
        assertTrue(ModelProviderUtils.hasVariant("x-ai/grok-4-fast:free", ModelProviderUtils.ModelVariant.FREE))
    }

    @Test
    fun `hasVariant should return false for non-matching variant`() {
        assertFalse(ModelProviderUtils.hasVariant("x-ai/grok-4-fast:free", ModelProviderUtils.ModelVariant.NITRO))
    }

    @Test
    fun `hasVariant should return false for model without variant`() {
        assertFalse(ModelProviderUtils.hasVariant("x-ai/grok-4-fast", ModelProviderUtils.ModelVariant.FREE))
    }

    @Test
    fun `hasVariant should return false for unknown variant`() {
        assertFalse(ModelProviderUtils.hasVariant("x-ai/grok-4-fast:unknown", ModelProviderUtils.ModelVariant.FREE))
    }

    // --- ModelVariant enum tests ---

    @Test
    fun `ModelVariant fromSuffix should parse known suffixes`() {
        assertEquals(ModelProviderUtils.ModelVariant.FREE, ModelProviderUtils.ModelVariant.fromSuffix(":free"))
        assertEquals(ModelProviderUtils.ModelVariant.EXTENDED, ModelProviderUtils.ModelVariant.fromSuffix(":extended"))
        assertEquals(ModelProviderUtils.ModelVariant.EXACTO, ModelProviderUtils.ModelVariant.fromSuffix(":exacto"))
        assertEquals(ModelProviderUtils.ModelVariant.THINKING, ModelProviderUtils.ModelVariant.fromSuffix(":thinking"))
        assertEquals(ModelProviderUtils.ModelVariant.ONLINE, ModelProviderUtils.ModelVariant.fromSuffix(":online"))
        assertEquals(ModelProviderUtils.ModelVariant.NITRO, ModelProviderUtils.ModelVariant.fromSuffix(":nitro"))
        assertEquals(ModelProviderUtils.ModelVariant.FLOOR, ModelProviderUtils.ModelVariant.fromSuffix(":floor"))
    }

    @Test
    fun `ModelVariant fromSuffix should return null for unknown suffix`() {
        assertNull(ModelProviderUtils.ModelVariant.fromSuffix(":unknown"))
        assertNull(ModelProviderUtils.ModelVariant.fromSuffix("free")) // Missing colon
        assertNull(ModelProviderUtils.ModelVariant.fromSuffix(""))
    }

    // --- extractProvider with variant awareness ---

    @Test
    fun `extractProvider should ignore variant suffix`() {
        assertEquals("xAI", ModelProviderUtils.extractProvider("x-ai/grok-4-fast:free"))
        assertEquals("OpenAI", ModelProviderUtils.extractProvider("openai/gpt-4o:thinking"))
        assertEquals("Anthropic", ModelProviderUtils.extractProvider("anthropic/claude-3.5-sonnet:nitro"))
    }

    @Test
    fun `extractProvider should handle preset slug`() {
        assertEquals("@preset", ModelProviderUtils.extractProvider("@preset/email-copywriter"))
    }
}
