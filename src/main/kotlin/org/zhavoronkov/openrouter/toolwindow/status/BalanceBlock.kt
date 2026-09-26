package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Font
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The account-balance section of the status tab: remaining credits, the
 * total they are drawn from, the recent burn rate, an optional days-left
 * estimate, a spend sparkline and a last-updated line.
 *
 * [update] renders exactly what it is given and computes nothing beyond
 * simple string formatting and the one days-left division. It does not
 * decide staleness, degraded-mode wording, or what the series actually
 * measures - `seriesLabel` states that, this class only displays it - all
 * of which belongs to Task 11.
 *
 * A `null` `remaining`, a zero or absent `total`, or a `null` `perDay`, is a
 * genuinely unknown quantity, not zero: each renders as [NO_VALUE], and for
 * the days-left row - which would otherwise divide by an unknown or zero
 * rate - the whole row is hidden. Never a computed number that merely looks
 * plausible, which is the "Infinity%" defect [StatusTabPanel.renderCredits]
 * used to have and the reason this whole redesign exists. `remaining` is
 * nullable for exactly this reason: before the shared cache has any data -
 * loading, or not configured - there is no balance to report, and a
 * non-nullable `Double` would have forced every such caller to invent one
 * (fix round 1 caught "$0.00 remaining" rendered during both those states).
 */
class BalanceBlock {

    private val remainingLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        font = font.deriveFont(Font.BOLD, REMAINING_FONT_SIZE)
        border = JBUI.Borders.emptyBottom(TIGHT_GAP)
    }

    private val totalLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(ROW_GAP)
    }

    private val burnRateLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(TIGHT_GAP)
    }

    private val daysLeftLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(ROW_GAP)
    }

    private val seriesCaptionLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.emptyBottom(TIGHT_GAP)
    }

    private val sparklineView = SparklineView()

    /** [sparklineView]'s component, gated on the same condition as [seriesCaptionLabel] so an
     * empty/absent series does not still reserve a blank fixed-size box with no caption above it. */
    private val sparklineComponent = sparklineView.component.apply { alignmentX = Component.LEFT_ALIGNMENT }

    private val lastUpdatedLabel = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        border = JBUI.Borders.emptyTop(ROW_GAP)
    }

    val component: JComponent = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        add(remainingLabel)
        add(totalLabel)
        add(burnRateLabel)
        add(daysLeftLabel)
        add(seriesCaptionLabel)
        add(sparklineComponent)
        add(lastUpdatedLabel)
    }

    /**
     * Renders the block from scratch - see the class KDoc for the
     * "unknown, not zero" rule applied to [total] and [perDay].
     *
     * @param remaining account credits left, `total - usage`, or null when there is no cached
     *   balance yet (loading, or not configured) - never fabricated as `0.0`
     * @param total the balance's denominator; zero or absent renders [NO_VALUE]
     *   instead of a share computed against an unknown or missing limit
     * @param perDay the recent burn rate, or null when it cannot be computed
     * @param series the spend sparkline's data, oldest first
     * @param seriesLabel what [series] measures - a different quantity in
     *   degraded mode (Task 11), so it is passed in rather than fixed here
     * @param lastUpdatedText pre-formatted staleness text produced elsewhere
     *   (Task 11 owns that); blank or empty hides the row rather than
     *   rendering an empty line
     */
    fun update(
        remaining: Double?,
        total: Double,
        perDay: Double?,
        series: List<Double>,
        seriesLabel: String,
        lastUpdatedText: String
    ) {
        // Explicitly re-shown every call, not just given text: showLocalSeriesOnly() (DEGRADED)
        // hides these three, and a later transition back to a state that calls update() (e.g.
        // DEGRADED -> READY once a provisioning key is added) must undo that, not leave them
        // stuck hidden.
        remainingLabel.isVisible = true
        totalLabel.isVisible = true
        burnRateLabel.isVisible = true

        remainingLabel.text = remaining?.let { "$${formatAmount(it)} remaining" } ?: NO_VALUE
        totalLabel.text = if (total > 0.0) "of $${formatAmount(total)} total" else NO_VALUE
        burnRateLabel.text = perDay?.let { "$${formatAmount(it)}/day burn rate" } ?: NO_VALUE

        // Both an unknown remaining and an unknown/non-positive rate make the division
        // meaningless, so either one hides this row rather than rendering it against a guess.
        val daysLeft = remaining?.let { r -> perDay?.takeIf { it > 0.0 }?.let { r / it } }
        daysLeftLabel.isVisible = daysLeft != null
        daysLeftLabel.text = daysLeft?.let { "${formatDays(it)} days left" }.orEmpty()

        seriesCaptionLabel.isVisible = seriesLabel.isNotBlank()
        sparklineComponent.isVisible = seriesLabel.isNotBlank()
        seriesCaptionLabel.text = seriesLabel
        sparklineView.values = series

        lastUpdatedLabel.isVisible = lastUpdatedText.isNotBlank()
        lastUpdatedLabel.text = lastUpdatedText
    }

    /**
     * DEGRADED's rendering for the moment there is genuinely no balance to show yet - originally
     * (fix round 1, finding 1) this was DEGRADED's ENTIRE rendering, on the spec's D6 claim of a
     * fallback to `/credits`/`/activity` without a management key. Measured against the live API
     * (2026-09-21, correction C1's amendment), that claim was wrong for `/credits`: it answers for
     * an ordinary API key too, so [StatusTabPanel] now calls [update] with the real balance once
     * the shared cache has credits, and reserves this method for the narrower case that remains -
     * mid-refresh, or a refresh that failed, with no credits cached yet. There, [update]'s
     * "unknown, not zero" [NO_VALUE] em dash would still be the wrong rendering: a dash in the
     * headline row reads "your balance is unknown, permanently", when the true fact is "not loaded
     * yet". So this HIDES the remaining/total/burn-rate/days-left/last-updated rows entirely rather
     * than rendering them as unknown, and shows only what genuinely exists either way: the locally
     * observed spend sparkline (and its caption, gated exactly like [update]'s).
     */
    fun showLocalSeriesOnly(series: List<Double>, seriesLabel: String) {
        remainingLabel.isVisible = false
        totalLabel.isVisible = false
        burnRateLabel.isVisible = false
        daysLeftLabel.isVisible = false
        lastUpdatedLabel.isVisible = false

        seriesCaptionLabel.isVisible = seriesLabel.isNotBlank()
        sparklineComponent.isVisible = seriesLabel.isNotBlank()
        seriesCaptionLabel.text = seriesLabel
        sparklineView.values = series
    }

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatDays(value: Double): String = String.format(Locale.US, "%.0f", value)

    private companion object {
        const val NO_VALUE = "—" // em dash: rendered when a figure has no known value
        const val REMAINING_FONT_SIZE = 18f
        const val TIGHT_GAP = 2
        const val ROW_GAP = 8
    }
}
