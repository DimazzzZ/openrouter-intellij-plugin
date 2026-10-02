package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestBodies
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.toolwindow.chat.ReplySummary
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
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
    private var confirmed = true
    private var groupBursts = true
    private var keepBodies = false
    private val kept = mutableMapOf<String, RequestBodies>()
    private val shownBodies = mutableListOf<RequestBodies>()
    private var bodiesGone = 0

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
            confirmClear = { confirmed },
            background = background,
            edt = Runnable::run,
            clock = { noon },
            zone = { ZoneOffset.UTC },
            groupBurstsSetting = { groupBursts },
            saveGroupBursts = { groupBursts = it },
            keepBodiesSetting = { keepBodies },
            saveKeepBodies = { keepBodies = it },
            loadBodies = { kept[it] },
            showBodies = { _, bodies -> shownBodies += bodies },
            sayBodiesGone = { bodiesGone++ }
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
        assertEquals(listOf(RequestDetails.OPEN_LOG_TEXT, "Copy generation id"), links(panel))

        panel.table.setRowSelectionInterval(1, 1)
        assertEquals("no generation id, no log to open", emptyList<String>(), links(panel))
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

        val switches = listOf(panel.warningsOnly, panel.groupBursts, panel.keepBodies)
        val controls = listOf(panel.senderFilter, panel.modelFilter) + switches +
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

    fun testClearCancelledKeepsTheLog() {
        confirmed = false
        val panel = panel()

        UIUtil.findComponentsOfType(panel.component, ActionLink::class.java).single { it.text == "Clear" }.doClick()

        assertFalse(cleared)
        assertEquals(3, panel.table.rowCount)
    }

    /** A reply that finishes after its request was listed updates the row in place. */
    fun testARequestUpdatedInTheLogIsShownUpdated() {
        var current = records
        val panel = panel(shown = { current })

        current = listOf(records[0].copy(reply = ReplyFacts(finishReason = "stop", cost = 0.5))) + records.drop(1)
        ApplicationManager.getApplication().messageBus.syncPublisher(RequestLogListener.TOPIC).updated()

        assertEquals(ReplySummary.formatCost(0.5), column(panel, RequestsColumn.COST)[0])
    }

    /** A request filtered out of sight takes the selection with it, and the details say to pick one. */
    fun testASelectedRequestFilteredOutOfSightLeavesNothingSelected() {
        val panel = panel()
        panel.table.setRowSelectionInterval(1, 1)

        panel.senderFilter.selectedItem = "Junie"

        assertEquals(-1, panel.table.selectedRow)
        assertTrue(RequestsTabPanel.SELECT_TEXT in detailTexts(panel))
    }

    /** Bodies read after the tab was closed are not shown on it. */
    fun testBodiesReadAfterTheTabClosedAreNotShown() {
        kept["0f0e0d0c-0000-4000-8000-000000000002"] = RequestBodies(received = "{}", reply = "{}")
        val withBodies = record().copy(bodiesId = "0f0e0d0c-0000-4000-8000-000000000002")
        val pending = mutableListOf<Runnable>()
        val panel = panel(shown = { listOf(withBodies) }, background = { pending += it })
        pending.removeAt(0).run()
        panel.table.setRowSelectionInterval(0, 0)

        UIUtil.findComponentsOfType(panel.detailsPanel, ActionLink::class.java)
            .single { it.text == RequestsTabPanel.SHOW_BODIES_TEXT }
            .doClick()
        Disposer.dispose(panel)
        pending.single().run()

        assertTrue(shownBodies.isEmpty())
    }

    fun testTheWarningMarkIsPaintedOnlyOnARowThatWentWrong() {
        val panel = panel()

        assertTrue("a cut-off reply must paint a warning mark", colourfulPixels(warningCell(panel, 2)) > 0)
        assertEquals("a normal stop paints no mark", 0, colourfulPixels(warningCell(panel, 0)))
    }

    fun testARequestWithKeptBodiesOffersThemAndOneWithoutDoesNot() {
        val bodies = RequestBodies(received = "{}", reply = "{}")
        kept["0f0e0d0c-0000-4000-8000-000000000001"] = bodies
        val withBodies = record(model = "kept/model").copy(bodiesId = "0f0e0d0c-0000-4000-8000-000000000001")
        val panel = panel(shown = { listOf(withBodies) + records })

        panel.table.setRowSelectionInterval(1, 1)
        assertFalse(RequestsTabPanel.SHOW_BODIES_TEXT in links(panel))

        panel.table.setRowSelectionInterval(0, 0)
        UIUtil.findComponentsOfType(panel.detailsPanel, ActionLink::class.java)
            .single { it.text == RequestsTabPanel.SHOW_BODIES_TEXT }
            .doClick()

        assertEquals(listOf(bodies), shownBodies)
    }

    /** Bodies dropped past the log's limit, or cleared, since the request was listed are said to be gone. */
    fun testBodiesGoneSinceTheRequestWasListedAreSaidToBeGone() {
        val withBodies = record(model = "kept/model").copy(bodiesId = "0f0e0d0c-0000-4000-8000-000000000002")
        val panel = panel(shown = { listOf(withBodies) })
        panel.table.setRowSelectionInterval(0, 0)

        UIUtil.findComponentsOfType(panel.detailsPanel, ActionLink::class.java)
            .single { it.text == RequestsTabPanel.SHOW_BODIES_TEXT }
            .doClick()

        assertEquals(1, bodiesGone)
        assertTrue(shownBodies.isEmpty())
    }

    /** The tab's switch is the settings page's: it shows what is stored, and turning it on stores that. */
    fun testKeepingPromptAndReplyCanBeTurnedOnFromTheTab() {
        val panel = panel()
        assertFalse("off unless the user turns it on", panel.keepBodies.isSelected)

        panel.keepBodies.doClick()
        assertTrue(keepBodies)

        assertTrue("a tab built later shows it on", panel().keepBodies.isSelected)
    }

    /** A prompt is one long JSON string; it wraps rather than scrolling sideways. */
    fun testAKeptBodyWrapsInsteadOfScrollingSideways() {
        val view = RequestBodiesDialog.bodyView("x ".repeat(2_000))
        val area = view.viewport.view as javax.swing.JTextArea

        assertTrue(area.lineWrap)
        assertTrue(area.wrapStyleWord)
        assertEquals(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, view.horizontalScrollBarPolicy)
        assertFalse(area.isEditable)
    }

    // --- Bursts --------------------------------------------------------------------------------

    /** AI Assistant's shape: one chat reply, and the context checks it sent to a fast model first. */
    private val burst = listOf(
        record(sender = "ktor-client", model = "meta-llama/llama-3.1-70b", reply = ReplyFacts(cost = 0.011)).at(2_000),
        record(sender = "ktor-client", model = "google/gemini-2.5-flash", reply = ReplyFacts(cost = 0.0003)).at(300),
        record(sender = "ktor-client", model = "google/gemini-2.5-flash", reply = ReplyFacts(cost = 0.0002)).at(200),
        record(sender = "ktor-client", model = "google/gemini-2.5-flash", reply = ReplyFacts(cost = 0.0001)).at(100),
        record(sender = "ktor-client", model = "google/gemini-2.5-flash", reply = ReplyFacts(cost = 0.0004)).at(0)
    )

    private fun RequestRecord.at(millis: Long) = copy(startedAtMillis = startedAtMillis + millis)

    private fun clickArrow(panel: RequestsTabPanel, row: Int) {
        val table = panel.table
        val cell = table.getCellRect(row, table.convertColumnIndexToView(RequestsColumn.TIME.ordinal), true)
        table.dispatchEvent(
            MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, cell.x + 2, cell.y + 2, 1, false)
        )
    }

    private fun press(panel: RequestsTabPanel, action: String) {
        val event = ActionEvent(panel.table, ActionEvent.ACTION_PERFORMED, action)
        panel.table.actionMap.get(action).actionPerformed(event)
    }

    fun testABurstIsFoldedIntoOneRowThatAddsUpItsRequests() {
        val panel = panel(shown = { burst })

        assertEquals(
            listOf("meta-llama/llama-3.1-70b", "google/gemini-2.5-flash ×4"),
            column(panel, RequestsColumn.MODEL)
        )
        assertEquals(listOf("$0.011", "$0.001"), column(panel, RequestsColumn.COST))
        assertEquals(listOf("12:00:02", "▸ 12:00:00"), column(panel, RequestsColumn.TIME))
        assertEquals("the totals still count every request", "Today: 5 requests · $0.012", panel.todayLabel.text)
    }

    fun testABurstOpensFromItsArrowAndClosesAgain() {
        val panel = panel(shown = { burst })

        clickArrow(panel, 1)

        assertEquals(
            listOf("12:00:02", "▾ 12:00:00", "    12:00:00", "    12:00:00", "    12:00:00", "    12:00:00"),
            column(panel, RequestsColumn.TIME)
        )
        assertEquals("the burst's row stays selected", 1, panel.table.selectedRow)

        clickArrow(panel, 1)
        assertEquals(2, panel.table.rowCount)
    }

    fun testRightOpensABurstAndLeftClosesItFromAnyOfItsRequests() {
        val panel = panel(shown = { burst })
        panel.table.setRowSelectionInterval(1, 1)

        press(panel, "openBurst")
        assertEquals(6, panel.table.rowCount)

        panel.table.setRowSelectionInterval(4, 4)
        press(panel, "closeBurst")

        assertEquals(2, panel.table.rowCount)
        assertEquals("closing from a request selects its burst", 1, panel.table.selectedRow)
    }

    fun testABurstsDetailsSayWhatItAddsUpToAndHowToOpenIt() {
        val panel = panel(shown = { burst })

        panel.table.setRowSelectionInterval(1, 1)

        val texts = detailTexts(panel)
        assertTrue("the count: $texts", "4, sent together" in texts)
        assertTrue("the summed cost: $texts", "$0.001" in texts)
        assertTrue("how to open it: $texts", RequestsTabPanel.BURST_CLOSED_TEXT in texts)
        assertEquals("a burst has no one generation to link to", emptyList<String>(), links(panel))

        panel.toggle(1)
        panel.table.setRowSelectionInterval(2, 2)
        assertTrue("a request under it has its own details", ReplySummary.formatCost(0.0003) in detailTexts(panel))
    }

    fun testGroupingCanBeTurnedOffAndStaysOffForTheNextTab() {
        val panel = panel(shown = { burst })

        panel.groupBursts.doClick()

        assertEquals(5, panel.table.rowCount)
        assertEquals(List(4) { "google/gemini-2.5-flash" }, column(panel, RequestsColumn.MODEL).drop(1))
        assertFalse("turning it off is kept", groupBursts)

        val reopened = panel(shown = { burst })
        assertFalse(reopened.groupBursts.isSelected)
        assertEquals(5, reopened.table.rowCount)
    }

    fun testAnOpenBurstStaysOpenAsANewerRequestJoinsIt() {
        var current = burst
        val panel = panel(shown = { current })
        panel.toggle(1)

        val newer = burst[1].copy(reply = ReplyFacts(cost = 0.0005)).at(100)
        current = burst.take(1) + newer + burst.drop(1)
        panel.reload()

        assertEquals("▾ 12:00:00", column(panel, RequestsColumn.TIME)[1])
        assertEquals(7, panel.table.rowCount)
    }

    fun testRevealingARequestInAClosedBurstOpensItAndSelectsTheRequest() {
        val panel = panel(shown = { burst })

        panel.reveal(burst[3])

        assertEquals(6, panel.table.rowCount)
        assertEquals(ReplySummary.formatCost(0.0001), column(panel, RequestsColumn.COST)[panel.table.selectedRow])
    }

    private fun click(panel: RequestsTabPanel, row: Int, column: RequestsColumn, clicks: Int) {
        val table = panel.table
        val cell = table.getCellRect(row, table.convertColumnIndexToView(column.ordinal), true)
        table.dispatchEvent(
            MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, cell.x + 2, cell.y + 2, clicks, false)
        )
    }

    fun testABurstOpensFromADoubleClickAnywhereOnItsRowButNotASingleOne() {
        val panel = panel(shown = { burst })

        click(panel, 1, RequestsColumn.MODEL, clicks = 1)
        assertEquals("a single click off the arrow only selects", 2, panel.table.rowCount)
        click(panel, 1, RequestsColumn.TIME, clicks = 2)
        assertEquals("a double click on the arrow is not two toggles", 2, panel.table.rowCount)

        click(panel, 1, RequestsColumn.MODEL, clicks = 2)
        assertEquals(6, panel.table.rowCount)

        panel.table.dispatchEvent(MouseEvent(panel.table, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 10_000, 1, false))
        assertEquals("a click below the last row changes nothing", 6, panel.table.rowCount)
    }

    /** Past the last column, the row is still the row: a double click there opens the burst too. */
    fun testADoubleClickPastTheLastColumnOpensABurst() {
        val panel = panel(shown = { burst })
        val table = panel.table
        val y = table.getCellRect(1, 0, true).y + 2

        table.dispatchEvent(MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, table.width + 10, y, 2, false))

        assertEquals(6, table.rowCount)
    }

    fun testRevealingARequestInAnOpenBurstKeepsItOpenAndSelectsTheRequest() {
        val panel = panel(shown = { burst })
        panel.toggle(1)

        panel.reveal(burst[3])

        assertEquals(6, panel.table.rowCount)
        assertEquals(ReplySummary.formatCost(0.0001), column(panel, RequestsColumn.COST)[panel.table.selectedRow])
    }

    fun testRevealingARequestOutsideEveryBurstLeavesTheBurstsClosed() {
        val panel = panel(shown = { burst })

        panel.reveal(burst[0])

        assertEquals(2, panel.table.rowCount)
        assertEquals(0, panel.table.selectedRow)
    }

    fun testARequestNotInABurstHasNothingToOpenOrClose() {
        val panel = panel(shown = { burst })
        panel.table.setRowSelectionInterval(0, 0)

        panel.toggle(0)
        press(panel, "openBurst")
        panel.toggle(99)

        assertEquals(2, panel.table.rowCount)
    }

    fun testToggleOnARequestUnderABurstClosesTheBurst() {
        val panel = panel(shown = { burst })
        panel.toggle(1)

        panel.toggle(3)

        assertEquals(2, panel.table.rowCount)
        assertEquals(1, panel.table.selectedRow)
    }

    fun testAFilterKeepsItsChoiceWhileTheChoiceIsStillListed() {
        var current = records
        val panel = panel(shown = { current })
        panel.senderFilter.selectedItem = "Chat"

        current = records + record(sender = "Cursor")
        panel.reload()
        assertEquals("Chat", panel.senderFilter.selectedItem)

        current = records.filter { it.sender != "Chat" }
        panel.reload()
        assertEquals(RequestsTabPanel.ALL_SENDERS, panel.senderFilter.selectedItem)
    }

    fun testTheSelectionFollowsItsRequestAcrossAReload() {
        var current = records
        val panel = panel(shown = { current })
        panel.table.setRowSelectionInterval(1, 1)
        val selected = records[1]

        current = listOf(record(sender = "Cursor").at(5_000)) + records
        panel.reload()

        assertEquals("Chat", column(panel, RequestsColumn.SENDER)[panel.table.selectedRow])
        assertTrue("the same request: ${detailTexts(panel)}", selected.sender in detailTexts(panel).joinToString())
    }

    fun testAClosedBurstStaysSelectedAsANewerRequestJoinsIt() {
        var current = burst
        val panel = panel(shown = { current })
        panel.table.setRowSelectionInterval(1, 1)

        current = burst.take(1) + burst[1].at(100) + burst.drop(1)
        panel.reload()

        assertEquals("google/gemini-2.5-flash ×5", column(panel, RequestsColumn.MODEL)[panel.table.selectedRow])
    }

    fun testMovingOrSelectingAColumnChangesNoWidth() {
        val panel = panel()
        val before = (0 until panel.table.columnCount).map { panel.table.columnModel.getColumn(it).preferredWidth }

        panel.table.columnModel.moveColumn(0, 1)
        panel.table.columnModel.moveColumn(1, 0)
        panel.table.columnModel.selectionModel.setSelectionInterval(0, 0)

        val after = (0 until panel.table.columnCount).map { panel.table.columnModel.getColumn(it).preferredWidth }
        assertEquals(before, after)
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
