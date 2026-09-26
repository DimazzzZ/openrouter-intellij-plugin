package org.zhavoronkov.openrouter.toolwindow.composer

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.toolwindow.chat.ChatComposer
import org.zhavoronkov.openrouter.toolwindow.chat.assertNoDescendantClippedByBottomEdge
import org.zhavoronkov.openrouter.toolwindow.chat.layoutTreeRecursively
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.plaf.basic.BasicComboBoxUI

private const val WIDE = 600
private val NARROW_WIDTHS = listOf(0, 8, 24, 40, 120)
private const val ROW_HEIGHT = 30
private const val MODEL_ID = "anthropic/claude-sonnet-4.5"
private const val LONG_INPUT_WORD_COUNT = 200

/**
 * Regression test for the circular measurement that latched the model combo to
 * zero width.
 *
 * [ComposerLayout] used to derive `Parts.modelPreferred` from the combo's own
 * `preferredSize`, which `BasicComboBoxUI.getDisplaySize()` computes by asking
 * the renderer with `index == -1` — precisely the branch where
 * [MiddleEllipsisComboRenderer] truncates the text to fit `combo.width`. The
 * policy's input was therefore derived from the policy's own previous output,
 * and on the very first pass (`combo.width == 0`) it measured an empty string
 * and pinned the combo at that size for good.
 */
class ComposerLayoutPlatformTest : BasePlatformTestCase() {

    private lateinit var row: JPanel
    private lateinit var combo: ComboBox<String>

    override fun setUp() {
        super.setUp()
        val composer = ChatComposer()
        combo = ComboBox(arrayOf(MODEL_ID))
        combo.selectedItem = MODEL_ID
        composer.attachModelCombo(combo)
        row = composer.component.components
            .filterIsInstance<JPanel>()
            .first { it.layout is ComposerLayout }
    }

    private fun layoutAt(width: Int) {
        row.setBounds(0, 0, width, ROW_HEIGHT)
        row.doLayout()
    }

    fun testModelComboGetsARealWidthOnTheFirstPass() {
        layoutAt(WIDE)

        assertTrue(
            "the combo must be measured from its full text, not from what the renderer last drew;" +
                " got ${combo.width}px",
            combo.width > JBUI.scale(ComposerLayoutPolicy.MODEL_MIN_WIDTH)
        )
    }

    fun testModelComboRecoversAfterACollapsedPass() {
        layoutAt(WIDE)
        val wide = combo.width

        layoutAt(0)
        layoutAt(WIDE)

        assertEquals("the combo must return to its proper width when the panel widens again", wide, combo.width)
    }

    fun testModelComboNeverCollapsesBelowItsFloor() {
        layoutAt(0)

        assertEquals(
            "MODEL_MIN_WIDTH is the floor a miscalculation has to land on",
            JBUI.scale(ComposerLayoutPolicy.MODEL_MIN_WIDTH),
            combo.width
        )
    }

    fun testACrampedRowStillPlacesEveryPartInsideThePanel() {
        NARROW_WIDTHS.forEach { width ->
            layoutAt(width)

            row.components.forEach { part ->
                assertTrue(
                    "at ${width}px, ${part.javaClass.simpleName} was placed at x=${part.x}," +
                        " left of the panel's insets",
                    part.x >= row.insets.left
                )
                assertTrue(
                    "at ${width}px, ${part.javaClass.simpleName} was given a negative width (${part.width})",
                    part.width >= 0
                )
            }
        }
    }

    /**
     * Width-then-height audit (see
     * docs/superpowers/2026-09-18-visual-pass-fixes.md): the composer's input
     * area wraps its text, so a long draft's reported height depends on the
     * width it is finally laid out at, the same shape as the two bugs this
     * class of test now catches generically.
     */
    fun testLongComposerInputIsNotClippedByBottomEdge() {
        val composer = ChatComposer()
        composer.text = "draft text ".repeat(LONG_INPUT_WORD_COUNT).trim()
        val component = composer.component

        component.setSize(WIDE, component.preferredSize.height)
        layoutTreeRecursively(component)
        component.setSize(WIDE, component.preferredSize.height)
        layoutTreeRecursively(component)

        assertNoDescendantClippedByBottomEdge(component)
    }

    /**
     * Spacing pass (see docs/superpowers/2026-09-18-visual-pass-fixes.md,
     * "Spacing pass"): the gear used to sit only [ComposerLayout]'s single
     * `gap` away from the model combo, same as every other pair in the row,
     * which read as visibly tighter than the rest of the row. Measured why:
     * the gear ([com.intellij.ui.InplaceButton]) has no border and zero
     * insets of its own — real, live values, not assumed — unlike its
     * neighbours (the model combo is bordered; on the gear's other side,
     * Send is a real bordered [javax.swing.JButton]). [ComposerLayout] now
     * doubles the gap for the model-gear pair only, reusing its own existing
     * gap unit rather than a new number, while every other pair keeps the
     * single standard gap.
     */
    fun testGearGetsDoubleGapFromModelComboButStandardGapElsewhere() {
        layoutAt(WIDE)

        val settings = row.components[1] as JComponent
        val counters = row.components[2]
        val send = row.components[3]

        assertEquals(
            "the gear must contribute zero border/inset margin of its own",
            Insets(0, 0, 0, 0),
            settings.insets
        )

        val standardGap = counters.x - (settings.x + settings.width)
        val modelToSettingsGap = settings.x - (combo.x + combo.width)
        val countersToSendGap = send.x - (counters.x + counters.width)

        assertEquals(
            "model->gear gap must be exactly double the row's standard gap, to offset the " +
                "gear's zero margin",
            standardGap * 2,
            modelToSettingsGap
        )
        assertEquals(
            "every other pair in the row keeps the row's standard gap",
            standardGap,
            countersToSendGap
        )
    }

    /**
     * Pins the reported complaint directly: `anthropic/claude-sonnet-4.5` (27
     * characters — a genuinely medium model id, longer than `openrouter/auto`
     * but well short of the longest ids in `ModelPresets`/the favourites
     * table) must show in full, not middle-ellipsised, once the row has
     * enough width to spare.
     *
     * Renders the closed cell exactly as the platform does: through the
     * wrapped [MiddleEllipsisComboRenderer], called with the combo's own
     * internal `listBox` (see `BasicComboBoxUI.getCurrentRendererComponent`,
     * which every look and feel's `paintCurrentValue` calls through) rather
     * than a throwaway `JList`, whose font would not be guaranteed to match
     * the combo's.
     */
    fun testMediumModelIdIsNotEllipsisedWhenTheRowHasRoom() {
        layoutAt(WIDE)

        val rendered = renderClosedCellText()

        assertEquals(
            "a medium id with room to spare must show in full",
            MODEL_ID,
            rendered
        )
        assertFalse("must not contain an ellipsis when it fits", rendered.contains("…"))
    }

    private fun renderClosedCellText(): String {
        val listBoxField = BasicComboBoxUI::class.java.getDeclaredField("listBox")
        listBoxField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val listBox = listBoxField.get(combo.ui) as JList<String>

        val label = combo.renderer.getListCellRendererComponent(listBox, MODEL_ID, -1, false, false)
        return (label as javax.swing.JLabel).text
    }
}
