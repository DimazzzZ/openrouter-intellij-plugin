package org.zhavoronkov.openrouter.settings.favorites

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ModelVariant

class VariantFilterTest {

    @Test
    fun `ANY matches every id`() {
        assertTrue(VariantFilter.ANY.matches("openai/gpt-4o"))
        assertTrue(VariantFilter.ANY.matches("x-ai/grok-4-fast:free"))
        assertTrue(VariantFilter.ANY.matches("some/model:brand-new"))
    }

    @Test
    fun `BASE_ONLY matches ids without a suffix`() {
        assertTrue(VariantFilter.BASE_ONLY.matches("openai/gpt-4o"))
        assertTrue(VariantFilter.BASE_ONLY.matches("@preset/email"))
        assertFalse(VariantFilter.BASE_ONLY.matches("x-ai/grok-4-fast:free"))
        assertFalse(VariantFilter.BASE_ONLY.matches("some/model:brand-new"))
    }

    @ParameterizedTest
    @EnumSource(ModelVariant::class)
    fun `every known variant has a filter that matches only its suffix`(variant: ModelVariant) {
        val filter = VariantFilter.forVariant(variant)

        assertEquals(variant, filter.variant)
        assertTrue(filter.matches("openai/gpt-4o${variant.suffix}"))
        assertFalse(filter.matches("openai/gpt-4o"))
        assertFalse(filter.matches("openai/gpt-4o:brand-new"))
        val other = ModelVariant.entries.first { it != variant }
        assertFalse(filter.matches("openai/gpt-4o${other.suffix}"))
    }

    @Test
    fun `OTHER matches unknown and retired suffixes only`() {
        assertTrue(VariantFilter.OTHER.matches("some/model:brand-new"))
        assertTrue(VariantFilter.OTHER.matches("openai/gpt-4o:thinking"))
        assertFalse(VariantFilter.OTHER.matches("openai/gpt-4o"))
        assertFalse(VariantFilter.OTHER.matches("openai/gpt-4o:free"))
    }

    @Test
    fun `entries are ordered ANY, BASE_ONLY, known variants in enum order, OTHER`() {
        val expected = listOf(VariantFilter.ANY, VariantFilter.BASE_ONLY) +
            ModelVariant.entries.map { VariantFilter.forVariant(it) } +
            VariantFilter.OTHER
        assertEquals(expected, VariantFilter.entries.toList())
    }

    @Test
    fun `display names are non-blank and unique`() {
        val names = VariantFilter.entries.map { it.displayName }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }
}
