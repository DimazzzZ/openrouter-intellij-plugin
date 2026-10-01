package org.zhavoronkov.openrouter.settings

import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout

/** The Requests tab's settings on the OpenRouter page: its warning balloons, and how many requests it keeps. */
class RequestsSection {

    val warningBalloons = JBCheckBox("Show a balloon when a tool's request fails or stops early")

    /** How many requests the Requests tab keeps, most recent first. */
    val limit = JBIntSpinner(DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT, LIMIT_STEP)

    fun addTo(panel: Panel) = with(panel) {
        row {
            cell(warningBalloons)
        }.layout(RowLayout.PARENT_GRID)

        row("Requests kept:") {
            cell(limit).comment("The Requests tab keeps this many of the most recent requests.")
        }.layout(RowLayout.PARENT_GRID)
    }

    companion object {
        private const val DEFAULT_LIMIT = 1000
        private const val MIN_LIMIT = 100
        private const val MAX_LIMIT = 100_000
        private const val LIMIT_STEP = 100
    }
}
