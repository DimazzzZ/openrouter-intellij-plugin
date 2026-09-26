package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The API key spend-cap section of the status tab: how much of a key's own limit has been used.
 *
 * This restores the one genuinely useful thing the old tab's "Quota" row was trying to show,
 * after Task 10's redesign separates it from the account balance ([BalanceBlock]) it used to sit
 * beside under a near-identical label. The two are different quantities - a per-key limit versus
 * the account's real credit balance - and must never read as the same thing again, which is why
 * [headingLabel] spells out "API key spend cap" rather than a bare "Limit" or "Quota".
 *
 * [update] decides nothing about WHETHER a cap exists - that is [KeyLimit]'s job, run over the
 * cached key list by the caller ([StatusTabPanel]). This class only renders the two numbers it is
 * given and gates its own visibility on `limit`. Hidden (not merely blank) whenever there is no
 * cap, because rendering a fabricated "$0.00 of $0.00" - what the old "Quota" row did for any key
 * without an explicit limit - is exactly the defect this block exists to remove.
 */
class KeyLimitBlock {

    private val headingLabel = JBLabel(HEADING_TEXT).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.emptyBottom(TIGHT_GAP)
    }

    private val amountLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    /** Hidden by default: no [update] call has happened yet, so there is nothing to report. */
    val component: JComponent = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        isVisible = false
        add(headingLabel)
        add(amountLabel)
    }

    /**
     * @param used spend counted against [limit] - meaningless, and not rendered, unless [limit]
     *   is a real positive cap
     * @param limit the API key(s)' spend cap, or `null`/non-positive when none of the enabled
     *   keys carries one - either hides the whole block rather than rendering a "$0.00" cap
     */
    fun update(used: Double, limit: Double?) {
        val cap = limit?.takeIf { it > 0.0 }
        component.isVisible = cap != null
        if (cap != null) {
            amountLabel.text = "$${formatAmount(used)} used of $${formatAmount(cap)} API key cap"
        }
    }

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val HEADING_TEXT = "API Key Spend Cap"
        const val TIGHT_GAP = 2
    }
}
