package org.zhavoronkov.openrouter.ui

import com.intellij.ui.JBColor
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ModelVariant
import java.awt.Color

/**
 * Renders model variant chips (colored labels) alongside model IDs.
 *
 * Instead of a custom multi-component Swing renderer (which is fiddly to align
 * inside JBList / JBTable cells), this uses HTML rendering. Callers embed the
 * chip HTML in the cell text and Swing's HTML renderer paints it inline.
 *
 * Example:
 *   val html = ModelVariantChipRenderer.renderRow("x-ai/grok-4-fast:free")
 *   // returns "<html>x-ai/grok-4-fast &nbsp; <span style='...'>Free</span></html>"
 */
object ModelVariantChipRenderer {

    private const val RGB_MASK = 0xFFFFFF

    /**
     * Hex color codes for each variant chip. Tuned for good contrast in both
     * light and dark IntelliJ themes.
     */
    private val VARIANT_COLORS: Map<ModelVariant, ChipColor> = mapOf(
        ModelVariant.FREE to ChipColor(
            bgLight = "#E8F5E9", bgDark = "#1B5E20", fgLight = "#1B5E20", fgDark = "#E8F5E9"
        ),
        ModelVariant.EXACTO to ChipColor(
            bgLight = "#F3E5F5", bgDark = "#6A1B9A", fgLight = "#6A1B9A", fgDark = "#F3E5F5"
        ),
        ModelVariant.NITRO to ChipColor(
            bgLight = "#FFF3E0", bgDark = "#E65100", fgLight = "#E65100", fgDark = "#FFF3E0"
        ),
        ModelVariant.FLOOR to ChipColor(
            bgLight = "#E0F2F1", bgDark = "#00695C", fgLight = "#00695C", fgDark = "#E0F2F1"
        )
    )

    private val UNKNOWN_CHIP_COLOR = ChipColor(
        bgLight = "#FFF9C4",
        bgDark = "#F57F17",
        fgLight = "#F57F17",
        fgDark = "#FFF9C4"
    )

    /**
     * Chip color set with theme-aware selection.
     */
    private data class ChipColor(
        val bgLight: String,
        val bgDark: String,
        val fgLight: String,
        val fgDark: String
    ) {
        fun bg(): String = if (JBColor.isBright()) bgLight else bgDark
        fun fg(): String = if (JBColor.isBright()) fgLight else fgDark
        fun bgColor(): Color = Color.decode(bg())
        fun fgColor(): Color = Color.decode(fg())
    }

    /**
     * Theme-aware background color for a known variant chip, for use by a
     * Graphics2D-based renderer that paints real rounded rectangles (Swing's
     * HTML/CSS engine cannot draw `border-radius`). Returns null for a base
     * model that has no chip.
     */
    fun chipBackground(variant: ModelVariant): Color = (VARIANT_COLORS[variant] ?: UNKNOWN_CHIP_COLOR).bgColor()

    /** Theme-aware foreground (text) color for a known variant chip. */
    fun chipForeground(variant: ModelVariant): Color = (VARIANT_COLORS[variant] ?: UNKNOWN_CHIP_COLOR).fgColor()

    /** Background color for the "unknown variant" chip. */
    fun unknownChipBackground(): Color = UNKNOWN_CHIP_COLOR.bgColor()

    /** Foreground color for the "unknown variant" chip. */
    fun unknownChipForeground(): Color = UNKNOWN_CHIP_COLOR.fgColor()

    /**
     * The short label shown inside a chip for a model id, or null when the id
     * has no variant. Unknown variants use a `? suffix` label.
     */
    fun chipLabelFor(modelId: String): String? {
        val parsed = ModelProviderUtils.parseModelId(modelId)
        return when {
            parsed.variant != null -> parsed.variant.displayName
            parsed.unknownVariant != null -> "? " + parsed.unknownVariant.removePrefix(":")
            else -> null
        }
    }

    /**
     * Render a model ID as HTML with an inline variant chip when applicable.
     * The returned HTML starts with `<html>` and can be placed directly into a
     * label's text.
     *
     * @param modelId the full model ID (e.g., "x-ai/grok-4-fast:free")
     * @param baseTextColor optional foreground color for the base ID (defaults to inherit)
     */
    fun renderRow(modelId: String, baseTextColor: Color? = null): String {
        val parsed = ModelProviderUtils.parseModelId(modelId)
        val baseDisplay = escapeHtml(ModelProviderUtils.stripVariant(modelId))

        val baseHtml = if (baseTextColor != null) {
            val hex = "#%06x".format(baseTextColor.rgb and RGB_MASK)
            "<span style='color:$hex'>$baseDisplay</span>"
        } else {
            baseDisplay
        }

        val chipHtml = when {
            parsed.variant != null -> chipFor(parsed.variant)
            parsed.unknownVariant != null -> unknownChip(parsed.unknownVariant)
            else -> ""
        }

        // white-space:nowrap keeps the base id and its chip on a single line inside
        // narrow JBTable / JBList cells; without it the chip wraps below the id.
        return if (chipHtml.isEmpty()) {
            "<html><nobr>$baseHtml</nobr></html>"
        } else {
            "<html><nobr>$baseHtml &nbsp; $chipHtml</nobr></html>"
        }
    }

    /**
     * Render a variant chip fragment for a known variant.
     * Returns just the chip HTML span (no wrapping <html>) so it can be composed.
     */
    fun chipFor(variant: ModelVariant): String {
        val color = VARIANT_COLORS[variant] ?: UNKNOWN_CHIP_COLOR
        return buildChip(variant.displayName, color)
    }

    /**
     * Render an "Unknown" chip for a variant this plugin doesn't recognize
     * (typically a brand-new OpenRouter variant introduced after this build).
     */
    fun unknownChip(rawSuffix: String): String {
        val label = escapeHtml(rawSuffix.removePrefix(":"))
        return buildChip("? $label", UNKNOWN_CHIP_COLOR)
    }

    /**
     * Tooltip text for a model ID, describing its variant (if any).
     */
    fun tooltipFor(modelId: String): String {
        val parsed = ModelProviderUtils.parseModelId(modelId)
        return when {
            parsed.variant != null -> "${parsed.provider} — ${parsed.variant.tooltip}"
            parsed.unknownVariant != null ->
                "Unknown variant '${parsed.unknownVariant}'. This variant may be new " +
                    "since the plugin was released — it will still work in the proxy."
            else -> parsed.provider
        }
    }

    private fun buildChip(label: String, color: ChipColor): String {
        val bg = color.bg()
        val fg = color.fg()
        // Swing's javax.swing.text.html.CSS only understands a CSS1 subset. Two
        // properties we previously emitted make it throw an NPE from
        // CSS.getInternalCSSValue when the HTML is set on a JLabel-backed cell
        // renderer (crashes the whole Settings dialog paint loop):
        //   - border-radius        -> unrecognized property, null converter
        //   - padding: 1px 6px     -> two-value shorthand, unrecognized
        // Emit only long-form, single-value properties Swing can parse, and use
        // non-breaking spaces to fake the horizontal padding. Rounded corners are
        // dropped because Swing cannot render them anyway.
        return "<span style='background-color:$bg;color:$fg;" +
            "font-size:10px;font-weight:bold;'>&nbsp;$label&nbsp;</span>"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
