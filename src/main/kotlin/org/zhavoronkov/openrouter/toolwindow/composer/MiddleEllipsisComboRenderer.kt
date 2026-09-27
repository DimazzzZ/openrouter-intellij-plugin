package org.zhavoronkov.openrouter.toolwindow.composer

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Middle-truncates the text of a combo's closed cell.
 *
 * Only the closed cell (index == -1) is affected; the popup list has all the
 * width it wants and is left to the delegate, which draws variant chips and
 * group headers.
 */
class MiddleEllipsisComboRenderer(
    private val combo: ComboBox<String>,
    private val delegate: ListCellRenderer<in String>
) : ListCellRenderer<String> {

    override fun getListCellRendererComponent(
        list: JList<out String>,
        value: String?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        // combo.width is 0 until the layout has placed the combo for the first
        // time. Fitting to `0 - chrome` is fitting to a negative budget, for
        // which MiddleEllipsis.fit correctly returns "" - and an empty closed
        // cell is both wrong to draw and, because the platform measures a combo
        // through this very renderer, wrong to measure. There is nothing to fit
        // yet, so the delegate's component is handed back untouched.
        if (index != -1 || component !is JLabel || combo.width <= 0) return component

        val available = combo.width - JBUI.scale(COMBO_CHROME)
        val metrics = combo.getFontMetrics(component.font ?: combo.font)
        // The delegate's text for the CLOSED cell is HTML markup (variant chips,
        // colored spans - see ModelVariantChipRenderer) meant for the popup's
        // richer rendering. Middle-ellipsising that markup as if it were plain
        // text mangles the tags and leaves a fragment like ".../html>" on
        // screen. The closed cell gets its own plain-text rendering instead,
        // built straight from the raw model id (`value`), never from whatever
        // HTML the delegate put in `component.text`.
        component.text = MiddleEllipsis.fit(value.orEmpty(), available, metrics::stringWidth)
        component.toolTipText = value
        return component
    }

    internal companion object {
        /**
         * Arrow button plus insets — the part of the combo not available to text.
         *
         * Confirmed against the platform's DarculaComboBoxUI (decompiled):
         * getDefaultComboBoxInsets() = JBUI.insets(3) (3px each side), and the
         * arrow button's own preferred width is
         * JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH (UI default 23px) plus
         * the insets' right value again (3px). left(3) + right(3) + arrow(23) +
         * arrow's own right inset(3) = 32, for the default (non-compact,
         * non-borderless) combo box chrome at 100% scale. A custom LAF theme
         * could override the "Component.arrowAreaWidth" UI key, but 32 is the
         * correct default.
         *
         * Shared with [ComposerLayout], which adds it to the combo's
         * untruncated text width to work out what the combo would like to be:
         * both have to agree about how much of the combo is not text.
         */
        internal const val COMBO_CHROME = 32
    }
}
