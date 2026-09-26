package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.toolwindow.ChatPanel
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.DateFormat
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * The chat-list card. Owns list state and gestures only; creating, opening and
 * deleting sessions stays with ChatPanel, reached through the callbacks.
 */
class ChatListView(dateFormat: DateFormat) {

    private val model = DefaultListModel<ChatPanel.ChatSession>()

    // Overrides getToolTipText so the ✕ (and, off-hover, the title) get a
    // real tooltip: JList does not delegate tooltips to the cell renderer on
    // its own, so this is the one place a per-row tooltip can live.
    private val list = object : JBList<ChatPanel.ChatSession>(model) {
        init {
            // Setting a (throwaway) tooltip text is what registers this
            // component with ToolTipManager in the first place; the actual,
            // per-row text always comes from the override below.
            toolTipText = ""
        }

        override fun getToolTipText(event: MouseEvent): String? {
            val index = rowAt(event)
            if (index < 0) return null
            val session = model.getElementAt(index) ?: return null
            val cellBounds = getCellBounds(index, index) ?: return null
            return if (index == hoveredIndex && renderer.closeIconRect(cellBounds).contains(event.point)) {
                "Delete chat"
            } else {
                session.title
            }
        }
    }
    private val renderer = ChatListCellRenderer(dateFormat)

    /** Row currently under the mouse, or -1. Mirrored onto [renderer] for painting. */
    private var hoveredIndex = -1

    var onOpen: (ChatPanel.ChatSession) -> Unit = {}
    var onDelete: (ChatPanel.ChatSession) -> Unit = {}
    var onRename: (ChatPanel.ChatSession, String) -> Unit = { _, _ -> }

    val component: JComponent

    private val popupMenu = buildPopupMenu()

    init {
        list.cellRenderer = renderer

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // isPopupTrigger is only reliable on PRESSED/RELEASED (it differs
                // by platform which one carries it); the synthesized CLICKED event
                // is not guaranteed to mirror it. Gate on the button instead, so a
                // right-click can never race showPopupIfNeeded into also opening
                // (or ✕-deleting) the row it just force-selected.
                if (e.clickCount == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    handleSingleClick(e)
                }
            }

            override fun mousePressed(e: MouseEvent) = showPopupIfNeeded(e)
            override fun mouseReleased(e: MouseEvent) = showPopupIfNeeded(e)

            override fun mouseMoved(e: MouseEvent) = updateHover(rowAt(e))
            override fun mouseExited(e: MouseEvent) = updateHover(-1)

            private fun showPopupIfNeeded(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    val index = list.locationToIndex(e.point)
                    if (index >= 0) {
                        list.selectedIndex = index
                        popupMenu.show(list, e.x, e.y)
                    }
                }
            }
        }
        list.addMouseListener(mouseHandler)
        list.addMouseMotionListener(mouseHandler)

        list.registerKeyboardAction(
            { list.selectedValue?.let(::promptRename) },
            KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        list.registerKeyboardAction(
            { list.selectedValue?.let(onDelete) },
            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )

        val scrollPane = JBScrollPane(list)
        scrollPane.border = JBUI.Borders.empty()

        component = JPanel(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    fun setSessions(sessions: List<ChatPanel.ChatSession>) {
        model.clear()
        sessions.forEach(model::addElement)
        // Row identities are gone; drop any stale hover so a leftover ✕ can't
        // paint over the wrong (possibly unrelated) row.
        updateHover(-1)
    }

    /** The row index under [e], or -1 if the point is not over any row. */
    private fun rowAt(e: MouseEvent): Int {
        val index = list.locationToIndex(e.point)
        if (index < 0) return -1
        val bounds = list.getCellBounds(index, index) ?: return -1
        return if (bounds.contains(e.point)) index else -1
    }

    private fun updateHover(newIndex: Int) {
        if (newIndex == hoveredIndex) return
        val oldIndex = hoveredIndex
        hoveredIndex = newIndex
        renderer.hoveredIndex = newIndex
        if (oldIndex >= 0) list.getCellBounds(oldIndex, oldIndex)?.let(list::repaint)
        if (newIndex >= 0) list.getCellBounds(newIndex, newIndex)?.let(list::repaint)
    }

    private fun handleSingleClick(e: MouseEvent) {
        val index = rowAt(e)
        if (index < 0) return
        val session = model.getElementAt(index) ?: return

        if (index == hoveredIndex) {
            val cellBounds = list.getCellBounds(index, index)
            if (cellBounds != null && renderer.closeIconRect(cellBounds).contains(e.point)) {
                // Route through the same callback the context menu's Delete
                // item uses, which is wired to a confirm dialog in ChatPanel —
                // the ✕ must never delete without confirmation.
                onDelete(session)
                return
            }
        }

        onOpen(session)
    }

    private fun promptRename(session: ChatPanel.ChatSession) {
        val newTitle = Messages.showInputDialog(
            list,
            "Chat name:",
            "Rename Chat",
            null,
            session.title,
            null
        )?.trim()
        if (!newTitle.isNullOrEmpty() && newTitle != session.title) {
            onRename(session, newTitle)
        }
    }

    private fun buildPopupMenu(): JPopupMenu {
        val popup = JPopupMenu()
        popup.add(
            JMenuItem("Open").apply {
                addActionListener { list.selectedValue?.let(onOpen) }
            }
        )
        popup.add(
            JMenuItem("Rename").apply {
                addActionListener { list.selectedValue?.let(::promptRename) }
            }
        )
        popup.add(
            JMenuItem("Delete").apply {
                addActionListener { list.selectedValue?.let(onDelete) }
            }
        )
        return popup
    }
}
