package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.table.JBTable
import javax.swing.JLabel
import javax.swing.table.DefaultTableModel

/** The Requests table's own cell renderers, given an empty cell as Swing may hand one. */
class RequestsTableRenderersPlatformTest : BasePlatformTestCase() {

    private val table = JBTable(DefaultTableModel(1, 1)).apply { setSize(200, 40) }

    fun testAnEmptyRequestedIdCellShowsNothing() {
        val cell = MiddleEllipsisRenderer().getTableCellRendererComponent(table, null, false, false, 0, 0) as JLabel

        assertEquals("", cell.text)
        assertNull("nothing was cut, so there is no tooltip", cell.toolTipText)
    }

    fun testAnEmptyWarningCellShowsNoMark() {
        val cell = WarningRenderer().getTableCellRendererComponent(table, null, false, false, 0, 0) as JLabel

        assertNull(cell.icon)
        assertNull(cell.toolTipText)
    }

    fun testAWarningCellShowsTheMarkWithTheReasonAsItsTooltip() {
        val cell = WarningRenderer().getTableCellRendererComponent(table, "Cut off", false, false, 0, 0) as JLabel

        assertSame(WarningMark, cell.icon)
        assertEquals("Cut off", cell.toolTipText)
        assertEquals("the mark is drawn, not written", "", cell.text)
    }
}
