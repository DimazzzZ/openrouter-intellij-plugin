package org.zhavoronkov.openrouter.toolwindow.composer

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent

/**
 * Lays the composer's control row out by applying ComposerLayoutPolicy.
 *
 * Holds no rules of its own: every decision about what collapses first lives in
 * the pure policy, which is unit-tested. This class only measures, asks, and
 * places.
 */
class ComposerLayout(
    private val model: JComboBox<*>,
    private val settings: JComponent,
    private val counters: JComponent,
    private val send: AbstractButton,
    private val sendIcon: Icon,
    private val sendText: String
) : LayoutManager {

    private val gap get() = JBUI.scale(GAP)

    /**
     * Extra space placed between the model combo and the gear ONLY, on top of
     * the row's normal single [gap].
     *
     * Measured, not eyeballed: a headless layout of the real row
     * ([ComposerLayoutPlatformTest]) shows `settings` (the gear, an
     * [com.intellij.ui.InplaceButton]) has `ui == null`, `border == null` and
     * `insets` all zero, and its `preferredSize` is exactly its icon's size —
     * it is the only part of the row that contributes literally no visual
     * margin of its own. Every other neighbour in the row does: the model
     * combo is a bordered control, and on the gear's other side the token
     * counters are followed by Send, whose real measured insets are
     * `[5, 17, 5, 17]` (a live [javax.swing.JButton] under the platform's
     * look and feel). A single [gap] next to a zero-margin control reads as
     * touching rather than a deliberate space, so this pairing gets one more
     * [gap] added, reusing the row's own spacing unit rather than a new
     * invented number.
     */
    private val settingsLeftGapBoost get() = gap

    override fun addLayoutComponent(name: String?, comp: java.awt.Component?) = Unit

    override fun removeLayoutComponent(comp: java.awt.Component?) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension {
        val parts = measure()
        val height = listOf(model, settings, counters, send)
            .maxOf { it.preferredSize.height }
        val width = parts.modelPreferred + parts.settings + parts.counters +
            parts.sendFull + gap * GAPS_WITH_COUNTERS + settingsLeftGapBoost
        val insets = parent.insets
        return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(parent: Container): Dimension {
        val preferred = preferredLayoutSize(parent)
        return Dimension(JBUI.scale(ComposerLayoutPolicy.MIN_PANEL_WIDTH), preferred.height)
    }

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val available = parent.width - insets.left - insets.right
        val height = parent.height - insets.top - insets.bottom
        val plan = ComposerLayoutPolicy.plan(available, measure())

        applySendPresentation(plan)
        counters.isVisible = plan.countersVisible

        // The applier is where the collapse floor is enforced, so that a
        // miscalculation upstream lands on MODEL_MIN_WIDTH instead of collapsing
        // the combo to nothing. It cannot be delegated to the combo's own
        // minimumSize: com.intellij.openapi.ui.ComboBox returns its PREFERRED
        // size from getMinimumSize() whatever is set on it, which would floor
        // the combo at its full width and defeat the collapse entirely.
        val modelWidth = plan.modelWidth.coerceAtLeast(JBUI.scale(ComposerLayoutPolicy.MODEL_MIN_WIDTH))

        var x = insets.left
        place(model, x, insets.top, modelWidth, height, insets.left)
        x += modelWidth + gap + settingsLeftGapBoost
        place(settings, x, insets.top, settings.preferredSize.width, height, insets.left)
        x += settings.preferredSize.width + gap

        // A cramped plan is one the row cannot honour: the parts already sum to
        // more than `available`, so right-aligning Send would place it left of
        // the parts before it and, at very small widths, left of the panel's own
        // insets (`insets.left + available - sendWidth` goes negative). Packing
        // Send directly after the parts instead keeps every origin inside the
        // panel and lets Send clip on the right, which is what "does not fit"
        // has to look like.
        val sendX = if (plan.cramped) x else insets.left + available - plan.sendWidth
        if (plan.countersVisible) {
            val countersWidth = (sendX - gap - x).coerceAtLeast(0)
            place(counters, x, insets.top, countersWidth, height, insets.left)
        }
        place(send, sendX, insets.top, plan.sendWidth, height, insets.left)
    }

    private fun applySendPresentation(plan: ComposerLayoutPolicy.Plan) {
        if (plan.sendCompact) {
            send.text = null
            send.icon = sendIcon
        } else {
            send.text = sendText
            send.icon = null
        }
        send.toolTipText = sendText
    }

    /**
     * Places one part, never left of [minX] and never at a negative size.
     *
     * Swing accepts a negative width or a negative x without complaint and
     * simply draws nothing, so a bad number upstream disappears silently. The
     * clamps here are what make that impossible.
     */
    private fun place(component: Component, x: Int, y: Int, width: Int, height: Int, minX: Int) {
        component.setBounds(
            x.coerceAtLeast(minX),
            y,
            width.coerceAtLeast(0),
            height.coerceAtLeast(0)
        )
    }

    /**
     * Measures the parts at their natural size. Send is measured in both
     * presentations by asking the button for each; the row is re-measured on
     * every layout pass so font and DPI changes are picked up for free.
     */
    private fun measure(): ComposerLayoutPolicy.Parts {
        val sendFull = textWidth(sendText) + JBUI.scale(SEND_TEXT_PADDING)
        val sendCompact = sendIcon.iconWidth + JBUI.scale(SEND_ICON_PADDING)
        return ComposerLayoutPolicy.Parts(
            modelPreferred = modelPreferredWidth(),
            modelMin = JBUI.scale(ComposerLayoutPolicy.MODEL_MIN_WIDTH),
            settings = settings.preferredSize.width,
            counters = counters.preferredSize.width,
            sendFull = sendFull,
            sendCompact = sendCompact,
            gap = gap
        )
    }

    /**
     * Width the model combo needs to show its selected id in full.
     *
     * Deliberately NOT `model.preferredSize.width`: BasicComboBoxUI derives that
     * from the renderer called with `index == -1`, which is exactly the branch
     * where MiddleEllipsisComboRenderer truncates the text to fit the combo's
     * CURRENT width. Reading it here would make the policy's input a function of
     * the policy's own previous output — and on the first pass, with the combo
     * still 0 wide, it measured an empty string and latched the combo shut for
     * good. Measuring the selected item's own text breaks that loop.
     *
     * Includes [MODEL_PREFERRED_SLACK] on top of the text width and chrome.
     * Without it, this number and the renderer's fitting budget
     * (`combo.width - COMBO_CHROME`) are exactly equal whenever the policy
     * hands the combo its full preferred width — a boundary with zero pixels
     * of margin, where the smallest disagreement between the two
     * measurements (a stale font mid-theme-change, a fractional-DPI rounding
     * difference) tips a name that should fit into showing an ellipsis. The
     * slack is a fixed few pixels of headroom, not a fudge factor tuned to
     * any one id: it makes the "full text" width a few pixels wider than
     * strictly necessary, so the renderer's `<=` check has room to spare.
     */
    private fun modelPreferredWidth(): Int {
        val text = model.selectedItem?.toString().orEmpty()
        val metrics = model.getFontMetrics(model.font)
        return metrics.stringWidth(text) +
            JBUI.scale(MiddleEllipsisComboRenderer.COMBO_CHROME) +
            JBUI.scale(MODEL_PREFERRED_SLACK)
    }

    private fun textWidth(text: String): Int =
        send.getFontMetrics(send.font).stringWidth(text)

    private companion object {
        const val GAP = 4
        const val GAPS_WITH_COUNTERS = 3
        const val SEND_TEXT_PADDING = 24
        const val SEND_ICON_PADDING = 16
        const val MODEL_PREFERRED_SLACK = 8
    }
}
