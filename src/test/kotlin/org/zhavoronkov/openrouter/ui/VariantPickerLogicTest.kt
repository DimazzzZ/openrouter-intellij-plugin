package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

class VariantPickerLogicTest {

    private fun model(id: String) = OpenRouterModelInfo(
        id = id,
        name = id,
        created = 0L
    )

    @Test
    fun `baseModelIds strips variants and deduplicates`() {
        val models = listOf(
            model("x-ai/grok-4-fast"),
            model("x-ai/grok-4-fast:free"),
            model("x-ai/grok-4-fast:nitro"),
            model("openai/gpt-4o")
        )

        val bases = VariantPickerLogic.baseModelIds(models)

        assertEquals(listOf("openai/gpt-4o", "x-ai/grok-4-fast"), bases)
    }

    @Test
    fun `variantsForBase includes base sentinel and all variants`() {
        val models = listOf(
            model("x-ai/grok-4-fast"),
            model("x-ai/grok-4-fast:free"),
            model("x-ai/grok-4-fast:nitro")
        )

        val variants = VariantPickerLogic.variantsForBase(models, "x-ai/grok-4-fast")

        // Base sentinel first, then sorted variants
        assertTrue(variants[0] == VariantPickerLogic.BASE_SENTINEL)
        assertTrue(variants.contains(":free"))
        assertTrue(variants.contains(":nitro"))
        assertEquals(3, variants.size)
    }

    @Test
    fun `variantsForBase returns only sentinel for model with no variants`() {
        val models = listOf(model("openai/gpt-4o"))

        val variants = VariantPickerLogic.variantsForBase(models, "openai/gpt-4o")

        assertEquals(listOf(VariantPickerLogic.BASE_SENTINEL), variants)
    }

    @Test
    fun `variantsForBase handles unknown variants`() {
        val models = listOf(
            model("some/model:brand-new")
        )

        val variants = VariantPickerLogic.variantsForBase(models, "some/model")

        assertTrue(variants.contains(":brand-new"))
    }

    @Test
    fun `toModelId with base sentinel returns base unchanged`() {
        assertEquals("openai/gpt-4o", VariantPickerLogic.toModelId("openai/gpt-4o", ""))
    }

    @Test
    fun `toModelId with variant suffix appends it`() {
        assertEquals("x-ai/grok-4-fast:free", VariantPickerLogic.toModelId("x-ai/grok-4-fast", ":free"))
    }
}
