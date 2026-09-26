package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import java.awt.Container
import javax.swing.JEditorPane

/**
 * Task 13: a prose segment containing a `<table>` or a `<pre>` (a fenced
 * block nested inside a list or blockquote, per MessageSegmenter's contract)
 * scrolls horizontally on its own instead of clipping; plain prose keeps
 * using the in-flow [WrappingEditorPane] unchanged.
 */
class MessageViewPlatformTest : BasePlatformTestCase() {

    private fun <T> findDescendant(root: java.awt.Component, type: Class<T>): T? {
        if (type.isInstance(root)) {
            @Suppress("UNCHECKED_CAST")
            return root as T
        }
        if (root is Container) {
            for (child in root.components) {
                findDescendant(child, type)?.let { return it }
            }
        }
        return null
    }

    private fun <T> countDescendants(root: java.awt.Component, type: Class<T>): Int {
        var count = if (type.isInstance(root)) 1 else 0
        if (root is Container) {
            for (child in root.components) {
                count += countDescendants(child, type)
            }
        }
        return count
    }

    fun testPlainProseIsNotWrappedInAScrollPane() {
        val view = MessageView("Just a short reply, nothing fancy.", isUser = false, footnote = null)

        assertNull(
            "plain prose must not get its own scroll pane",
            findDescendant(view.component, HorizontallyScrollingPane::class.java)
        )
        assertNotNull(
            "plain prose must still render via the in-flow wrapping pane",
            findDescendant(view.component, WrappingEditorPane::class.java)
        )
    }

    fun testTableGetsItsOwnHorizontalScrollPane() {
        val markdown = """
            | a | b |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val view = MessageView(markdown, isUser = false, footnote = null)

        val scrollPane = findDescendant(view.component, HorizontallyScrollingPane::class.java)
        assertNotNull("a table must be wrapped in its own horizontal scroll pane", scrollPane)
        assertEquals(JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, scrollPane!!.horizontalScrollBarPolicy)
        assertEquals(JBScrollPane.VERTICAL_SCROLLBAR_NEVER, scrollPane.verticalScrollBarPolicy)
    }

    /**
     * The exact shape the task 13 brief calls out: a fenced code block nested
     * inside a numbered list stays in prose (MessageSegmenter only extracts
     * top-level fences), so it must reach the reader through this scroll
     * pane rather than being clipped - and the list's numbering/indentation
     * must survive because nothing tore the fence out of its list item.
     */
    fun testFencedCodeInsideANumberedListStaysInProseAndScrolls() {
        val markdown = """
            1. Install it:

               ```bash
               npm install foo
               ```

            2. Run it.
        """.trimIndent()

        val segments = MessageSegmenter.split(markdown)
        assertTrue(
            "the fence must stay inside prose, not become its own Code segment",
            segments.none { it is MessageSegment.Code }
        )

        val view = MessageView(markdown, isUser = false, footnote = null)

        val scrollPane = findDescendant(view.component, HorizontallyScrollingPane::class.java)
        assertNotNull("the list-with-code prose segment must scroll horizontally", scrollPane)

        val pane = findDescendant(scrollPane!!, JEditorPane::class.java)
        assertNotNull(pane)
        val html = pane!!.text
        // Ordered-list numbering in rendered HTML is native <ol>/<li> markup,
        // not literal "1."/"2." text - flexmark renders it that way, and the
        // browser/view engine supplies the numbers. Checking for the tags is
        // what "numbering survives" actually means here.
        assertTrue("the fence's container must still be an ordered list", html.contains("<ol"))
        assertTrue("the fence must still be inside a list item, not torn out", html.contains("<li"))
        assertTrue("the code itself must still be present", html.contains("npm install foo"))

        // Exactly one CodeSegmentView-style scroll pane for this whole segment -
        // the code did not get torn out into a second, separate component.
        assertEquals(1, countDescendants(view.component, HorizontallyScrollingPane::class.java))
    }

    fun testMultipleTopLevelCodeFencesEachGetTheirOwnScrollPane() {
        val markdown = """
            ```kotlin
            val a = 1
            ```

            Some text in between.

            ```kotlin
            val b = 2
            ```
        """.trimIndent()

        val view = MessageView(markdown, isUser = false, footnote = null)

        assertEquals(2, countDescendants(view.component, HorizontallyScrollingPane::class.java))
    }
}
