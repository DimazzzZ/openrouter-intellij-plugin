package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private const val THRESHOLD_MS = 1000L

@DisplayName("ActivationRefreshGate")
class ActivationRefreshGateTest {

    @Test
    @DisplayName("the very first call always succeeds, even against a fake clock starting at zero")
    fun `the first call succeeds`() {
        val gate = ActivationRefreshGate(THRESHOLD_MS, now = { 0L })

        assertTrue(gate.tryAcquire())
    }

    @Test
    @DisplayName("a burst of calls within the threshold - simulating tab-flicking - succeeds once, not every time")
    fun `a burst within the threshold succeeds only once`() {
        var clock = 0L
        val gate = ActivationRefreshGate(THRESHOLD_MS, now = { clock })

        val first = gate.tryAcquire()
        clock += 1 // still well inside the 1000ms threshold
        val second = gate.tryAcquire()
        clock += 1
        val third = gate.tryAcquire()

        assertTrue(first, "the first call in the burst must succeed")
        assertFalse(second, "a second call inside the threshold must not re-trigger a refresh")
        assertFalse(third, "a third call inside the threshold must not re-trigger a refresh either")
    }

    @Test
    @DisplayName("positive control: once the threshold has actually elapsed, the gate opens again")
    fun `the gate opens again once the threshold elapses`() {
        var clock = 0L
        val gate = ActivationRefreshGate(THRESHOLD_MS, now = { clock })

        assertTrue(gate.tryAcquire(), "the first call must succeed")
        clock += THRESHOLD_MS
        assertTrue(
            gate.tryAcquire(),
            "once the threshold has elapsed the gate must open again - otherwise it would " +
                "refresh at most once ever, not on a rolling window"
        )
    }

    @Test
    @DisplayName("a call one millisecond short of the threshold still fails")
    fun `just under the threshold still fails`() {
        var clock = 0L
        val gate = ActivationRefreshGate(THRESHOLD_MS, now = { clock })

        assertTrue(gate.tryAcquire())
        clock += THRESHOLD_MS - 1
        assertFalse(gate.tryAcquire(), "one millisecond short of the threshold must still be gated")
    }
}
