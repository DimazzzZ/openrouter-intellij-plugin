package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * The per-model spend breakdown: a period selector (24 hours / 7 days / 30
 * days, defaulting to 24 hours) above a two-column list of model name and
 * spend for that STATED period.
 *
 * This answers the other question the status tab exists for - where the
 * money actually goes - which the old "Recent Activity" total could not
 * answer even in principle, since it summed whatever window the activity
 * endpoint happened to return and never said what that window was. Naming
 * the period is not decoration here; it is the fix.
 *
 * Owns no fetching of its own: [onPeriodChanged] fires when the user picks a
 * different period, and the caller ([StatusTabPanel]) decides - depending on
 * whether an [org.zhavoronkov.openrouter.services.AnalyticsService] is
 * available - whether that means a fresh analytics query or a re-run of
 * [ActivityAggregator.byModel] against the cached activity it already has.
 * The two paths are NOT interchangeable on failure: [showError] exists
 * because an analytics query error must never be quietly answered with the
 * degraded (no-key) path's numbers under the same-looking list - that would
 * let two users asking "how much on this model this week" see different
 * figures with no visible sign anything was substituted (see the status tab
 * redesign spec's D12). [show], [showError], [showLoading] and [showUnavailable]
 * are the only ways this block is ever updated - each states a different fact
 * ("here are the rows", "the query failed", "nothing has been queried yet",
 * "nothing CAN be queried") and none of them may stand in for another.
 *
 * Two columns only - model name (left, middle-ellipsised via [MiddleEllipsis]
 * when it does not fit - see [ModelNameLabel]) and spend (right, NEVER
 * truncated: the number is the answer). At the roughly 280px this tab
 * affords, a model id already wants about 150px, so a third column has
 * nowhere honest to go.
 */
class BreakdownBlock {

    /** Fired once per user-driven period change, never for the initial default selection. */
    var onPeriodChanged: (ActivityAggregator.Period) -> Unit = {}

    private val periodSelector = ComboBox(PERIODS).apply {
        renderer = object : SimpleListCellRenderer<ActivityAggregator.Period>() {
            override fun customize(
                list: JList<out ActivityAggregator.Period>,
                value: ActivityAggregator.Period?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                text = value?.let(::periodLabel).orEmpty()
            }
        }
    }

    private val selectorRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(periodSelector)
    }

    /** Shown above [rowsPanel] only when the last successful query reported truncation. */
    private val truncatedLabel = JBLabel(TRUNCATED_TEXT).apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        isVisible = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(ROW_VERTICAL_GAP)
    }

    private val rowsPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val listPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(truncatedLabel, BorderLayout.NORTH)
        add(rowsPanel, BorderLayout.CENTER)
    }

    val component: JComponent = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(selectorRow, BorderLayout.NORTH)
        add(listPanel, BorderLayout.CENTER)
    }

    init {
        // ComboBox's own default selection is PERIODS[0] (DAY / "24 hours"), which satisfies
        // "defaulting to 24 hours" without an explicit setSelectedItem call here - one that
        // would otherwise risk firing onPeriodChanged for a selection nobody chose. The listener
        // is attached only AFTER that default is already in place.
        periodSelector.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                onPeriodChanged(event.item as ActivityAggregator.Period)
            }
        }
        show(emptyList())
    }

    /**
     * Renders [rows] as the two-column list, or - when [rows] is empty - an explicit
     * "no activity in this period" line. Never an empty box: an empty box is
     * indistinguishable from a broken panel, which is exactly the ambiguity a stated period
     * exists to remove.
     *
     * @param truncated whether the query that produced [rows] reported
     *   `AnalyticsQueryPayload.metadata.truncated == true` - i.e. the server had more rows than
     *   it returned. A short list must not silently pass itself off as the whole answer, so this
     *   renders an explicit notice rather than being discarded, as it previously was.
     */
    fun show(rows: List<ActivityAggregator.ModelSpend>, truncated: Boolean = false) {
        truncatedLabel.isVisible = truncated

        rowsPanel.removeAll()
        if (rows.isEmpty()) {
            rowsPanel.add(JBLabel(NO_ACTIVITY_TEXT), emptyStateConstraints())
        } else {
            rows.forEachIndexed { index, spend -> addRow(spend, index) }
        }

        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    /**
     * Renders an explicit "couldn't load" line for an analytics query failure.
     *
     * This is deliberately NOT [show] with an empty list (that means "queried successfully, no
     * activity") and deliberately does not fall back to rendering the degraded (no-key) path's
     * numbers under the same list shape - either would let a transient failure quietly present
     * as a real, if boring, answer.
     */
    fun showError() = renderPlaceholder(ERROR_TEXT)

    /**
     * Renders an explicit "loading" line (fix round 1, finding 2). Deliberately NOT [show] with an
     * empty list: nothing has been queried yet, so "no activity in this period" would claim an
     * answer that has not arrived - the same defect class as [showUnavailable] below.
     */
    fun showLoading() = renderPlaceholder(LOADING_TEXT)

    /**
     * Renders an explicit "needs a provisioning key" line for DEGRADED (fix round 1, finding 2).
     *
     * Deliberately NOT [show] with an empty list: DEGRADED never queries anything - there is no
     * analytics endpoint reachable without a provisioning key at all - so "no activity in this
     * period" would tell the user "you spent nothing", which nobody checked. Also deliberately NOT
     * [showError]: no query was attempted, so nothing failed either. Conflating any of these three
     * lets the tab present a claim nobody verified as if it had been - exactly the defect class
     * this whole plan exists to remove.
     */
    fun showUnavailable() = renderPlaceholder(UNAVAILABLE_TEXT)

    private fun renderPlaceholder(text: String) {
        truncatedLabel.isVisible = false

        rowsPanel.removeAll()
        rowsPanel.add(JBLabel(text), emptyStateConstraints())

        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun emptyStateConstraints() = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(ROW_VERTICAL_GAP, 0)
    }

    private fun addRow(spend: ActivityAggregator.ModelSpend, rowIndex: Int) {
        val modelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = rowIndex
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(ROW_VERTICAL_GAP, 0, ROW_VERTICAL_GAP, ROW_GAP_BETWEEN_COLUMNS)
        }
        rowsPanel.add(ModelNameLabel(spend.model), modelConstraints)

        val spendConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = rowIndex
            weightx = 0.0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.EAST
            insets = JBUI.insets(ROW_VERTICAL_GAP, 0)
        }
        rowsPanel.add(JBLabel(formatSpend(spend.usage)), spendConstraints)
    }

    private fun formatSpend(usage: Double): String = "$${String.format(Locale.US, "%.4f", usage)}"

    private fun periodLabel(period: ActivityAggregator.Period): String = when (period) {
        ActivityAggregator.Period.DAY -> "24 hours"
        ActivityAggregator.Period.WEEK -> "7 days"
        ActivityAggregator.Period.MONTH -> "30 days"
    }

    /**
     * A [JBLabel] that keeps [fullText] as its underlying identity (the tooltip, and what is
     * reported if never laid out) but DISPLAYS a middle-ellipsised fit to whatever width the
     * layout manager actually allocates it, recomputed on every [setBounds].
     *
     * A plain [JBLabel]'s own built-in end-clipping was rejected: OpenRouter model ids share
     * their vendor prefix and differ at the tail
     * (`anthropic/claude-sonnet-4.5` vs `anthropic/claude-opus-4.5`), so end-clipping both to
     * `anthropic/claude-...` deletes exactly the part that distinguishes them, while
     * [MiddleEllipsis] keeps head AND tail. Measuring at layout time rather than at [show] time
     * mirrors [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer], which
     * solves the identical "no width is known until the layout manager assigns one" problem for
     * the model combo's closed cell.
     *
     * Computing the fitted string into an actual `text` value - rather than relying on paint-time
     * clipping, which exposes nothing a test can read - is also what makes the truncation
     * assertable at all.
     */
    private class ModelNameLabel(private val fullText: String) : JBLabel(fullText) {
        init {
            toolTipText = fullText
        }

        override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
            super.setBounds(x, y, width, height)
            if (width <= 0) return
            val fitted = MiddleEllipsis.fit(fullText, width, getFontMetrics(font)::stringWidth)
            if (fitted != text) text = fitted
        }
    }

    private companion object {
        val PERIODS = arrayOf(
            ActivityAggregator.Period.DAY,
            ActivityAggregator.Period.WEEK,
            ActivityAggregator.Period.MONTH
        )
        const val NO_ACTIVITY_TEXT = "No activity in this period"
        const val ERROR_TEXT = "Couldn't load the breakdown"
        const val LOADING_TEXT = "Loading breakdown..."
        const val UNAVAILABLE_TEXT = "Needs a provisioning key"
        const val TRUNCATED_TEXT = "Showing a partial result - the server truncated this query"
        const val ROW_VERTICAL_GAP = 2
        const val ROW_GAP_BETWEEN_COLUMNS = 12
    }
}
