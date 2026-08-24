package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

class ModelVariantChipRendererTest {

    @Test
    fun `renderRow produces html with no chip for base-only model`() {
        val html = ModelVariantChipRenderer.renderRow("openai/gpt-4o")
        assertTrue(html.startsWith("<html>"))
        assertTrue(html.contains("openai/gpt-4o"))
        assertFalse(html.contains("<span style='background:"))
    }

    @Test
    fun `renderRow produces chip for known variant`() {
        val html = ModelVariantChipRenderer.renderRow("x-ai/grok-4-fast:free")
        // Base ID appears without variant suffix
        assertTrue(html.contains("x-ai/grok-4-fast"))
        assertFalse(html.contains(":free"), "Raw suffix should not appear in the display")
        // Chip label "Free" appears
        assertTrue(html.contains(">Free<"))
        // Chip is styled
        assertTrue(html.contains("background:"))
        assertTrue(html.contains("border-radius:"))
    }

    @Test
    fun `renderRow produces unknown-variant chip for unknown suffix`() {
        val html = ModelVariantChipRenderer.renderRow("some/model:brand-new-thing")
        assertTrue(html.contains("some/model"))
        // Unknown chip uses "? " prefix
        assertTrue(html.contains(">? brand-new-thing<"))
    }

    @Test
    fun `chipFor renders each known variant`() {
        for (variant in ModelProviderUtils.ModelVariant.entries) {
            val chip = ModelVariantChipRenderer.chipFor(variant)
            assertTrue(
                chip.contains(">${variant.displayName}<"),
                "Chip for $variant should contain display name"
            )
            assertTrue(chip.contains("background:"))
        }
    }

    @Test
    fun `tooltipFor known variant includes provider and tooltip`() {
        val tooltip = ModelVariantChipRenderer.tooltipFor("x-ai/grok-4-fast:free")
        assertTrue(tooltip.contains("xAI"))
        assertTrue(tooltip.contains("Free"))
    }

    @Test
    fun `tooltipFor unknown variant explains forward-compatibility`() {
        val tooltip = ModelVariantChipRenderer.tooltipFor("some/model:brand-new")
        assertTrue(tooltip.contains("Unknown variant"))
        assertTrue(tooltip.contains("proxy"), "Tooltip should reassure it still works")
    }

    @Test
    fun `tooltipFor base model returns provider name`() {
        val tooltip = ModelVariantChipRenderer.tooltipFor("openai/gpt-4o")
        assertEquals("OpenAI", tooltip)
    }

    @Test
    fun `renderRow escapes HTML in model IDs`() {
        val html = ModelVariantChipRenderer.renderRow("some/mo<del>&x")
        assertTrue(html.contains("&lt;del&gt;"))
        assertTrue(html.contains("&amp;x"))
    }
}
