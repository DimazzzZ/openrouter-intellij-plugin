package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.ui.InplaceButton
import java.awt.Cursor
import java.awt.Graphics
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * The borderless icon button the chat uses everywhere: the composer's gear, the per-message copy
 * button and the one on a code block.
 *
 * It exists because a bare [InplaceButton] is not the affordance it looks like. Its
 * [InplaceButton.paintHover] hook is an empty method, so out of the box the button paints nothing
 * but its icon: no hover background, no pressed state, and no cursor change either. Next to the
 * conversation toolbar - whose buttons are `AnAction`s rendered by the platform's own
 * `ActionButton`, and therefore get all three for free - the difference reads as the chat's own
 * buttons being dead.
 *
 * The painting the platform withholds is not missing, only unwired: the protected
 * `paintHover(Graphics, Boolean)` overload draws the rounded background using
 * [com.intellij.util.ui.JBUI.CurrentTheme.ActionButton]'s own hover and pressed colours - the very
 * colours `ActionButton` paints with. Overriding the hook to call it is what the platform's own
 * subclasses do, and it is what makes these buttons match the toolbar in every theme rather than
 * approximating it with colours of our own.
 *
 * Pressed state is tracked here rather than read from the button's behaviour object, which
 * [InplaceButton] keeps private.
 */
internal open class ChatIconButton(
    tooltip: String,
    icon: Icon,
    onClick: () -> Unit
) : InplaceButton(tooltip, icon, ActionListener { onClick() }) {

    private var pressedByMouse = false

    init {
        // The pointer is the only clickability cue a borderless icon has before it is hovered.
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isFocusable = false
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = setPressedByMouse(true)
            override fun mouseReleased(e: MouseEvent) = setPressedByMouse(false)
            override fun mouseExited(e: MouseEvent) = setPressedByMouse(false)
        })
    }

    override fun paintHover(g: Graphics) {
        paintHover(g, pressedByMouse)
    }

    private fun setPressedByMouse(value: Boolean) {
        if (pressedByMouse == value) return
        pressedByMouse = value
        repaint()
    }
}
