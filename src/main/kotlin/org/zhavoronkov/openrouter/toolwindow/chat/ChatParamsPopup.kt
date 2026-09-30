package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ScreenUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.toJBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * The send parameters (reasoning, verbosity, router param, web search), in a popup form.
 *
 * They used to be a right-aligned FlowLayout row nested in a BoxLayout.Y_AXIS
 * panel. FlowLayout can wrap, but BoxLayout asks it for its preferred height
 * at its preferred WIDTH — one row — so wrapped controls fell outside the
 * height they were granted and simply vanished (the reported bug: "Verbosity:"
 * with no combo beside it, "Cost tier" gone entirely). A popup has its own
 * width, independent of the tool window, and a vertical form fixed that.
 *
 * The form is now two columns, label to the LEFT of its control — `row(label)`
 * — not a label ABOVE the control. That reverses the first fix's D7 reading;
 * see the "Correction" recorded for D7 in the redesign spec for why: a label
 * above every control made three fields into up to nine visual lines
 * (~480px), which made the platform relocate the popup once it could not fit
 * below the gear. Shortening the form to ~186px (see [FORM_WIDTH]) never
 * fixed that relocation, though — the tool window this popup lives in is
 * itself pinned to the bottom of the screen, so there is no space below the
 * gear at ANY content height, tall or short. [anchorPoint] always asked to
 * drop downward regardless, which is the actual bug four rounds missed: it
 * is a bug in [anchorPoint] itself, not merely a symptom of a too-tall form.
 * [anchorPoint] now decides its direction from the space [show] measures
 * below the gear, and opens upward — hugging the gear's top edge instead of
 * floating wherever the platform's own relocation happens to land it — when
 * that space is not enough. Labels-to-the-left is also the convention
 * [RouterDefaultsSettingsPanel] already uses for the same three router
 * parameters in Settings, trailing colon included — matching it here means
 * the two surfaces read the same way.
 *
 * Unsupported parameters stay visible but disabled, with the reason as a
 * visible sub-label rather than a tooltip nobody hovers — hiding them reads
 * as a bug (spec D7). The router-param description becomes a comment beneath
 * its row, spanning the form's full interior width; see [withComment] for why
 * it spans rather than indenting under the control column the way
 * [RouterDefaultsSettingsPanel] renders it.
 *
 * [buildForm] is called fresh on every [show]. Re-adding an already-parented
 * Swing component to a new container is safe — `Container.add` removes it
 * from its previous parent first — so rebuilding the form each time and
 * re-parenting the same combo box instances into it does not leak or corrupt
 * their state. The combos themselves (and their models/listeners) are owned
 * by ChatPanel and outlive any single popup instance.
 *
 * This class caches nothing from the async model-capability refresh beyond
 * what the combo boxes' own state (enabled flag, model, selection) already
 * holds; [buildForm] reads current combo/field state at [show] time, so a
 * popup opened after the update coroutine has run always reflects the latest
 * values, and there is no stale snapshot to go wrong regardless of ordering
 * between that coroutine and the user clicking the gear.
 *
 * The gear-badge decision itself ([hasNonDefaultSelection]/[activeSummary])
 * is not implemented here: it is pulled out into the pure, unit-tested
 * [ChatParamsState] (no Swing/AWT imports, runs in the fast `test` task),
 * the same way layout decisions were pulled into `ComposerLayoutPolicy`.
 * These two methods are thin readers that snapshot the live combos into a
 * [ChatParamsState.Selection] and delegate.
 */
class ChatParamsPopup(
    private val reasoning: ComboBox<String>,
    private val verbosity: ComboBox<String>,
    private val routerParam: ComboBox<String>,
    private val webSearch: JCheckBox
) {

    // Until the caller reports a Model, nothing is known to be servable but Off.
    private var outputChoices: List<OutputModeChoice> = listOf(OutputModeChoice(OutputMode.Off, null))

    /** Set by the caller; invoked whenever the Output Mode selection changes. */
    var onOutputModeChanged: () -> Unit = {}

    /** Invoked whenever the web search switch changes, by the user or by setting the controls. */
    var onWebSearchChanged: () -> Unit = {}

    /** Set by the caller; invoked by "Save as Preset…", to save the controls as a preset. */
    var onSaveAsPreset: () -> Unit = {}

    /** The popup on screen, if any, closed before "Save as Preset…" opens its dialog. */
    private var shown: JBPopup? = null

    /** The popup's "Save as Preset…" link. */
    internal val saveAsPreset = ActionLink(SAVE_AS_PRESET_TEXT) {
        shown?.cancel()
        onSaveAsPreset()
    }

    /**
     * The Output Mode control. Owned here rather than by the caller: nothing but this popup shows
     * it, and the caller reads it through [requestOptions] like every other control.
     *
     * Its entries are the modes [setOutputModes] was last given, and an entry the Model cannot
     * serve is drawn greyed out and cannot be picked. A selection that stops being servable because
     * the Model changed is kept and marked, though - and kept as an entry even when it is no longer
     * offered at all - see [OutputModeModel].
     */
    internal val outputMode = ComboBox(OutputModeModel())

    /** The line under the Output Mode control, saying why an entry is greyed out or blocks sending. */
    internal val outputComment = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }

    init {
        webSearch.text = WEB_SEARCH_TEXT
        outputMode.renderer = SimpleListCellRenderer.create { label, mode, index ->
            val choice = mode?.let(::choiceFor)
            label.text = mode?.label.orEmpty()
            if (choice != null && !choice.supported) {
                label.foreground = UIUtil.getLabelDisabledForeground()
                // In the list it is an entry that cannot be picked; closed, it is the kept selection
                // that is blocking the send, and says so.
                label.icon = if (index == -1) AllIcons.General.Warning else null
                label.toolTipText = choice.unsupportedReason
            } else if (choice?.warning != null) {
                label.toolTipText = choice.warning
            }
        }
        outputMode.addActionListener {
            refreshOutputComment()
            onOutputModeChanged()
        }
        // An item listener, so setting the switch from a pair's settings is heard as well
        webSearch.addItemListener { onWebSearchChanged() }
        refreshOutputComment()
    }

    /**
     * What the selected Model can serve, as [ChatExchange.outputModes] decided it. The selection is
     * left alone even when it is no longer servable; [requestOptions] still reports it, and the
     * caller blocks sending until the user resolves it.
     */
    fun setOutputModes(choices: List<OutputModeChoice>) {
        outputChoices = choices
        (outputMode.model as OutputModeModel).showOffered()
        refreshOutputComment()
        outputMode.repaint()
    }

    /**
     * How [mode] stands for the current Model. A mode that is not offered at all - a kept selection
     * the Model's modes no longer include - is unservable, never assumed to be fine.
     */
    private fun choiceFor(mode: OutputMode): OutputModeChoice =
        outputChoices.firstOrNull { it.mode == mode }
            ?: OutputModeChoice(mode, ChatExchange.noLongerOfferedReason(mode))

    private fun selectedMode(): OutputMode = outputMode.selectedItem as? OutputMode ?: OutputMode.Off

    private fun selectedOutputChoice(): OutputModeChoice = choiceFor(selectedMode())

    private fun refreshOutputComment() {
        val choice = selectedOutputChoice()
        val blocked = !choice.supported
        outputComment.text = when {
            blocked && choice.unsupportedReason == ChatExchange.WEB_SEARCH_DROPS_JSON -> OUTPUT_DROPPED_BY_SEARCH_TEXT
            blocked -> OUTPUT_BLOCKED_TEXT
            choice.warning != null -> OUTPUT_SEARCH_WARNING_TEXT
            outputChoices.any { !it.supported } -> OUTPUT_UNAVAILABLE_TEXT
            else -> ""
        }
        outputComment.toolTipText = choice.unsupportedReason ?: choice.warning
        outputComment.foreground = if (blocked) CHAT_WARNING_FOREGROUND else UIUtil.getContextHelpForeground()
    }

    /**
     * Refuses a pick of an entry the Model cannot serve, but never unselects one already chosen:
     * a selection is only ever changed by the user choosing something that can be sent.
     */
    private inner class OutputModeModel : DefaultComboBoxModel<OutputMode>() {
        init {
            showOffered()
        }

        /**
         * Lists the offered modes, keeping the current selection selected - and listed, when it is
         * no longer offered, so that it can be seen and changed rather than silently disappear.
         */
        fun showOffered() {
            val previous = selectedItem as? OutputMode ?: OutputMode.Off
            val offered = outputChoices.map { it.mode }
            // The offered entry when there is one - a schema renamed only in case is the same one.
            val kept = offered.firstOrNull { it == previous } ?: previous
            removeAllElements()
            (if (kept in offered) offered else offered + kept).forEach(::addElement)
            super.setSelectedItem(kept)
        }

        override fun setSelectedItem(item: Any?) {
            val mode = item as? OutputMode ?: return
            if (!choiceFor(mode).supported && mode != selectedItem) return
            super.setSelectedItem(mode)
        }

        /** Selects [mode] whether or not the Model can serve it, listing it when it is not offered. */
        fun keep(mode: OutputMode) {
            if (getIndexOf(mode) < 0) addElement(mode)
            super.setSelectedItem(mode)
        }
    }

    private var reasoningComment: String = ""
    private var verbosityComment: String = ""
    private var routerLabel: String? = null
    private var routerComment: String? = null
    private var routerVisible = false

    fun setReasoningSupport(supported: Boolean, reason: String) {
        reasoning.isEnabled = supported
        reasoningComment = if (supported) "" else reason
    }

    fun setVerbositySupport(supported: Boolean, reason: String) {
        verbosity.isEnabled = supported
        verbosityComment = if (supported) "" else reason
    }

    fun setRouterParam(label: String?, description: String?, visible: Boolean) {
        routerLabel = label
        routerComment = description
        routerVisible = visible
    }

    /**
     * Sets every control from [controls], as picking a pair does: a setting its preset leaves
     * unset goes back to its default, so nothing is left over from an earlier message. The user may
     * still change a control afterwards, for the messages that follow.
     */
    fun applyControls(controls: ChatControls) {
        reasoning.selectedItem = controls.reasoning
        verbosity.selectedItem = controls.verbosity
        webSearch.isSelected = controls.webSearch
        // Chosen even when the Model cannot give it: kept and marked, it blocks sending and says
        // why, rather than the pair going out with an output its model does not declare
        (outputMode.model as OutputModeModel).keep(controls.outputMode)
        refreshOutputComment()
    }

    /** Every control back to its default, as leaving a pair for a plain model does. */
    fun resetControls() = applyControls(ChatControls())

    /** What the controls currently say, as the next request's options. */
    fun requestOptions(): ChatRequestOptions = ChatRequestOptions(
        reasoning = reasoning.selectedItem as? String,
        verbosity = verbosity.selectedItem as? String,
        routerParam = routerParam.selectedItem as? String,
        webSearch = webSearch.isSelected,
        outputMode = selectedMode()
    )

    fun hasNonDefaultSelection(): Boolean = ChatParamsState.hasNonDefaultSelection(currentSelection())

    fun activeSummary(): String = ChatParamsState.activeSummary(currentSelection())

    /**
     * The badge's view of the controls: the values [requestOptions] reads, plus the positions and
     * the router row's visibility that only the badge needs.
     */
    private fun currentSelection(): ChatParamsState.Selection {
        val options = requestOptions()
        return ChatParamsState.Selection(
            reasoningIndex = reasoning.selectedIndex,
            reasoningValue = options.reasoning,
            verbosityIndex = verbosity.selectedIndex,
            verbosityValue = options.verbosity,
            routerVisible = routerVisible,
            routerLabel = routerLabel,
            routerValue = options.routerParam,
            webSearch = options.webSearch,
            outputMode = options.outputMode
        )
    }

    /**
     * No [com.intellij.openapi.ui.popup.ComponentPopupBuilder.setRequestFocus]:
     * a context menu does not grab keyboard focus and paint a focus ring the
     * moment it opens, and neither should this. Every control here stays
     * fully mouse-operable without it — the gear click itself was a mouse
     * action — so nothing is lost for the popup's actual usage pattern, and
     * the loud blue ring around the Reasoning combo (the thing that made it
     * look shabby) never paints.
     */
    fun show(under: Component) {
        val form = buildForm()
        val anchor = anchorPoint(
            buttonHeight = under.height,
            contentHeight = form.preferredSize.height,
            spaceBelow = spaceBelowScreen(under)
        )
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(form, reasoning)
            .setResizable(false)
            .createPopup()
            .also { shown = it }
            .show(RelativePoint(under, anchor))
    }

    /**
     * The gear lives in the composer, which sits at the bottom of the tool
     * window, which sits at the bottom of the screen — there is normally NO
     * room below the gear, not "a little", regardless of how short [buildForm]
     * measures. Always asking to drop downward (the previous, four-rounds-wrong
     * behaviour) meant [com.intellij.openapi.ui.popup.JBPopup]'s own on-screen
     * relocation was doing the real positioning work, sliding the popup upward
     * to wherever it happened to fit — which reads as the popup floating loose
     * over the transcript instead of hugging the button, exactly what was
     * reported.
     *
     * A context menu on a button pinned to the bottom of the screen opens
     * UPWARD, sharing the button's left edge either way:
     * - room below ([spaceBelow] >= [contentHeight]): anchor at the button's
     *   bottom-left corner, same as before, so the popup's TOP-left corner
     *   (what [JBPopup.show]/[RelativePoint] place at the anchor) lands there
     *   and the popup drops down from the button.
     * - not enough room below: anchor [contentHeight] pixels ABOVE the
     *   button's top-left corner, so the popup's BOTTOM edge lands on the
     *   button's TOP edge instead — it opens upward and still hugs the button.
     *
     * The boundary (exactly enough room) goes downward, matching the first
     * branch's `>=`; see the platform test for why that specific edge is
     * pinned rather than left to fall out of the arithmetic.
     *
     * [show] measures the three real inputs — [under]'s own height, the
     * built form's preferred height, and how much screen space is actually
     * left below [under] (via [spaceBelowScreen]) — and this function stays a
     * plain, pure function of the three numbers so it can be driven headlessly:
     * a real [com.intellij.openapi.ui.popup.JBPopup] needs a live event
     * queue/screen to drive, which a platform test cannot do, but this
     * geometry is what actually decides which way the popup opens, so it is
     * what gets asserted against instead of nothing.
     *
     * [JBPopup.show] can still relocate the popup itself if what is asked for
     * here does not fit either — e.g. the content is taller than the space
     * above the gear as well. That residual case is the platform's own
     * fallback, not this function's job: see the class doc for what happens
     * then.
     */
    internal fun anchorPoint(buttonHeight: Int, contentHeight: Int, spaceBelow: Int): Point =
        if (spaceBelow >= contentHeight) Point(0, buttonHeight) else Point(0, -contentHeight)

    /**
     * Usable screen space below [under]'s bottom edge, in pixels — "usable"
     * meaning [ScreenUtil]'s screen rectangle, which already excludes the
     * dock/taskbar, not the raw device bounds from
     * [java.awt.GraphicsEnvironment]. Using the raw device bounds would let
     * the "room below" check pass over space that is actually covered by the
     * dock, so the popup would hug something sitting underneath it.
     *
     * Falls back to [Int.MAX_VALUE] (i.e. "plenty of room, drop down") when
     * [under] is not currently showing on screen — [Component.getLocationOnScreen]
     * throws otherwise — which only happens outside real usage (the gear is
     * always showing when it is clicked) and keeps that unreachable edge case
     * from ever producing a nonsensical anchor.
     */
    private fun spaceBelowScreen(under: Component): Int {
        if (!under.isShowing) return Int.MAX_VALUE
        val screenBounds = ScreenUtil.getScreenRectangle(under)
        val gearBottomOnScreen = under.locationOnScreen.y + under.height
        return screenBounds.y + screenBounds.height - gearBottomOnScreen
    }

    /**
     * `Cell.comment()`'s default `maxLineLength` is 70 - a CHARACTER count,
     * not pixels (confirmed against the platform's DslLabel/UtilsKt,
     * decompiled). Below that count the comment gets no wrap at all: it
     * renders as one unbroken HTML line and reports ITS OWN full, unclamped
     * text width as the component's preferred width, which is exactly what
     * clipped the "openrouter/auto does not support verbosity" explanation -
     * a reason string that is well under 70 characters, so it never wrapped.
     *
     * Passing [MAX_LINE_LENGTH_WORD_WRAP] instead switches the comment to the
     * platform's dynamic mode: no forced single line, no fixed-column wrap -
     * the label reflows to whatever width its row is actually laid out at,
     * and its preferred/minimum size are capped instead of dictating the
     * form's width. That only works once the form itself has a real width to
     * reflow against, which is what [FORM_WIDTH] gives it.
     *
     * Two-column rows: `row(label)` puts the label in its own column, and the
     * comment is attached to the ROW rather than the control's cell, so it
     * reflows against the form's whole interior width instead of the narrower
     * control column - see [withComment].
     */
    private fun buildForm(): JComponent = panel {
        row(REASONING_LABEL) { filling(reasoning) }.withComment(reasoningComment)
        row(VERBOSITY_LABEL) { filling(verbosity) }
            .customize(TIGHT_ROW_GAP)
            .withComment(verbosityComment)
        if (routerVisible) {
            row("${routerLabel.orEmpty()}:") { filling(routerParam) }
                .customize(TIGHT_ROW_GAP)
                .withComment(routerComment.orEmpty())
        }
        row(WEB_SEARCH_LABEL) { cell(webSearch) }.customize(TIGHT_ROW_GAP)
        row(OUTPUT_LABEL) { filling(outputMode) }.customize(TIGHT_ROW_GAP)
        row { cell(outputComment) }
        row { cell(saveAsPreset) }.customize(TIGHT_ROW_GAP)
    }.apply {
        // The border must be set BEFORE the width is clamped and the height is
        // measured: it shrinks the interior width the DSL grid actually has to
        // lay its rows out in (a Swing border's insets come out of the
        // container's own bounds), so the comment-wrap measurement below has
        // to see it first. See FORM_BORDER's doc for where this padding
        // convention comes from.
        border = FORM_BORDER

        // Width must be applied BEFORE the height is measured: the comment
        // rows only wrap (and therefore only need their true, taller height)
        // once they are laid out at FORM_WIDTH, not at the panel's own wider
        // natural width. `setSize` (rather than only setting `preferredSize`'s
        // width) is what actually gets the DSL grid to re-measure its rows at
        // the clamped width before `preferredSize.height` is read - the same
        // "clamp the width, then ask for the height" ordering as
        // [WrappingEditorPane.getPreferredSize].
        val clampedWidth = JBUI.scale(FORM_WIDTH)
        setSize(clampedWidth, Short.MAX_VALUE.toInt())
        doLayout()
        preferredSize = Dimension(clampedWidth, preferredSize.height)
    }

    /**
     * `AlignX.FILL` makes the control span the rest of its row instead of
     * sitting at its own preferred (combo-box-sized) width: with it, every
     * control shares one right edge regardless of which row is widest. The
     * combos carry no fixed preferred width of their own to fight this — that
     * was removed when they moved out of the old FlowLayout row (see
     * `SETTINGS_COMBO_BOX_WIDTH`'s removal in ChatPanel's history) — so FILL
     * is unopposed.
     */
    private fun Row.filling(component: JComponent): Cell<JComponent> = cell(component).align(AlignX.FILL)

    /**
     * The comment goes on the ROW, not on the control's cell, so it spans the
     * label column as well and gets the form's whole interior width to reflow
     * in rather than only what is left over beside the widest label.
     *
     * `Cell.comment()` — a comment attached to the control's cell, indented
     * under the control column — is what this used to use, to mirror
     * [RouterDefaultsSettingsPanel]. That indent is affordable only if the
     * leftover control column is wide enough for the comment, and it is not:
     * the label column is sized to the widest label ("Cost tier (editable):"),
     * whose width is a function of the platform's own UI font. Measured at
     * [FORM_WIDTH] with the same text, the control column is 175px on macOS
     * but only 155px on Linux, while "One of: low, medium, high, xhigh, max"
     * needs 218px and 245px respectively — so the comments wrapped to two
     * lines on both platforms, and on Linux all three of them did, pushing the
     * form from 206px to 234px.
     *
     * Spanning the row instead hands the comment the full interior width
     * (300px at [FORM_WIDTH]), which clears the widest comment on both
     * platforms with room to spare and makes the form's height the same on
     * each. See [MAX_LINE_LENGTH_WORD_WRAP] for why the wrap mode matters
     * as much as the width.
     */
    private fun Row.withComment(comment: String): Row =
        if (comment.isEmpty()) this else rowComment(comment, MAX_LINE_LENGTH_WORD_WRAP)

    internal companion object {
        const val SAVE_AS_PRESET_TEXT = "Save as Preset…"

        /**
         * Trailing colon matches [RouterDefaultsSettingsPanel]'s
         * `row("${def.displayName}:")` convention for the same three
         * parameters — labels to the LEFT of their controls is exactly the
         * case that panel already uses a colon for.
         */
        private const val REASONING_LABEL = "Reasoning:"
        private const val VERBOSITY_LABEL = "Verbosity:"
        private const val WEB_SEARCH_LABEL = "Web search:"
        private const val OUTPUT_LABEL = "Output mode:"

        /**
         * Short on purpose: the line has to fit the popup on one line whatever the Model's slug,
         * so it names no Model, and Linux's wider UI font needs more room than macOS's.
         * The full reason, slug included, is on the Send button's tooltip.
         */
        const val OUTPUT_BLOCKED_TEXT = "Not supported by this model. Pick another."
        const val OUTPUT_UNAVAILABLE_TEXT = "Greyed out: not supported by this model."
        const val OUTPUT_DROPPED_BY_SEARCH_TEXT = "Dropped with web search. Turn it off or pick a schema."
        const val OUTPUT_SEARCH_WARNING_TEXT = "With web search, may come back as plain text."

        /**
         * The cost is said on the control itself rather than in a comment beneath it: each search
         * is billed on top of inference, and that has to be read before the box is ticked, not
         * discovered on the invoice. "Allow" because ticking it lets the model search, as often as
         * it decides to, rather than making it search once. On the checkbox it costs the form no
         * extra line.
         */
        private const val WEB_SEARCH_TEXT = "Allow web search (charged per search)"

        /**
         * Extra top gap above the second/third row is dropped to
         * [TIGHTENED_ROW_GAP_PX] — below the platform's own default row gap
         * ([IntelliJSpacingConfiguration][com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration]
         * measures 6px unscaled between rows, 2px between a comment and the
         * row after it) — so three two-column fields read as one compact
         * form instead of three dialog-sized sections.
         */
        private const val TIGHTENED_ROW_GAP_PX = 2
        val TIGHT_ROW_GAP = UnscaledGapsY(top = TIGHTENED_ROW_GAP_PX)

        /**
         * Internal padding for the popup form (spacing pass, 2026-09-19).
         *
         * Before this, the form had NO border at all: the "Reasoning:" label
         * sat flush against the popup's left edge, the combos flush against
         * the right, and the first label/last comment flush against the top
         * and bottom - it read as content that had overflowed its container
         * rather than a composed panel.
         *
         * The inset values are not invented: they are
         * [IntelliJSpacingConfiguration.dialogUnscaledGaps] - the UI DSL's
         * own convention for "how much padding a dialog/popup's content gets
         * from its edge", exposed specifically so a raw panel can be given
         * dialog-shaped padding without a plugin author picking a number.
         * That property resolves to `top=10, left=12, bottom=10, right=12`
         * (raw, unscaled px). [RouterDefaultsSettingsPanel] - the sibling
         * settings surface these three router parameters were deliberately
         * matched to - never sets its own border either; it relies on the
         * Settings dialog's own content-pane padding, which is this exact
         * convention applied one level up. Using it here directly is what
         * "match RouterDefaultsSettingsPanel's surface" means for a popup
         * that is not itself hosted in a Settings dialog.
         *
         * [toJBEmptyBorder] is the DSL's own conversion from that gap value
         * to a real Swing border - not `JBUI.Borders.empty(10, 12, 10, 12)`
         * typed out by hand - so the raw numbers live in exactly one place
         * (the platform's), not duplicated into this file.
         */
        val FORM_BORDER = IntelliJSpacingConfiguration().dialogUnscaledGaps.toJBEmptyBorder()

        /**
         * The width the form is clamped to before its height is read.
         *
         * Two earlier derivations of this number were wrong in the same way,
         * so the reasoning is worth keeping:
         *
         * 1. (2026-09-18) 280 was measured before `buildForm()` applied the
         *    width clamp ahead of reading the height, so it was taken at the
         *    panel's own wider natural width. Laid out for real, 280 put the
         *    comments inside their two-line wrap band and the last line was
         *    sliced by the popup's bottom edge - the originally reported bug.
         *    The ordering was fixed (see the `.apply` block above) and the
         *    width raised to 300, then to 324 to pay for [FORM_BORDER]'s
         *    24px of horizontal insets.
         * 2. (2026-09-26) That second derivation budgeted the comment against
         *    the form's INTERIOR width, but a cell-level `comment()` never had
         *    the interior width: the label column takes its slice first, and
         *    the comment only ever got what was left. The "renders on one
         *    line at 324" claim recorded here was therefore untrue even on the
         *    machine it was measured on - the router comment wrapped to two
         *    lines on macOS too - and on Linux, where the platform UI font is
         *    about 12% wider, all three comments wrapped and the form grew
         *    from 206px to 234px, past [MAX_SANE_FORM_HEIGHT_PX]. CI caught
         *    what a macOS-only measurement could not.
         *
         * The fix was not a third width: comments now span the whole row (see
         * [withComment]), so the budget the width is chosen against is the one
         * the comment actually gets. At 324 that interior is 300px, against a
         * widest comment of 218px on macOS and 245px on Linux - enough margin
         * that a platform font wider still than Linux's keeps the comments on
         * one line, which is the property
         * `ChatParamsPopupLayoutPlatformTest` now pins directly instead of
         * inferring it from the form's total height.
         */
        const val FORM_WIDTH = 324
    }
}
