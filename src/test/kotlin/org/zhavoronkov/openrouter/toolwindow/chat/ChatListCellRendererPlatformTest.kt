package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.toolwindow.ChatPanel
import java.text.SimpleDateFormat
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class ChatListCellRendererPlatformTest : BasePlatformTestCase() {

    private fun session(title: String) = ChatPanel.ChatSession(
        id = "id",
        title = title,
        messages = mutableListOf(),
        totalTokens = 0,
        createdAt = 0L
    )

    fun testRendererProducesComponentsNotHtml() {
        val renderer = ChatListCellRenderer(SimpleDateFormat("dd.MM.yyyy, HH:mm"))
        val component = renderer.getListCellRendererComponent(
            JList<ChatPanel.ChatSession>(),
            session("Pareto demo"),
            0,
            false,
            false
        )

        assertTrue("row must be a panel, not an HTML label", component is JPanel)

        val labels = (component as JPanel).components.filterIsInstance<JLabel>()
        assertEquals(2, labels.size)
        assertEquals("Pareto demo", labels[0].text)
        assertFalse("no HTML in the row", labels.any { it.text.contains("<html") })
    }

    fun testHoveredRowShowsCloseIconInsteadOfDate() {
        val renderer = ChatListCellRenderer(SimpleDateFormat("dd.MM.yyyy, HH:mm"))
        renderer.hoveredIndex = 0

        val hoveredRow = renderer.getListCellRendererComponent(
            JList<ChatPanel.ChatSession>(),
            session("Hovered chat"),
            0,
            false,
            false
        ) as JPanel
        val hoveredLabels = hoveredRow.components.filterIsInstance<JLabel>()
        assertEquals(2, hoveredLabels.size)
        assertNotNull("hovered row must show the delete icon", hoveredLabels[1].icon)
        assertTrue("hovered row must not show the date as text", hoveredLabels[1].text.isNullOrEmpty())

        val otherRow = renderer.getListCellRendererComponent(
            JList<ChatPanel.ChatSession>(),
            session("Other chat"),
            1,
            false,
            false
        ) as JPanel
        val otherLabels = otherRow.components.filterIsInstance<JLabel>()
        assertEquals(2, otherLabels.size)
        assertNull("non-hovered row must not show the delete icon", otherLabels[1].icon)
        assertFalse("non-hovered row must show the date", otherLabels[1].text.isNullOrEmpty())
    }
}
