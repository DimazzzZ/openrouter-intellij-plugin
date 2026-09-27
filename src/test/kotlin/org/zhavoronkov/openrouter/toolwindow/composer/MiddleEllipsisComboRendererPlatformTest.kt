package org.zhavoronkov.openrouter.toolwindow.composer

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

private const val MODEL_ID = "anthropic/claude-3.5-sonnet-with-a-very-long-suffix-to-force-truncation"
private const val COMBO_WIDTH = 160
private const val CLOSED_CELL_INDEX = -1

/**
 * Regression test for the visual-pass defect where the closed model combo
 * showed ".../html>" instead of a model name.
 *
 * ChatPanel's real delegate renderer sets HTML markup (variant chip spans) as
 * the cell's text; that markup is meant for the popup's rich rendering only.
 * [MiddleEllipsisComboRenderer] must rebuild the CLOSED cell (`index == -1`)
 * from the raw model id as plain text, never by middle-ellipsising the
 * delegate's HTML string itself.
 */
class MiddleEllipsisComboRendererPlatformTest : BasePlatformTestCase() {

    /** Mimics ChatPanel's real renderer: sets HTML chip markup as cell text. */
    private val htmlDelegate = ListCellRenderer<String> { _, value, _, _, _ ->
        JLabel().apply {
            text = "<html><nobr>$value &nbsp; <span style='color:#000'>Free</span></nobr></html>"
        }
    }

    fun testClosedCellIsPlainTextDerivedFromTheModelId() {
        val combo = ComboBox(arrayOf(MODEL_ID))
        val renderer = MiddleEllipsisComboRenderer(combo, htmlDelegate)
        combo.renderer = renderer
        combo.setSize(COMBO_WIDTH, combo.preferredSize.height)

        val list = JList(arrayOf(MODEL_ID))
        val component = renderer.getListCellRendererComponent(list, MODEL_ID, CLOSED_CELL_INDEX, false, false)
        val text = (component as JLabel).text

        assertFalse("closed cell must contain no HTML markup at all, got '$text'", text.contains("<"))

        val ellipsis = "…"
        val parts = text.split(ellipsis)
        if (parts.size == 2) {
            assertTrue("head of '$text' must be a prefix of the model id", MODEL_ID.startsWith(parts[0]))
            assertTrue("tail of '$text' must be a suffix of the model id", MODEL_ID.endsWith(parts[1]))
        } else {
            assertEquals("untruncated closed cell text must be the raw model id", MODEL_ID, text)
        }
    }

    fun testPopupRowKeepsTheDelegatesHtmlUntouched() {
        val combo = ComboBox(arrayOf(MODEL_ID))
        val renderer = MiddleEllipsisComboRenderer(combo, htmlDelegate)
        combo.renderer = renderer
        combo.setSize(COMBO_WIDTH, combo.preferredSize.height)

        val list = JList(arrayOf(MODEL_ID))
        val component = renderer.getListCellRendererComponent(list, MODEL_ID, 0, false, false)
        val text = (component as JLabel).text

        assertTrue("popup row must keep the delegate's HTML, got '$text'", text.startsWith("<html>"))
    }
}
