package org.zhavoronkov.openrouter.toolwindow.composer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The composer's resize contract (spec D8), covered without a running IDE.
 *
 * `modelPreferred` (200) and `modelMin` (160, matching the real
 * [ComposerLayoutPolicy.MODEL_MIN_WIDTH]) are deliberately close together —
 * 40px apart — because that is the real relationship now: the floor was
 * raised to fit a medium model id, so there is less daylight between "shrunk
 * to the floor" and "shown in full" than there used to be. Every threshold
 * below is derived from these numbers, not eyeballed.
 *
 * Reference widths (scaled px) and the totals they produce:
 *   full        = 200 + 28 + 72 + 120 + 4*3 = 432
 *   no counters = 200 + 28 + 72       + 4*2 = 308
 *   + compact   = 200 + 28 + 32       + 4*2 = 268
 *   fixed only  =   0 + 28 + 32       + 4*2 =  68
 */
@DisplayName("ComposerLayoutPolicy")
class ComposerLayoutPolicyTest {

    private val parts = ComposerLayoutPolicy.Parts(
        modelPreferred = 200,
        modelMin = 160,
        settings = 28,
        counters = 120,
        sendFull = 72,
        sendCompact = 32,
        gap = 4
    )

    @Test
    @DisplayName("wide panel shows everything at full size")
    fun `wide panel shows everything at full size`() {
        val plan = ComposerLayoutPolicy.plan(600, parts)

        assertEquals(200, plan.modelWidth)
        assertTrue(plan.countersVisible)
        assertEquals(72, plan.sendWidth)
        assertFalse(plan.sendCompact)
        assertFalse(plan.cramped)
    }

    @Test
    @DisplayName("counters are the first thing dropped")
    fun `counters are the first thing dropped`() {
        // Between "no counters" (308) and "full" (432): only the counters give.
        val plan = ComposerLayoutPolicy.plan(350, parts)

        assertFalse(plan.countersVisible)
        assertEquals(200, plan.modelWidth)
        assertFalse(plan.sendCompact)
    }

    @Test
    @DisplayName("at the 280px floor Send compacts but the model stays readable")
    fun `at the floor Send compacts but the model stays readable`() {
        val plan = ComposerLayoutPolicy.plan(ComposerLayoutPolicy.MIN_PANEL_WIDTH, parts)

        assertFalse(plan.countersVisible)
        assertTrue(plan.sendCompact)
        assertEquals(32, plan.sendWidth)
        assertEquals(200, plan.modelWidth)
        assertFalse(plan.cramped)
    }

    @Test
    @DisplayName("below the floor the model shrinks, Send never disappears")
    fun `below the floor the model shrinks and Send survives`() {
        // 250 is below the "+ compact" threshold (268) but above where the
        // floor (160) itself would bind (228), so this is a genuine partial
        // shrink: forModel = 250 - 68 = 182.
        val plan = ComposerLayoutPolicy.plan(250, parts)

        assertEquals(182, plan.modelWidth)
        assertEquals(32, plan.sendWidth)
        assertTrue(plan.sendCompact)
        assertFalse(plan.cramped)
    }

    @Test
    @DisplayName("the model never shrinks past its floor, and says so")
    fun `the model never shrinks past its floor`() {
        val plan = ComposerLayoutPolicy.plan(120, parts)

        assertEquals(160, plan.modelWidth)
        assertTrue(plan.cramped)
        assertEquals(32, plan.sendWidth)
    }

    @Test
    @DisplayName("a zero-width panel still yields a usable plan")
    fun `a zero width panel still yields a usable plan`() {
        val plan = ComposerLayoutPolicy.plan(0, parts)

        assertEquals(160, plan.modelWidth)
        assertTrue(plan.cramped)
    }

    @Test
    @DisplayName("in the shrink band the model grows one-for-one with available width")
    fun `in the shrink band the model grows one for one with available width`() {
        // Both widths sit strictly inside the unclamped shrink band (floor
        // binds below 228, the "+ compact" cap is reached at 268).
        val narrow = ComposerLayoutPolicy.plan(230, parts)
        val wider = ComposerLayoutPolicy.plan(240, parts)

        assertEquals(narrow.modelWidth + 10, wider.modelWidth)
        assertFalse(narrow.cramped)
        assertFalse(wider.cramped)
    }

    @Test
    @DisplayName("the model stops growing exactly at the width where its text is fully shown")
    fun `the model stops growing exactly at the width where its text is fully shown`() {
        // 267 = the last width one px short of the case-3 (full model, compact send) threshold;
        // 268 = 200 + 28 + 32 + 4*2, the threshold itself.
        val justBelowCap = ComposerLayoutPolicy.plan(267, parts)
        val atCap = ComposerLayoutPolicy.plan(268, parts)

        assertEquals(199, justBelowCap.modelWidth)
        assertTrue(justBelowCap.sendCompact)
        assertEquals(200, atCap.modelWidth)
        assertTrue(atCap.sendCompact)
    }

    @Test
    @DisplayName("once the model shows its full text, extra room is genuine leftover, not more model")
    fun `once the model shows its full text extra room stays empty`() {
        val plan = ComposerLayoutPolicy.plan(1000, parts)

        assertEquals(200, plan.modelWidth)
        assertTrue(plan.countersVisible)
        assertFalse(plan.sendCompact)
    }

    @Test
    @DisplayName("a model id shorter than the floor is shown at its own width, not stretched to the floor")
    fun `a short model's preferred width can be below the floor without crashing`() {
        // openrouter/auto only needs ~136px (with COMBO_CHROME, no slack) at
        // 100% scale -- below MODEL_MIN_WIDTH (160). modelWidthFor's coerceIn
        // must not throw just because modelMin > modelPreferred here.
        val shortIdParts = parts.copy(modelPreferred = 136)

        val plan = ComposerLayoutPolicy.plan(ComposerLayoutPolicy.MIN_PANEL_WIDTH, shortIdParts)

        assertEquals(136, plan.modelWidth)
        assertFalse(plan.cramped)
    }

    @Test
    @DisplayName("the collapse sequence is monotonic across widths")
    fun `the collapse sequence is monotonic across widths`() {
        val widths = listOf(600, 420, 300, 280, 200, 120)
        val plans = widths.map { ComposerLayoutPolicy.plan(it, parts) }

        plans.zipWithNext { wider, narrower ->
            assertTrue(
                wider.modelWidth >= narrower.modelWidth,
                "model width must never grow as the panel shrinks"
            )
            assertTrue(
                !narrower.countersVisible || wider.countersVisible,
                "counters must never reappear as the panel shrinks"
            )
        }
    }
}
