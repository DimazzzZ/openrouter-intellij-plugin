package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.JComponent

private const val EXPECTED_FORM_WIDTH = 324
private const val MAX_SANE_FORM_HEIGHT_PX = 210
private const val GEAR_BUTTON_HEIGHT = 24
private const val CONTENT_HEIGHT = 186
private const val EXTRA_ROOM_PX = 40
private const val NEGATIVE_SPACE_BELOW = -50
private const val UNSUPPORTED_REASON = "Not supported by this model"
private const val ROUTER_LABEL = "Cost tier (editable)"
private const val ROUTER_DESCRIPTION = "One of: low, medium, high, xhigh, max"

/**
 * Regression tests for the gear-popup two-column rebuild and its placement:
 *
 * 1. it anchors from the gear's corner either downward or upward depending on
 *    the space actually available below it (context-menu behaviour, sharing
 *    the button's left edge either way), instead of always dropping down and
 *    letting the platform relocate it wherever it happens to fit - see
 *    [testAnchorPointDropsDownWhenThereIsRoomBelow],
 *    [testAnchorPointOpensUpwardAndHugsTheButtonWhenThereIsNoRoomBelow],
 *    [testAnchorPointOpensUpwardWhenSpaceBelowIsNegative] and
 *    [testAnchorPointWithExactlyEnoughRoomBelowGoesDown];
 * 2. every control fills the rest of ITS OWN row (the control column), so all
 *    three combos share one right edge, instead of each sitting at its own
 *    narrow preferred width - see
 *    [testControlsFillTheirControlColumnInsteadOfSittingAtTheirOwnNarrowWidth];
 * 3. the form stays compact (labels to the LEFT of their controls rather than
 *    ABOVE), which took the worst-case height from 241px (three fields, each
 *    up to three lines) down to 186px (three fields, each one line plus at
 *    most one comment line, correctly measured with the width applied BEFORE
 *    the height is read - see
 *    [testFormPreferredWidthMatchesIntendedValueAndHeightStaysUnderSaneCeiling]);
 * 4. the router comment's second line is never sliced by the popup's bottom
 *    edge - the width-then-height ordering bug that briefly regressed (3)
 *    back to a wrapped comment and a too-short reported height - see
 *    [testRouterCommentSecondLineIsNotClippedByFormBottomEdge];
 * 5. no comment wraps at all, on any platform's UI font - the property (3)'s
 *    height ceiling only measures indirectly, and the one that actually
 *    regressed once the comments were reflowed against the leftover control
 *    column instead of the form's interior - see
 *    [testEveryCommentIsLaidOutWideEnoughToRenderOnOneLine].
 *
 * A real [com.intellij.openapi.ui.popup.JBPopup] cannot be driven headlessly
 * (it needs a live screen/event queue), so (1) is covered by testing
 * [ChatParamsPopup.anchorPoint] directly - the plain geometry that decides
 * where the popup lands - rather than asserting nothing about [ChatParamsPopup.show].
 */
class ChatParamsPopupLayoutPlatformTest : BasePlatformTestCase() {

    private fun buildForm(popup: ChatParamsPopup): JComponent {
        val method = ChatParamsPopup::class.java.getDeclaredMethod("buildForm")
        method.isAccessible = true
        return method.invoke(popup) as JComponent
    }

    /**
     * Mirrors [MessageViewLayoutPlatformTest]'s approach: nothing under test
     * ever gets a real, peered window in a headless platform-test process, so
     * [java.awt.Container.validate] is a no-op here - laying out the tree by
     * hand, twice, does the same job.
     */
    private fun layoutAtPreferredSize(component: JComponent): JComponent {
        component.setSize(component.preferredSize.width, component.preferredSize.height)
        layoutTreeRecursively(component)
        component.setSize(component.preferredSize.width, component.preferredSize.height)
        layoutTreeRecursively(component)
        return component
    }

    /**
     * The form is now two columns (label, then control), so a control fills
     * only the CONTROL column, not the raw form width - the label column
     * (sized to the widest label, "Cost tier (editable):") takes a real slice
     * of [EXPECTED_FORM_WIDTH] first. What FILL still buys: every control's
     * column-relative width is the same, so all three combos share one right
     * edge, and that shared width is well past each combo's own intrinsic
     * preferred size.
     */
    fun testControlsFillTheirControlColumnInsteadOfSittingAtTheirOwnNarrowWidth() {
        val reasoning = ComboBox(arrayOf("Default", "None", "Minimal"))
        val verbosity = ComboBox(arrayOf("Default", "Low"))
        val router = ComboBox(arrayOf("x"))
        val naturalWidth = reasoning.preferredSize.width

        val popup = ChatParamsPopup(reasoning, verbosity, router)
        val form = layoutAtPreferredSize(buildForm(popup))

        assertTrue("expected the form to be laid out at a real width, got ${form.width}", form.width > 0)
        assertTrue(
            "expected the reasoning combo (width=${reasoning.width}) to be widened by " +
                "AlignX.FILL well past its own narrow preferred width ($naturalWidth)",
            reasoning.width > naturalWidth
        )
        assertTrue(
            "expected the reasoning combo (width=${reasoning.width}) to leave room in the " +
                "form (width=${form.width}) for the label column next to it",
            reasoning.width < form.width
        )
        assertEquals(
            "expected the reasoning and verbosity combos to share one right edge (same " +
                "control-column width), since both rows use the same grid columns",
            reasoning.width,
            verbosity.width
        )
    }

    fun testFormPreferredWidthMatchesIntendedValueAndHeightStaysUnderSaneCeiling() {
        val popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))
        // Worst case: both comments visible (unsupported reason text) plus the
        // router row with its longest comment, so the height ceiling is
        // checked against the tallest the form actually gets.
        popup.setReasoningSupport(supported = false, reason = UNSUPPORTED_REASON)
        popup.setVerbositySupport(supported = false, reason = UNSUPPORTED_REASON)
        popup.setRouterParam(label = ROUTER_LABEL, description = ROUTER_DESCRIPTION, visible = true)

        val form = layoutAtPreferredSize(buildForm(popup))

        assertEquals(
            "FORM_WIDTH should be ${JBUI.scale(EXPECTED_FORM_WIDTH)} (unscaled $EXPECTED_FORM_WIDTH)",
            JBUI.scale(EXPECTED_FORM_WIDTH),
            form.preferredSize.width
        )
        assertTrue(
            "expected the three-field, two-column form's height (${form.preferredSize.height}) " +
                "to stay compact (<= $MAX_SANE_FORM_HEIGHT_PX), not balloon back into the " +
                "label-above-control form's dialog-sized territory",
            form.preferredSize.height <= JBUI.scale(MAX_SANE_FORM_HEIGHT_PX)
        )
    }

    /**
     * The height ceiling above is a consequence; this is the cause, and it is
     * the assertion that travels between platforms.
     *
     * Every comment must be laid out at least as wide as the width it needs,
     * so it renders on ONE line. When it is not, the comment wraps, each wrap
     * adds a line's height, and the form grows - which is how this regressed:
     * the comments used to hang off the control's cell, so they were reflowed
     * against the column left over beside the widest label rather than the
     * form's interior. That leftover column is a function of the platform's
     * own UI font, so the layout fitted on the machine it was measured on
     * (macOS, 175px) and wrapped on CI (Linux, 155px, with ~12% wider text on
     * top). A total-height ceiling can only catch that after the fact, and
     * only if the slack happens to be small enough - this catches the wrap
     * itself, on whichever platform the suite runs.
     */
    fun testEveryCommentIsLaidOutWideEnoughToRenderOnOneLine() {
        val popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))
        popup.setReasoningSupport(supported = false, reason = UNSUPPORTED_REASON)
        popup.setVerbositySupport(supported = false, reason = UNSUPPORTED_REASON)
        popup.setRouterParam(label = ROUTER_LABEL, description = ROUTER_DESCRIPTION, visible = true)

        val form = layoutAtPreferredSize(buildForm(popup))

        val comments = collectComments(form).filter { comment ->
            val text = comment.text.orEmpty()
            text.contains(UNSUPPORTED_REASON) || text.contains(ROUTER_DESCRIPTION)
        }
        assertEquals("expected all three comments to be present in the form", 3, comments.size)
        comments.forEach { comment ->
            assertTrue(
                "expected the comment <${plainText(comment)}> to be laid out at least as wide " +
                    "(width=${comment.width}) as the width it needs to render on one line " +
                    "(preferred=${comment.preferredSize.width}); a narrower row wraps it and " +
                    "grows the form by a line",
                comment.width >= comment.preferredSize.width
            )
        }
    }

    /** The comment's text is a whole HTML document; only its words are useful in a failure message. */
    private fun plainText(comment: javax.swing.JEditorPane) =
        comment.text.orEmpty().substringAfter("<body>").replace(Regex("<[^>]*>"), " ").trim()
            .replace(Regex("\\s+"), " ")

    /**
     * The DSL renders a comment as a `DslLabel`, which despite the name is a
     * [javax.swing.JEditorPane] - it displays an HTML document, style sheet
     * and all, which is also why its text has to be matched by substring
     * rather than compared.
     */
    private fun collectComments(root: java.awt.Container): List<javax.swing.JEditorPane> {
        val found = mutableListOf<javax.swing.JEditorPane>()
        fun walk(container: java.awt.Container) {
            container.components.forEach {
                if (it is javax.swing.JEditorPane) found += it
                if (it is java.awt.Container) walk(it)
            }
        }
        walk(root)
        return found
    }

    /**
     * The reported bug: the router comment "One of: low, medium, high, xhigh,
     * max" wraps onto a second line at [EXPECTED_FORM_WIDTH]'s control-column
     * width, and that second line was sliced by the popup's bottom edge.
     * `buildForm()` used to read `preferredSize.height` BEFORE clamping the
     * width to [EXPECTED_FORM_WIDTH] (see `ChatParamsPopup.buildForm`'s
     * trailing `.apply`), so the captured height was measured at the panel's
     * own wider, unclamped natural width - where this comment still fit on
     * ONE line - and was therefore too short once the width was forced down
     * and the comment reflowed onto two lines. Guards against that ordering
     * regressing.
     */
    fun testRouterCommentSecondLineIsNotClippedByFormBottomEdge() {
        val popup = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))
        popup.setRouterParam(label = ROUTER_LABEL, description = ROUTER_DESCRIPTION, visible = true)

        val form = layoutAtPreferredSize(buildForm(popup))

        assertNoDescendantClippedByBottomEdge(form)
    }

    /**
     * Spacing pass (see the visual-pass audit,
     * "Spacing pass"): the form used to have no border at all, so the
     * "Reasoning:" label sat flush against the popup's left edge and the
     * combos flush against the right. The form now gets
     * [ChatParamsPopup.FORM_BORDER] - the UI DSL's own
     * `IntelliJSpacingConfiguration.dialogUnscaledGaps` convention
     * (`top=10, left=12, bottom=10, right=12`, raw) - so this pins both the
     * border's actual insets and that real content (not just the panel's
     * own bounds) sits inset from the left edge because of it.
     */
    fun testFormHasRealInternalPaddingOnAllFourSides() {
        val popup = newPopup()

        val form = layoutAtPreferredSize(buildForm(popup))

        val insets = form.insets
        assertEquals("left padding should be the dialog convention's 12px (raw)", JBUI.scale(12), insets.left)
        assertEquals("right padding should be the dialog convention's 12px (raw)", JBUI.scale(12), insets.right)
        assertEquals("top padding should be the dialog convention's 10px (raw)", JBUI.scale(10), insets.top)
        assertEquals("bottom padding should be the dialog convention's 10px (raw)", JBUI.scale(10), insets.bottom)

        val labels = mutableListOf<Pair<javax.swing.JLabel, Int>>()
        fun collect(container: java.awt.Container, xOffset: Int) {
            container.components.forEach {
                val absoluteX = xOffset + it.x
                if (it is javax.swing.JLabel) labels += it to absoluteX
                if (it is java.awt.Container) collect(it, absoluteX)
            }
        }
        collect(form, 0)
        val (_, reasoningLabelX) = labels.first { (label, _) -> label.text == "Reasoning:" }

        assertTrue(
            "expected the Reasoning label (absolute x=$reasoningLabelX) to sit inset from the " +
                "form's left edge (insets.left=${insets.left}), not flush against it",
            reasoningLabelX >= insets.left
        )
    }

    private fun newPopup() = ChatParamsPopup(ComboBox(arrayOf("a")), ComboBox(arrayOf("b")), ComboBox(arrayOf("c")))

    /**
     * Room below the gear: anchors at the button's bottom-left corner, like a
     * context menu, not its center or top-left. [JBPopup.show] places the
     * popup's TOP-left corner at this anchor, so the popup drops down from
     * the button.
     */
    fun testAnchorPointDropsDownWhenThereIsRoomBelow() {
        val popup = newPopup()

        val anchor = popup.anchorPoint(
            buttonHeight = GEAR_BUTTON_HEIGHT,
            contentHeight = CONTENT_HEIGHT,
            spaceBelow = CONTENT_HEIGHT + EXTRA_ROOM_PX
        )

        assertEquals(
            "expected the popup to drop down from the button's bottom-left corner " +
                "when there is more than enough room below",
            Point(0, GEAR_BUTTON_HEIGHT),
            anchor
        )
    }

    /**
     * The bug four rounds missed: the gear sits at the bottom of the screen,
     * so there is normally NO room below it regardless of content height.
     * Anchoring [contentHeight] pixels ABOVE the button's top-left corner
     * puts the popup's BOTTOM edge on the button's TOP edge - it opens
     * upward and still hugs the button's left edge, instead of dropping down
     * and being relocated by the platform to wherever it happens to fit.
     */
    fun testAnchorPointOpensUpwardAndHugsTheButtonWhenThereIsNoRoomBelow() {
        val popup = newPopup()

        val anchor = popup.anchorPoint(
            buttonHeight = GEAR_BUTTON_HEIGHT,
            contentHeight = CONTENT_HEIGHT,
            spaceBelow = 0
        )

        assertEquals(
            "expected the popup to open upward, bottom edge on the button's top edge, " +
                "when there is no room below",
            Point(0, -CONTENT_HEIGHT),
            anchor
        )
    }

    /**
     * Negative space below (the button is already partly off-screen, or the
     * measurement is otherwise degenerate) must not fall through to a
     * nonsensical anchor - it is just an extreme case of "not enough room",
     * so it takes the same upward branch as zero room.
     */
    fun testAnchorPointOpensUpwardWhenSpaceBelowIsNegative() {
        val popup = newPopup()

        val anchor = popup.anchorPoint(
            buttonHeight = GEAR_BUTTON_HEIGHT,
            contentHeight = CONTENT_HEIGHT,
            spaceBelow = NEGATIVE_SPACE_BELOW
        )

        assertEquals(
            "expected negative space below to still produce a sane upward anchor, " +
                "not a nonsensical point",
            Point(0, -CONTENT_HEIGHT),
            anchor
        )
    }

    /**
     * Boundary: exactly enough room below to fit the content pins the
     * direction rather than leaving it to fall out of the arithmetic by
     * accident. [ChatParamsPopup.anchorPoint] uses `>=`, so an exact fit
     * goes downward, same as having room to spare.
     */
    fun testAnchorPointWithExactlyEnoughRoomBelowGoesDown() {
        val popup = newPopup()

        val anchor = popup.anchorPoint(
            buttonHeight = GEAR_BUTTON_HEIGHT,
            contentHeight = CONTENT_HEIGHT,
            spaceBelow = CONTENT_HEIGHT
        )

        assertEquals(
            "expected an exact fit below to go downward, not upward",
            Point(0, GEAR_BUTTON_HEIGHT),
            anchor
        )
    }
}
