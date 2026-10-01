package org.zhavoronkov.openrouter.settings

import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager

/**
 * The Requests tab's settings on the OpenRouter page: its warning balloons, how many requests it
 * keeps, and whether it keeps their bodies.
 */
class RequestsSection {

    val warningBalloons = JBCheckBox("Show a balloon when a tool's request fails or stops early")

    /** Whether every request's bodies are kept; off by default, since they hold the user's code. */
    val keepBodies = JBCheckBox("Keep each request's prompt and reply")

    /** How many requests the Requests tab keeps, most recent first. */
    val limit = JBIntSpinner(DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT, LIMIT_STEP)

    fun addTo(panel: Panel) = with(panel) {
        row {
            cell(warningBalloons)
        }.layout(RowLayout.PARENT_GRID)

        row("Requests kept:") {
            cell(limit).comment("The Requests tab keeps this many of the most recent requests.")
        }.layout(RowLayout.PARENT_GRID)

        row {
            cell(keepBodies).comment(KEEP_BODIES_COMMENT)
        }.layout(RowLayout.PARENT_GRID)
    }

    /** Whether the section shows something other than what [prefs] stores. */
    fun isModified(prefs: UIPreferencesManager): Boolean =
        warningBalloons.isSelected != prefs.requestWarningBalloons ||
            limit.number != prefs.requestLogLimit ||
            keepBodies.isSelected != prefs.keepRequestBodies

    /** Stores what the section shows in [prefs]. */
    fun apply(prefs: UIPreferencesManager) {
        prefs.requestWarningBalloons = warningBalloons.isSelected
        prefs.requestLogLimit = limit.number
        prefs.keepRequestBodies = keepBodies.isSelected
    }

    /** Shows what [prefs] stores. */
    fun reset(prefs: UIPreferencesManager) {
        warningBalloons.isSelected = prefs.requestWarningBalloons
        limit.number = prefs.requestLogLimit
        keepBodies.isSelected = prefs.keepRequestBodies
    }

    companion object {
        private const val DEFAULT_LIMIT = 1000
        private const val MIN_LIMIT = 100
        private const val MAX_LIMIT = 100_000
        private const val LIMIT_STEP = 100

        const val KEEP_BODIES_COMMENT =
            "Shown in each request's details on the Requests tab. Kept as plain text in the IDE's " +
                "configuration directory, and they hold whatever a tool sends - your code included. " +
                "New requests only; Clear on the Requests tab deletes what is kept."
    }
}
