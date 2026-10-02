package org.zhavoronkov.openrouter.toolwindow.status

import java.util.Locale

/**
 * Formats [BalanceBlock]'s three headline rows - remaining, total, burn rate - so an unknown
 * figure still names what the row IS, never a bare em dash with the row's own descriptor gone.
 *
 * Fix round: [BalanceBlock.update] used to set the label's ENTIRE text to a bare em dash
 * (`"—"`) whenever a figure was unknown, e.g. `remainingLabel.text = remaining?.let { ... } ?:
 * NO_VALUE`. That reads correctly for exactly one row at a time, but three such rows stacked -
 * remaining, total, burn rate, all unknown at once (a fresh install with a bad key, say) - render
 * as three bare, unlabelled dashes with no word next to any of them, which looks like a broken
 * panel rather than "three figures are unknown". The em dash was always meant to stand in for the
 * FIGURE only - "— remaining" says "the remaining balance is unknown"; a bare "—" says nothing
 * legible on its own.
 *
 * Deliberately free of Swing/AWT/IntelliJ imports (only [java.util.Locale], used purely for
 * number formatting) - see [BreakdownColumnPolicy]'s own KDoc for why: it is what lets this be
 * covered by the fast headless `test` task as an ordinary unit test, with no platform runner
 * needed, and it is NOT excluded from Kover for the same reason - this is exactly the "decision
 * logic in a pure module" this codebase already has a house style for.
 */
object BalanceLineText {
    /** Em dash: stands in for the FIGURE only, never for the row's own descriptor word(s). */
    private const val NO_VALUE = "—"

    /** @param remaining account credits left, or null when unknown - see [BalanceBlock]'s own
     *   "unknown, not zero" rule. */
    fun remaining(remaining: Double?): String =
        // Unreachable branch: the let block returns a non-null String, so only a null remaining reaches NO_VALUE
        remaining?.let { "$${formatAmount(it)} remaining" } ?: "$NO_VALUE remaining"

    /** @param total the balance's denominator; zero or absent is "unknown", never a real zero
     *   limit - see [BalanceBlock]'s own KDoc. */
    fun total(total: Double): String =
        if (total > 0.0) "of $${formatAmount(total)} total" else "of $NO_VALUE total"

    /** @param perDay the recent burn rate, or null when it cannot be computed. */
    fun burnRate(perDay: Double?): String =
        // Unreachable branch: the let block returns a non-null String, so only a null perDay reaches NO_VALUE
        perDay?.let { "$${formatAmount(it)}/day burn rate" } ?: "$NO_VALUE/day burn rate"

    /**
     * @param daysLeft the computed days-left estimate, or null when either input to that division
     *   is itself unknown/non-positive - in which case [BalanceBlock] hides the whole row rather
     *   than rendering it against a guess, so this returns blank rather than an em-dash line: an
     *   invisible row has no descriptor to preserve in the first place.
     */
    fun daysLeft(daysLeft: Double?): String = daysLeft?.let { "${formatDays(it)} days left" }.orEmpty()

    private fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatDays(value: Double): String = String.format(Locale.US, "%.0f", value)
}
