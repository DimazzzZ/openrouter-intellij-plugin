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
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ItemEvent
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * The spend breakdown: a period selector (24 hours / 7 days / 30 days,
 * defaulting to 24 hours) and a dimension selector (by model / by key - G6,
 * defaulting to by model) above a list of a name, an optional request count,
 * and spend for that STATED period.
 *
 * This answers the other question the status tab exists for - where the
 * money actually goes - which the old "Recent Activity" total could not
 * answer even in principle, since it summed whatever window the activity
 * endpoint happened to return and never said what that window was. Naming
 * the period is not decoration here; it is the fix.
 *
 * G6 (which API key spent it) is a DIMENSION TOGGLE on this same block, not a
 * fifth one: the tab already carries four other blocks against a 280px floor,
 * so a second per-key panel would crowd a view most users want only
 * occasionally, and a toggle shows exactly one dimension at a time anyway -
 * matching the one query [StatusTabPanel] ever issues for this list, never
 * both. See [onDimensionChanged] and [setDimensionSelectorEnabled], the exact
 * counterparts of [onPeriodChanged]/[setPeriodSelectorEnabled] for this axis.
 *
 * Owns no fetching of its own: [onPeriodChanged]/[onDimensionChanged] fire
 * when the user picks a different period/dimension, and the caller
 * ([StatusTabPanel]) decides - depending on
 * whether an [org.zhavoronkov.openrouter.services.AnalyticsService] is
 * available - whether that means a fresh analytics query or (MODEL only; see
 * [StatusTabPanel]'s own handling of KEY without an analytics key) a re-run of
 * [ActivityAggregator.byModel] against the cached activity it already has.
 * The two paths are NOT interchangeable on failure: [showError] exists
 * because an analytics query error must never be quietly answered with the
 * degraded (no-key) path's numbers under the same-looking list - that would
 * let two users asking "how much on this model this week" see different
 * figures with no visible sign anything was substituted (see the status tab
 * redesign spec's D12). [show], [showError], [showLoading], [showUnavailable],
 * [showNotConfigured] and [showMissingNames] are the only ways this block is ever updated - each
 * states a different fact ("here are the rows", "the query failed", "nothing
 * has been queried yet", "nothing CAN be queried without a management key",
 * "nothing CAN be queried because there is no API key at all", "the server no
 * longer has a name this build relies on") and none of them may stand in for another.
 *
 * Two OR three columns - model name (left, middle-ellipsised via [MiddleEllipsis]
 * when it does not fit - see [ModelNameLabel]), an OPTIONAL request count, and
 * spend (right, NEVER truncated: the number is the answer, pinned to the same
 * right edge regardless of whether the middle column is present). Which shape
 * applies is [BreakdownColumnPolicy]'s call, not a hard-coded width: this was
 * shipped once as a tooltip-only count (see [BreakdownRowTooltip]) on the
 * reasoning that the roughly 280px this tab's floor affords is the tab's
 * design width - it is only the FLOOR. [BreakdownColumnPolicy.MODEL_MIN_WIDTH]
 * is the floor below which the model column is never squeezed just to fit the
 * requests column; below that width the column drops and the count is
 * available only via the tooltip, which is why the tooltip carries it in
 * EVERY case, column or no column (see [BreakdownRowTooltip] and
 * [addRow]'s own KDoc for why the tooltip is not narrowed to "column absent"
 * only).
 */
class BreakdownBlock {

    /** Fired once per user-driven period change, never for the initial default selection. */
    var onPeriodChanged: (ActivityAggregator.Period) -> Unit = {}

    /**
     * Fired once per user-driven dimension change, never for the initial default selection - the
     * G6 counterpart of [onPeriodChanged]. The caller ([StatusTabPanel]) decides what re-querying
     * a dimension change means, exactly as it already does for a period change: this block owns
     * no fetching of its own either way.
     */
    var onDimensionChanged: (AnalyticsBreakdown.Dimension) -> Unit = {}

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

    /**
     * G6: which column the list groups by - [AnalyticsBreakdown.Dimension.MODEL] (this combo's
     * own default selection, matching [BreakdownBlock]'s existing "by model" behaviour) or
     * [AnalyticsBreakdown.Dimension.KEY]. A dimension TOGGLE beside the existing period selector,
     * not a fifth block: the tab already carries five blocks against a 280px floor, and a second
     * per-key panel would crowd a view most users want only occasionally, while a toggle costs no
     * extra vertical space and still shows exactly one dimension - the one currently selected -
     * at a time, matching the one query [StatusTabPanel] ever issues for this list.
     */
    private val dimensionSelector = ComboBox(DIMENSIONS).apply {
        renderer = object : SimpleListCellRenderer<AnalyticsBreakdown.Dimension>() {
            override fun customize(
                list: JList<out AnalyticsBreakdown.Dimension>,
                value: AnalyticsBreakdown.Dimension?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                text = value?.let(::dimensionLabel).orEmpty()
            }
        }
    }

    private val selectorRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(ROW_GAP_BETWEEN_COLUMNS), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(periodSelector)
        add(dimensionSelector)
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

    /**
     * The rows [show] last rendered, kept so [renderRows] can be replayed on a resize without the
     * caller having to call [show] again. Reset to empty by every placeholder (`showX()` below),
     * not just by an empty [show] result, so a resize arriving while a placeholder - which has no
     * requests/spend columns to re-decide at all - is on screen is a no-op rather than replaying a
     * stale row set from before the state changed.
     */
    private var lastRows: List<ActivityAggregator.ModelSpend> = emptyList()

    init {
        // A resize, not a viewport/scroll listener: `rowsPanel`'s own bounds only change when the
        // tool window is actually resized (Swing fires `componentResized` exactly then, never on a
        // scroll tick), which is the one signal `BreakdownColumnPolicy`'s answer can change on. A
        // scroll/viewport-change listener was tried and rejected once already on the chat side of
        // this repo for firing on every scroll tick and recomputing needlessly - see
        // `ChatConversationView`'s own resize wiring for the precedent this follows instead.
        rowsPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (lastRows.isNotEmpty()) renderRows()
            }
        })
        // ComboBox's own default selection is PERIODS[0] (DAY / "24 hours"), which satisfies
        // "defaulting to 24 hours" without an explicit setSelectedItem call here - one that
        // would otherwise risk firing onPeriodChanged for a selection nobody chose. The listener
        // is attached only AFTER that default is already in place.
        periodSelector.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                onPeriodChanged(event.item as ActivityAggregator.Period)
            }
        }
        // Same "default is DIMENSIONS[0] (MODEL), listener attached only after" shape as
        // periodSelector above - "the default is still by model" must hold without an explicit
        // setSelectedItem call here, which would otherwise risk firing onDimensionChanged for a
        // selection nobody chose.
        dimensionSelector.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                onDimensionChanged(event.item as AnalyticsBreakdown.Dimension)
            }
        }
        show(emptyList())
    }

    /**
     * Enables or disables the period selector - one of the two controls on this block a user can
     * interact with, the other being [setDimensionSelectorEnabled]. Disabled by the caller
     * ([StatusTabPanel.render], close-out round 2, Critical)
     * in every state where a period change cannot answer anything (NOT_CONFIGURED, DEGRADED,
     * LOADING): a control the user can click but that silently does nothing (or, before that
     * round's fix, silently substituted a wrong answer) is confusing in a way a greyed-out one
     * is not. Swing's own `JComboBox.isEnabled = false` already blocks user interaction and greys
     * the control out with no extra styling needed here, and does not prevent a TEST from still
     * driving it programmatically via `combo.selectedItem = ...` - only real mouse/keyboard input
     * is blocked, which is exactly what should differ between a test and a user.
     */
    fun setPeriodSelectorEnabled(enabled: Boolean) {
        periodSelector.isEnabled = enabled
    }

    /**
     * Enables or disables the dimension selector (G6) - gated by the exact same condition as
     * [setPeriodSelectorEnabled], and always called alongside it by [StatusTabPanel.render]: a
     * state where a period change cannot answer anything cannot answer a dimension change either,
     * KEY least of all - see [StatusTabPanel]'s own handling of KEY without an analytics key for
     * why that dimension is management-only even more strictly than MODEL.
     */
    fun setDimensionSelectorEnabled(enabled: Boolean) {
        dimensionSelector.isEnabled = enabled
    }

    /**
     * Renders [rows] as the list, or - when [rows] is empty - an explicit "no activity in this
     * period" line. Never an empty box: an empty box is indistinguishable from a broken panel,
     * which is exactly the ambiguity a stated period exists to remove.
     *
     * @param truncated whether the query that produced [rows] reported
     *   `AnalyticsQueryPayload.metadata.truncated == true` - i.e. the server had more rows than
     *   it returned. A short list must not silently pass itself off as the whole answer, so this
     *   renders an explicit notice rather than being discarded, as it previously was.
     */
    fun show(rows: List<ActivityAggregator.ModelSpend>, truncated: Boolean = false) {
        lastRows = rows
        truncatedLabel.isVisible = truncated
        renderRows()
    }

    /**
     * (Re)renders [lastRows] into [rowsPanel] - the body [show] runs, and the body the resize
     * listener above replays when [lastRows] is non-empty. Split out from [show] so a resize can
     * redo exactly this work without touching [truncatedLabel] (whose visibility depends only on
     * the last query's own `truncated` flag, never on width) or reassigning [lastRows] itself.
     */
    private fun renderRows() {
        rowsPanel.removeAll()
        if (lastRows.isEmpty()) {
            rowsPanel.add(JBLabel(NO_ACTIVITY_TEXT), emptyStateConstraints())
        } else {
            val showRequestsColumn = showsRequestsColumn(lastRows)
            lastRows.forEachIndexed { index, spend -> addRow(spend, index, showRequestsColumn) }
        }

        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    /**
     * Measures this row set's own widest request count and widest spend figure, then asks
     * [BreakdownColumnPolicy] whether the requests column fits beside them at [rowsPanel]'s
     * CURRENT width - re-measured on every call (including every resize), never cached, since
     * both the available width and (on a period change) the rows themselves can change out from
     * under a stale answer.
     *
     * One decision for the WHOLE row set, not per row: [rowsPanel] is one `GridBagLayout` grid, so
     * either every row gets a requests column or none do - there is no per-row answer to give.
     * Measuring the widest of each rather than each row's own keeps that one decision honest: a
     * requests column sized to a SHORT row's own count would clip a longer row's count sitting in
     * the same column.
     */
    private fun showsRequestsColumn(rows: List<ActivityAggregator.ModelSpend>): Boolean {
        val metrics = rowsPanel.getFontMetrics(rowsPanel.font)
        return BreakdownColumnPolicy.showsRequestsColumn(
            availableWidth = rowsPanel.width,
            modelMinWidth = JBUI.scale(BreakdownColumnPolicy.MODEL_MIN_WIDTH),
            requestsWidth = rows.maxOf { metrics.stringWidth(formatCount(it.requests)) },
            spendWidth = rows.maxOf { metrics.stringWidth(formatSpend(it.usage)) },
            gap = JBUI.scale(ROW_GAP_BETWEEN_COLUMNS)
        )
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
     * Renders an explicit "needs a Management Key" line for DEGRADED (fix round 1, finding 2).
     *
     * Deliberately NOT [show] with an empty list: DEGRADED never queries anything - there is no
     * analytics endpoint reachable without a management key at all - so "no activity in this
     * period" would tell the user "you spent nothing", which nobody checked. Also deliberately NOT
     * [showError]: no query was attempted, so nothing failed either. Conflating any of these three
     * lets the tab present a claim nobody verified as if it had been - exactly the defect class
     * this whole plan exists to remove.
     */
    fun showUnavailable() = renderPlaceholder(UNAVAILABLE_TEXT)

    /**
     * Renders an explicit "needs an API key" line for NOT_CONFIGURED.
     *
     * Deliberately NOT [showUnavailable]: that line names a missing MANAGEMENT key, which
     * implies an API key is already present - true in DEGRADED, false here. NOT_CONFIGURED has
     * no API key at all, so naming the management key specifically would send the user to fix
     * the wrong thing first. Also deliberately NOT [show] with an empty list, for the same reason
     * [showUnavailable] is not: nothing has been queried, so "no activity in this period" would
     * tell the user they spent nothing, which nobody checked.
     */
    fun showNotConfigured() = renderPlaceholder(NOT_CONFIGURED_TEXT)

    /**
     * Renders an explicit line naming exactly which hard-coded metric/dimension/granularity
     * name(s) [AnalyticsMetaCheck] found missing from the server's own `/analytics/meta`
     * response, for [G2][AnalyticsMetaCheck]'s "validated and something is missing" state - the
     * ONLY one of its three states allowed to change what this block shows (see that object's own
     * KDoc; "validated and fine" and "could not check" both leave the ordinary query/[show]/
     * [showError] path untouched).
     *
     * Deliberately NOT [showError]: nothing failed to reach the server - it answered, and the
     * answer is that one of our own assumptions is now wrong. Deliberately NOT [show] with an
     * empty list: an empty list here would be [AnalyticsBreakdown]'s query silently coming back
     * with no rows because it asked for a name the server no longer has - exactly the "renders a
     * breakdown that silently came back empty" failure this check exists to replace with a named
     * cause. The actionable read is "this plugin build is out of date against the API", not "wait"
     * ([showLoading]) or "add/fix a key" ([showUnavailable]/[showNotConfigured]) - a different
     * fact from every other placeholder, so it gets its own line rather than reusing one of theirs.
     */
    fun showMissingNames(missingNames: List<String>) =
        renderPlaceholder("$MISSING_NAMES_TEXT${missingNames.joinToString(", ")}")

    private fun renderPlaceholder(text: String) {
        // Reset, not merely left stale: a resize arriving while a placeholder is on screen must
        // be a no-op (see the resize listener's own KDoc above) - not a replay of whatever rows
        // were on screen before the state changed to this placeholder.
        lastRows = emptyList()
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

    private fun addRow(spend: ActivityAggregator.ModelSpend, rowIndex: Int, showRequestsColumn: Boolean) {
        // Every label on the row shares ONE tooltip - see [ModelNameLabel]'s own KDoc for why this
        // extends its existing tooltip rather than adding a second one, and [BreakdownRowTooltip]
        // for the wording. This does NOT change when the requests count is also visible as its own
        // column: repeating it in the tooltip is not obviously wrong (the model id can still be
        // middle-ellipsised regardless of whether the column fits, so the row must stay readable
        // by hover in every width, column or no column), and a tooltip that read differently
        // depending on the column's own presence would be one more state for a reader to track for
        // no benefit - the count is a single word restated, not new information invented for the
        // hover case.
        val tooltip = BreakdownRowTooltip.forRow(spend.model, spend.requests)

        val modelConstraints = GridBagConstraints().apply {
            gridx = MODEL_COLUMN
            gridy = rowIndex
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(ROW_VERTICAL_GAP, 0, ROW_VERTICAL_GAP, ROW_GAP_BETWEEN_COLUMNS)
        }
        rowsPanel.add(ModelNameLabel(spend.model, tooltip), modelConstraints)

        // The requests column sits BETWEEN model and spend, never after it: spend stays pinned to
        // the row's own right edge - the same column index it already occupies with this column
        // absent - so the existing visual does not shift when this column is absent (see this
        // class's own KDoc for why that edge must not move).
        if (showRequestsColumn) {
            val requestsConstraints = GridBagConstraints().apply {
                gridx = REQUESTS_COLUMN
                gridy = rowIndex
                weightx = 0.0
                fill = GridBagConstraints.NONE
                anchor = GridBagConstraints.EAST
                insets = JBUI.insets(ROW_VERTICAL_GAP, 0, ROW_VERTICAL_GAP, ROW_GAP_BETWEEN_COLUMNS)
            }
            rowsPanel.add(JBLabel(formatCount(spend.requests)).apply { toolTipText = tooltip }, requestsConstraints)
        }

        val spendConstraints = GridBagConstraints().apply {
            gridx = if (showRequestsColumn) SPEND_COLUMN_WITH_REQUESTS else SPEND_COLUMN_WITHOUT_REQUESTS
            gridy = rowIndex
            weightx = 0.0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.EAST
            insets = JBUI.insets(ROW_VERTICAL_GAP, 0)
        }
        rowsPanel.add(JBLabel(formatSpend(spend.usage)).apply { toolTipText = tooltip }, spendConstraints)
    }

    private fun formatSpend(usage: Double): String = "$${String.format(Locale.US, "%.4f", usage)}"

    /** Plain, unformatted - matching [BreakdownRowTooltip]'s own bare `$requests`, so the column
     * and the tooltip never disagree about how the same count is written (e.g. one adding
     * thousands separators the other omits). */
    private fun formatCount(requests: Long): String = requests.toString()

    private fun periodLabel(period: ActivityAggregator.Period): String = when (period) {
        ActivityAggregator.Period.DAY -> "24 hours"
        ActivityAggregator.Period.WEEK -> "7 days"
        ActivityAggregator.Period.MONTH -> "30 days"
    }

    private fun dimensionLabel(dimension: AnalyticsBreakdown.Dimension): String = when (dimension) {
        AnalyticsBreakdown.Dimension.MODEL -> "By model"
        AnalyticsBreakdown.Dimension.KEY -> "By key"
    }

    /**
     * A [JBLabel] that keeps [fullText] as its underlying identity (what is reported if never
     * laid out) but DISPLAYS a middle-ellipsised fit to whatever width the layout manager
     * actually allocates it, recomputed on every [setBounds]. Its `toolTipText` is [tooltip], not
     * [fullText] directly - see [BreakdownRowTooltip] - but [tooltip] itself always starts with
     * [fullText] verbatim, so the full id this tooltip originally existed to preserve is still
     * recoverable from it.
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
    private class ModelNameLabel(private val fullText: String, tooltip: String) : JBLabel(fullText) {
        init {
            toolTipText = tooltip
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

        /** MODEL first - the ComboBox's own default selection - so "the default is still by
         * model" holds without an explicit setSelectedItem call, same as [PERIODS] above. */
        val DIMENSIONS = arrayOf(
            AnalyticsBreakdown.Dimension.MODEL,
            AnalyticsBreakdown.Dimension.KEY
        )
        const val NO_ACTIVITY_TEXT = "No activity in this period"
        const val ERROR_TEXT = "Couldn't load the breakdown"
        const val LOADING_TEXT = "Loading breakdown..."
        const val UNAVAILABLE_TEXT = "Needs a Management Key"
        const val NOT_CONFIGURED_TEXT = "Needs an API key"
        const val MISSING_NAMES_TEXT = "Plugin is out of date with the API - missing: "
        const val TRUNCATED_TEXT = "Showing a partial result - the server truncated this query"
        const val ROW_VERTICAL_GAP = 2
        const val ROW_GAP_BETWEEN_COLUMNS = 12

        // GridBagLayout column indices. The requests column sits strictly BETWEEN model and
        // spend, never after it, so spend's own index shifts by exactly one (1 -> 2) depending on
        // whether the requests column is present - never a fixed, always-last index of its own.
        const val MODEL_COLUMN = 0
        const val REQUESTS_COLUMN = 1
        const val SPEND_COLUMN_WITH_REQUESTS = 2
        const val SPEND_COLUMN_WITHOUT_REQUESTS = 1
    }
}
