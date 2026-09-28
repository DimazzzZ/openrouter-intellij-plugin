package org.zhavoronkov.openrouter.toolwindow.chat

/**
 * Pure decision logic for the send-parameters gear badge (spec D7): whether
 * any parameter differs from Default, and what the gear's tooltip should say
 * when it does.
 *
 * This is the single compensation for moving reasoning/verbosity/router-param
 * out of sight into [ChatParamsPopup] — if it is wrong, the user silently
 * sends a request with settings they can no longer see. Following the same
 * pattern as `ComposerLayoutPolicy`/`MiddleEllipsis`, the decision is pulled
 * out into a pure function with zero `com.intellij`/`javax.swing`/`java.awt`
 * imports so it runs in the fast headless `test` task and is covered by unit
 * tests instead of a manual visual check. [ChatParamsPopup] keeps
 * `hasNonDefaultSelection()`/`activeSummary()` as thin readers that snapshot
 * the live combo boxes into [Selection] and delegate here.
 */
object ChatParamsState {

    /**
     * A snapshot of the three send-parameter controls, carrying only what the
     * badge decision needs — no Swing types.
     *
     * [reasoningIndex]/[verbosityIndex] follow the combo boxes' own convention
     * that index 0 is "Default"; anything else is a non-default choice.
     * [routerValue] is the router-param control's raw current value: null or
     * blank means "no selection", regardless of whether the router row is
     * currently shown. [routerVisible] gates whether a router value counts at
     * all — a value left over from a previously shown router must NOT count
     * once the control is hidden (the model changed away from that router).
     * [webSearch] always counts when on: a search is charged per request, so
     * a toggle left on is exactly what the badge exists to keep in sight.
     */
    data class Selection(
        val reasoningIndex: Int,
        val reasoningValue: String?,
        val verbosityIndex: Int,
        val verbosityValue: String?,
        val routerVisible: Boolean,
        val routerLabel: String?,
        val routerValue: String?,
        val webSearch: Boolean = false
    )

    fun hasNonDefaultSelection(selection: Selection): Boolean =
        selection.reasoningIndex > 0 ||
            selection.verbosityIndex > 0 ||
            (selection.routerVisible && !selection.routerValue.isNullOrBlank()) ||
            selection.webSearch

    fun activeSummary(selection: Selection): String {
        val parts = mutableListOf<String>()
        if (selection.reasoningIndex > 0) parts += "Reasoning: ${selection.reasoningValue}"
        if (selection.verbosityIndex > 0) parts += "Verbosity: ${selection.verbosityValue}"
        if (selection.routerVisible && !selection.routerValue.isNullOrBlank()) {
            parts += "${selection.routerLabel.orEmpty()}: ${selection.routerValue}"
        }
        if (selection.webSearch) parts += "Web search"
        return if (parts.isEmpty()) "Send parameters" else parts.joinToString(" · ")
    }
}
