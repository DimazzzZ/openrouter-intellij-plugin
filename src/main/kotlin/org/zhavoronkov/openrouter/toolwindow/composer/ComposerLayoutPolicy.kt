package org.zhavoronkov.openrouter.toolwindow.composer

/**
 * Decides what the composer's control row shows at a given width.
 *
 * Deliberately free of Swing and IntelliJ imports: the fast `test` Gradle task
 * runs headless and excludes platform-touching tests, so keeping this pure is
 * what lets resize behaviour be covered by ordinary unit tests at all. All
 * widths arrive already device-scaled — callers pass them through JBUI.scale.
 *
 * Collapse order (spec D8): counters drop, then Send compacts to an icon, then
 * the model combo shrinks toward its floor. Send and the model combo are never
 * removed: Send is the action, and the model is the only indication of where
 * the message is going.
 */
object ComposerLayoutPolicy {

    /** Narrowest width for which a usable layout is promised. */
    const val MIN_PANEL_WIDTH = 280

    /**
     * Floor below which the model combo is never shrunk, in unscaled pixels.
     *
     * Lives here because it is a rule, not a measurement: it is the one number
     * both the policy (as `Parts.modelMin`) and the applier (as the combo's own
     * minimum size) must agree on. A second, drifting copy is how a collapse
     * floor stops being a floor.
     *
     * Measured, not eyeballed: 162px is what `openai/gpt-4o-mini` (18 chars, a
     * representative MEDIUM model id — shorter than `anthropic/claude-sonnet-4.5`
     * at 216px, longer than `openrouter/auto` at 136px) needs at 100% scale
     * with [MiddleEllipsisComboRenderer.COMBO_CHROME] included and no slack.
     * 160 rounds that down by 2px, which the id's own tail character absorbs
     * unnoticeably. This is comfortably below every router slug in
     * `RouterCatalog` and every default in `ModelPresets`/the favourites table
     * except the longest (`anthropic/claude-3.5-sonnet-20241022` at 288px),
     * which still ellipsises gracefully once genuinely squeezed — raising the
     * floor to fit that one too would defeat the floor's purpose of leaving
     * room for the rest of the row.
     *
     * At the real `MIN_PANEL_WIDTH` this floor rarely even binds: measuring
     * the actual row (settings ~16px, counters ~89px, Send compact 32px, one
     * gap unit 4px) leaves ~224px for the model at 280px width, above this
     * floor for all but the very longest ids. The floor's job is the deeper
     * safety net below that promised minimum, where the old 80px value showed
     * only 7-8 characters — unreadable — and 160px keeps roughly a medium id
     * legible instead.
     */
    const val MODEL_MIN_WIDTH = 160

    /**
     * Measured widths of the row's parts, in scaled pixels.
     *
     * @param modelPreferred width at which the model combo shows its full text
     * @param modelMin floor below which the model combo is never shrunk
     * @param settings width of the gear button
     * @param counters combined width of the token counters
     * @param sendFull width of Send with its text label
     * @param sendCompact width of Send as an icon only
     * @param gap gap placed between each pair of visible parts
     */
    data class Parts(
        val modelPreferred: Int,
        val modelMin: Int,
        val settings: Int,
        val counters: Int,
        val sendFull: Int,
        val sendCompact: Int,
        val gap: Int
    )

    /**
     * @param cramped true when even the floor plan overflows — the caller may
     *   surface this (for example by suppressing the gear's badge) but the
     *   layout still has to draw something sane.
     */
    data class Plan(
        val modelWidth: Int,
        val countersVisible: Boolean,
        val sendWidth: Int,
        val sendCompact: Boolean,
        val cramped: Boolean
    )

    fun plan(availableWidth: Int, parts: Parts): Plan {
        if (rowWidth(parts.modelPreferred, true, parts.sendFull, parts) <= availableWidth) {
            return Plan(
                modelWidthFor(availableWidth, counters = true, sendWidth = parts.sendFull, parts),
                countersVisible = true,
                sendWidth = parts.sendFull,
                sendCompact = false,
                cramped = false
            )
        }
        if (rowWidth(parts.modelPreferred, false, parts.sendFull, parts) <= availableWidth) {
            return Plan(
                modelWidthFor(availableWidth, counters = false, sendWidth = parts.sendFull, parts),
                countersVisible = false,
                sendWidth = parts.sendFull,
                sendCompact = false,
                cramped = false
            )
        }
        if (rowWidth(parts.modelPreferred, false, parts.sendCompact, parts) <= availableWidth) {
            return Plan(
                modelWidthFor(availableWidth, counters = false, sendWidth = parts.sendCompact, parts),
                countersVisible = false,
                sendWidth = parts.sendCompact,
                sendCompact = true,
                cramped = false
            )
        }
        val forModel = availableWidth - rowWidth(0, false, parts.sendCompact, parts)
        return Plan(
            modelWidth = forModel.coerceAtLeast(parts.modelMin),
            countersVisible = false,
            sendWidth = parts.sendCompact,
            sendCompact = true,
            cramped = forModel < parts.modelMin
        )
    }

    /**
     * What's left over for the model once the other visible parts (and the
     * gaps between them) are subtracted from [availableWidth], clamped to
     * never shrink the combo past its floor or grow it past the width where
     * its text is already fully shown. Any leftover beyond that upper bound
     * is genuine slack the row has nothing useful to spend on the model, and
     * is left for the caller to leave empty (or, when counters are visible,
     * to hand to them) - it is never a "hole" for the model itself, since the
     * model does not need it once its text is showing in full.
     *
     * Shared by every branch of [plan] rather than special-cased per branch:
     * a branch is only reached once its own feasibility check guarantees this
     * formula resolves to exactly [Parts.modelPreferred], except the last
     * (shrinking) branch, where it is genuinely below that cap.
     *
     * The lower bound is `min(modelMin, modelPreferred)`, not `modelMin`
     * outright: a short selected id (`openrouter/auto` needs far less than
     * [MODEL_MIN_WIDTH]) can have a preferred width below the floor, and
     * `coerceIn` throws if its lower bound exceeds its upper bound. The floor
     * exists to stop shrinking below a usable width, never to demand more
     * than a short id's own full text needs.
     */
    private fun modelWidthFor(availableWidth: Int, counters: Boolean, sendWidth: Int, parts: Parts): Int {
        val forModel = availableWidth - rowWidth(0, counters, sendWidth, parts)
        val floor = parts.modelMin.coerceAtMost(parts.modelPreferred)
        return forModel.coerceIn(floor, parts.modelPreferred)
    }

    private fun rowWidth(modelWidth: Int, counters: Boolean, sendWidth: Int, parts: Parts): Int {
        val partsWidth = modelWidth + parts.settings + sendWidth + if (counters) parts.counters else 0
        val gapCount = if (counters) GAPS_WITH_COUNTERS else GAPS_WITHOUT_COUNTERS
        return partsWidth + parts.gap * gapCount
    }

    private const val GAPS_WITHOUT_COUNTERS = 2
    private const val GAPS_WITH_COUNTERS = 3
}
