package org.zhavoronkov.openrouter.toolwindow.chat

import org.junit.Assert.assertTrue
import java.awt.Container
import javax.swing.JScrollPane
import javax.swing.JViewport

/**
 * Shared headless-layout helpers for the chat platform tests.
 *
 * Nothing under test in these platform tests ever gets a real, peered window
 * in a headless test process, so [java.awt.Component.isValid] is always
 * false and [java.awt.Container.validate] is a no-op here. Laying out the
 * tree by hand, one [Container.doLayout] pass per level, does the same job
 * `validate()` does in production - callers typically do this twice (see
 * e.g. [MessageViewLayoutPlatformTest]'s `layoutInPanel`), since a
 * [WrappingEditorPane]-style component only learns its real width on the
 * first pass, so anything measured from its height needs a second one.
 */
fun layoutTreeRecursively(container: Container) {
    container.doLayout()
    container.components.forEach { if (it is Container) layoutTreeRecursively(it) }
}

/**
 * Whether [viewport] belongs to a [JScrollPane] whose vertical scrollbar
 * policy is not `NEVER` - i.e. whether its view is ALLOWED to be taller than
 * the viewport, with the excess reachable by scrolling, rather than merely
 * clipped and gone. `viewport.parent` is the owning [JScrollPane] itself:
 * that is how [JScrollPane] wires its viewport (see [JScrollPane.setViewport]),
 * so this needs no upward search.
 */
private fun canScrollVertically(viewport: JViewport): Boolean {
    val scrollPane = viewport.parent as? JScrollPane ?: return false
    return scrollPane.viewport === viewport &&
        scrollPane.verticalScrollBarPolicy != JScrollPane.VERTICAL_SCROLLBAR_NEVER
}

/**
 * Walks the whole laid-out tree rooted at [root] and reports every
 * descendant whose bottom edge (in [root]'s own coordinate space, i.e.
 * summing ancestor `y` offsets) falls below the current ceiling - the
 * geometric shape of "this content got sliced by its container's bottom
 * edge", as opposed to merely checking that the container's own reported
 * height stays under some ceiling (that can pass while a child is clipped,
 * because a height field can be wrong independently of what its children
 * actually need).
 *
 * The ceiling starts at [containerHeight] and is only ever RE-ANCHORED, never
 * dropped, when the walk enters a [JViewport] that [canScrollVertically]: a
 * view taller than a viewport it can actually scroll is the normal, correct
 * shape of a scroll pane (that is what produces the scrollbar) - the content
 * is reachable, unlike the two real bugs this helper exists to catch, where
 * the clipped content was reachable by nothing at all. So the view itself is
 * exempted from the check, but the walk still continues INTO it, re-anchored
 * to the view's own real bottom edge, so a genuine clipping bug nested inside
 * a legitimately-scrollable region (e.g. a message with its own internal
 * clipping bug, sitting inside the scrollable conversation) is still caught.
 * A [JScrollPane] whose policy is `NEVER` (both `HorizontallyScrollingPane`
 * uses of it) gets no exemption at all - nothing there is meant to overflow
 * vertically, so any overflow is exactly the bug this helper looks for.
 *
 * The same reasoning applies on the horizontal axis for a right-edge
 * variant, keyed off `horizontalScrollBarPolicy` instead - not implemented
 * here since nothing currently asserts on the right edge, but any future
 * version of this check must exempt a legitimately horizontally-scrolling
 * view (e.g. a code segment's [HorizontallyScrollingPane]) the same way.
 */
fun findBottomOverflowingDescendants(root: Container, containerHeight: Int = root.height): List<String> {
    val violations = mutableListOf<String>()
    fun walk(container: Container, yOffset: Int, ceiling: Int) {
        val exemptFromCeiling = container is JViewport && canScrollVertically(container)
        container.components.forEach { child ->
            val absoluteBottom = yOffset + child.y + child.height
            if (!exemptFromCeiling && absoluteBottom > ceiling) {
                violations += "${child.javaClass.simpleName} absoluteBottom=$absoluteBottom " +
                    "(y=${child.y}, height=${child.height}) > containerHeight=$ceiling"
            }
            if (child is Container) {
                val childCeiling = if (exemptFromCeiling) absoluteBottom else ceiling
                walk(child, yOffset + child.y, childCeiling)
            }
        }
    }
    walk(root, 0, containerHeight)
    return violations
}

/**
 * The one assertion behind both width-then-height bugs this plugin has
 * shipped (see docs/superpowers/2026-09-18-visual-pass-fixes.md, "Width-then
 * -height audit"): a component whose preferred HEIGHT depends on the WIDTH
 * it is given, measured before that width is applied, ends up clipping a
 * descendant against its container's bottom edge. Call this on any
 * laid-out ([layoutTreeRecursively]) component tree to catch that shape
 * generically, instead of relying on a human spotting it in a screenshot.
 *
 * A descendant inside a viewport that can actually scroll past its own
 * bounds is exempt - see [findBottomOverflowingDescendants]'s KDoc for why
 * that is not weakening this check, and resist "simplifying" it back to a
 * flat height comparison; that reopens the composer's input area (which is
 * deliberately scrollable) as a permanent false positive.
 */
fun assertNoDescendantClippedByBottomEdge(root: Container, containerHeight: Int = root.height) {
    val violations = findBottomOverflowingDescendants(root, containerHeight)
    assertTrue(
        "expected no descendant to be clipped by the container's bottom edge " +
            "(height=$containerHeight), but found: $violations",
        violations.isEmpty()
    )
}
