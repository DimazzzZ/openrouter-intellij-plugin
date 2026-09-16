package org.zhavoronkov.openrouter.settings.favorites

import com.intellij.ide.HelpTooltip
import com.intellij.ui.ContextHelpLabel
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ModelVariant

/**
 * The "?" icon next to the page comment explaining what the variant chips mean.
 */
object VariantLegend {

    const val TITLE = "Model variants"

    /**
     * Placed below the icon on purpose.
     *
     * [HelpTooltip.Alignment.HELP_BUTTON], which [ContextHelpLabel.create] installs
     * by default, offsets the popup by `-popupHeight` — it hangs above the icon and
     * climbs higher the taller it gets. A legend-sized popup then lands off the top
     * of the screen, the platform clamps it back down over the icon, and the icon
     * starts alternating mouseExited / mouseEntered: the tooltip flickers forever
     * (IDEA-330235). [HelpTooltip.Alignment.BOTTOM] anchors the popup under the
     * icon instead, so its position no longer depends on its height.
     */
    val ALIGNMENT: HelpTooltip.Alignment = HelpTooltip.Alignment.BOTTOM

    /**
     * Only `:free` and `:batch` are catalogue entries with their own pricing, so
     * they are the only chips most users ever see. The routing shortcuts are listed
     * too: they are valid on any model id and may sit in favourites saved earlier.
     */
    fun describe(): String {
        val known = ModelVariant.entries.joinToString("<br>") { "<b>${it.displayName}</b> — ${it.tooltip}" }
        return known +
            "<br><b>Other</b> — a suffix this plugin does not recognise" +
            "<br><br>Nitro, Floor and Exacto are request-time routing shortcuts rather than " +
            "catalog entries, so no model is listed under them."
    }

    /**
     * setTitle(Supplier) is the non-deprecated overload available on the 2025.3 compile
     * target. setDescription(String), by contrast, is the ONLY description setter in
     * 2025.3 — every overload is deprecated in 2026.2 and the replacement
     * setDescription(HtmlChunk) is not reachable from 2025.3. There is no method that
     * sets the description AND exists in both versions, so the @Suppress below is
     * unavoidable while platformVersion stays on 2025.x; it silences the verifier for
     * this single call only.
     *
     * TODO(platform 2026.x): when platformVersion moves to 2026.x, drop the @Suppress
     * and replace `.setDescription(describe())` with
     * `.setDescription(HtmlChunk.raw(describe()))` (import com.intellij.util.ui.HtmlChunk).
     */
    @Suppress("DEPRECATION")
    fun createLabel(): ContextHelpLabel = ContextHelpLabel.createFromTooltip(
        HelpTooltip()
            .setTitle { TITLE }
            .setDescription(describe())
            .setNeverHideOnTimeout(true)
            .setLocation(ALIGNMENT)
    )
}
