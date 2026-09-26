package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Bounds the status line's text to a fixed character budget, so a server-supplied sentence of
 * arbitrary length degrades VISIBLY - an ellipsis at the tail - rather than running silently past
 * the panel's right edge with nothing to tell the user text is missing (the screenshot this fix
 * responds to showed a 403 body cut off mid-JSON, with no sign anything had been cropped at all).
 *
 * Deliberately free of Swing/AWT/IntelliJ imports - see [BreakdownColumnPolicy]'s own KDoc for
 * why: it keeps this coverable by the fast headless `test` task as an ordinary unit test, and it
 * is NOT excluded from Kover for the same reason.
 *
 * Truncates from the TAIL, not the middle (contrast
 * [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis], which preserves both a model
 * id's head AND tail because an id's distinguishing characters sit at the tail as much as the
 * head). A status sentence puts its meaning at the FRONT - "Couldn't refresh: ..." - so keeping
 * the head and dropping the tail is what stays useful once shortened; [StatusTabPanel] puts the
 * untruncated original in the label's own tooltip, so nothing said by the server is actually
 * discarded, only deferred to a hover.
 */
object StatusLineText {
    /**
     * Characters, not pixels: an exact pixel budget would need real font metrics, which this pure
     * module deliberately has no access to (see the class KDoc). A fixed character count is
     * necessarily an approximation for a variable-width font, but it is trivially unit-testable
     * and, at this label's font, comfortably below what even the tab's own narrowest realistic
     * width can display without a measured budget - the same trade [BreakdownColumnPolicy] makes
     * the opposite way (it DOES have real widths to measure, because [BreakdownBlock] hands them
     * in) is not available here: [StatusTabPanel] sets this text before the label has ever been
     * laid out once (the very first render, on construction).
     */
    const val MAX_LENGTH = 96
    private const val ELLIPSIS = "…"

    fun truncate(text: String, maxLength: Int = MAX_LENGTH): String {
        if (text.length <= maxLength) return text
        val keep = (maxLength - ELLIPSIS.length).coerceAtLeast(0)
        return text.take(keep).trimEnd() + ELLIPSIS
    }
}
