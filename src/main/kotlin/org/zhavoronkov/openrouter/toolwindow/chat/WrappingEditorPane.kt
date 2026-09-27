package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JEditorPane

/**
 * A JEditorPane that reports the height its HTML actually needs at its current
 * width, rather than the height it would need at its preferred width.
 *
 * Swing has no built-in way to say "wrap to this width, then tell me how tall
 * you are"; forcing the width before asking is the standard way to get it.
 * `setSize()` marks the component (and its ancestors) invalid, but that alone
 * does not re-enter layout synchronously — Swing only re-lays-out on the next
 * explicit `revalidate()`/`validate()`, so measuring here does not recurse.
 */
class WrappingEditorPane(contentType: String, text: String) : JEditorPane(contentType, text) {

    init {
        isEditable = false
        border = null
        margin = JBUI.emptyInsets()
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
        putClientProperty(W3C_LENGTH_UNITS, true)
    }

    override fun getPreferredSize(): Dimension {
        val currentWidth = if (width > 0) width else JBUI.scale(FALLBACK_WIDTH)
        setSize(currentWidth, Short.MAX_VALUE.toInt())
        return Dimension(currentWidth, super.getPreferredSize().height)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    private companion object {
        const val FALLBACK_WIDTH = 200
    }
}
