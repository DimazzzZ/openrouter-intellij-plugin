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
 * The spec's D6 claimed that without a provisioning key the tab falls back to `/credits` and
 * `/activity`. That fallback does not exist - both endpoints require the provisioning key
 * themselves, and so does listing API keys - so DEGRADED is not a thinner READY with blanks where
 * numbers would go. It is a genuinely different surface: no account data at all, and a line
 * explaining exactly what is missing (a provisioning key) and how to add one, never a silent gap.
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
        const val TITLE = "Limited: no provisioning key"
        const val MESSAGE = "Account balance, activity and API key limits need a provisioning " +
            "key, which is not set. Add one to see them - locally observed spend recorded " +
            "while the IDE was running is shown below instead, when there is enough of it to plot."
        const val BUTTON_TEXT = "Add Provisioning Key"
    }
}
