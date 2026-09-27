package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.ui.InplaceButton
import java.awt.Cursor

/**
 * Makes a borderless icon button say it is clickable when the pointer is over it.
 *
 * [InplaceButton] sets no cursor of its own, so it inherits the arrow from whatever contains it.
 * That is fine for a button with a border, which announces itself by looking like a button; a
 * borderless icon has only its hover highlight, and until the pointer is already on it there is
 * nothing to distinguish it from decoration.
 *
 * Here rather than at each button so the two the chat window has - the composer's gear and the
 * per-message copy button - cannot drift apart, and so a third one inherits the decision instead
 * of rediscovering it.
 */
internal fun <T : InplaceButton> T.withHandCursor(): T = apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
}
