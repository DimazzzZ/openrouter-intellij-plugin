package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Decides whether the per-model breakdown row has room for a third column - the request count -
 * beside the model name and the spend figure.
 *
 * Deliberately free of Swing and IntelliJ imports, matching
 * [org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayoutPolicy] on the chat side of this
 * repo: keeping this pure is what lets the fast headless `test` Gradle task cover the resize
 * behaviour with ordinary unit tests, rather than needing a platform runner. All widths arrive
 * already device-scaled, exactly like that sibling policy - [BreakdownBlock] passes real component
 * widths and `JBUI.scale`-d constants through unchanged.
 *
 * No pixel threshold is hard-coded here: fonts and DPI vary machine to machine, so a magic width
 * like "show the column above 600px" would be right on one screen and wrong on another. Instead
 * this takes the MEASURED widths of the parts that actually have to coexist on the row and asks
 * whether they fit - [BreakdownBlock] is the one place that measures (font metrics, the widest
 * rendered count, the widest spend string) and this is the one place that does the arithmetic.
 *
 * The layout this checks: `[model][gap][requests][gap][spend]`, with spend pinned to the row's
 * right edge exactly where it already sits with the column absent - see [BreakdownBlock]'s own
 * KDoc for why that edge must never move. [spendWidth] is passed in even though the spend column
 * itself never truncates and so never NEEDS a fit check of its own; it is included in the sum
 * because room for the requests column can only ever come out of the model column's share of
 * [availableWidth], and that share has to account for every fixed-width neighbour on the row, spend
 * included, not just the new one.
 */
object BreakdownColumnPolicy {

    /**
     * Floor below which the model column is never squeezed just to make room for the requests
     * column, in unscaled pixels.
     *
     * This is a different number from [org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayoutPolicy.MODEL_MIN_WIDTH]
     * (160) on purpose: that floor budgets for a live `ComboBox`'s own chrome (arrow button,
     * border - [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer.COMBO_CHROME],
     * 32px) on top of the text. [BreakdownBlock]'s model column is a plain label with no such
     * chrome, so the same text budget buys more legible width per pixel here.
     *
     * Measured, not eyeballed: at this tab's font, `anthropic/claude-sonnet-4.5` (27 chars, a
     * representative model id - shorter than the 36-char
     * `anthropic/claude-3.5-sonnet-20241022`, longer than the 15-char `openrouter/auto`) renders
     * at roughly 170px, and this repo's own [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis]
     * keeps a head and a tail around one ellipsis glyph. 140px is enough room for a
     * middle-ellipsised fit of that id (`anthropic/…-4.5` measures under 100px) with real slack
     * left over, or to show a short id like `openrouter/auto` in full - comfortably above the
     * "unreadable stub" territory (a handful of characters either side of the ellipsis) a much
     * smaller floor would allow, while still leaving genuine room at wide widths for the requests
     * column this floor exists to gate.
     */
    const val MODEL_MIN_WIDTH = 140

    /** One [org.zhavoronkov.openrouter.toolwindow.status.BreakdownBlock] row gap, spent twice:
     * once between the model and requests columns, once between requests and spend. */
    private const val GAP_COUNT = 2

    /**
     * @param availableWidth the row's own current width - what [BreakdownBlock]'s `rowsPanel`
     *   actually measures at, re-evaluated on every resize.
     * @param modelMinWidth the model column's floor - see [MODEL_MIN_WIDTH]'s own KDoc for why
     *   this is a parameter rather than read directly: it keeps this function a pure arithmetic
     *   answer over its inputs, the same shape as
     *   [org.zhavoronkov.openrouter.toolwindow.composer.ComposerLayoutPolicy.plan].
     * @param requestsWidth the widest rendered request count currently shown, in scaled pixels -
     *   the requests column must fit ALL rows' counts, not merely the one being laid out, since
     *   every row in [BreakdownBlock] either has the column or does not; there is no per-row
     *   answer.
     * @param spendWidth the widest rendered spend figure currently shown, in scaled pixels - see
     *   this object's own class KDoc for why the never-truncating spend column is still counted
     *   here even though it never needs to be checked for its own fit.
     * @param gap the row's own single gap unit, in scaled pixels - spent [GAP_COUNT] times, once
     *   on each side of the requests column.
     * @return `true` when [availableWidth] leaves the model column at least [modelMinWidth] once
     *   the requests column, the spend column, and both gaps are accounted for.
     */
    fun showsRequestsColumn(
        availableWidth: Int,
        modelMinWidth: Int,
        requestsWidth: Int,
        spendWidth: Int,
        gap: Int
    ): Boolean {
        val requiredWidth = modelMinWidth + requestsWidth + spendWidth + gap * GAP_COUNT
        return availableWidth >= requiredWidth
    }
}
