package org.zhavoronkov.openrouter.toolwindow.status

/**
 * Decides whether enough time has passed since the last activation-triggered analytics refresh to
 * justify another one.
 *
 * [StatusTabPanel.onActivated] is called every time the Status tab becomes selected, which - for a
 * user flicking between tabs - can fire many times in quick succession. Unlike the shared stats
 * cache (refreshed on its own timer by the status-bar widget regardless of whether this tab is
 * even open), analytics queries are made only while this tab is visible and would otherwise spend
 * the user's quota on every glance at the tab. This class is the gate that makes "flicking tabs
 * does not burst requests" true; [StatusTabPanel] only has to ask it.
 *
 * Pure - no Swing, no AWT, no IntelliJ - so it is covered by ordinary unit tests in the fast
 * headless task, the same split as [SparklineGeometry] and [DegradedSpend]. `now` is injected
 * rather than read from the clock directly so a test can simulate the threshold elapsing without
 * an actual wait.
 */
class ActivationRefreshGate(
    private val thresholdMillis: Long,
    private val now: () -> Long = System::currentTimeMillis
) {

    // Nullable rather than a 0L sentinel: a fake clock in a test may legitimately start at or
    // near 0, which a sentinel would misread as "already refreshed at time zero" and wrongly
    // block the very first call.
    @Volatile
    private var lastRefreshAt: Long? = null

    /**
     * @return `true` the first time this is called, and again once [thresholdMillis] has elapsed
     *   since the last `true` result - `false` every time in between, during which nothing should
     *   be refreshed.
     */
    fun tryAcquire(): Boolean {
        val current = now()
        val last = lastRefreshAt
        if (last != null && current - last < thresholdMillis) return false
        lastRefreshAt = current
        return true
    }
}
