package org.zhavoronkov.openrouter.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import javax.swing.JLabel
import javax.swing.plaf.basic.BasicHTML

class ModelVariantChipRendererTest {

    @Test
    fun `renderRow produces html with no chip for base-only model`() {
        val html = ModelVariantChipRenderer.renderRow("openai/gpt-4o")
        assertTrue(html.startsWith("<html>"))
        assertTrue(html.contains("openai/gpt-4o"))
        assertFalse(html.contains("<span style='background-color:"))
    }

    @Test
    fun `renderRow produces chip for known variant`() {
        val html = ModelVariantChipRenderer.renderRow("x-ai/grok-4-fast:free")
        // Base ID appears without variant suffix
        assertTrue(html.contains("x-ai/grok-4-fast"))
        assertFalse(html.contains(":free"), "Raw suffix should not appear in the display")
        // Chip label "Free" appears
        // Label is padded with &nbsp; on each side to fake CSS padding Swing can't parse.
        assertTrue(html.contains("Free"))
        assertTrue(html.contains(">&nbsp;Free&nbsp;<"))
        // Chip is styled
        assertTrue(html.contains("background-color:"))
        // border-radius is intentionally NOT emitted: Swing's CSS parser
        // throws NPE on it. See chipSpan().
        assertFalse(html.contains("border-radius:"))
    }

    @Test
    fun `renderRow produces unknown-variant chip for unknown suffix`() {
        val html = ModelVariantChipRenderer.renderRow("some/model:brand-new-thing")
        assertTrue(html.contains("some/model"))
        // Unknown chip uses "? " prefix
        assertTrue(html.contains(">&nbsp;? brand-new-thing&nbsp;<"))
    }

    @Test
    fun `renderRow renders each known variant's chip`() {
        for (variant in ModelProviderUtils.ModelVariant.entries) {
            val html = ModelVariantChipRenderer.renderRow("openai/gpt-4o${variant.suffix}")
            assertTrue(
                html.contains(">&nbsp;${variant.displayName}&nbsp;<"),
                "Chip for $variant should contain display name"
            )
            assertTrue(html.contains("background-color:"))
        }
    }

    private fun labels(id: String): List<String> = ModelVariantChipRenderer.chipsFor(id).map { it.label }

    private fun chipOf(variant: ModelProviderUtils.ModelVariant) =
        ModelVariantChipRenderer.chipsFor("openai/gpt-4o${variant.suffix}").single()

    private val unknownChip get() = ModelVariantChipRenderer.chipsFor("some/model:brand-new").single()

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

    // --- Graphics2D chip seams (used by the rounded-corner table renderer) ---

    @Test
    fun `chipsFor returns display name for known variant`() {
        assertEquals(listOf("Free"), labels("x-ai/grok-4-fast:free"))
        assertEquals(listOf("Batch"), labels("openai/gpt-4o:batch"))
    }

    @Test
    fun `chipsFor returns nothing for base model`() {
        assertEquals(emptyList<String>(), labels("openai/gpt-4o"))
    }

    @Test
    fun `chipsFor prefixes unknown variant`() {
        assertEquals(listOf("? brand-new"), labels("some/model:brand-new"))
    }

    @Test
    fun `every known variant has its own color, not the unknown-variant fallback`() {
        ModelProviderUtils.ModelVariant.entries.forEach { variant ->
            assertNotEquals(
                unknownChip.background,
                chipOf(variant).background,
                "${'$'}{variant.displayName} must not reuse the unknown-variant chip color"
            )
            assertNotEquals(unknownChip.foreground, chipOf(variant).foreground)
        }
    }

    /**
     * Regression test for the Settings-dialog crash: setting chip HTML on a
     * real JLabel used to throw NullPointerException from
     * javax.swing.text.html.CSS.getInternalCSSValue because the chip span emitted
     * `border-radius` and a two-value `padding` shorthand that Swing's CSS
     * parser cannot handle. The renderer must produce HTML that Swing parses
     * into a non-null View for every variant.
     */
    @Test
    fun `renderRow html is parseable by Swing HTML view for every variant`() {
        System.setProperty("java.awt.headless", "true")
        val ids = buildList {
            add("openai/gpt-4o")
            add("some/model:brand-new-thing")
            for (variant in ModelProviderUtils.ModelVariant.entries) {
                add("x-ai/model:${variant.name.lowercase()}")
            }
        }
        for (id in ids) {
            val label = JLabel()
            // setText triggers BasicHTML.updateRenderer, which parses the CSS.
            // Before the fix this threw NPE for any id that produced a chip.
            label.text = ModelVariantChipRenderer.renderRow(id)
            val view = label.getClientProperty(BasicHTML.propertyKey)
            assertNotNull(view, "Swing failed to build an HTML view for id=$id")
        }
    }

    @Test
    fun `a latest slug gets a Latest chip`() {
        assertEquals(listOf("Latest"), labels("~openai/gpt-astra-latest"))
        assertTrue(ModelVariantChipRenderer.renderRow("~openai/gpt-astra-latest").contains("Latest"))
    }

    @Test
    fun `a latest slug with a catalog variant gets both chips, variant first`() {
        val labels = labels("~google/gemini-flash-latest:free")

        assertEquals(listOf("Free", "Latest"), labels)
    }

    @Test
    fun `an ordinary slug gets no Latest chip`() {
        assertEquals(listOf("Free"), labels("x-ai/grok-4-fast:free"))
        assertEquals(emptyList<String>(), labels("openai/gpt-4o"))
    }

    @Test
    fun `tooltipFor a latest slug names its real author and says it moves`() {
        val tooltip = ModelVariantChipRenderer.tooltipFor("~openai/gpt-astra-latest")

        assertTrue(tooltip.startsWith("OpenAI"), tooltip)
        assertTrue(tooltip.contains("newest"), tooltip)
    }
}
