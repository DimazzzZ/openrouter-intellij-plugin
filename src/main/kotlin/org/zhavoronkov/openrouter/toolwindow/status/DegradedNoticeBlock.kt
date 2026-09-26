package org.zhavoronkov.openrouter.toolwindow.status

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The DEGRADED state's explanatory banner.
 *
 * The spec's D6 claimed that without a management key the tab falls back to `/credits` and
 * `/activity`. Measured against the live API (2026-09-21), that is half true: `/credits` answers
 * for an ordinary API key too (it is account-scoped, not key-scoped), but `/activity`,
 * `/analytics/query`, `/analytics/meta` and `/keys` all still require a management key. So
 * DEGRADED is not "no account data at all" any more - it is "the balance is real, the rest needs
 * a management key" - and this banner states exactly that, rather than implying nothing on the
 * tab can be trusted.
 *
 * Shaped like [StatusTabPanel]'s "not configured" panel - a bordered message plus an action
 * button - but built as its own component in the same family as [BalanceBlock]/[KeyLimitBlock]/
 * [BreakdownBlock] rather than grown inline, so [StatusTabPanel] gains one field and a visibility
 * toggle for this state instead of another panel-building method of its own. Takes [onConfigure]
 * rather than a [com.intellij.openapi.project.Project] itself - the caller already owns the
 * project reference needed to open the settings dialog.
 *
 * Hidden by default, like [KeyLimitBlock]: nothing has decided this is the active state yet.
 */
class DegradedNoticeBlock(onConfigure: () -> Unit) {

    val component: JComponent = JPanel(BorderLayout()).apply {
        isVisible = false
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor()),
            JBUI.Borders.empty(BORDER_SIZE)
        )

        add(JBLabel("<html><b>$TITLE</b><br/>$MESSAGE</html>"), BorderLayout.CENTER)
        add(
            JButton(BUTTON_TEXT).apply { addActionListener { onConfigure() } },
            BorderLayout.SOUTH
        )
    }

    private companion object {
        const val BORDER_SIZE = 10
        const val TITLE = "Limited: Management Key needed for full details"
        const val MESSAGE = "The per-model breakdown, activity history and the API key spend " +
            "cap need a Management Key, which is not set. The account balance below already " +
            "works from the API key alone."
        const val BUTTON_TEXT = "Add Management Key"
    }
}
