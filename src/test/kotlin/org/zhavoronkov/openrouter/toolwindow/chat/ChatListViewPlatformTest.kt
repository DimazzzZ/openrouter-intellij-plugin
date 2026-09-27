package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.toolwindow.ChatPanel
import java.awt.Dimension
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.text.SimpleDateFormat
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Pins the Task 6 fix-review finding: a right-button click must never open a
 * chat. `isPopupTrigger` is only reliable on PRESSED/RELEASED (it differs by
 * platform which event carries it), so `mouseClicked` must gate on the mouse
 * button instead — otherwise a right-click's synthesized CLICKED event could
 * race `showPopupIfNeeded` into also opening the row it just force-selected.
 */
class ChatListViewPlatformTest : BasePlatformTestCase() {

    private fun session(id: String) = ChatPanel.ChatSession(
        id = id,
        title = "Chat $id",
        messages = mutableListOf(),
        totalTokens = 0,
        createdAt = 0L
    )

    @Suppress("UNCHECKED_CAST")
    private fun innerList(chatListView: ChatListView): JList<ChatPanel.ChatSession> {
        val panel = chatListView.component as JPanel
        val scrollPane = panel.components.filterIsInstance<JScrollPane>().first()
        return scrollPane.viewport.view as JList<ChatPanel.ChatSession>
    }

    fun testRightClickDoesNotOpenChat() {
        val chatListView = ChatListView(SimpleDateFormat("dd.MM.yyyy, HH:mm"))
        var opened: ChatPanel.ChatSession? = null
        chatListView.onOpen = { opened = it }
        chatListView.setSessions(listOf(session("id-1")))

        val list = innerList(chatListView)
        list.size = Dimension(LIST_WIDTH, LIST_HEIGHT)

        val cellBounds = list.getCellBounds(0, 0)
        assertNotNull("test setup needs a real row rectangle to be meaningful", cellBounds)
        assertTrue(
            "test setup needs a non-empty row rectangle, or the click below would be " +
                "rejected by locationToIndex/cellBounds for a reason unrelated to the button check",
            cellBounds!!.width > 0 && cellBounds.height > 0
        )
        val point = Point(cellBounds.x + 1, cellBounds.y + 1)

        val rightClick = MouseEvent(
            list,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            InputEvent.BUTTON3_DOWN_MASK,
            point.x,
            point.y,
            1,
            false,
            MouseEvent.BUTTON3
        )
        list.dispatchEvent(rightClick)

        assertNull("a right-button click must never open the chat", opened)
    }

    private companion object {
        const val LIST_WIDTH = 300
        const val LIST_HEIGHT = 400
    }
}
