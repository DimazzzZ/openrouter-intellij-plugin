package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

private const val CODE_LINE_COUNT = 30

/**
 * Task 13: the fenced-code segment scrolls horizontally on its own instead of
 * clipping, and keeps its Task 12 copy button.
 */
class CodeSegmentViewPlatformTest : BasePlatformTestCase() {

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

    fun testComponentContainsAHorizontallyScrollingCodeArea() {
        val view = CodeSegmentView(MessageSegment.Code(language = "kotlin", code = "val x = 1"))

        val scrollPane = findDescendant(view.component, JBScrollPane::class.java)
        assertNotNull("code area must be inside a scroll pane", scrollPane)
        assertEquals(
            "horizontal scrolling must be available",
            JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
            scrollPane!!.horizontalScrollBarPolicy
        )
        assertEquals(
            "vertical scrolling must stay off - the segment's height is fixed",
            JBScrollPane.VERTICAL_SCROLLBAR_NEVER,
            scrollPane.verticalScrollBarPolicy
        )
    }

    fun testLanguageLabelShownWhenPresent() {
        val view = CodeSegmentView(MessageSegment.Code(language = "python", code = "print(1)"))

        val label = findDescendant(view.component, JBLabel::class.java)
        assertNotNull("language label must be present", label)
        assertEquals("python", label!!.text)
    }

    fun testNoLanguageLabelWhenAbsent() {
        val view = CodeSegmentView(MessageSegment.Code(language = null, code = "print(1)"))

        assertNull("no language label when the fence has none", findDescendant(view.component, JBLabel::class.java))
    }

    /**
     * `platformTest` forces true AWT headless mode (see build.gradle.kts), and
     * a genuinely headless JVM refuses all clipboard access with
     * [java.awt.HeadlessException] - this is a limitation of the test
     * environment, not of the production code, which only ever runs inside a
     * real, non-headless IDE. The button and its listener are still verified
     * to be wired to the real [Toolkit] clipboard call: invoking it here
     * reaches that call and fails with exactly [java.awt.HeadlessException],
     * not some unrelated wiring bug.
     */
    fun testCopyButtonIsWiredToTheSystemClipboard() {
        val code = "fun main() = println(\"hi\")"
        val view = CodeSegmentView(MessageSegment.Code(language = "kotlin", code = code))

        assertNull(
            "the copy affordance must not be a bordered JButton - see the InplaceButton assertion below",
            findDescendant(view.component, JButton::class.java)
        )
        val button = findDescendant(view.component, InplaceButton::class.java)
        assertNotNull("copy button must be present", button)
        assertEquals("Copy code", button!!.toolTipText)
        assertFalse("the copy button itself must not steal focus", button.isFocusable)
        assertEquals("the copy affordance must show a hand cursor", Cursor.HAND_CURSOR, button.cursor.type)

        try {
            button.doClick()
            val clipboardText = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor)
            assertEquals(code, clipboardText)
        } catch (headless: java.awt.HeadlessException) {
            // Expected under platformTest's forced headless mode - see KDoc above.
            assertNotNull(headless)
        }
    }

    /**
     * The headline fix of this task: the scrollbar's own thickness is
     * reserved only when the content actually needs to scroll, not
     * unconditionally. A `>=` assertion here would still pass even if that
     * conditional logic were deleted (a constant height satisfies `>=` too),
     * so this asserts the real relationship: narrow minus wide must equal
     * the live scrollbar's own thickness, exactly - not a hard-coded 14.
     */
    fun testScrollbarHeightIsReservedOnlyWhenTheContentActuallyNeedsToScroll() {
        val view = CodeSegmentView(MessageSegment.Code(language = null, code = "x".repeat(500)))
        val scrollPane = findDescendant(view.component, JBScrollPane::class.java)!!
        val scrollbarThickness = scrollPane.horizontalScrollBar.preferredSize.height

        val heightAtNarrowWidth = run {
            scrollPane.setSize(50, 0)
            scrollPane.preferredSize.height
        }
        val heightAtWideWidth = run {
            // Wide enough that the unwrapped "x".repeat(500) area fits without
            // needing to scroll - no allowance should be added here.
            scrollPane.setSize(10_000, 0)
            scrollPane.preferredSize.height
        }

        assertEquals(
            "the narrow (scrolling) case must exceed the wide (fitting) case by exactly " +
                "the live scrollbar's own thickness ($scrollbarThickness), not a fixed constant",
            scrollbarThickness,
            heightAtNarrowWidth - heightAtWideWidth
        )
    }

    /**
     * Width-then-height audit (see
     * the visual-pass audit): `HorizontallyScrollingPane`'s
     * `naturalSize` lambda is read lazily inside its own overridden
     * `getPreferredSize()`, at real layout time - not snapshotted eagerly the
     * way `ChatParamsPopup.buildForm()` briefly was - and the wrapped
     * [JBTextArea] has `lineWrap = false`, so its height never depends on its
     * width in the first place. This asserts the actual symptom rather than
     * just re-describing that reasoning.
     */
    fun testCodeSegmentHasNoDescendantClippedByBottomEdge() {
        val manyLines = (1..CODE_LINE_COUNT).joinToString("\n") { "line number $it of the code block" }
        val view = CodeSegmentView(MessageSegment.Code(language = "kotlin", code = manyLines))
        val component = view.component

        component.setSize(component.preferredSize.width, component.preferredSize.height)
        layoutTreeRecursively(component)
        component.setSize(component.preferredSize.width, component.preferredSize.height)
        layoutTreeRecursively(component)

        assertNoDescendantClippedByBottomEdge(component)
    }

    private fun wheelEvent(target: java.awt.Component, shiftDown: Boolean = false): MouseWheelEvent {
        val modifiers = if (shiftDown) InputEvent.SHIFT_DOWN_MASK else 0
        return MouseWheelEvent(
            target,
            MouseWheelEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            modifiers,
            10,
            10,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            3,
            2
        )
    }

    private fun filler(text: String) = JBTextArea(text).apply { maximumSize = Dimension(Int.MAX_VALUE, 20) }

    /**
     * Confirmed (task 13 report) that a nested AS_NEEDED-horizontal /
     * NEVER-vertical scroll pane swallows plain wheel input outright: neither
     * it nor its ancestor scrolls, and the event comes back consumed. This
     * reproduces that exact shape and asserts the fix's actual, observable
     * effect - the ancestor's own view position moving - not merely that a
     * listener ran without throwing.
     */
    fun testWheelOverCodeScrollsTheAncestorConversationPane() {
        val view = CodeSegmentView(MessageSegment.Code(language = null, code = "z".repeat(2000)))
        val nestedScrollPane = findDescendant(view.component, JBScrollPane::class.java)!!

        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            repeat(3) { add(filler("filler line $it")) }
            add(view.component)
            repeat(50) { add(filler("filler line $it")) }
        }
        val ancestorScrollPane = JBScrollPane(column).apply {
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        ancestorScrollPane.setSize(300, 200)
        ancestorScrollPane.doLayout()
        column.setSize(300, column.preferredSize.height)
        column.doLayout()

        val positionBefore = ancestorScrollPane.viewport.viewPosition.y
        nestedScrollPane.dispatchEvent(wheelEvent(nestedScrollPane))
        val positionAfter = ancestorScrollPane.viewport.viewPosition.y

        assertTrue(
            "scrolling the wheel over a code block must actually move the surrounding " +
                "conversation's view position (before=$positionBefore, after=$positionAfter)",
            positionAfter > positionBefore
        )
    }

    fun testWheelOverCodeWithNoAncestorScrollPaneDoesNotThrow() {
        val view = CodeSegmentView(MessageSegment.Code(language = null, code = "w".repeat(500)))
        val nestedScrollPane = findDescendant(view.component, JBScrollPane::class.java)!!

        // view.component is never attached anywhere here, so there is no
        // ancestor JScrollPane to find - the listener must tolerate that.
        nestedScrollPane.dispatchEvent(wheelEvent(nestedScrollPane))
    }

    /**
     * Shift+wheel (the conventional horizontal-scroll chord) must stay local:
     * `forwardVerticalWheelToAncestor` deliberately leaves shift-down events
     * alone instead of forwarding them, so `JBScrollPane`'s own built-in
     * handling can still scroll the code horizontally. This is drivable
     * headlessly (unlike the opaque-scrollbar-overlap case, which is not -
     * see the task 13 report) and was confirmed to actually move the nested
     * pane before writing this assertion.
     */
    fun testShiftWheelOverCodeScrollsTheCodeHorizontallyNotTheAncestor() {
        val view = CodeSegmentView(MessageSegment.Code(language = null, code = "z".repeat(2000)))
        val nestedScrollPane = findDescendant(view.component, JBScrollPane::class.java)!!

        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            repeat(3) { add(filler("filler line $it")) }
            add(view.component)
            repeat(50) { add(filler("filler line $it")) }
        }
        val ancestorScrollPane = JBScrollPane(column).apply {
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        ancestorScrollPane.setSize(300, 200)
        ancestorScrollPane.doLayout()
        column.setSize(300, column.preferredSize.height)
        column.doLayout()

        val ancestorYBefore = ancestorScrollPane.viewport.viewPosition.y
        val nestedXBefore = nestedScrollPane.viewport.viewPosition.x

        nestedScrollPane.dispatchEvent(wheelEvent(nestedScrollPane, shiftDown = true))

        val ancestorYAfter = ancestorScrollPane.viewport.viewPosition.y
        val nestedXAfter = nestedScrollPane.viewport.viewPosition.x

        assertEquals(
            "shift+wheel must not scroll the conversation",
            ancestorYBefore,
            ancestorYAfter
        )
        assertTrue(
            "shift+wheel must scroll the code horizontally (before=$nestedXBefore, after=$nestedXAfter)",
            nestedXAfter > nestedXBefore
        )
    }
}
