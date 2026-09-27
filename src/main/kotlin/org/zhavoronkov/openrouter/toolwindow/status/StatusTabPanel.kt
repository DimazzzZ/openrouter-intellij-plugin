package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.zhavoronkov.openrouter.listeners.OpenRouterStatsListener
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.CreditsData
import org.zhavoronkov.openrouter.services.AnalyticsService
import org.zhavoronkov.openrouter.services.CreditUsageHistoryService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.OpenRouterStatsCache
import org.zhavoronkov.openrouter.settings.OpenRouterConfigurable
import org.zhavoronkov.openrouter.utils.applicationServiceOrNull
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.time.LocalDate
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Status tab panel for the OpenRouter tool window.
 *
 * Reads exclusively from [OpenRouterStatsCache], the single source of truth also used by the
 * status-bar widget and the stats popup. This tab used to call [org.zhavoronkov.openrouter.services.OpenRouterService]
 * directly, which produced its own, uncoordinated fetch and its own numbers - including a
 * "Quota" figure computed from API key limits (via `getQuotaInfo()`) presented as if it were the
 * account balance, and a percentage divided by a total that was silently `0.0` for any key
 * without an explicit limit.
 *
 * @param analyticsService owned here, not by [BreakdownBlock] - "BreakdownBlock owns no
 *   fetching" (Task 9 brief) - so this panel is the one that decides, in [refreshBreakdown],
 *   which of the two paths answers a period. Defaulted to a real instance in production;
 *   overridable so a test can point it at a [okhttp3.mockwebserver.MockWebServer] instead of the
 *   real OpenRouter API - see [applyAnalyticsResultForTest].
 */
class StatusTabPanel(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService,
    private val analyticsService: AnalyticsService = AnalyticsService()
) : Disposable {

    companion object {
        // UI Dimensions
        private const val TITLE_FONT_SIZE = 16f
        private const val CONTENT_SPACING = 5

        /** `GridBagConstraints.weightx` for every full-width row (the four blocks, the
         * configuration panel, and the Status row's value column) - see [createContentPanel]'s own
         * KDoc for why `weightx`, not `fill` alone, is what makes a row actually absorb the tool
         * window's real width instead of centring at its preferred width. */
        private const val FULL_WIDTH_WEIGHT = 1.0
        private const val LABEL_SPACING_LARGE = 20
        private const val CONFIGURATION_PANEL_BORDER = 10

        // Grid position constants
        private const val GRID_STATUS_ROW = 0
        private const val GRID_DEGRADED_ROW = 1
        private const val GRID_BALANCE_ROW = 2
        private const val GRID_KEY_LIMIT_ROW = 3
        private const val GRID_BREAKDOWN_ROW = 4
        private const val GRID_CONFIG_ROW = 5

        /** [BreakdownBlock]'s own default period - see its "defaulting to 24 hours" contract. */
        private val DEFAULT_BREAKDOWN_PERIOD = ActivityAggregator.Period.DAY

        /** [BreakdownBlock]'s own default dimension (G6) - see its "defaulting to by model" contract. */
        private val DEFAULT_BREAKDOWN_DIMENSION = AnalyticsBreakdown.Dimension.MODEL

        /** What DEGRADED's sparkline measures - a DIFFERENT quantity than READY's (D12): locally
         * observed spend from [CreditUsageHistoryService], not the analytics API's server-side
         * usage. Labelling the two identically would let two users compare "the same chart" and
         * get different numbers. Blank (like [NO_SERIES_LABEL]) when there is no local history to
         * plot, so the caption/sparkline pair hides itself exactly as it does everywhere else. */
        private const val DEGRADED_SERIES_LABEL = "Locally observed spend (no management key)"

        /** What READY/ERROR's sparkline measures (Task 13) - the analytics API's own account-wide
         * spend total ([AnalyticsBreakdown.spendSeriesRequestFor]/[AnalyticsBreakdown.toSpendSeries]),
         * never [DEGRADED_SERIES_LABEL]'s locally-observed one. Each names both the QUANTITY
         * ("Hourly"/"Daily spend", "server-reported" - see [READY_SERIES_LABEL_DAY]'s own KDoc for
         * why DAY's quantity word differs from WEEK/MONTH's) and the WINDOW it covers (D12: two
         * users comparing "the same chart" must get the same numbers, which starts with the
         * caption saying what window it is) - and stays textually distinct from
         * [DEGRADED_SERIES_LABEL] so neither can
         * be mistaken for the other. Keyed by [ActivityAggregator.Period] rather than formatted
         * from it, matching [BreakdownBlock]'s own fixed "24 hours" / "7 days" / "30 days" wording -
         * except for DAY, where fix round 2 (finding 8) deliberately does NOT reuse that "24
         * hours" wording: [AnalyticsBreakdown.spendSeriesRequestFor]'s window for DAY is the
         * current CALENDAR day, not a rolling 24-hour one, and this caption sits directly above a
         * computed rate, so it has to say what the window actually is rather than borrow
         * [BreakdownBlock]'s (different) window's name for a similar-sounding period.
         *
         * DAY's QUANTITY word is "Hourly", not "Daily" (fix round 3, finding 3): the request this
         * caption sits above asks for [AnalyticsBreakdown.spendSeriesRequestFor]'s HOUR
         * granularity for DAY - see its `GRANULARITY_HOUR` branch - so the points behind this
         * caption are hourly amounts. "Daily spend" naming an hourly series is exactly the kind of
         * quantity/caption mismatch D12 forbids, even though it happens to share a prefix with
         * WEEK/MONTH's genuinely daily captions below. */
        private const val READY_SERIES_LABEL_DAY = "Hourly spend (server-reported), today so far"
        private const val READY_SERIES_LABEL_WEEK = "Daily spend (server-reported), last 7 days"
        private const val READY_SERIES_LABEL_MONTH = "Daily spend (server-reported), last 30 days"

        /** How stale the last activation-triggered analytics refresh must be before
         * [onActivated] issues a new one - see [ActivationRefreshGate]'s own KDoc for why this
         * exists at all. */
        private const val ACTIVATION_REFRESH_THRESHOLD_MS = 5 * 60 * 1000L

        // Placeholder arguments for BalanceBlock.update() for the states with no balance to show
        // at all (NOT_CONFIGURED, LOADING) - never used for READY/ERROR's perDay/series/seriesLabel,
        // which come from currentSpendPerDay/currentSpendSeries/currentSpendSeriesLabel (Task 13)
        // instead. BalanceBlock's own "unknown, not zero" rule renders these as em dashes / hidden
        // rows rather than as fabricated numbers. NO_REMAINING in particular must stay null, never
        // 0.0 - a real (if unlikely) $0.00 balance and "no balance known yet" are different facts,
        // and fix round 1 found exactly this pair conflated.
        private val NO_REMAINING: Double? = null
        private val NO_PER_DAY_RATE: Double? = null
        private val NO_SERIES = emptyList<Double>()
        private const val NO_SERIES_LABEL = ""
        private const val NO_LAST_UPDATED_TEXT = ""

        /** No known API key spend cap - [KeyLimitBlock] hides itself for a null limit. */
        private val NO_KEY_LIMIT: Double? = null
    }

    private val statsCache = OpenRouterStatsCache.getInstance()

    private val statusPanel: JPanel
    private var configurationPanel: JPanel? = null
    private val statusLabel = JBLabel("Loading...")
    private val balanceBlock = BalanceBlock()
    private val keyLimitBlock = KeyLimitBlock()
    private val breakdownBlock = BreakdownBlock()

    /** DEGRADED's explanatory banner - see its own KDoc for why this state needs one at all. */
    private val degradedNoticeBlock = DegradedNoticeBlock(
        onConfigure = {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, OpenRouterConfigurable::class.java)
        }
    )

    /** Gates [onActivated]'s analytics re-query so a burst of tab-flicking issues at most one. */
    private val activationRefreshGate = ActivationRefreshGate(ACTIVATION_REFRESH_THRESHOLD_MS)

    /** Counts every [onActivated] call, gated or not - unlike [ActivationRefreshGate.tryAcquire]'s
     * own boolean, which only tells a test "has the gate been consumed at least once", this can
     * distinguish "called once" from "called twice", which is what proves a `ChangeListener` fires
     * for exactly one of the two tabs (fix round 1, finding 3). */
    @Volatile
    private var onActivatedCallCountForTest = 0

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentState: StatusTabState.State = StatusTabState.State.NOT_CONFIGURED

    /** The period [breakdownBlock] currently shows - survives cache refreshes so a period the
     * user picked is not silently reset back to the default on the next stats tick. The
     * account-wide spend series/burn rate (Task 13) follows this SAME field - [refreshBreakdown]
     * reads it once and passes it to both queries - so picking "7 days" for the breakdown can
     * never leave the sparkline showing a different window. */
    private var currentBreakdownPeriod: ActivityAggregator.Period = DEFAULT_BREAKDOWN_PERIOD

    /** The dimension [breakdownBlock] currently groups by (G6) - [AnalyticsBreakdown.Dimension.MODEL]
     * by [DEFAULT_BREAKDOWN_DIMENSION], matching [BreakdownBlock]'s own default selection. Read
     * once by [refreshBreakdown], the same way [currentBreakdownPeriod] already is, so a period
     * change can never silently reset a dimension the user picked, and vice versa. */
    private var currentBreakdownDimension: AnalyticsBreakdown.Dimension = DEFAULT_BREAKDOWN_DIMENSION

    /** The account-wide daily spend series/burn rate/label [renderCredits] feeds to [balanceBlock]
     * in READY/ERROR - populated by [applySpendSeriesResult], never computed inline in
     * [renderCredits] itself, so an analytics error can leave them exactly as they were (D12; see
     * [applySpendSeriesResult]'s own KDoc) instead of [renderCredits] having to invent a fallback.
     * Start at the same "nothing known yet" placeholders [clearBalance] uses, so a state that has
     * never successfully queried analytics renders identically to one that explicitly has none. */
    /** Runs the two analytics queries; see [BreakdownQueries] for why they left this class. */
    private val breakdownQueries = BreakdownQueries(analyticsService)

    private var currentSpendSeries: List<Double> = NO_SERIES
    private var currentSpendPerDay: Double? = NO_PER_DAY_RATE
    private var currentSpendSeriesLabel: String = NO_SERIES_LABEL

    val component: JComponent

    /**
     * Owned by `this`, not by [project]: parenting to the project would outlive this panel and
     * leak the subscription across tool-window closes. This panel must itself be registered as a
     * Disposer child of its owner ([org.zhavoronkov.openrouter.toolwindow.OpenRouterToolWindowContent])
     * - a plain `statusTab.dispose()` method call runs only [dispose]'s body and never asks the
     * Disposer to walk this connection, so the subscription would otherwise survive forever.
     */
    private val statsConnection = ApplicationManager.getApplication().messageBus.connect(this)

    init {
        statusPanel = createStatusPanel()
        component = statusPanel

        breakdownBlock.onPeriodChanged = { period ->
            currentBreakdownPeriod = period
            refreshBreakdown()
        }
        // G6: reuses refreshBreakdown() verbatim rather than a dimension-only variant - it already
        // issues exactly one breakdown query (for whichever dimension currentBreakdownDimension
        // now names) plus the one, dimension-independent spend-series query, so a dimension
        // switch costs the SAME two requests a period switch already does - never a third, and
        // never a query for the dimension not shown (AnalyticsBreakdown.requestFor only ever
        // builds one dimension's query at a time - see its own KDoc).
        breakdownBlock.onDimensionChanged = { dimension ->
            currentBreakdownDimension = dimension
            refreshBreakdown()
        }

        statsConnection.subscribe(
            OpenRouterStatsListener.TOPIC,
            object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    SwingUtilities.invokeLater { render() }
                }

                override fun onStatsLoading() {
                    SwingUtilities.invokeLater { render() }
                }

                override fun onStatsError(errorMessage: String) {
                    SwingUtilities.invokeLater { render() }
                }
            }
        )

        refresh()
    }

    private fun createStatusPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Header
        val headerPanel = JPanel(BorderLayout())
        val titleLabel = JBLabel("OpenRouter API Status")
        titleLabel.font = titleLabel.font.deriveFont(TITLE_FONT_SIZE)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener { refresh() }
        headerPanel.add(refreshButton, BorderLayout.EAST)

        panel.add(headerPanel, BorderLayout.NORTH)

        // Content
        val contentPanel = createContentPanel()
        val scrollPane = JBScrollPane(contentPanel)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createContentPanel(): JPanel {
        // StatusContentPanel, not a bare JPanel(GridBagLayout()) (defect D): see its own KDoc for
        // why this view must implement Scrollable to keep the breakdown's spend column on screen.
        val panel = StatusContentPanel()
        val gbc = GridBagConstraints()

        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(CONTENT_SPACING)

        // Status: a label pair, not a block - the VALUE column (gridx = 1) is given the row's
        // weightx, not the "Status:" label, so a wide tool window grows the space to the right of
        // the value rather than pushing the fixed "Status:" label itself off-centre. `anchor =
        // WEST` (set once, above) keeps statusLabel pinned to the LEFT of its now-wider cell -
        // never floating toward the middle of the panel the way an unanchored fill would.
        gbc.gridx = 0
        gbc.gridy = GRID_STATUS_ROW
        panel.add(JBLabel("Status:"), gbc)
        gbc.gridx = 1
        gbc.weightx = FULL_WIDTH_WEIGHT
        panel.add(statusLabel, gbc)
        gbc.weightx = 0.0

        // Degraded notice: only visible in DEGRADED - see DegradedNoticeBlock. Spans both columns
        // and fills horizontally like the configuration panel below, since it is a banner rather
        // than a single label.
        //
        // `weightx` (not just `fill`) is what actually makes this stretch: `fill` only stretches a
        // component WITHIN its cell, and with every column's weightx at zero `GridBagLayout` sizes
        // the whole grid to its preferred width and centres that narrower block in the container -
        // so `fill` alone never had any extra width to distribute. No `weighty` is added alongside
        // it: that would let this (and every full-width row below) claim a share of any extra
        // VERTICAL space too, pulling the content down away from the top of a tall tool window
        // instead of leaving it exactly where the existing (unweighted) vertical packing puts it.
        gbc.gridx = 0
        gbc.gridy = GRID_DEGRADED_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = FULL_WIDTH_WEIGHT
        panel.add(degradedNoticeBlock.component, gbc)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0

        // Balance: remaining credits, total, burn rate, sparkline - see BalanceBlock. Spans both
        // columns and fills horizontally like the configuration panel below, since it is a whole
        // block rather than a single label. See the degraded notice above for why `weightx` (not
        // `weighty`) is what this row needs.
        gbc.gridx = 0
        gbc.gridy = GRID_BALANCE_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = FULL_WIDTH_WEIGHT
        panel.add(balanceBlock.component, gbc)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0

        // Key limit: the API key's own spend cap - see KeyLimitBlock. Hidden entirely when no
        // key carries one, so unlike the balance/breakdown blocks above it does not always
        // occupy visible space. Spans both columns for the same reason as those blocks; see the
        // degraded notice above for why `weightx` (not `weighty`) is what this row needs.
        gbc.gridx = 0
        gbc.gridy = GRID_KEY_LIMIT_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = FULL_WIDTH_WEIGHT
        panel.add(keyLimitBlock.component, gbc)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0

        // Breakdown: per-model spend over a stated period - see BreakdownBlock. Spans both
        // columns like the balance block above, since it is a whole block, not a single label; see
        // the degraded notice above for why `weightx` (not `weighty`) is what this row needs.
        gbc.gridx = 0
        gbc.gridy = GRID_BREAKDOWN_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = FULL_WIDTH_WEIGHT
        panel.add(breakdownBlock.component, gbc)
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0

        // Configuration section (dynamic); see the degraded notice above for why `weightx` (not
        // `weighty`) is what this row needs - this is the last use of `gbc`, so it is not reset
        // back to 0.0 afterward.
        gbc.gridx = 0
        gbc.gridy = GRID_CONFIG_ROW
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = FULL_WIDTH_WEIGHT
        gbc.insets = JBUI.insets(LABEL_SPACING_LARGE, CONTENT_SPACING, CONTENT_SPACING, CONTENT_SPACING)

        configurationPanel = createConfigurationPanel()
        // Starts hidden: the real visibility is decided once, from the derived state, by the
        // render() that refresh() triggers synchronously later in init - never guessed here from
        // settingsService directly (fix round 1, finding 7: a second notion of state, agreeing
        // with StatusTabState.derive only because both happened to read the same predicate).
        configurationPanel!!.isVisible = false
        panel.add(configurationPanel!!, gbc)

        return panel
    }

    private fun createConfigurationPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor()),
            JBUI.Borders.empty(CONFIGURATION_PANEL_BORDER)
        )

        val messageLabel = JBLabel(
            "<html><b>OpenRouter not configured</b><br/>" +
                "Please configure your API key to use OpenRouter features.</html>"
        )
        panel.add(messageLabel, BorderLayout.CENTER)

        val configButton = JButton("Configure")
        configButton.addActionListener {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, OpenRouterConfigurable::class.java)
        }
        panel.add(configButton, BorderLayout.SOUTH)

        return panel
    }

    /**
     * Re-renders from whatever is currently in the shared cache, and - if configured - asks the
     * cache to refresh. The cache de-duplicates concurrent refreshes itself, so this is safe to
     * call as often as the user clicks "Refresh"; it never issues a request of its own.
     */
    fun refresh() {
        render()
        if (settingsService.isConfigured()) {
            // A deliberate refresh must never serve a stale answer for a previously-seen
            // period from AnalyticsService's own query cache - see its invalidate() KDoc.
            analyticsService.invalidate()
            statsCache.refresh()
        }
    }

    /**
     * Called by [org.zhavoronkov.openrouter.toolwindow.OpenRouterToolWindowContent] whenever the
     * Status tab becomes the selected tab.
     *
     * Re-queries analytics only when [activationRefreshGate] says the last activation-triggered
     * query is stale enough to be worth another - there is deliberately no timer of its own here:
     * a tool window can sit open all day in the background, and unlike the shared stats cache
     * (refreshed on its own schedule by the status-bar widget regardless of this tab) analytics
     * queries spend the user's quota only while this tab is actually visible. When the gate says
     * no, this call is a no-op: whatever is already rendered stays on screen.
     *
     * A full [render] runs whenever the gate opens (fix round 1, finding 6) - not just
     * [refreshBreakdown] - because [lastUpdatedText] is computed from "now" on every render call.
     * Without this, switching away for an hour and back would still read "Updated moments ago":
     * a stale number presented confidently, exactly the defect class this plan exists to remove.
     * [render] itself decides per-state whether that includes a real analytics query (READY/ERROR)
     * or not (DEGRADED never shows a breakdown at all, so this must never call [refreshBreakdown]
     * on its own - that would repopulate it from the degraded, no-key path behind DEGRADED's back).
     *
     * @return `true` if a refresh was actually triggered, `false` if the gate held it back - lets
     *   a test observe the gate's decision directly without needing to await the async query.
     */
    fun onActivated(): Boolean {
        onActivatedCallCountForTest++
        if (!activationRefreshGate.tryAcquire()) return false
        analyticsService.invalidate()
        render()
        return true
    }

    private fun render() {
        val inputs = StatusTabState.Inputs(
            configured = settingsService.isConfigured(),
            hasProvisioningKey = settingsService.getProvisioningKey().isNotBlank(),
            hasData = statsCache.hasCachedData(),
            isLoading = statsCache.isLoading(),
            error = statsCache.getLastError()
        )
        val state = StatusTabState.derive(inputs)
        currentState = state

        // Both driven from the SAME derived `state`, once - never a second predicate of their own
        // (fix round 1, finding 7: configurationPanel used to read settingsService.isConfigured()
        // directly, agreeing with `state` only because both happened to read the same condition).
        configurationPanel?.isVisible = state == StatusTabState.State.NOT_CONFIGURED
        degradedNoticeBlock.component.isVisible = state == StatusTabState.State.DEGRADED

        when (state) {
            StatusTabState.State.NOT_CONFIGURED -> renderNotConfigured()
            StatusTabState.State.LOADING -> renderLoading()
            StatusTabState.State.ERROR -> renderError()
            StatusTabState.State.DEGRADED -> renderDegraded()
            StatusTabState.State.READY -> renderReady()
        }

        // The period selector is the only interactive control that can trigger a breakdown/
        // spend-series mutation OUTSIDE this dispatch (via onPeriodChanged -> refreshBreakdown(),
        // never through render() itself) - see refreshBreakdown()'s and applyAnalyticsResult()'s
        // own KDoc for the guard that makes such a call inert in every other state. Disabling the
        // control here as well (close-out round 2, Critical) is the cheap, honest half of that
        // fix: a user should never be able to click a control that cannot answer, not merely have
        // the click silently discarded after the fact.
        breakdownBlock.setPeriodSelectorEnabled(breakdownStateAllowsQuery(state))
        // G6: the dimension selector is gated by the exact same predicate, for the exact same
        // reason - see BreakdownBlock.setDimensionSelectorEnabled's own KDoc.
        breakdownBlock.setDimensionSelectorEnabled(breakdownStateAllowsQuery(state))
    }

    /**
     * Whether [state] is one where a breakdown/spend-series query means anything at all - READY
     * and ERROR are the only two [StatusTabState.derive] outcomes reached with a provisioning key
     * AND (for ERROR) previously-successful data on screen; NOT_CONFIGURED/DEGRADED/LOADING each
     * have their own [BreakdownBlock] placeholder precisely because nothing has been (or can be)
     * queried in them.
     *
     * The single choke point for this rule (close-out round 2, Critical): every place that can
     * mutate [breakdownBlock] or [balanceBlock]'s account-data rows reads this ONE function before
     * doing so - [refreshBreakdown] at entry, [applyAnalyticsResult] before touching
     * [breakdownBlock], [applySpendSeriesResult] before touching [balanceBlock], and [render]
     * itself (above) to decide whether the period selector should even be clickable. Before this
     * round, [refreshBreakdown] and [applyAnalyticsResult] had NO check of their own - only
     * [applySpendSeriesResult] guarded its own re-render, with its own private inline condition -
     * so a state guard kept being added wherever a path was noticed rather than swept for as a
     * class. A per-call-site check is exactly the shape that keeps missing the next call site (this
     * was the fourth instance); reading [currentState] through one shared predicate at every
     * mutation point is what keeps a fifth from being possible without also being obviously wrong.
     */
    private fun breakdownStateAllowsQuery(state: StatusTabState.State): Boolean =
        state == StatusTabState.State.READY || state == StatusTabState.State.ERROR

    /**
     * The ONE place [statusLabel]'s text is ever set (defect B) - every `renderX()` below calls
     * this instead of assigning `statusLabel.text` directly, so the bound this fixes cannot be
     * bypassed by a future state gaining its own dynamic message and forgetting to apply it.
     *
     * Only [renderError] currently carries genuinely unbounded, server-supplied text (the other
     * four states are short, fixed strings that never need shortening), but a fixed-string caller
     * paying for a length check it can never trigger is cheaper than a second, unguarded call site
     * silently reintroducing this defect the day its own text stops being fixed.
     *
     * [StatusLineText.truncate] shortens what is actually DISPLAYED; the untruncated [text] is set
     * as the label's tooltip regardless, so hovering always reveals the whole sentence - nothing
     * the server said is discarded, only deferred to a hover (see [StatusLineText]'s own KDoc for
     * why truncation, not wrapping, and why the tail is dropped rather than the middle).
     */
    private fun setStatusText(text: String) {
        statusLabel.text = StatusLineText.truncate(text)
        statusLabel.toolTipText = text
    }

    private fun renderNotConfigured() {
        setStatusText("Not configured")
        clearBalance()
        clearKeyLimit()
        // Not show(emptyList()) (fix round 3, finding 1 - the third state carrying the same
        // defect already fixed for LOADING/DEGRADED below): NOT_CONFIGURED queries nothing at
        // all, so "no activity in this period" would tell the user they spent nothing, which
        // nobody checked. Not showUnavailable() either - that names a missing PROVISIONING key,
        // which would misstate the actual problem (no API key at all).
        breakdownBlock.showNotConfigured()
    }

    private fun renderLoading() {
        setStatusText("Loading...")
        clearBalance()
        clearKeyLimit()
        // Not show(emptyList()) (fix round 1, finding 2): nothing has returned yet, so "no
        // activity in this period" would claim an answer that has not arrived.
        breakdownBlock.showLoading()
    }

    private fun clearBalance() = balanceBlock.update(
        remaining = NO_REMAINING,
        total = 0.0,
        perDay = NO_PER_DAY_RATE,
        series = NO_SERIES,
        seriesLabel = NO_SERIES_LABEL,
        lastUpdatedText = NO_LAST_UPDATED_TEXT
    )

    /** No cap known yet (loading / not configured) - [KeyLimitBlock] hides itself for a null limit. */
    private fun clearKeyLimit() = keyLimitBlock.update(used = 0.0, limit = NO_KEY_LIMIT)

    /**
     * The last known numbers stay fully rendered - via [renderAccountData], the same path
     * READY uses - beside a visible "couldn't refresh" line, never a blank panel. [renderCredits]
     * attaches [lastUpdatedText] to those numbers, which matters most here: an hour-old balance
     * during an outage must not look as fresh as one from a second ago.
     */
    private fun renderError() {
        setStatusText("Couldn't refresh: ${statsCache.getLastError() ?: "Unknown error"}")
        renderAccountData()
    }

    private fun renderReady() {
        setStatusText("Ready")
        renderAccountData()
    }

    /**
     * DEGRADED is no longer "no account data at all". Measured against the live API
     * (2026-09-21, correction C1): `/credits` answers for an ordinary API key exactly as it does
     * for a management key - it is account-scoped, not key-scoped - so the spec's D6 claim that
     * the tab falls back to `/credits` without one is, for credits specifically, now TRUE, even
     * though it never was for `/activity`, `/analytics/query`, `/analytics/meta` or `/keys` (all
     * still 403/401 for an API key). So this renders the real balance via
     * [renderCredits]/[balanceBlock] exactly like
     * READY does, but layers [DegradedSpend]'s locally-observed sparkline in alongside it (a
     * DIFFERENT quantity than READY's server-reported spend series, D12) since the analytics
     * query that would populate [currentSpendSeries] is never issued here - [refreshBreakdown]
     * (and so [applySpendSeriesResult]) is gated to READY/ERROR only, and DEGRADED must not
     * pretend to a burn rate or spend series it cannot check. [degradedNoticeBlock] (toggled
     * visible in [render]) states which three things genuinely still need a management key -
     * the breakdown, activity and the key spend cap - all three of which fail closed to an
     * explicit notice here ([breakdownBlock.showUnavailable]/[keyLimitBlock.showUnavailable]),
     * never a silent empty state. Falls back to no local history rather than throwing if
     * [CreditUsageHistoryService] is ever unavailable, the same defensiveness
     * [OpenRouterStatsCache] applies to its own service lookups.
     */
    private fun renderDegraded() {
        setStatusText("Limited")
        // Not show(emptyList()) (fix round 1, finding 2): DEGRADED never queries the breakdown at
        // all, so "no activity in this period" would claim a real, checked answer of zero.
        breakdownBlock.showUnavailable()
        // GET /keys is 401 for an ordinary API key - nothing was checked, so this is not the
        // same "no cap configured" fact keyLimitBlock.update(limit = null) would report.
        keyLimitBlock.showUnavailable()

        val snapshots = applicationServiceOrNull(CreditUsageHistoryService::class.java)
            ?.getState()?.snapshots
            ?.map { it.timestampUtc to it.totalUsed }
            ?: emptyList()
        val series = DegradedSpend.spendSeries(snapshots)
        val seriesLabel = if (series.isEmpty()) NO_SERIES_LABEL else DEGRADED_SERIES_LABEL

        // No balance here, deliberately. /credits is Management-Key-only, so in this state there
        // is nothing to show: an earlier build rendered a real balance on the strength of one
        // dashboard-created key that answered 200, which turned out to be an inconsistency on
        // OpenRouter's side rather than a capability - see getCredits' own note. All this state
        // can honestly offer is the spend the plugin recorded locally while the IDE was running.
        balanceBlock.showLocalSeriesOnly(series = series, seriesLabel = seriesLabel)
    }

    /**
     * Renders the two account-data blocks that READY/ERROR share, then re-queries the breakdown.
     *
     * Used to also render an unstated-window "N requests, $X" summary line via `renderActivity` -
     * deleted (fix round 3, finding 2; the spec's D5 named this root cause, and the pre-branch
     * total it carried over verbatim summed every cached row with no window at all). The period
     * selector below already answers the question that line was groping at, over a period the
     * user actually chose - restating an unstated-window total next to a stated-window breakdown
     * would only reintroduce the "two numbers, no way to tell why they differ" defect this whole
     * plan exists to remove.
     */
    private fun renderAccountData() {
        renderCredits(statsCache.getCachedCredits())
        renderKeyLimit(statsCache.getCachedApiKeys())
        refreshBreakdown()
    }

    /**
     * Renders [keyLimitBlock] from the shared cache's own API key list - never from
     * [org.zhavoronkov.openrouter.services.OpenRouterService.getQuotaInfo], which this panel no
     * longer calls at all (Task 7 removed its `openRouterService` field, and this tab makes no
     * network calls of its own). [KeyLimit.from] decides whether any of those keys carries a real
     * cap; a `null` result means "hide the block", which [KeyLimitBlock.update] already does for
     * a `null` limit - so this wiring passes the reading straight through without inventing its
     * own zero.
     */
    private fun renderKeyLimit(apiKeys: ApiKeysListResponse?) {
        val reading = KeyLimit.from(apiKeys)
        keyLimitBlock.update(used = reading?.used ?: 0.0, limit = reading?.limit)
    }

    /**
     * Answers [currentBreakdownPeriod] for [breakdownBlock], choosing the path per the Task 9
     * brief: with an analytics (provisioning) key available, a query with the matching
     * `time_range` (see [AnalyticsBreakdown.requestFor]); without one, the degraded path re-runs
     * [ActivityAggregator.byModel] against the shared cache's own activity.
     *
     * These two paths are chosen ONLY by [AnalyticsService.isAvailable] - never by whether the
     * query itself then succeeds. An analytics query error is handled entirely inside
     * [applyAnalyticsResult] via [BreakdownBlock.showError], and never falls back to the degraded
     * path: silently substituting [ActivityAggregator]'s locally-aggregated numbers under the
     * same-looking list on a transient failure is exactly what the status tab redesign's spec
     * (D12) forbids - it would let two users asking for "7 days" see different figures with no
     * visible sign anything was substituted. [ActivityAggregator] is the no-provisioning-key path
     * and nothing else.
     *
     * Also issues [applySpendSeriesResult] for the SAME [period] (Task 13), reading
     * [currentBreakdownPeriod] into a single local `period` used for both - never two separate
     * reads that a later edit could let drift apart. Only when analytics is actually available:
     * the no-provisioning-key branch above returns before reaching it, matching DEGRADED (which
     * never calls this method at all) in leaving [currentSpendSeries]/[currentSpendPerDay]/
     * [currentSpendSeriesLabel] untouched rather than inventing a local-history stand-in under the
     * READY/ERROR label - that would be exactly the kind of silent substitution D12 forbids, just
     * in the other direction.
     *
     * Guarded by [breakdownStateAllowsQuery] at entry (close-out round 2, Critical) - this is
     * called not only from [renderAccountData] (always safe: [render] sets [currentState] to
     * READY/ERROR immediately before dispatching to it) but also directly from
     * [breakdownBlock]'s `onPeriodChanged`/`onDimensionChanged` callbacks, wired in `init`, which
     * fire on a user's period/dimension selection in EVERY state, not only READY/ERROR. Without
     * this guard, changing the period while NOT_CONFIGURED or DEGRADED would silently replace
     * that state's own placeholder with a real (or empty) [ActivityAggregator] result built from
     * whatever the shared cache still has cached - reintroducing, by a path nobody had swept for,
     * the exact "claims a checked answer nobody checked" defect this whole plan exists to remove.
     *
     * [currentBreakdownDimension] (G6) is read into `dimension` the same way [period] is read
     * from [currentBreakdownPeriod] - once, for both the no-provisioning-key branch below and
     * [applyAnalyticsResult]. The no-provisioning-key branch, though, answers
     * [AnalyticsBreakdown.Dimension.KEY] differently than [AnalyticsBreakdown.Dimension.MODEL]:
     * [ActivityAggregator.byModel] re-aggregates
     * [org.zhavoronkov.openrouter.models.ActivityData], which carries no `api_key_id` field at
     * all - there is no local equivalent to fall back to for the key dimension, unlike model.
     * Rendering [BreakdownBlock.showUnavailable] here is therefore not this method treating KEY as
     * MORE restricted than MODEL by policy; it is the honest answer to a query this path genuinely
     * cannot run - the same "say so, never guess or fabricate" rule every other placeholder in
     * this class already follows.
     */
    private fun refreshBreakdown() {
        if (!breakdownStateAllowsQuery(currentState)) return
        val period = currentBreakdownPeriod
        val dimension = currentBreakdownDimension
        if (!analyticsService.isAvailable()) {
            if (dimension == AnalyticsBreakdown.Dimension.KEY) {
                breakdownBlock.showUnavailable()
                return
            }
            // The no-provisioning-key path: re-aggregate the shared cache's own activity locally.
            val activity = statsCache.getCachedActivity().orEmpty()
            breakdownBlock.show(ActivityAggregator.byModel(activity, period, LocalDate.now()))
            return
        }

        // Close-out round 3, Important A: this branch writes NOTHING synchronously - both calls
        // below only launch coroutines - so whatever showX() call last ran stays on screen for the
        // whole round trip. On the single most ordinary path into READY (the status-bar widget has
        // already warmed the shared cache before the user ever opens the tab, so derive() returns
        // READY immediately, with no LOADING state in between), that leftover render is still
        // BreakdownBlock's own init-time show(emptyList()) - "No activity in this period", the
        // string whose own KDoc means "queried successfully, found nothing" - shown before this
        // query has even been sent, let alone answered. showLoading() here closes the fifth
        // instance of the class of bug this whole plan exists to remove, on the happy path this
        // time rather than a state transition.
        breakdownBlock.showLoading()
        coroutineScope.launch { applyAnalyticsResult(period, dimension) }
        coroutineScope.launch { applySpendSeriesResult(period) }
    }

    /**
     * Applies one breakdown query's outcome to [breakdownBlock].
     *
     * The query itself, the row mapping and the "why did this come back empty" metadata check all
     * live in [BreakdownQueries] now - this is only the rendering half. It re-checks
     * [breakdownStateAllowsQuery] AFTER the suspending call, not only at [refreshBreakdown]'s
     * launch time (close-out round 2, Critical): the panel can leave READY/ERROR while the request
     * is in flight - the Management Key is removed, say - and a late answer must not overwrite the
     * placeholder the new state's own render path put there.
     */
    private suspend fun applyAnalyticsResult(
        period: ActivityAggregator.Period,
        dimension: AnalyticsBreakdown.Dimension
    ) {
        val outcome = breakdownQueries.breakdown(period, dimension, LocalDate.now())
        if (!breakdownStateAllowsQuery(currentState)) return

        when (outcome) {
            is BreakdownQueries.Breakdown.Rows -> breakdownBlock.show(outcome.rows, outcome.truncated)
            is BreakdownQueries.Breakdown.Empty -> breakdownBlock.show(emptyList(), outcome.truncated)
            is BreakdownQueries.Breakdown.MissingNames -> breakdownBlock.showMissingNames(outcome.names)
            BreakdownQueries.Breakdown.Failed -> breakdownBlock.showError()
        }
    }

    /**
     * Applies one spend-series query to the balance block's chart, rate and days-left estimate.
     *
     * A null result is "we learned nothing", not "spend was zero", so it leaves whatever is
     * already on screen rather than replacing a real series with an invented flat one. Same
     * post-await state re-check as [applyAnalyticsResult], for the same race.
     */
    private suspend fun applySpendSeriesResult(period: ActivityAggregator.Period) {
        val result = breakdownQueries.spendSeries(period, LocalDate.now()) ?: return

        currentSpendSeries = result.series
        currentSpendPerDay = result.perDay
        currentSpendSeriesLabel = if (result.series.isEmpty()) {
            NO_SERIES_LABEL
        } else {
            when (period) {
                ActivityAggregator.Period.DAY -> READY_SERIES_LABEL_DAY
                ActivityAggregator.Period.WEEK -> READY_SERIES_LABEL_WEEK
                ActivityAggregator.Period.MONTH -> READY_SERIES_LABEL_MONTH
            }
        }

        if (breakdownStateAllowsQuery(currentState)) {
            renderCredits(statsCache.getCachedCredits())
        }
    }

    /**
     * Test-only entry point for [applyAnalyticsResult], bypassing [coroutineScope]'s
     * `Dispatchers.Main` launch so a test can `runBlocking { }` it directly against an injected
     * [analyticsService] (e.g. pointed at a [okhttp3.mockwebserver.MockWebServer]) without
     * needing to pump an IDE event queue.
     *
     * @param dimension defaults to [AnalyticsBreakdown.Dimension.MODEL] (G6) so every existing
     *   caller of this seam - written before the dimension toggle existed - keeps testing the
     *   model dimension unchanged; a KEY-dimension test passes it explicitly.
     */
    internal suspend fun applyAnalyticsResultForTest(
        period: ActivityAggregator.Period,
        dimension: AnalyticsBreakdown.Dimension = AnalyticsBreakdown.Dimension.MODEL
    ) = applyAnalyticsResult(period, dimension)

    /**
     * Test-only entry point for [applySpendSeriesResult], the same seam [applyAnalyticsResultForTest]
     * provides for the breakdown query - bypasses [coroutineScope]'s `Dispatchers.Main` launch so a
     * test can `runBlocking { }` it directly against an injected [analyticsService].
     */
    internal suspend fun applySpendSeriesResultForTest(period: ActivityAggregator.Period) =
        applySpendSeriesResult(period)

    /**
     * Renders the account balance from the shared cache's credits data via [BalanceBlock].
     *
     * `remaining` is `totalCredits - totalUsage` - real account credits, never the API key
     * limits that `getQuotaInfo()` used to sum. [BalanceBlock] applies its own "unknown, not
     * zero" rule to a zero or absent total, so a missing limit can no longer masquerade as a
     * computed `Infinity%` the way the old inline usage-share label did.
     *
     * Burn rate and the spend series (Task 13) come from [currentSpendPerDay]/[currentSpendSeries]/
     * [currentSpendSeriesLabel] - populated by [applySpendSeriesResult], not computed here - so
     * this method stays a plain, synchronous read of whatever the last successful analytics query
     * (if any) left behind; it never issues a query of its own. Before any such query has ever
     * succeeded, those fields hold the same "nothing known yet" placeholders [clearBalance] uses,
     * so [BalanceBlock] renders them exactly as absent as it would have before this task.
     * [lastUpdatedText] (Task 11) is read here too, by both READY and ERROR (both reach this
     * method through [renderAccountData], and [applySpendSeriesResult] re-invokes it
     * directly once its own query resolves) - it matters most in ERROR, where these are the last
     * known numbers, not fresh ones.
     */
    private fun renderCredits(credits: CreditsData?) {
        if (credits == null) {
            clearBalance()
            return
        }

        val remaining = credits.totalCredits - credits.totalUsage
        balanceBlock.update(
            remaining = remaining,
            total = credits.totalCredits,
            perDay = currentSpendPerDay,
            series = currentSpendSeries,
            seriesLabel = currentSpendSeriesLabel,
            lastUpdatedText = lastUpdatedText
        )
    }

    /**
     * The shared cache's own freshness timestamp, formatted via the platform's [DateFormatUtil]
     * rather than hand-rolled relative-time formatting - the codebase has no such helper of its
     * own, and this is not the task to invent one.
     *
     * Blank when there is no timestamp yet (the cache's own zero "never updated" value) -
     * [BalanceBlock] hides the row for blank text rather than rendering an epoch date.
     */
    private val lastUpdatedText: String
        get() {
            val timestamp = statsCache.getLastUpdateTimestamp()
            if (timestamp <= 0L) return ""
            return "Updated ${DateFormatUtil.formatBetweenDates(timestamp, System.currentTimeMillis())}"
        }

    override fun dispose() {
        coroutineScope.cancel()
    }

    internal fun getStateForTest(): StatusTabState.State = currentState

    /** How many times [onActivated] has been called, gated or not - see [onActivatedCallCountForTest]'s own KDoc. */
    internal fun getOnActivatedCallCountForTest(): Int = onActivatedCallCountForTest

    /**
     * Re-derives and re-renders from whatever is currently in [settingsService]/[statsCache],
     * without [refresh]'s side effect of calling [OpenRouterStatsCache.refresh] - which, whenever
     * both a config key and a provisioning key are present, launches a REAL background fetch
     * against the live OpenRouter API. That singleton has no reset seam of its own, so a test
     * exercising READY/ERROR/LOADING - which all require exactly that combination - drives them
     * through this seam instead: mutate the shared cache/settings mock directly, then call this to
     * see the result, with no network ever in the loop.
     */
    internal fun renderForTest() = render()

    /**
     * The wired-in [balanceBlock]/[keyLimitBlock]/[breakdownBlock]/[degradedNoticeBlock]
     * components, so a test can prove each one is really in the tree (and, for
     * [TestComponent.DEGRADED_NOTICE], that DEGRADED - and only DEGRADED - toggles its
     * visibility). Replaces four separate `getXComponentForTest()` accessors (fix round 1: they
     * were the class's only real duplication - four functions differing solely in which block
     * they returned - and collapsing them, not the seven other, genuinely distinct test seams,
     * is what brought [StatusTabPanel] back under detekt's function-count threshold). Takes
     * [TestComponent] rather than a raw `String` key so a typo'd lookup is a COMPILE error - the
     * `when` below is exhaustive - rather than a `null` a caller could silently treat as "not
     * visible" and pass an assertion that never actually checked anything.
     */
    internal fun componentForTest(component: TestComponent): JComponent = when (component) {
        TestComponent.BALANCE -> balanceBlock.component
        TestComponent.KEY_LIMIT -> keyLimitBlock.component
        TestComponent.BREAKDOWN -> breakdownBlock.component
        TestComponent.DEGRADED_NOTICE -> degradedNoticeBlock.component
        TestComponent.CONFIGURATION -> configurationPanel!!
    }

    /** [currentBreakdownPeriod] as the real period-selector ComboBox (inside [breakdownBlock])
     * actually left it - proof that selecting a period there updates the SAME field [refreshBreakdown]
     * reads for BOTH the per-model breakdown query and the account-wide spend-series query
     * (Task 13), rather than the two ever reading two different notions of "the current period". */
    internal fun getCurrentBreakdownPeriodForTest(): ActivityAggregator.Period = currentBreakdownPeriod

    /**
     * True once the shared-cache subscription has actually been torn down.
     *
     * [statsConnection] is a Disposer child of `this`, so `Disposer.dispose(this)` disposes it
     * automatically; a bare `this.dispose()` method call does not, because it only runs this
     * class's own [dispose] body without invoking the Disposer. This accessor lets a test tell
     * the two apart, the way [org.zhavoronkov.openrouter.statusbar.OpenRouterStatusBarWidget]'s
     * `isRefreshAlarmDisposedForTest()` proves its own Disposer-parented resource is torn down.
     */
    internal fun isStatsConnectionDisposedForTest(): Boolean = Disposer.isDisposed(statsConnection)
}

/**
 * Keys for [StatusTabPanel.componentForTest] - one entry per Swing sub-block a test can ask for.
 *
 * A top-level (not nested inside [StatusTabPanel]) type - but NOT because nesting it would have
 * cost that class function-count headroom (fix round 2, finding 6 corrects this: detekt's
 * `TooManyFunctions` counts functions, not nested types, so a nested `enum class` here would have
 * been entirely free either way). It is top-level simply because it is not a detail of
 * [StatusTabPanel]'s own implementation - it is the set of keys test code passes IN, independent
 * of where the class that consumes them happens to live.
 */
internal enum class TestComponent { BALANCE, KEY_LIMIT, BREAKDOWN, DEGRADED_NOTICE, CONFIGURATION }
