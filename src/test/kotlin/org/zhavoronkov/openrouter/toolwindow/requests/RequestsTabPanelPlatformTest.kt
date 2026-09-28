package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import java.awt.Color
import java.awt.Component
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Requests tab as a user sees it: the rows, the details of a selected one, the filters and the
 * day's totals, the columns a narrow tool window keeps, and the warning mark as painted.
 *
 * Every seam runs synchronously here, so a record shows the moment the panel is built.
 */
class RequestsTabPanelPlatformTest : BasePlatformTestCase() {

    private val noon = Instant.parse("2026-09-29T12:00:00Z")
    private var cleared = false

    private fun record(
        sender: String = "Junie",
        model: String = "openai/gpt-4o",
        reply: ReplyFacts = ReplyFacts(finishReason = "stop", cost = 0.01),
        error: String? = null,
        source: RequestSource = RequestSource.PROXY
    ) = RequestRecord(noon.toEpochMilli(), 800, source, sender, model, reply, error)

    private val records = listOf(
        record(
            model = "anthropic/claude-sonnet-4.5",
            reply = ReplyFacts(generationId = "gen-1", finishReason = "stop", cost = 0.02)
        ),
        record(sender = "Chat", model = "openai/gpt-4o", source = RequestSource.CHAT),
        record(sender = "Junie", model = "openai/gpt-4o", reply = ReplyFacts(finishReason = "length", cost = 0.005))
    )

    private fun panel(
        shown: () -> List<RequestRecord> = { records },
        background: (Runnable) -> Unit = Runnable::run
    ): RequestsTabPanel {
        val panel = RequestsTabPanel(
            recent = shown,
            clearLog = { cleared = true },
            confirmClear = { true },
            background = background,
            edt = Runnable::run,
            clock = { noon },
            zone = { ZoneOffset.UTC }
        )
        Disposer.register(testRootDisposable, panel)
        resize(panel, 700)
        return panel
    }

    private fun resize(panel: RequestsTabPanel, width: Int) {
        panel.component.setSize(width, 500)
        layOut(panel.component)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun layOut(component: Component) {
        component.doLayout()
        (component as? java.awt.Container)?.components?.forEach(::layOut)
    }

    private fun column(panel: RequestsTabPanel, column: RequestsColumn): List<String> =
        (0 until panel.table.rowCount).map { panel.table.model.getValueAt(it, column.ordinal).toString() }

    private fun visibleTitles(panel: RequestsTabPanel): List<String> =
        (0 until panel.table.columnCount).map { panel.table.getColumnName(it) }

    private fun detailTexts(panel: RequestsTabPanel): List<String> =
        UIUtil.findComponentsOfType(panel.detailsPanel, JBLabel::class.java).map { it.text }

    private fun links(panel: RequestsTabPanel): List<String> =
        UIUtil.findComponentsOfType(panel.detailsPanel, ActionLink::class.java).map { it.text }

    fun testRowsAreListedNewestFirstWithTheirFacts() {
        val panel = panel()

        assertEquals(
            listOf("anthropic/claude-sonnet-4.5", "openai/gpt-4o", "openai/gpt-4o"),
            column(panel, RequestsColumn.MODEL)
        )
        assertEquals(listOf("Junie", "Chat", "Junie"), column(panel, RequestsColumn.SENDER))
        assertEquals(listOf("$0.02", "$0.01", "$0.005"), column(panel, RequestsColumn.COST))
        assertEquals(listOf("12:00:00", "12:00:00", "12:00:00"), column(panel, RequestsColumn.TIME))
    }

    fun testSelectingARowShowsEveryFactAndTheLinkToTheGeneration() {
        val panel = panel()
        assertTrue("nothing selected yet", RequestsTabPanel.SELECT_TEXT in detailTexts(panel))

        panel.table.setRowSelectionInterval(0, 0)

        val texts = detailTexts(panel)
        assertTrue("the requested id: $texts", "anthropic/claude-sonnet-4.5" in texts)
        assertTrue("the generation id: $texts", "gen-1" in texts)
        assertEquals(listOf("Open logs on openrouter.ai", "Copy generation id"), links(panel))

        panel.table.setRowSelectionInterval(1, 1)
        assertEquals("no generation id, nothing to copy", listOf("Open logs on openrouter.ai"), links(panel))
    }

    /** Until the first read arrives the log is unknown, not empty, and must not be called empty. */
    fun testNothingIsCalledEmptyBeforeTheLogIsRead() {
        val panel = panel(background = { })

        assertFalse(RequestsTabPanel.EMPTY_TEXT in detailTexts(panel))
    }

    /** A read that finishes after the tab was closed must not touch it. */
    fun testAReadFinishingAfterDisposalIsDropped() {
        val pending = mutableListOf<Runnable>()
        val panel = panel(background = { pending += it })

        Disposer.dispose(panel)
        pending.single().run()

        assertEquals(0, panel.table.rowCount)
    }

    fun testAnEmptyLogSaysWhereRequestsComeFrom() {
        val panel = panel(shown = { emptyList() })

        assertTrue(RequestsTabPanel.EMPTY_TEXT in detailTexts(panel))
        assertEquals("Today: 0 requests · $0", panel.todayLabel.text)
    }

    fun testFiltersNarrowTheTableAndTheTotalsFollowThem() {
        val panel = panel()
        assertEquals("Today: 3 requests · $0.035 · 1 warning", panel.todayLabel.text)

        panel.senderFilter.selectedItem = "Junie"
        assertEquals(2, panel.table.rowCount)
        assertEquals("Today: 2 requests · $0.025 · 1 warning", panel.todayLabel.text)

        panel.warningsOnly.doClick()
        assertEquals(listOf("openai/gpt-4o"), column(panel, RequestsColumn.MODEL))

        panel.warningsOnly.doClick()
        panel.senderFilter.selectedItem = RequestsTabPanel.ALL_SENDERS
        panel.modelFilter.selectedItem = "openai/gpt-4o"
        assertEquals(listOf("Chat", "Junie"), column(panel, RequestsColumn.SENDER))
    }

    fun testANewRecordAppearsWhenTheLogSaysItChanged() {
        var current = records.drop(1)
        val panel = panel(shown = { current })
        assertEquals(2, panel.table.rowCount)

        current = records
        ApplicationManager.getApplication().messageBus.syncPublisher(RequestLogListener.TOPIC).changed(records[0])

        assertEquals(3, panel.table.rowCount)
    }

    /** A read that finishes after a newer one was asked for must not overwrite what the newer one showed. */
    fun testAStaleReadIsDropped() {
        val pending = mutableListOf<Runnable>()
        var current = records.take(1)
        val panel = panel(shown = { current }, background = { pending += it })
        pending.removeAt(0).run()

        panel.reload() // the older read, still pending
        panel.reload()
        current = records
        pending.removeAt(1).run() // the newer read finishes first, and shows every record
        current = records.take(1)
        pending.removeAt(0).run() // the older one would show a single record, and must not

        assertEquals(3, panel.table.rowCount)
    }

    fun testANarrowTabDropsColumnsInsteadOfScrollingSideways() {
        val panel = panel()
        assertEquals(listOf("Time", "Sender", "Requested", "Cost", ""), visibleTitles(panel))

        resize(panel, 180)

        assertEquals(listOf("Requested", ""), visibleTitles(panel))
        assertColumnsFit(panel)

        resize(panel, 700)
        assertEquals(listOf("Time", "Sender", "Requested", "Cost", ""), visibleTitles(panel))
        assertColumnsFit(panel)
    }

    /** A column dragged wider keeps its width over a resize and a refill; the requested id gives way. */
    fun testADraggedColumnKeepsItsWidth() {
        val panel = panel()
        val table = panel.table
        val sender = table.columnModel.getColumn(table.columnModel.getColumnIndex("Sender"))
        val requested = table.columnModel.getColumn(table.columnModel.getColumnIndex("Requested"))
        val requestedBefore = requested.width

        table.tableHeader.resizingColumn = sender
        sender.width = sender.width + 60
        table.tableHeader.resizingColumn = null
        val dragged = sender.width
        resize(panel, 700)
        panel.reload()
        resize(panel, 700)

        assertEquals(dragged, sender.width)
        assertTrue("the requested id takes what is left", requested.width < requestedBefore)
        assertColumnsFit(panel)
    }

    fun testTheWarningMarkCannotBeDragged() {
        val table = panel().table

        assertFalse(table.columnModel.getColumn(table.columnModel.getColumnIndex("")).resizable)
    }

    /** The filters wrap onto another line in a narrow tab; none is pushed past its right edge. */
    fun testANarrowTabKeepsEveryFilterInSight() {
        val panel = panel(
            shown = { records + record(model = "anthropic/claude-3.5-sonnet-20241022:thinking:extended-context") }
        )

        resize(panel, 220)

        val controls = listOf(panel.senderFilter, panel.modelFilter, panel.warningsOnly) +
            UIUtil.findComponentsOfType(panel.component, ActionLink::class.java).filter { it.text == "Clear" }
        controls.forEach {
            val right = javax.swing.SwingUtilities.convertPoint(it.parent, it.x + it.width, 0, panel.component).x
            assertTrue("${it.javaClass.simpleName} ends at $right in a 220 px tab", right <= 220)
        }
    }

    /** The columns add up to no more than the visible width, so nothing hides past the right edge. */
    private fun assertColumnsFit(panel: RequestsTabPanel) {
        val table = panel.table
        val columns = (0 until table.columnCount).sumOf { table.columnModel.getColumn(it).width }
        val viewport = table.parent.width
        assertTrue("columns $columns px wide in a $viewport px tab", columns <= viewport)
    }

    fun testRevealingARequestClearsTheFiltersAndSelectsIt() {
        val panel = panel()
        panel.senderFilter.selectedItem = "Chat"
        panel.warningsOnly.doClick()

        panel.reveal(records[2])

        assertEquals(RequestsTabPanel.ALL_SENDERS, panel.senderFilter.selectedItem)
        assertFalse(panel.warningsOnly.isSelected)
        assertEquals(2, panel.table.selectedRow)
    }

    /** The balloon can be quicker than the tab's own read of the log. */
    fun testARequestRevealedBeforeTheTabReadItIsSelectedWhenItArrives() {
        var current = records.take(2)
        val panel = panel(shown = { current })

        panel.reveal(records[2])
        assertEquals(-1, panel.table.selectedRow)

        current = records
        panel.reload()
        assertEquals(2, panel.table.selectedRow)
    }

    fun testClearAsksAndClearsTheLog() {
        val panel = panel()

        UIUtil.findComponentsOfType(panel.component, ActionLink::class.java).single { it.text == "Clear" }.doClick()

        assertTrue(cleared)
    }

    fun testTheWarningMarkIsPaintedOnlyOnARowThatWentWrong() {
        val panel = panel()

        assertTrue("a cut-off reply must paint a warning mark", colourfulPixels(warningCell(panel, 2)) > 0)
        assertEquals("a normal stop paints no mark", 0, colourfulPixels(warningCell(panel, 0)))
    }

    /** The warning cell of [row] as the table itself paints it. */
    private fun warningCell(panel: RequestsTabPanel, row: Int): BufferedImage {
        val table = panel.table
        val viewColumn = table.convertColumnIndexToView(RequestsColumn.WARNING.ordinal)
        val painted = BufferedImage(table.width, table.height, BufferedImage.TYPE_INT_RGB)
        val g = painted.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, painted.width, painted.height)
            table.paint(g)
        } finally {
            g.dispose()
        }
        val cell = table.getCellRect(row, viewColumn, false)
        return painted.getSubimage(cell.x, cell.y, cell.width, cell.height)
    }

    /** Pixels of a strong hue: the warning mark is amber, and nothing else in the cell has colour. */
    private fun colourfulPixels(image: BufferedImage): Int {
        var count = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val c = Color(image.getRGB(x, y))
                if (maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue) > CHROMA && c.red > c.blue) count++
            }
        }
        return count
    }

    private companion object {
        const val CHROMA = 80
    }
}
