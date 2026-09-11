package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.settings.favorites.VariantFilter
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.Capability
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ContextRange

@DisplayName("ModelFilterCriteria")
class ModelFilterCriteriaTest {

    private fun model(
        id: String,
        name: String = id,
        description: String? = null,
        contextLength: Int? = 128_000,
        inputModalities: List<String> = listOf("text"),
        outputModalities: List<String> = listOf("text"),
        supportedParameters: List<String> = emptyList(),
    ) = OpenRouterModelInfo(
        id = id,
        name = name,
        created = 0L,
        description = description,
        architecture = ModelArchitecture(inputModalities = inputModalities, outputModalities = outputModalities),
        contextLength = contextLength,
        supportedParameters = supportedParameters,
    )

    @Nested
    @DisplayName("active filters")
    inner class ActiveFilters {

        @Test
        fun `default has no active filters and no input`() {
            val criteria = ModelFilterCriteria.default()

            assertFalse(criteria.hasActiveFilters())
            assertFalse(criteria.hasAnyInput())
            assertEquals(0, criteria.activeFilterCount())
        }

        @Test
        fun `provider flips hasActiveFilters`() {
            assertTrue(ModelFilterCriteria(provider = "OpenAI").hasActiveFilters())
        }

        @Test
        fun `context range flips hasActiveFilters`() {
            assertTrue(ModelFilterCriteria(contextRange = ContextRange.LARGE).hasActiveFilters())
        }

        @Test
        fun `capabilities flip hasActiveFilters`() {
            assertTrue(ModelFilterCriteria(capabilities = setOf(Capability.VISION)).hasActiveFilters())
        }

        @Test
        fun `variant flips hasActiveFilters`() {
            assertTrue(ModelFilterCriteria(variant = VariantFilter.FREE).hasActiveFilters())
        }

        @Test
        fun `search text is input but not a filter`() {
            val criteria = ModelFilterCriteria(searchText = "gpt")

            assertFalse(criteria.hasActiveFilters())
            assertTrue(criteria.hasAnyInput())
        }

        @Test
        fun `activeFilterCount counts dimensions not individual capabilities`() {
            val criteria = ModelFilterCriteria(
                provider = "OpenAI",
                contextRange = ContextRange.LARGE,
                capabilities = setOf(Capability.VISION, Capability.TOOLS),
                variant = VariantFilter.FREE,
                searchText = "ignored",
            )

            assertEquals(4, criteria.activeFilterCount())
        }
    }

    @Nested
    @DisplayName("describe")
    inner class Describe {

        @Test
        fun `no filters`() {
            assertEquals("No filters", ModelFilterCriteria.default().describe())
        }

        @Test
        fun `all dimensions`() {
            val criteria = ModelFilterCriteria(
                provider = "OpenAI",
                contextRange = ContextRange.LARGE,
                capabilities = setOf(Capability.TOOLS, Capability.VISION),
                variant = VariantFilter.FREE,
            )

            assertEquals(
                "Provider: OpenAI | Context: > 128K | Capabilities: Vision, Tools | Variant: Free",
                criteria.describe()
            )
        }
    }

    @Nested
    @DisplayName("matches")
    inner class Matches {

        @Test
        fun `default matches everything`() {
            val criteria = ModelFilterCriteria.default()

            assertTrue(criteria.matches(model("openai/gpt-4o")))
            assertTrue(criteria.matches(model("x-ai/grok-4-fast:free", contextLength = null)))
        }

        @Test
        fun `provider uses display name and ignores variant suffix`() {
            val criteria = ModelFilterCriteria(provider = "xAI")

            assertTrue(criteria.matches(model("x-ai/grok-4-fast")))
            assertTrue(criteria.matches(model("x-ai/grok-4-fast:free")))
            assertFalse(criteria.matches(model("openai/gpt-4o")))
        }

        private fun inRange(range: ContextRange, contextLength: Int?): Boolean =
            ModelFilterCriteria(contextRange = range).matches(model("a/b", contextLength = contextLength))

        @Test
        fun `context range boundaries`() {
            assertTrue(inRange(ContextRange.SMALL, 8_000))
            assertFalse(inRange(ContextRange.SMALL, 32_000))
            assertTrue(inRange(ContextRange.MEDIUM, 32_000))
            assertTrue(inRange(ContextRange.MEDIUM, 128_000))
            assertTrue(inRange(ContextRange.LARGE, 200_000))
            assertFalse(inRange(ContextRange.LARGE, 128_000))
        }

        @Test
        fun `null context length is excluded by any non-ANY range`() {
            assertTrue(inRange(ContextRange.ANY, null))
            assertFalse(inRange(ContextRange.SMALL, null))
            assertFalse(inRange(ContextRange.LARGE, null))
        }

        @Test
        fun `capabilities are ANDed`() {
            val visionAndTools = model(
                "openai/gpt-4o",
                inputModalities = listOf("text", "image"),
                supportedParameters = listOf("tools"),
            )
            val visionOnly = model("a/vision", inputModalities = listOf("text", "image"))
            val criteria = ModelFilterCriteria(capabilities = setOf(Capability.VISION, Capability.TOOLS))

            assertTrue(criteria.matches(visionAndTools))
            assertFalse(criteria.matches(visionOnly))
        }

        @Test
        fun `reasoning capability is honoured`() {
            val reasoning = model("a/thinker", supportedParameters = listOf("reasoning"))
            val plain = model("a/plain")
            val criteria = ModelFilterCriteria(capabilities = setOf(Capability.REASONING))

            assertTrue(criteria.matches(reasoning))
            assertFalse(criteria.matches(plain))
        }

        @Test
        fun `variant filter is applied`() {
            val free = ModelFilterCriteria(variant = VariantFilter.FREE)
            val base = ModelFilterCriteria(variant = VariantFilter.BASE_ONLY)
            val other = ModelFilterCriteria(variant = VariantFilter.OTHER)

            assertTrue(free.matches(model("x-ai/grok-4-fast:free")))
            assertFalse(free.matches(model("x-ai/grok-4-fast")))
            assertTrue(base.matches(model("x-ai/grok-4-fast")))
            assertFalse(base.matches(model("x-ai/grok-4-fast:free")))
            assertTrue(other.matches(model("some/model:brand-new")))
            assertFalse(other.matches(model("x-ai/grok-4-fast:free")))
        }

        @Test
        fun `search is case-insensitive across id, name and description`() {
            val byId = model("openai/gpt-4o", name = "GPT-4o")
            val byName = model("anthropic/claude-3.5-sonnet", name = "Claude Sonnet")
            val byDescription = model("google/gemini", name = "Gemini", description = "Multimodal SONNET-class model")
            val miss = model("meta-llama/llama-3", name = "Llama")

            val criteria = ModelFilterCriteria(searchText = "sonnet")

            assertFalse(criteria.matches(byId))
            assertTrue(criteria.matches(byName))
            assertTrue(criteria.matches(byDescription))
            assertFalse(criteria.matches(miss))
            assertTrue(ModelFilterCriteria(searchText = "GPT").matches(byId))
        }

        @Test
        fun `blank search text matches everything`() {
            assertTrue(ModelFilterCriteria(searchText = "   ").matches(model("a/b")))
        }

        @Test
        fun `dimensions compose with AND`() {
            val criteria = ModelFilterCriteria(
                provider = "OpenAI",
                capabilities = setOf(Capability.VISION),
                searchText = "4o",
            )
            val hit = model("openai/gpt-4o", inputModalities = listOf("text", "image"))
            val wrongProvider = model("anthropic/claude-4o", inputModalities = listOf("text", "image"))
            val noVision = model("openai/gpt-4o-text")
            val wrongSearch = model("openai/gpt-5", inputModalities = listOf("text", "image"))

            assertTrue(criteria.matches(hit))
            assertFalse(criteria.matches(wrongProvider))
            assertFalse(criteria.matches(noVision))
            assertFalse(criteria.matches(wrongSearch))
        }
    }
}
