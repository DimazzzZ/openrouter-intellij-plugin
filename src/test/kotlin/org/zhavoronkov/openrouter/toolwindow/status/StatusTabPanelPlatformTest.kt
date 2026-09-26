package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ActivityResponse
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.models.CreditsResponse
import org.zhavoronkov.openrouter.services.AnalyticsService
import org.zhavoronkov.openrouter.services.CreditUsageHistoryService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterStatsCache
import org.zhavoronkov.openrouter.toolwindow.chat.assertNoDescendantClippedByBottomEdge
import org.zhavoronkov.openrouter.toolwindow.chat.layoutTreeRecursively
import java.awt.Component
import java.awt.Container
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel

private const val LAYOUT_WIDTH = 300
private const val BREAKDOWN_LAYOUT_WIDTH = 280
private const val EM_DASH = "—"
private const val NO_ACTIVITY_TEXT = "No activity in this period"
private const val BREAKDOWN_ERROR_TEXT = "Couldn't load the breakdown"
private const val LOADING_BREAKDOWN_TEXT = "Loading breakdown..."
private const val TRUNCATED_TEXT = "Showing a partial result - the server truncated this query"

/**
 * Platform test for [BalanceBlock]'s arithmetic surface, and for the fact
 * that [StatusTabPanel] actually wires it in.
 *
 * Filed under `StatusTabPanelPlatformTest` per the task brief's own Files
 * list, even though most of these assertions exercise [BalanceBlock]
 * directly rather than [StatusTabPanel]: [BalanceBlock] has no seam of its
 * own to render "loading"/"not configured" chrome around, and constructing
 * it standalone is what lets these tests pin the exact defect class this
 * plan exists to remove - a plausible-looking number standing in for
 * something unknown - without a real [org.zhavoronkov.openrouter.services.OpenRouterStatsCache]
 * behind it. [testStatusTabPanelActuallyWiresInTheBalanceBlockComponent]
 * closes the other half: that the panel really uses this class, not merely
 * that the class exists.
 *
 * Fix round 1 added the "remaining must be nullable" tests below: a
 * non-nullable `remaining: Double` forced [StatusTabPanel.clearBalance] to
 * invent `0.0`, which [BalanceBlock] then rendered as a real-looking
 * "$0.00 remaining" during loading and while unconfigured - the exact
 * "plausible number standing in for something unknown" defect this whole
 * plan exists to remove.
 *
 * Extends [BasePlatformTestCase] because [BalanceBlock] builds real
 * [com.intellij.ui.components.JBLabel]s and reads theme colours via
 * [com.intellij.util.ui.JBUI.CurrentTheme], which need IntelliJ's platform
 * to be initialised - the same reason every other Swing view under this
 * package is tested as a platform test rather than a plain unit test.
 */
class StatusTabPanelPlatformTest : BasePlatformTestCase() {

    // --- Step 3: the block's arithmetic surface -----------------------------

    fun testZeroTotalRendersEmDashInsteadOfANumber() {
        val block = BalanceBlock()

        block.update(
            remaining = 12.34,
            total = 0.0,
            perDay = null,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val texts = collectLabelSnapshots(block.component).map { it.text }

        assertTrue(
            "a zero total must render the em dash somewhere in the block, found: $texts",
            texts.contains(EM_DASH)
        )
        assertFalse(
            "a zero total must never be paired with a fabricated total figure like " +
                "'of \$0.00 total' - that is exactly the old Infinity%-style defect: $texts",
            texts.any { it.contains(Regex("""of \$[0-9.]+ total""")) }
        )
    }

    fun testNullRateHidesDaysLeftRowInsteadOfRenderingNullOrInfinity() {
        val block = BalanceBlock()

        block.update(
            remaining = 100.0,
            total = 200.0,
            perDay = null,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val labels = collectLabelSnapshots(block.component)

        assertTrue(
            "no VISIBLE row may claim a days-left figure when the rate is unknown - the row " +
                "must be hidden, not merely left blank: $labels",
            labels.none { it.visible && it.text.contains("days left", ignoreCase = true) }
        )
        assertFalse(
            "an unknown rate must never render the literal word 'null' anywhere in the block: $labels",
            labels.any { it.text.contains("null", ignoreCase = true) }
        )
        assertFalse(
            "an unknown rate must never render Infinity (a division by an absent rate): $labels",
            labels.any { it.text.contains("infinity", ignoreCase = true) }
        )
    }

    fun testZeroRateHidesDaysLeftRowInsteadOfRenderingInfinity() {
        val block = BalanceBlock()

        block.update(
            remaining = 100.0,
            total = 200.0,
            perDay = 0.0,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val labels = collectLabelSnapshots(block.component)

        assertTrue(
            "a zero rate must not produce a visible days-left row - dividing by it is Infinity: $labels",
            labels.none { it.visible && it.text.contains("days left", ignoreCase = true) }
        )
        assertFalse(
            "a zero rate must never render Infinity anywhere in the block: $labels",
            labels.any { it.text.contains("infinity", ignoreCase = true) }
        )
    }

    /**
     * Positive control for the two tests above: without this, a "days left" row that is
     * ALWAYS hidden - never wired to a real rate at all - would still pass both null/zero-rate
     * tests, because "hidden" is not evidence of "correctly conditional" on its own.
     */
    fun testKnownPositiveRateShowsAVisibleDaysLeftRow() {
        val block = BalanceBlock()

        block.update(
            remaining = 100.0,
            total = 200.0,
            perDay = 10.0,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val labels = collectLabelSnapshots(block.component)

        assertTrue(
            "a known positive rate must produce a visible '...days left' row, found: $labels",
            labels.any { it.visible && it.text.contains("days left", ignoreCase = true) }
        )
    }

    fun testBlankLastUpdatedTextHidesTheRowInsteadOfRenderingAnEmptyLine() {
        val block = BalanceBlock()

        block.update(
            remaining = 10.0,
            total = 20.0,
            perDay = 1.0,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = "   "
        )

        val labels = collectLabelSnapshots(block.component)

        assertFalse(
            "blank last-updated text must hide the row rather than leave a visible " +
                "blank/whitespace line: $labels",
            labels.any { it.visible && it.text.isBlank() }
        )
    }

    fun testNonBlankLastUpdatedTextIsShown() {
        val block = BalanceBlock()

        block.update(
            remaining = 10.0,
            total = 20.0,
            perDay = 1.0,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = "Updated 2 minutes ago"
        )

        val labels = collectLabelSnapshots(block.component)

        assertTrue(
            "a non-blank last-updated text must be shown verbatim: $labels",
            labels.any { it.visible && it.text == "Updated 2 minutes ago" }
        )
    }

    // --- Fix round 1: remaining must be nullable, never fabricated as 0.0 ----------------------

    /**
     * Direct proof of the fix-round-1 defect: [StatusTabPanel.clearBalance] passes exactly these
     * values (remaining = null, total = 0.0, perDay = null, empty series/labels) for BOTH the
     * loading and the not-configured states - it is the single, shared render path for both, so
     * testing it once here covers both states' balance rendering.
     *
     * A live [StatusTabPanel] actually in the LOADING state is deliberately NOT driven here:
     * reaching it for real requires the process-wide [org.zhavoronkov.openrouter.services.OpenRouterStatsCache]
     * singleton to issue a genuine background network refresh (`refresh()` launches a coroutine
     * against the real OpenRouter API), which would leak an uncontrolled, unmockable async call -
     * and its mutation of that singleton's `lastError` - into every other test sharing it.
     * [testNotConfiguredStateRendersNoDollarFigureInTheRemainingRow] below covers the
     * not-configured half through the real, live panel instead, since that path never touches
     * the cache's `refresh()` at all when `isConfigured()` is false.
     */
    fun testClearBalanceValuesRenderNoDollarFigureForRemaining() {
        val block = BalanceBlock()

        block.update(
            remaining = null,
            total = 0.0,
            perDay = null,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val texts = collectLabelSnapshots(block.component).map { it.text }

        assertFalse(
            "the loading/not-configured balance values must never render a dollar figure - " +
                "there is no known balance yet: $texts",
            texts.any { it.contains(Regex("""\$[0-9]""")) }
        )
        assertTrue(
            "the remaining row must render the em dash when remaining is unknown: $texts",
            texts.contains(EM_DASH)
        )
    }

    /**
     * Positive control: without it, a remaining row that is ALWAYS the em dash - never wired to
     * the real value at all - would still pass the test above.
     */
    fun testKnownRemainingRendersItsDollarFigure() {
        val block = BalanceBlock()

        block.update(
            remaining = 12.34,
            total = 0.0,
            perDay = null,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val texts = collectLabelSnapshots(block.component).map { it.text }

        assertTrue(
            "a known remaining must render its dollar figure: $texts",
            texts.contains("\$12.34 remaining")
        )
    }

    /**
     * The not-configured half of the fix-round-1 ask, through the real, live panel: unlike
     * loading, this state never calls the shared cache's `refresh()` (the wiring in
     * [StatusTabPanel.refresh] gates that call on `settingsService.isConfigured()`), so it is
     * safe to drive for real without touching any process-wide singleton's network behaviour.
     */
    fun testNotConfiguredStateRendersNoDollarFigureInTheRemainingRow() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertEquals(StatusTabState.State.NOT_CONFIGURED, statusTab.getStateForTest())

            val texts = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE)).map { it.text }

            assertFalse(
                "the not-configured state must not render a dollar figure in the balance block - " +
                    "there is nothing to report before an API key exists: $texts",
                texts.any { it.contains(Regex("""\$[0-9]""")) }
            )
        } finally {
            statusTab.dispose()
        }
    }

    // --- Fix round 1 (minor): the sparkline view is gated like its caption ---------------------

    fun testBlankSeriesLabelHidesTheSparklineComponentTooNotJustItsCaption() {
        val block = BalanceBlock()

        block.update(
            remaining = 10.0,
            total = 20.0,
            perDay = 1.0,
            series = emptyList(),
            seriesLabel = "",
            lastUpdatedText = ""
        )

        val sparklineComponent = findNonLabelChild(block.component)

        assertFalse(
            "an absent series/caption must hide the sparkline view too, not just its caption " +
                "label - otherwise it still reserves a blank fixed-size box",
            sparklineComponent.isVisible
        )
    }

    /**
     * Positive control: without it, a sparkline view that is ALWAYS hidden - never wired to the
     * caption's condition at all - would still pass the test above.
     */
    fun testKnownSeriesLabelShowsTheSparklineComponent() {
        val block = BalanceBlock()

        block.update(
            remaining = 10.0,
            total = 20.0,
            perDay = 1.0,
            series = listOf(1.0, 2.0, 3.0),
            seriesLabel = "Last 7 days",
            lastUpdatedText = ""
        )

        val sparklineComponent = findNonLabelChild(block.component)

        assertTrue(
            "a known series/caption must show the sparkline view",
            sparklineComponent.isVisible
        )
    }

    // --- Width-then-height: laid out at a realistic width before any height is asserted on ----

    fun testAssembledBlockIsNotClippedByItsBottomEdgeAtARealisticWidth() {
        val block = BalanceBlock()
        block.update(
            remaining = 42.5,
            total = 100.0,
            perDay = 3.3,
            series = listOf(1.0, 2.0, 3.0, 2.5, 4.0),
            seriesLabel = "Last 7 days",
            lastUpdatedText = "Updated just now"
        )

        val panel = block.component
        // Two layout passes at the real width, mirroring MessageViewLayoutPlatformTest's
        // layoutInPanel: width must be applied before any height is read from a component whose
        // preferred height can depend on it - the bug class this branch has shipped twice.
        panel.setSize(LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)

        assertNoDescendantClippedByBottomEdge(panel)
    }

    // --- Fix round 2, finding 1: showLocalSeriesOnly()/update() cross-render on ONE instance ---
    //
    // Every other test in this file builds a fresh BalanceBlock (or a fresh StatusTabPanel), so
    // none of them exercise update()'s own re-showing of remainingLabel/totalLabel/burnRateLabel
    // - the invariant fix round 1 added specifically so DEGRADED -> READY (a real transition, once
    // a provisioning key is added) restores the rows showLocalSeriesOnly() hid. These two run both
    // transitions on the SAME block instance, which a fresh-panel-per-test style cannot catch.

    fun testReadyThenDegradedHidesTheFigureRowsButShowsTheLocalSeriesCaption() {
        val block = BalanceBlock()
        block.update(
            remaining = 42.5,
            total = 100.0,
            perDay = 3.3,
            series = listOf(1.0, 2.0),
            seriesLabel = "Last 7 days",
            lastUpdatedText = "Updated just now"
        )

        block.showLocalSeriesOnly(listOf(1.0, 2.0, 3.0), DEGRADED_CAPTION)

        val labels = collectLabelSnapshots(block.component)
        assertFalse(
            "READY -> DEGRADED must hide the remaining row: $labels",
            labels.any { it.visible && it.text == "\$42.50 remaining" }
        )
        assertFalse(
            "READY -> DEGRADED must hide the total row: $labels",
            labels.any { it.visible && it.text == "of \$100.00 total" }
        )
        assertFalse(
            "READY -> DEGRADED must hide the burn-rate row: $labels",
            labels.any { it.visible && it.text == "\$3.30/day burn rate" }
        )
        assertFalse(
            "READY -> DEGRADED must hide the days-left row: $labels",
            labels.any { it.visible && it.text.contains("days left") }
        )
        assertFalse(
            "READY -> DEGRADED must hide the last-updated row: $labels",
            labels.any { it.visible && it.text == "Updated just now" }
        )
        assertTrue(
            "READY -> DEGRADED must still show the local-series caption: $labels",
            labels.any { it.visible && it.text == DEGRADED_CAPTION }
        )
        assertTrue(
            "READY -> DEGRADED must still show the sparkline itself",
            findNonLabelChild(block.component).isVisible
        )
    }

    /**
     * Positive control, and the finding's own proof: deleting `update()`'s three
     * `remainingLabel.isVisible = true` / `totalLabel...` / `burnRateLabel...` lines leaves this
     * failing while every other test in the suite (all built on a fresh panel) stays green.
     */
    fun testDegradedThenReadyRestoresEveryHiddenRowWithItsRealValue() {
        val block = BalanceBlock()
        block.showLocalSeriesOnly(listOf(1.0, 2.0), DEGRADED_CAPTION)

        block.update(
            remaining = 42.5,
            total = 100.0,
            perDay = 3.3,
            series = listOf(1.0, 2.0),
            seriesLabel = "Last 7 days",
            lastUpdatedText = "Updated just now"
        )

        val labels = collectLabelSnapshots(block.component)
        assertTrue(
            "DEGRADED -> READY must restore the remaining row: $labels",
            labels.any { it.visible && it.text == "\$42.50 remaining" }
        )
        assertTrue(
            "DEGRADED -> READY must restore the total row: $labels",
            labels.any { it.visible && it.text == "of \$100.00 total" }
        )
        assertTrue(
            "DEGRADED -> READY must restore the burn-rate row: $labels",
            labels.any { it.visible && it.text == "\$3.30/day burn rate" }
        )
        assertTrue(
            "DEGRADED -> READY must restore the days-left row: $labels",
            labels.any { it.visible && it.text.contains("days left") }
        )
        assertTrue(
            "DEGRADED -> READY must restore the last-updated row: $labels",
            labels.any { it.visible && it.text == "Updated just now" }
        )
    }

    // --- Task 10 (KeyLimitBlock): hidden unless a real, positive cap exists --------------------

    /**
     * Direct proof of the brief's single most important behaviour: a null limit (no key carries
     * one) must HIDE the block entirely, never render a fabricated "$0.00" cap - the defect that
     * made the old "Quota" row look broken.
     */
    fun testNullLimitHidesTheKeyLimitBlock() {
        val block = KeyLimitBlock()

        block.update(used = 3.20, limit = null)

        assertFalse(
            "a null limit must hide the block entirely, not render a fabricated figure",
            block.component.isVisible
        )
    }

    /**
     * `limit == 0.0` must be treated the same as `null` (see [KeyLimit]'s own "0.0 means
     * uncapped" rule) - a real API key cap is never exactly zero, and rendering "$0.00 of $0.00"
     * is exactly what made the old tab look broken.
     */
    fun testZeroLimitHidesTheKeyLimitBlock() {
        val block = KeyLimitBlock()

        block.update(used = 0.0, limit = 0.0)

        assertFalse(
            "a zero limit must hide the block, exactly like a null limit",
            block.component.isVisible
        )
    }

    /**
     * Positive control for the two tests above: without it, a block that is ALWAYS hidden -
     * never wired to a real cap at all - would still pass both.
     */
    fun testPositiveLimitShowsTheKeyLimitBlockWithTheRightFigures() {
        val block = KeyLimitBlock()

        block.update(used = 3.20, limit = 10.0)

        assertTrue("a real, positive limit must show the block", block.component.isVisible)

        val texts = collectLabelSnapshots(block.component).map { it.text }
        assertTrue(
            "the visible block must render both the used and the cap figures: $texts",
            texts.any { it.contains("3.20") && it.contains("10.00") }
        )
        assertTrue(
            "the block must label itself as the API KEY's cap, never as the account balance: $texts",
            texts.any { it.contains("API key", ignoreCase = true) }
        )
    }

    fun testStatusTabPanelActuallyWiresInTheKeyLimitBlockComponent() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            val keyLimitComponent = statusTab.componentForTest(TestComponent.KEY_LIMIT)

            assertTrue(
                "KeyLimitBlock's component must actually be present in StatusTabPanel's tree, " +
                    "not merely constructed and left unattached",
                isDescendant(statusTab.component, keyLimitComponent)
            )
        } finally {
            statusTab.dispose()
        }
    }

    /**
     * The not-configured state has no cached API keys at all, so the block must be hidden -
     * through the real, live panel wiring, not [KeyLimitBlock] in isolation.
     */
    fun testNotConfiguredStateHidesTheKeyLimitBlock() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertFalse(
                "the not-configured state must not show the key-limit block - there is no known " +
                    "cap before an API key exists",
                statusTab.componentForTest(TestComponent.KEY_LIMIT).isVisible
            )
        } finally {
            statusTab.dispose()
        }
    }

    // --- Wiring: StatusTabPanel actually uses BalanceBlock, not merely constructs it -----------

    fun testStatusTabPanelActuallyWiresInTheBalanceBlockComponent() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            val balanceComponent = statusTab.componentForTest(TestComponent.BALANCE)

            assertTrue(
                "BalanceBlock's component must actually be present in StatusTabPanel's tree, " +
                    "not merely constructed and left unattached",
                isDescendant(statusTab.component, balanceComponent)
            )
        } finally {
            statusTab.dispose()
        }
    }

    // --- Step 3 (BreakdownBlock): selector default, change wiring, empty state -----------------

    fun testBreakdownSelectorDefaultsTo24Hours() {
        val block = BreakdownBlock()

        val combo = findPeriodCombo(block.component)

        assertEquals(
            "the period selector must default to 24 hours (Period.DAY)",
            ActivityAggregator.Period.DAY,
            combo.selectedItem
        )
    }

    fun testChangingSelectorFiresOnPeriodChangedExactlyOnceWithTheRightValue() {
        val block = BreakdownBlock()
        val received = mutableListOf<ActivityAggregator.Period>()
        block.onPeriodChanged = { received.add(it) }

        val combo = findPeriodCombo(block.component)
        combo.selectedItem = ActivityAggregator.Period.WEEK

        assertEquals(
            "changing the selector must fire onPeriodChanged exactly once, got $received",
            listOf(ActivityAggregator.Period.WEEK),
            received
        )
    }

    /**
     * Positive/negative control for the test above: selecting the SAME item the combo already
     * has selected must not re-fire onPeriodChanged - Swing's ItemEvent only fires on an actual
     * SELECTED transition, but this pins that behaviour against a future change to the wiring.
     */
    fun testReselectingTheSamePeriodDoesNotFireOnPeriodChangedAgain() {
        val block = BreakdownBlock()
        val received = mutableListOf<ActivityAggregator.Period>()
        block.onPeriodChanged = { received.add(it) }

        val combo = findPeriodCombo(block.component)
        combo.selectedItem = ActivityAggregator.Period.DAY

        assertTrue(
            "re-selecting the already-selected default must not fire onPeriodChanged: $received",
            received.isEmpty()
        )
    }

    fun testEmptyBreakdownRendersExplicitNoActivityLineNotAnEmptyBox() {
        val block = BreakdownBlock()

        block.show(emptyList())

        val texts = collectLabelSnapshots(block.component).map { it.text }

        assertTrue(
            "an empty breakdown must render an explicit 'no activity' line, found: $texts",
            texts.contains(NO_ACTIVITY_TEXT)
        )
    }

    /**
     * Positive control: without it, a breakdown that is ALWAYS empty regardless of [show]'s
     * argument would still pass the test above.
     */
    fun testNonEmptyBreakdownRendersModelAndSpend() {
        val block = BreakdownBlock()

        block.show(
            listOf(
                ActivityAggregator.ModelSpend("anthropic/claude-sonnet-4.5", 0.5123, 31L)
            )
        )

        val texts = collectLabelSnapshots(block.component).map { it.text }

        assertTrue("the model name must be rendered: $texts", texts.contains("anthropic/claude-sonnet-4.5"))
        assertTrue("the spend must be rendered as a dollar figure: $texts", texts.any { it.contains("0.5123") })
        assertFalse(
            "a non-empty result must not also render the empty-state line: $texts",
            texts.contains(NO_ACTIVITY_TEXT)
        )
    }

    // --- Width-then-height: laid out at a realistic (280px) width before any height is read ----

    fun testBreakdownBlockIsNotClippedByItsBottomEdgeAtARealisticWidthWithLongModelIds() {
        val block = BreakdownBlock()
        block.show(
            listOf(
                ActivityAggregator.ModelSpend("anthropic/claude-3.7-sonnet-20250219-extended-thinking", 12.3456, 500L),
                ActivityAggregator.ModelSpend("openai/gpt-4o-mini-2024-07-18-long-context-variant", 3.21, 120L),
                ActivityAggregator.ModelSpend("google/gemini-2.5-pro-preview-experimental", 1.0, 4L),
                ActivityAggregator.ModelSpend("m", 0.0001, 1L)
            )
        )

        val panel = block.component
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)

        assertNoDescendantClippedByBottomEdge(panel)
    }

    // --- Wiring: StatusTabPanel actually uses BreakdownBlock, not merely constructs it ----------

    fun testStatusTabPanelActuallyWiresInTheBreakdownBlockComponent() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            val breakdownComponent = statusTab.componentForTest(TestComponent.BREAKDOWN)

            assertTrue(
                "BreakdownBlock's component must actually be present in StatusTabPanel's tree, " +
                    "not merely constructed and left unattached",
                isDescendant(statusTab.component, breakdownComponent)
            )
        } finally {
            statusTab.dispose()
        }
    }

    fun testNotConfiguredStateRendersTheNoActivityLineInTheBreakdownBlock() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            val texts = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN)).map { it.text }

            assertTrue(
                "the not-configured state must render the explicit no-activity line, not an " +
                    "empty box: $texts",
                texts.contains(NO_ACTIVITY_TEXT)
            )
        } finally {
            statusTab.dispose()
        }
    }

    // --- Fix round 1, finding 2: metadata.truncated must be surfaced, not discarded ------------

    fun testTruncatedResultRendersAnExplicitTruncationNotice() {
        val block = BreakdownBlock()

        block.show(listOf(ActivityAggregator.ModelSpend("m", 1.0, 1L)), truncated = true)

        val labels = collectLabelSnapshots(block.component)

        assertTrue(
            "a truncated result must render a VISIBLE explicit truncation notice, found: $labels",
            labels.any { it.visible && it.text == TRUNCATED_TEXT }
        )
    }

    /**
     * Positive control: without it, a truncation notice that is ALWAYS rendered - never wired to
     * the `truncated` argument at all - would still pass the test above. The notice label always
     * exists in the tree (only its visibility toggles), so this must check `visible`, not merely
     * whether the text is present anywhere in the tree.
     */
    fun testNonTruncatedResultDoesNotRenderTheTruncationNotice() {
        val block = BreakdownBlock()

        block.show(listOf(ActivityAggregator.ModelSpend("m", 1.0, 1L)), truncated = false)

        val labels = collectLabelSnapshots(block.component)

        assertFalse(
            "a non-truncated result must not render a VISIBLE truncation notice: $labels",
            labels.any { it.visible && it.text == TRUNCATED_TEXT }
        )
    }

    /**
     * `truncated` defaults to `false` - the shape every existing caller (empty-state renders,
     * the degraded ActivityAggregator path) uses, none of which have any truncation to report.
     */
    fun testShowWithoutATruncatedArgumentDoesNotRenderTheNotice() {
        val block = BreakdownBlock()

        block.show(listOf(ActivityAggregator.ModelSpend("m", 1.0, 1L)))

        val labels = collectLabelSnapshots(block.component)

        assertFalse(
            "show() without a truncated argument must default to not truncated: $labels",
            labels.any { it.visible && it.text == TRUNCATED_TEXT }
        )
    }

    // --- Fix round 1, finding 3: an analytics error must never fall back to ActivityAggregator --

    /**
     * Direct proof of finding 3 (D12): drives [StatusTabPanel.applyAnalyticsResultForTest]
     * against a real [AnalyticsService] pointed at a [MockWebServer] that returns a 500, and
     * asserts the breakdown shows the explicit error line - never the empty-box "no activity"
     * line (which would look like a real, if boring, answer) and never any row that could have
     * come from [ActivityAggregator]'s degraded path.
     */
    fun testAnalyticsErrorRendersExplicitErrorLineNotASilentActivityAggregatorFallback() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(500)
                    .setBody("""{"error":{"message":"boom"}}""")
            )
            val settingsService = mock(OpenRouterSettingsService::class.java)
            `when`(settingsService.isConfigured()).thenReturn(false)
            `when`(settingsService.getProvisioningKey()).thenReturn("")
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { "test-key" }
            )

            val statusTab = StatusTabPanel(project, settingsService, analyticsService)
            try {
                statusTab.applyAnalyticsResultForTest(ActivityAggregator.Period.DAY)

                val texts = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN)).map { it.text }

                assertTrue(
                    "an analytics error must render the explicit error line, found: $texts",
                    texts.contains(BREAKDOWN_ERROR_TEXT)
                )
                assertFalse(
                    "an analytics error must NOT render the no-activity line either - that would " +
                        "look like a real 'no spend this period' answer rather than a failure: $texts",
                    texts.contains(NO_ACTIVITY_TEXT)
                )
            } finally {
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * Positive control for the test above: proves [StatusTabPanel.applyAnalyticsResultForTest]
     * really drives a live analytics query end to end (not merely always showing the error line),
     * by pointing the same wiring at a SUCCESSFUL, truncated response and checking both the rows
     * AND the truncation notice (finding 2) actually reach [BreakdownBlock].
     */
    fun testSuccessfulTruncatedAnalyticsResponseReachesTheBreakdownBlock() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":{"data":[{"model":"anthropic/claude-sonnet-4.5","total_usage":1.5,""" +
                        """"request_count":3}],"metadata":{"row_count":1,"truncated":true}}}"""
                )
            )
            val settingsService = mock(OpenRouterSettingsService::class.java)
            `when`(settingsService.isConfigured()).thenReturn(false)
            `when`(settingsService.getProvisioningKey()).thenReturn("")
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { "test-key" }
            )

            val statusTab = StatusTabPanel(project, settingsService, analyticsService)
            try {
                statusTab.applyAnalyticsResultForTest(ActivityAggregator.Period.DAY)

                val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN))
                val texts = labels.map { it.text }

                assertTrue("the queried model must be rendered: $texts", texts.contains("anthropic/claude-sonnet-4.5"))
                assertTrue(
                    "a truncated=true response must surface a VISIBLE truncation notice through the " +
                        "real StatusTabPanel wiring, not just BreakdownBlock in isolation: $labels",
                    labels.any { it.visible && it.text == TRUNCATED_TEXT }
                )
                assertFalse(
                    "a successful response must never render the error line: $texts",
                    texts.contains(BREAKDOWN_ERROR_TEXT)
                )
            } finally {
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    // --- Fix round 1, finding 4: the model name is middle-, not end-, ellipsised ---------------

    /**
     * At a width too narrow for a long model id, [BreakdownBlock.ModelNameLabel] must fit it via
     * [org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis], which keeps the head AND
     * the tail - never Swing's own end-clipping, which would delete the tail that distinguishes
     * two ids sharing a vendor prefix (`anthropic/claude-sonnet-4.5` vs
     * `anthropic/claude-opus-4.5`, both `anthropic/claude-...` under end-clipping).
     */
    fun testLongModelIdIsMiddleEllipsisedKeepingItsDistinguishingTail() {
        val longId = "anthropic/claude-3.7-sonnet-20250219-extended-thinking-preview-variant"
        val block = BreakdownBlock()
        block.show(listOf(ActivityAggregator.ModelSpend(longId, 1.2345, 1L)))

        val panel = block.component
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)

        val texts = collectLabelSnapshots(panel).map { it.text }
        val modelText = checkNotNull(texts.firstOrNull { it != longId && it.contains("…") }) {
            "expected a middle-ellipsised rendering of the long model id, found labels: $texts"
        }

        val ellipsis = "…"
        val parts = modelText.split(ellipsis)
        assertEquals("a middle-ellipsised string has exactly one ellipsis: $modelText", 2, parts.size)
        assertTrue(
            "the head of '$modelText' must be a PREFIX of the full id, not a re-ordered fragment",
            longId.startsWith(parts[0])
        )
        assertTrue(
            "the tail of '$modelText' must be a SUFFIX of the full id - this is the part end-" +
                "clipping would have deleted, and exactly what distinguishes two models sharing " +
                "a vendor prefix",
            longId.endsWith(parts[1])
        )
        assertTrue(
            "the fitted text must actually be shorter than the full id - otherwise nothing was fitted",
            modelText.length < longId.length
        )
    }

    /**
     * The spend column must never truncate, regardless of the model column's width: proves
     * finding 4's fix did not also start fitting the spend label.
     */
    fun testSpendColumnNeverTruncatesEvenWithAVeryLongModelId() {
        val longId = "anthropic/claude-3.7-sonnet-20250219-extended-thinking-preview-variant"
        val block = BreakdownBlock()
        block.show(listOf(ActivityAggregator.ModelSpend(longId, 1.2345, 1L)))

        val panel = block.component
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(BREAKDOWN_LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)

        val texts = collectLabelSnapshots(panel).map { it.text }

        assertTrue(
            "the spend figure must be rendered in full, never truncated: $texts",
            texts.contains("\$1.2345")
        )
    }

    /**
     * A [ComboBox]'s own `selectedItem` accessors are untyped - `getSelectedItem`/
     * `setSelectedItem` both take/return plain `Object` at the Swing level - so a wildcard-typed
     * reference is enough here - reading and writing it needs no generic cast.
     */
    private fun findPeriodCombo(root: Container): ComboBox<*> {
        fun walk(container: Container): ComboBox<*>? {
            container.components.forEach { child ->
                if (child is ComboBox<*>) return child
                if (child is Container) walk(child)?.let { return it }
            }
            return null
        }
        return checkNotNull(walk(root)) { "no period ComboBox found in the component tree" }
    }

    private data class LabelSnapshot(val text: String, val visible: Boolean)

    /**
     * `visible` on the returned snapshot is EFFECTIVE visibility - a label's own `isVisible` AND
     * every ancestor's, up to (and including) [root] - not merely the label's own flag (fix round
     * 1, finding 5). Swing's `setVisible(false)` on a container does not cascade down to its
     * children's own `isVisible`, so a label sitting inside a hidden container (e.g.
     * `degradedNoticeBlock.component` outside DEGRADED) would otherwise report itself as visible
     * even though nothing paints it - a regression that hides a whole block by hiding its
     * container would go uncaught by an assertion reading only the label's own flag.
     */
    private fun collectLabelSnapshots(root: Container): List<LabelSnapshot> {
        val result = mutableListOf<LabelSnapshot>()
        fun walk(container: Container, ancestorsVisible: Boolean) {
            container.components.forEach { child ->
                val effectiveVisible = ancestorsVisible && child.isVisible
                if (child is JLabel) result += LabelSnapshot(child.text.orEmpty(), effectiveVisible)
                if (child is Container) walk(child, effectiveVisible)
            }
        }
        walk(root, root.isVisible)
        return result
    }

    /** The one direct child of [BalanceBlock.component] that is not a [JLabel] - the sparkline. */
    private fun findNonLabelChild(root: Container): Component =
        root.components.first { it !is JLabel }

    private fun isDescendant(root: Container, target: Component): Boolean {
        if (root === target) return true
        return root.components.any { child ->
            child === target || (child is Container && isDescendant(child, target))
        }
    }

    // --- Task 11: the five states, driven through the real panel -------------------------------
    //
    // NOT_CONFIGURED and DEGRADED are driven through a genuinely live panel: both settle their
    // constructor's own `refresh()` call synchronously without ever reaching a real network fetch.
    // NOT_CONFIGURED because `refresh()` skips OpenRouterStatsCache.refresh() entirely while the
    // MOCKED settingsService reports unconfigured. DEGRADED's safety is NOT the mirror image of
    // that (fix round 1, finding 9, correcting the previous round's wrong explanation here):
    // OpenRouterStatsCache.refresh()'s own precondition check reads OpenRouterSettingsService
    // .getInstance() - the REAL, process-wide settings singleton - not the mock passed to this
    // test's StatusTabPanel. In this test sandbox that real singleton is unconfigured, so
    // validateRefreshPreconditions() bails on its FIRST check ("Not configured",
    // OpenRouterStatsCache.kt around its isConfigured() check) before ever reaching the
    // provisioning-key branch - the same reason NOT_CONFIGURED is safe, reached through a
    // different settings instance. This is why DEGRADED is stable rather than merely "currently
    // passing": it does not depend on the real singleton ever having had a provisioning key: it
    // depends on the real singleton having no API key at all, which is also this sandbox's
    // default state for every other test in this class. LOADING, READY and ERROR
    // all require BOTH a config key AND a provisioning key to be present in currentInputs(), and
    // at that combination OpenRouterStatsCache.refresh() unconditionally launches a REAL
    // coroutine against the live OpenRouter API - there is no way to reach those three states
    // through a normal `StatusTabPanel(...)` construction without doing that. Those three are
    // instead driven through StatusTabPanel.renderForTest() - constructed while unconfigured (so
    // construction itself is inert), then the shared OpenRouterStatsCache singleton and the
    // settings mock are set up directly, and renderForTest() re-derives and re-renders with zero
    // network in the loop.

    fun testNotConfiguredStateHidesTheDegradedBannerAndIsNotClippedAtARealisticWidth() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertEquals(StatusTabState.State.NOT_CONFIGURED, statusTab.getStateForTest())
            assertFalse(
                "NOT_CONFIGURED must not show the DEGRADED banner - a missing API key and a " +
                    "missing provisioning key are different problems with different fixes",
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE).isVisible
            )

            layoutAtRealisticWidthAndAssertNotClipped(statusTab.component)
        } finally {
            statusTab.dispose()
        }
    }

    fun testDegradedStateNamesTheMissingProvisioningKeyAndShowsNoAccountData() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertEquals(StatusTabState.State.DEGRADED, statusTab.getStateForTest())

            assertTrue(
                "DEGRADED must show its explanatory banner",
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE).isVisible
            )
            val bannerTexts = collectLabelSnapshots(
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE)
            ).map { it.text }
            assertTrue(
                "the banner must name the actual cause - a missing provisioning key - not a " +
                    "generic 'something is wrong' line: $bannerTexts",
                bannerTexts.any { it.contains("provisioning key", ignoreCase = true) }
            )

            val balanceLabels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
            assertFalse(
                "DEGRADED must never render a dollar figure - the spec's D6 fallback to " +
                    "/credits does not exist, so there is no account data at all: $balanceLabels",
                balanceLabels.any { it.text.contains(Regex("""\$[0-9]""")) }
            )
            // Fix round 1, finding 1: update()'s "unknown, not zero" em dash is the WRONG
            // rendering here - a dash says "your balance is unknown", but the true fact (already
            // stated by the banner above) is "this needs a provisioning key you have not set".
            // Checking only for an absent dollar figure let the em-dash rows through uncaught.
            assertFalse(
                "DEGRADED must not render the em dash in any VISIBLE row either - the balance " +
                    "rows must be HIDDEN entirely, not shown as an unknown value: $balanceLabels",
                balanceLabels.any { it.visible && it.text == EM_DASH }
            )
            assertFalse(
                "DEGRADED must not show the key-limit block either - no account data at all",
                statusTab.componentForTest(TestComponent.KEY_LIMIT).isVisible
            )

            // Fix round 1, finding 2: show(emptyList()) - what DEGRADED used to call - renders
            // NO_ACTIVITY_TEXT, which BreakdownBlock's own KDoc documents as "queried
            // successfully, found nothing". DEGRADED queries nothing at all, so that would tell
            // the user they spent $0 when nobody checked.
            val breakdownLabels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN))
            assertFalse(
                "DEGRADED must never claim the false-negative 'no activity in this period' " +
                    "answer - nothing was queried: $breakdownLabels",
                breakdownLabels.any { it.visible && it.text == NO_ACTIVITY_TEXT }
            )
            assertTrue(
                "DEGRADED's breakdown must name the real cause instead - a missing provisioning " +
                    "key: $breakdownLabels",
                breakdownLabels.any { it.visible && it.text.contains("provisioning key", ignoreCase = true) }
            )

            layoutAtRealisticWidthAndAssertNotClipped(statusTab.component)
        } finally {
            OpenRouterStatsCache.getInstance().clearCache()
            statusTab.dispose()
        }
    }

    /**
     * Fix round 1, finding 2 (LOADING half): the same `show(emptyList())` mistake was
     * pre-existing in `renderLoading` - nothing has returned yet, so "no activity in this period"
     * claims an answer that has not arrived.
     */
    fun testLoadingStateBreakdownDoesNotClaimNoActivityBeforeAnyQueryHasReturned() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.clearCache()
            setStatsCacheLoading(true)
            `when`(settingsService.isConfigured()).thenReturn(true)
            `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

            statusTab.renderForTest()

            assertEquals(StatusTabState.State.LOADING, statusTab.getStateForTest())

            val breakdownLabels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN))
            assertFalse(
                "LOADING has not queried anything yet - it must not claim 'no activity in this " +
                    "period': $breakdownLabels",
                breakdownLabels.any { it.visible && it.text == NO_ACTIVITY_TEXT }
            )
            // Fix round 2, finding 2: the assertion above only proves the WRONG line is gone - it
            // would also pass if LOADING mistakenly called showError(). Pin what LOADING must
            // actually say, not merely what it must not.
            assertTrue(
                "LOADING must show its own loading placeholder, VISIBLE, not merely omit the " +
                    "wrong one: $breakdownLabels",
                breakdownLabels.any { it.visible && it.text == LOADING_BREAKDOWN_TEXT }
            )
        } finally {
            setStatsCacheLoading(false)
            sharedCache.clearCache()
            statusTab.dispose()
        }
    }

    /**
     * D12: DEGRADED's sparkline measures a DIFFERENT quantity than READY's - locally observed
     * spend from [CreditUsageHistoryService], not analytics' server-side usage - so its caption
     * must say so, never reuse a label that could be mistaken for the same series.
     */
    fun testDegradedStateSparklineIsLabelledAsLocallyObservedSpendNotTheSameSeriesAsReady() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val historyService = CreditUsageHistoryService.getInstance()
        val originalSnapshots = historyService.getState().snapshots.toList()
        try {
            historyService.getState().snapshots.clear()
            val now = System.currentTimeMillis()
            historyService.getState().snapshots.addAll(
                listOf(
                    CreditUsageHistoryService.CreditSnapshot(timestampUtc = now - TWO_SECONDS_MS, totalUsed = 10.0),
                    CreditUsageHistoryService.CreditSnapshot(timestampUtc = now - ONE_SECOND_MS, totalUsed = 13.0)
                )
            )

            val statusTab = StatusTabPanel(project, settingsService)
            try {
                assertEquals(StatusTabState.State.DEGRADED, statusTab.getStateForTest())

                val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
                assertTrue(
                    "with local history present, DEGRADED's caption must say it is measuring " +
                        "locally observed spend, found: ${labels.map { it.text }}",
                    labels.any { it.visible && it.text.contains("Locally observed spend", ignoreCase = true) }
                )
            } finally {
                statusTab.dispose()
            }
        } finally {
            historyService.getState().snapshots.clear()
            historyService.getState().snapshots.addAll(originalSnapshots)
            OpenRouterStatsCache.getInstance().clearCache()
        }
    }

    /**
     * Positive control for the caption test above, and for [DegradedSpend]'s own "fewer than two
     * snapshots" rule reaching the panel: with no local history to difference, the caption must
     * not appear at all (matching [BalanceBlock]'s existing "blank label hides the row" rule),
     * rather than showing an empty chart under a caption that claims to measure something.
     */
    fun testDegradedStateWithNoLocalHistoryHidesTheSparklineCaptionEntirely() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val historyService = CreditUsageHistoryService.getInstance()
        val originalSnapshots = historyService.getState().snapshots.toList()
        try {
            historyService.getState().snapshots.clear()

            val statusTab = StatusTabPanel(project, settingsService)
            try {
                assertEquals(StatusTabState.State.DEGRADED, statusTab.getStateForTest())

                val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
                assertFalse(
                    "no local history means nothing to plot - the caption must not appear, " +
                        "found: ${labels.map { it.text }}",
                    labels.any { it.visible && it.text.contains("Locally observed spend", ignoreCase = true) }
                )
            } finally {
                statusTab.dispose()
            }
        } finally {
            historyService.getState().snapshots.clear()
            historyService.getState().snapshots.addAll(originalSnapshots)
            OpenRouterStatsCache.getInstance().clearCache()
        }
    }

    fun testLoadingStateClearsTheBalanceAndKeyLimitBlocksAndHidesTheDegradedBanner() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.clearCache()
            setStatsCacheLoading(true)
            `when`(settingsService.isConfigured()).thenReturn(true)
            `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

            statusTab.renderForTest()

            assertEquals(StatusTabState.State.LOADING, statusTab.getStateForTest())

            val texts = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE)).map { it.text }
            assertFalse(
                "LOADING must never render a dollar figure - there is no data yet: $texts",
                texts.any { it.contains(Regex("""\$[0-9]""")) }
            )
            assertFalse(
                "LOADING must not show the DEGRADED banner - a provisioning key is present",
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE).isVisible
            )

            layoutAtRealisticWidthAndAssertNotClipped(statusTab.component)
        } finally {
            setStatsCacheLoading(false)
            sharedCache.clearCache()
            statusTab.dispose()
        }
    }

    fun testReadyStateRendersRealNumbersWithAFreshnessLineAndHidesTheDegradedBanner() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.updateFromPopup(
                CreditsResponse(CreditsData(totalCredits = READY_TOTAL, totalUsage = READY_USAGE)),
                null,
                ApiKeysListResponse(data = emptyList())
            )
            `when`(settingsService.isConfigured()).thenReturn(true)
            `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

            statusTab.renderForTest()

            assertEquals(StatusTabState.State.READY, statusTab.getStateForTest())
            assertFalse(
                "READY must not show the DEGRADED banner - a provisioning key is present",
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE).isVisible
            )

            // Fix round 1, finding 5: checked via VISIBLE rows, not merely text presence anywhere
            // in the tree - a regression that renders the right text into a hidden row (e.g. by
            // reusing showLocalSeriesOnly()'s hidden remainingLabel by mistake) must fail this.
            val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
            assertTrue(
                "READY must render the real remaining balance in a VISIBLE row: $labels",
                labels.any {
                    it.visible &&
                        it.text == "\$${String.format(java.util.Locale.US, "%.2f", READY_TOTAL - READY_USAGE)} remaining"
                }
            )
            assertTrue(
                "READY must show a VISIBLE freshness line once the cache has a real timestamp: $labels",
                labels.any { it.visible && it.text.startsWith("Updated ") }
            )

            layoutAtRealisticWidthAndAssertNotClipped(statusTab.component)
        } finally {
            sharedCache.clearCache()
            statusTab.dispose()
        }
    }

    /**
     * Direct proof of the brief's own "an error with cached data still shows the numbers" ask:
     * the last known balance stays fully rendered beside a VISIBLE "couldn't refresh" line -
     * never a blank panel, and never the DEGRADED banner (the cause here is a failed network
     * refresh, not a missing provisioning key).
     */
    fun testErrorStateKeepsTheLastKnownNumbersVisibleWithAVisibleCouldntRefreshLine() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.updateFromPopup(
                CreditsResponse(CreditsData(totalCredits = ERROR_TOTAL, totalUsage = ERROR_USAGE)),
                null,
                ApiKeysListResponse(data = emptyList())
            )
            setStatsCacheLastError("Network error: timeout")
            `when`(settingsService.isConfigured()).thenReturn(true)
            `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

            statusTab.renderForTest()

            assertEquals(StatusTabState.State.ERROR, statusTab.getStateForTest())

            val labels = collectLabelSnapshots(statusTab.component)
            assertTrue(
                "ERROR must keep the last known balance VISIBLE, not blank it out: $labels",
                labels.any {
                    it.visible &&
                        it.text == "\$${String.format(java.util.Locale.US, "%.2f", ERROR_TOTAL - ERROR_USAGE)} remaining"
                }
            )
            assertTrue(
                "ERROR must show a VISIBLE line naming that the refresh failed: $labels",
                labels.any { it.visible && it.text.contains("Couldn't refresh", ignoreCase = true) }
            )
            // Fix round 1, finding 4: the brief's own "matters most in ERROR" case - the last
            // known numbers stay on screen, so they must carry WHEN they were last true. Removing
            // lastUpdatedText from renderCredits only ever failed a READY assertion before this.
            assertTrue(
                "ERROR must show a VISIBLE freshness line beside the stale numbers - an hour-old " +
                    "balance during an outage must not look as fresh as one from a second ago: $labels",
                labels.any { it.visible && it.text.startsWith("Updated ") }
            )
            assertFalse(
                "ERROR must not show the DEGRADED banner - a provisioning key is present, the " +
                    "failure here is a network/transport one",
                statusTab.componentForTest(TestComponent.DEGRADED_NOTICE).isVisible
            )

            layoutAtRealisticWidthAndAssertNotClipped(statusTab.component)
        } finally {
            sharedCache.clearCache()
            setStatsCacheLastError(null)
            statusTab.dispose()
        }
    }

    // --- Task 11: refresh on activation, gated so flicking tabs cannot burst requests -----------

    /**
     * Direct proof that [StatusTabPanel.onActivated] is gated: three calls made back to back
     * (simulating rapid tab-flicking) must refresh at most once. Uses an [AnalyticsService]
     * pointed at nothing reachable (`isAvailable() == false`) so even the one permitted refresh
     * takes the degraded, network-free path - the gate's OWN time-based behaviour (that it opens
     * again once the threshold elapses) is [ActivationRefreshGateTest]'s job, using a fake clock;
     * this proves [StatusTabPanel] actually wires the gate in and forwards its verdict.
     */
    fun testFlickingTabsWithinTheThresholdTriggersAtMostOneRefresh() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")
        val analyticsService = AnalyticsService(baseUrlOverride = null, provisioningKeyProvider = { "" })

        val statusTab = StatusTabPanel(project, settingsService, analyticsService)
        try {
            val results = listOf(statusTab.onActivated(), statusTab.onActivated(), statusTab.onActivated())

            assertEquals(
                "a burst of tab-flicking must refresh at most once, not on every call: $results",
                listOf(true, false, false),
                results
            )
        } finally {
            statusTab.dispose()
        }
    }

    /**
     * Fix round 1, finding 6: [StatusTabPanel.onActivated] must run a full [StatusTabPanel.render]
     * when its gate opens, not only [refreshBreakdown] - otherwise the freshness line freezes
     * (switching away for an hour and back would still read "Updated moments ago", a stale number
     * presented confidently), AND, more sharply testable without a real clock: a bare
     * `refreshBreakdown()` call would blindly re-run the DEGRADED (no-key) `ActivityAggregator`
     * path over whatever activity happens to be cached, even in DEGRADED, where the breakdown must
     * never show real numbers at all (finding 2). This seeds real cached activity from a time
     * when a provisioning key WAS presumably set, then proves `onActivated()` does not let it leak
     * back into DEGRADED's breakdown.
     */
    fun testOnActivatedReRendersDegradedRatherThanBlindlyRefreshingTheBreakdown() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("")
        val analyticsService = AnalyticsService(baseUrlOverride = null, provisioningKeyProvider = { "" })

        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.updateFromPopup(
                CreditsResponse(CreditsData(totalCredits = READY_TOTAL, totalUsage = READY_USAGE)),
                ActivityResponse(
                    listOf(
                        ActivityData(
                            date = LocalDate.now().toString(),
                            model = LEAKED_MODEL_NAME,
                            modelPermaslug = null,
                            endpointId = null,
                            providerName = null,
                            usage = 1.23,
                            byokUsageInference = null,
                            requests = 3,
                            promptTokens = null,
                            completionTokens = null,
                            reasoningTokens = null
                        )
                    )
                ),
                ApiKeysListResponse(data = emptyList())
            )

            val statusTab = StatusTabPanel(project, settingsService, analyticsService)
            try {
                assertEquals(StatusTabState.State.DEGRADED, statusTab.getStateForTest())

                statusTab.onActivated()

                val breakdownLabels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BREAKDOWN))
                assertFalse(
                    "onActivated() must not blindly re-run the degraded (no-key) breakdown path " +
                        "over real cached activity while DEGRADED - DEGRADED's breakdown must " +
                        "never show a real-looking model row: $breakdownLabels",
                    breakdownLabels.any { it.visible && it.text == LEAKED_MODEL_NAME }
                )
                assertTrue(
                    "onActivated() must keep DEGRADED's own 'needs a provisioning key' line: $breakdownLabels",
                    breakdownLabels.any { it.visible && it.text.contains("provisioning key", ignoreCase = true) }
                )
            } finally {
                statusTab.dispose()
            }
        } finally {
            sharedCache.clearCache()
        }
    }

    /**
     * Fix round 1, finding 8: an untested guard is not a real guard. Without
     * `if (timestamp <= 0L) return ""` in [StatusTabPanel]'s `lastUpdatedText`, a zero/absent
     * timestamp (the shared cache's own "never updated" sentinel) would flow straight into
     * [com.intellij.util.text.DateFormatUtil.formatBetweenDates] and render a decades-old
     * "ago" figure as if it were a real reading, in a VISIBLE row.
     */
    fun testZeroLastUpdateTimestampHidesTheFreshnessRowInsteadOfRenderingOne() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        val sharedCache = OpenRouterStatsCache.getInstance()
        try {
            sharedCache.updateFromPopup(
                CreditsResponse(CreditsData(totalCredits = READY_TOTAL, totalUsage = READY_USAGE)),
                null,
                ApiKeysListResponse(data = emptyList())
            )
            setStatsCacheLastUpdateTimestamp(0L)
            `when`(settingsService.isConfigured()).thenReturn(true)
            `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

            statusTab.renderForTest()

            assertEquals(StatusTabState.State.READY, statusTab.getStateForTest())

            val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
            assertFalse(
                "a zero/absent last-update timestamp must hide the freshness row entirely, not " +
                    "render a decades-old reading as if it were real: $labels",
                labels.any { it.visible && it.text.startsWith("Updated ") }
            )
        } finally {
            sharedCache.clearCache()
            statusTab.dispose()
        }
    }

    // --- Task 13: the READY/ERROR spend sparkline, burn rate and days-left estimate -------------

    /**
     * Builds a [StatusTabPanel] already in READY, reached via [analyticsService]'s LOCAL
     * (no-network) breakdown fallback - [analyticsService] must report `isAvailable() == false` at
     * the moment this is called (e.g. a provisioning key captured in a `var` a caller has not yet
     * set), so this setup's own implicit `refreshBreakdown()` call cannot race a [MockWebServer]
     * response the caller enqueues for a LATER, deliberate `applySpendSeriesResultForTest` call
     * (fix round 2, finding 5: mixing the two was exactly what made the READY/ERROR-only tests
     * below pass while actually driving the panel through NOT_CONFIGURED in fix round 1).
     */
    private fun buildReadyStatusTab(
        settingsService: OpenRouterSettingsService,
        analyticsService: AnalyticsService,
        sharedCache: OpenRouterStatsCache
    ): StatusTabPanel {
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")
        val statusTab = StatusTabPanel(project, settingsService, analyticsService)

        sharedCache.updateFromPopup(
            CreditsResponse(CreditsData(totalCredits = READY_TOTAL, totalUsage = READY_USAGE)),
            null,
            ApiKeysListResponse(data = emptyList())
        )
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")
        statusTab.renderForTest()

        return statusTab
    }

    /**
     * Direct proof that Task 13 closes the gap [StatusTabPanel.renderCredits] used to leave open:
     * with a real spend-series query answered, the burn rate, the days-left estimate and the
     * sparkline's own caption must actually render from it - not the `NO_PER_DAY_RATE`/`NO_SERIES`
     * placeholders `renderCredits` used to pass unconditionally, which only ever surfaced this
     * chart in DEGRADED, fed from local history instead.
     *
     * The 7 daily buckets below are WEEK's own window: 6 completed days at $3.75 each, then a
     * deliberately huge $999.00 for TODAY (2026-09-19, the still-filling final bucket fix round 2
     * findings 1/2 require [AnalyticsBreakdown.burnRatePerDay] to drop). If it were wrongly
     * included, the rate would come out far higher than $3.75 - this is the same red-before-green
     * shape as the dedicated `AnalyticsBreakdownTest` cases, exercised here through the real panel.
     */
    fun testSpendSeriesQuerySuccessPopulatesBurnRateDaysLeftAndTheSparklineCaption() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":{"data":[""" +
                        """{"date__day":"2026-09-13T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-14T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-15T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-16T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-17T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-18T00:00:00.000Z","total_usage":3.75},""" +
                        """{"date__day":"2026-09-19T00:00:00.000Z","total_usage":999.0}""" +
                        """],"metadata":{"row_count":7,"truncated":false}}}"""
                )
            )
            var provisioningKeyForTest = ""
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { provisioningKeyForTest }
            )
            val sharedCache = OpenRouterStatsCache.getInstance()

            val statusTab = buildReadyStatusTab(settingsService, analyticsService, sharedCache)
            try {
                assertEquals(StatusTabState.State.READY, statusTab.getStateForTest())
                provisioningKeyForTest = "test-key"

                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.WEEK)

                val labels = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE))
                val texts = labels.map { it.text }

                assertTrue(
                    "today's still-filling \$999.00 bucket must be dropped; the 6 completed days " +
                        "at \$3.75 each (sum 22.5) divided by 6 is \$3.75/day: $texts",
                    labels.any { it.visible && it.text == "\$3.75/day burn rate" }
                )
                assertTrue(
                    "remaining (37.5) / burn rate (3.75) is exactly 10 - proves the days-left row " +
                        "is really wired to the analytics-derived rate, not left hidden: $texts",
                    labels.any { it.visible && it.text == "10 days left" }
                )
                assertTrue(
                    "the caption must name the quantity (server-reported daily spend) AND the " +
                        "window (7 days, since WEEK was queried): $texts",
                    labels.any { it.visible && it.text == "Daily spend (server-reported), last 7 days" }
                )
                assertFalse(
                    "READY's caption must never equal DEGRADED's own label - D12 forbids two " +
                        "different quantities sharing a caption: $texts",
                    texts.contains(DEGRADED_CAPTION)
                )
            } finally {
                sharedCache.clearCache()
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * Direct proof of fix round 2, finding 5: a spend-series query resolving AFTER the panel has
     * already left READY/ERROR must NOT re-render the balance rows the CURRENT state's own render
     * path deliberately cleared. Before the fix, this was exactly the shape of bug the test above
     * had accidentally been passing with in fix round 1 (built against NOT_CONFIGURED instead of
     * a legitimate READY/ERROR state) - here the panel starts genuinely READY, is explicitly
     * driven to NOT_CONFIGURED, and only THEN does the (now late-arriving) query resolve.
     */
    fun testLateSpendSeriesSuccessDoesNotReshowTheBalanceAfterLeavingReadyOrError() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":{"data":[""" +
                        """{"date__day":"2026-09-13T00:00:00.000Z","total_usage":3.0},""" +
                        """{"date__day":"2026-09-19T00:00:00.000Z","total_usage":3.0}""" +
                        """],"metadata":{"row_count":2,"truncated":false}}}"""
                )
            )
            var provisioningKeyForTest = ""
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { provisioningKeyForTest }
            )
            val sharedCache = OpenRouterStatsCache.getInstance()

            val statusTab = buildReadyStatusTab(settingsService, analyticsService, sharedCache)
            try {
                assertEquals(StatusTabState.State.READY, statusTab.getStateForTest())

                // Simulate the provisioning key being removed (or any other cause) while the
                // query above is conceptually "in flight" - the panel leaves READY for
                // NOT_CONFIGURED, whose own render path clears the balance.
                `when`(settingsService.isConfigured()).thenReturn(false)
                statusTab.renderForTest()
                assertEquals(StatusTabState.State.NOT_CONFIGURED, statusTab.getStateForTest())

                provisioningKeyForTest = "test-key"
                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.WEEK)

                val texts = collectLabelSnapshots(statusTab.componentForTest(TestComponent.BALANCE)).map { it.text }
                assertFalse(
                    "a spend-series success resolving after the panel left READY must not " +
                        "re-show a dollar figure NOT_CONFIGURED's own render path just cleared: $texts",
                    texts.any { it.contains(Regex("""\$[0-9]""")) }
                )
                assertFalse(
                    "the server-reported caption must not leak into NOT_CONFIGURED either: $texts",
                    texts.any { it.contains("server-reported") }
                )
            } finally {
                sharedCache.clearCache()
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * D12 applied to the spend series - the same rule
     * [testAnalyticsErrorRendersExplicitErrorLineNotASilentActivityAggregatorFallback] already
     * pins for the breakdown: a failed spend-series query must never substitute a fabricated or
     * borrowed number. Drives one SUCCESSFUL query, then a second query (a different period, so it
     * cannot be served from [AnalyticsService]'s own request cache) that FAILS, and proves the
     * balance still shows the first, real answer - not an em dash, and not a number computed any
     * other way.
     */
    fun testAnalyticsErrorOnTheSpendSeriesLeavesThePreviousGoodReadingInPlace() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"data":{"data":[""" +
                        """{"date__day":"2026-09-13T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-14T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-15T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-16T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-17T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-18T00:00:00.000Z","total_usage":4.0},""" +
                        """{"date__day":"2026-09-19T00:00:00.000Z","total_usage":500.0}""" +
                        """],"metadata":{"row_count":7,"truncated":false}}}"""
                )
            )
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))

            var provisioningKeyForTest = ""
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { provisioningKeyForTest }
            )
            val sharedCache = OpenRouterStatsCache.getInstance()

            // WEEK then MONTH (not the same period twice): AnalyticsService caches by request, so
            // an identical second request would be served from cache rather than actually
            // reaching the enqueued 500 - a different period is a different request regardless.
            val statusTab = buildReadyStatusTab(settingsService, analyticsService, sharedCache)
            try {
                provisioningKeyForTest = "test-key"

                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.WEEK)
                val afterSuccess = collectLabelSnapshots(
                    statusTab.componentForTest(TestComponent.BALANCE)
                ).map { it.text }
                assertTrue(
                    "sanity check: 6 completed days at \$4.00 each (today's \$500.00 dropped) " +
                        "must render \$4.00/day: $afterSuccess",
                    afterSuccess.any { it == "\$4.00/day burn rate" }
                )

                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.MONTH)
                val afterError = collectLabelSnapshots(
                    statusTab.componentForTest(TestComponent.BALANCE)
                ).map { it.text }
                assertTrue(
                    "a failed query must leave the LAST KNOWN GOOD reading in place, not blank it " +
                        "or fabricate a new one: $afterError",
                    afterError.any { it == "\$4.00/day burn rate" }
                )
            } finally {
                sharedCache.clearCache()
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * Task 13's own "which window" decision: the account-wide spend series follows the SAME
     * period field the per-model breakdown already uses
     * ([StatusTabPanel.getCurrentBreakdownPeriodForTest]) rather than a fixed window of its own -
     * selecting a period in the REAL period ComboBox must update the exact field
     * `refreshBreakdown()` reads once and passes to both queries, so the two can never silently
     * drift onto different windows.
     */
    fun testSelectingAPeriodUpdatesTheSameFieldBothTheBreakdownAndSpendSeriesQueriesRead() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(true)
        `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertEquals(
                "BreakdownBlock's own default is DAY (24 hours) - see its 'defaulting to 24 hours' contract",
                ActivityAggregator.Period.DAY,
                statusTab.getCurrentBreakdownPeriodForTest()
            )

            val combo = findPeriodCombo(statusTab.component)
            combo.selectedItem = ActivityAggregator.Period.WEEK

            assertEquals(
                "selecting WEEK in the real period ComboBox must update currentBreakdownPeriod - " +
                    "the same field refreshBreakdown() reads once and passes to both the breakdown " +
                    "AND the spend-series query",
                ActivityAggregator.Period.WEEK,
                statusTab.getCurrentBreakdownPeriodForTest()
            )
        } finally {
            statusTab.dispose()
        }
    }

    /**
     * [StatusTabPanel.applySpendSeriesResultForTest] must actually use the
     * [ActivityAggregator.Period] it is given, not a hardcoded default - the specific regression
     * class a period-selector fix could still hide, since
     * [testSelectingAPeriodUpdatesTheSameFieldBothTheBreakdownAndSpendSeriesQueriesRead] above
     * only ever inspects the shared FIELD, never a query's actual result.
     */
    fun testSpendSeriesResultReflectsWhicheverPeriodItIsGivenNotAHardcodedDefault() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val monthRow = """{"data":{"data":[{"date__day":"2026-09-19T00:00:00.000Z","total_usage":1.0}],""" +
                """"metadata":{"row_count":1,"truncated":false}}}"""
            // DAY's own granularity is HOUR (fix round 2, finding 3), so its response buckets
            // under date__hour, not date__day - using that key here is what makes this test
            // realistic rather than merely tolerated by toSpendSeries' prefix match.
            val dayRow = """{"data":{"data":[{"date__hour":"2026-09-19T08:00:00.000Z","total_usage":1.0}],""" +
                """"metadata":{"row_count":1,"truncated":false}}}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(monthRow))
            server.enqueue(MockResponse().setResponseCode(200).setBody(dayRow))

            var provisioningKeyForTest = ""
            val settingsService = mock(OpenRouterSettingsService::class.java)
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { provisioningKeyForTest }
            )
            val sharedCache = OpenRouterStatsCache.getInstance()

            val statusTab = buildReadyStatusTab(settingsService, analyticsService, sharedCache)
            try {
                provisioningKeyForTest = "test-key"

                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.MONTH)
                val monthTexts = collectLabelSnapshots(
                    statusTab.componentForTest(TestComponent.BALANCE)
                ).map { it.text }
                assertTrue(
                    "querying MONTH must render MONTH's own caption ('last 30 days'): $monthTexts",
                    monthTexts.any { it == "Daily spend (server-reported), last 30 days" }
                )

                statusTab.applySpendSeriesResultForTest(ActivityAggregator.Period.DAY)
                val dayTexts = collectLabelSnapshots(
                    statusTab.componentForTest(TestComponent.BALANCE)
                ).map { it.text }
                assertTrue(
                    "querying DAY afterwards must render DAY's own caption ('today so far'), " +
                        "proving the function is not stuck on whichever period it saw first: $dayTexts",
                    dayTexts.any { it == "Daily spend (server-reported), today so far" }
                )
            } finally {
                sharedCache.clearCache()
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * Direct end-to-end proof of fix round 2, finding 4: [StatusTabPanel.refreshBreakdown] really
     * dispatches BOTH analytics queries over the REAL `coroutineScope.launch { }` /
     * `Dispatchers.Main` path - not merely through the `applyXResultForTest` bypass every other
     * test in this file uses (which would still pass even if the production `launch` call for the
     * spend series were deleted entirely). [PlatformTestUtil.dispatchAllInvocationEvents] drains
     * the EDT - where the platform's `Dispatchers.Main` resumes coroutines dispatched via
     * `ApplicationManager.invokeLater` - in a bounded poll loop until [MockWebServer] has actually
     * received both requests, so this does not depend on either query's response arriving inside
     * a single pump.
     */
    fun testRefreshBreakdownActuallyDispatchesBothAnalyticsQueriesOverTheRealAsyncPath() {
        val server = MockWebServer()
        server.start()
        try {
            val emptyBody = """{"data":{"data":[],"metadata":{"row_count":0,"truncated":false}}}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(emptyBody))
            server.enqueue(MockResponse().setResponseCode(200).setBody(emptyBody))

            val settingsService = mock(OpenRouterSettingsService::class.java)
            `when`(settingsService.isConfigured()).thenReturn(false)
            `when`(settingsService.getProvisioningKey()).thenReturn("")
            val analyticsService = AnalyticsService(
                baseUrlOverride = server.url("/api/v1").toString(),
                provisioningKeyProvider = { "test-key" }
            )

            val statusTab = StatusTabPanel(project, settingsService, analyticsService)
            val sharedCache = OpenRouterStatsCache.getInstance()
            try {
                // Sets the cache's own field directly via reflection rather than
                // updateFromPopup(), which - now that statusTab's messageBus subscription is
                // already active - would ALSO schedule its own async onStatsUpdated -> render()
                // notification (via ApplicationManager.invokeLater), racing the explicit
                // renderForTest() call below and non-deterministically doubling the request
                // count this test means to pin exactly.
                setCachedCredits(CreditsData(totalCredits = READY_TOTAL, totalUsage = READY_USAGE))
                `when`(settingsService.isConfigured()).thenReturn(true)
                `when`(settingsService.getProvisioningKey()).thenReturn("a-provisioning-key")

                statusTab.renderForTest()
                assertEquals(StatusTabState.State.READY, statusTab.getStateForTest())

                awaitServerRequests(server, expectedCount = 2)

                assertEquals(
                    "renderForTest() with a provisioning key present and analytics available " +
                        "must dispatch BOTH the breakdown query AND the spend-series query over " +
                        "the real coroutineScope.launch { } path, not just through the " +
                        "applyXResultForTest bypass - deleting the spend-series launch call " +
                        "would leave this at 1",
                    2,
                    server.requestCount
                )
            } finally {
                sharedCache.clearCache()
                statusTab.dispose()
            }
        } finally {
            server.shutdown()
        }
    }

    /**
     * Waits for [server]'s own request count to reach [expectedCount], pumping the IDE event queue
     * ([PlatformTestUtil.waitWithEventsDispatching]) while it does - the platform's
     * `Dispatchers.Main` resumes coroutines via `ApplicationManager.invokeLater`, so without this
     * pump a coroutine dispatched onto it would sit queued forever with nothing to run it. Bounded
     * by [timeoutSeconds] so a genuine regression fails the test instead of hanging the build.
     */
    private fun awaitServerRequests(
        server: MockWebServer,
        expectedCount: Int,
        timeoutSeconds: Int = AWAIT_TIMEOUT_SECONDS
    ) {
        PlatformTestUtil.waitWithEventsDispatching(
            "expected $expectedCount request(s), server has ${server.requestCount}",
            { server.requestCount >= expectedCount },
            timeoutSeconds
        )
    }

    /** Sets [OpenRouterStatsCache]'s own `cachedCredits` field directly via reflection - unlike
     * `updateFromPopup()`, this does not also schedule an async `onStatsUpdated` notification (see
     * [testRefreshBreakdownActuallyDispatchesBothAnalyticsQueriesOverTheRealAsyncPath]'s own
     * comment for why that matters once a real messageBus subscription is active). */
    private fun setCachedCredits(credits: CreditsData) {
        val field = OpenRouterStatsCache::class.java.getDeclaredField("cachedCredits")
        field.isAccessible = true
        field.set(OpenRouterStatsCache.getInstance(), credits)
    }

    private fun setStatsCacheLastUpdateTimestamp(value: Long) {
        val field = OpenRouterStatsCache::class.java.getDeclaredField("lastUpdateTimestamp")
        field.isAccessible = true
        field.set(OpenRouterStatsCache.getInstance(), value)
    }

    private fun setStatsCacheLoading(loading: Boolean) {
        val field = OpenRouterStatsCache::class.java.getDeclaredField("isLoading")
        field.isAccessible = true
        (field.get(OpenRouterStatsCache.getInstance()) as AtomicBoolean).set(loading)
    }

    private fun setStatsCacheLastError(message: String?) {
        val field = OpenRouterStatsCache::class.java.getDeclaredField("lastError")
        field.isAccessible = true
        field.set(OpenRouterStatsCache.getInstance(), message)
    }

    /** Two layout passes at a realistic width - width applied before any height is read, the bug
     * class this branch has shipped twice - then the standard clipping floor check. */
    private fun layoutAtRealisticWidthAndAssertNotClipped(panel: Container) {
        panel.setSize(LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        panel.setSize(LAYOUT_WIDTH, panel.preferredSize.height)
        layoutTreeRecursively(panel)
        assertNoDescendantClippedByBottomEdge(panel)
    }
}

private const val TWO_SECONDS_MS = 2000L
private const val ONE_SECOND_MS = 1000L
private const val READY_TOTAL = 50.0
private const val READY_USAGE = 12.5
private const val ERROR_TOTAL = 80.0
private const val ERROR_USAGE = 30.0
private const val LEAKED_MODEL_NAME = "anthropic/claude-sonnet-4.5"
private const val DEGRADED_CAPTION = "Locally observed spend (no provisioning key)"
private const val AWAIT_TIMEOUT_SECONDS = 10
