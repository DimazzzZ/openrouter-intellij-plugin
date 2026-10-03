package org.zhavoronkov.openrouter.settings.favorites

import com.intellij.ide.HelpTooltip
import com.intellij.ui.ContextHelpLabel
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ModelVariant
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

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
     * Built by [ContextHelpLabel.create], the one way to give a [HelpTooltip] a
     * title and a description that is deprecated in no supported platform: 2025.3
     * has only the String setters, 2026.1 deprecates them, and their HtmlChunk
     * replacements do not exist in 2025.3. The factory pins the popup to
     * [HelpTooltip.Alignment.HELP_BUTTON], and the tooltip it builds is reachable
     * only once the label is shown and has installed it, so the alignment is moved
     * to [ALIGNMENT] when the pointer enters. That still precedes the popup: the
     * tooltip shows it after a delay and reads the alignment only then.
     *
     * This is a compatibility workaround for as long as 2025.3 is supported.
     *
     * TODO(platform 2026.1): once pluginSinceBuild is 261 or later, build the
     * tooltip directly and drop the mouse listener:
     * ```
     * ContextHelpLabel.createFromTooltip(
     *     HelpTooltip()
     *         .setPlainTextTitle(TITLE)
     *         .setDescription(HtmlChunk.raw(describe()))
     *         .setNeverHideOnTimeout(true)
     *         .setLocation(ALIGNMENT)
     * )
     * ```
     * (import com.intellij.openapi.util.text.HtmlChunk). Keep
     * testVariantHelpTooltipOpensBelowTheIconOncePointedAt, minus its "before
     * pointed at" half.
     */
    fun createLabel(): ContextHelpLabel = ContextHelpLabel.create(TITLE, describe()).also { label ->
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                HelpTooltip.getTooltipFor(label)?.setLocation(ALIGNMENT)
            }
        })
    }
}
