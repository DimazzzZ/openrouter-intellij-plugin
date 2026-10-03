package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import org.zhavoronkov.openrouter.requests.RequestBodies
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestLogService
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.settings.RequestsSection
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer
import org.zhavoronkov.openrouter.toolwindow.requests.RequestDetails.Companion.hint
import org.zhavoronkov.openrouter.toolwindow.requests.RequestDetails.Companion.links
import org.zhavoronkov.openrouter.ui.Edt
import org.zhavoronkov.openrouter.utils.ExcludeFromCoverage
import org.zhavoronkov.openrouter.utils.MODAL_DIALOG
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableColumn

/**
 * The tool window's Requests tab: every request the plugin sent, newest first, with filters, the
 * day's totals and the selected request's details.
 *
 * It never reads the log on the EDT. The records are fetched on [background] and shown on [edt],
 * once at start and again whenever [RequestLogListener] says the log changed; a snapshot that
 * arrives after a newer one was asked for is dropped, so a slow read never overwrites a fresh one,
 * and one that arrives after the tab was closed is dropped too. The day's totals are worked out
 * again whenever the tab is shown, so they do not stay on yesterday past midnight. Every seam is a parameter so a test can run it synchronously on fixed records and a fixed clock.
 *
 * Requests sent in a burst ([RequestBursts]) are folded into one row, unless "Group bursts" is off.
 * A click on its arrow, a double click, or Right and Left open and close it; the bursts opened stay
 * open while the tab is, as newer requests join them.
 */
class RequestsTabPanel(
    private val recent: () -> List<RequestRecord> = { RequestLogService.getInstance().recent() },
    private val clearLog: () -> Unit = { RequestLogService.getInstance().clear() },
    private val confirmClear: (JComponent) -> Boolean = ::askToClear,
    private val background: (Runnable) -> Unit = { ApplicationManager.getApplication().executeOnPooledThread(it) },
    private val edt: (Runnable) -> Unit = Edt::later,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Whether "Group bursts" is on, and where turning it on or off is kept, across restarts. */
    groupBurstsSetting: () -> Boolean = {
        OpenRouterSettingsService.getInstance().uiPreferencesManager.requestsGroupBursts
    },
    private val saveGroupBursts: (Boolean) -> Unit = {
        OpenRouterSettingsService.getInstance().uiPreferencesManager.requestsGroupBursts = it
    },
    /** Whether new requests keep their prompt and reply, and where turning it on or off is kept. */
    private val keepBodiesSetting: () -> Boolean = {
        OpenRouterSettingsService.getInstance().uiPreferencesManager.keepRequestBodies
    },
    private val saveKeepBodies: (Boolean) -> Unit = {
        OpenRouterSettingsService.getInstance().uiPreferencesManager.keepRequestBodies = it
    },
    /** Reads a request's kept bodies by their id; called on [background]. */
    private val loadBodies: (String) -> RequestBodies? = { RequestLogService.getInstance().bodies(it) },
    private val showBodies: (JComponent, RequestBodies) -> Unit = RequestBodiesDialog::show,
    /** Says a request's bodies are gone - deleted past the log's limit, or cleared. */
    private val sayBodiesGone: (JComponent) -> Unit = ::bodiesGoneMessage,
    /** Whether the tab is on screen: Swing's own answer, which no headless test can make true. */
    private val isShowing: (JComponent) -> Boolean = JComponent::isShowing
) : Disposable {

    private var records: List<RequestRecord> = emptyList()
    private var shown: List<RequestRecord> = emptyList()
    private var rows: List<RequestsRow> = emptyList()

    /** The bursts the user opened, by [RequestBurst.key]. */
    private val expanded = mutableSetOf<RequestRecord>()
    private var loaded = false

    @Volatile
    private var disposed = false

    private val latestRead = AtomicInteger()
    private var rebuildingFilters = false
    private var filling = false
    private var cellWidths: Map<RequestsColumn, Int> = emptyMap()

    /** Widths the user dragged columns to, kept over the measured ones while the tab is open. */
    private val draggedWidths = mutableMapOf<RequestsColumn, Int>()
    private var toReveal: RequestRecord? = null

    private val tableModel = RequestsTableModel()
    internal val table = JBTable(tableModel)
    private val columns: Map<RequestsColumn, TableColumn>
    private var visibleColumns: List<RequestsColumn> = RequestsColumn.entries

    internal val senderFilter = filterCombo()
    internal val modelFilter = filterCombo()
    internal val warningsOnly = JBCheckBox("Warnings only")
    internal val groupBursts = JBCheckBox("Group bursts", groupBurstsSetting())

    /** The same setting as on the OpenRouter settings page, so it can be turned on where it is used. */
    internal val keepBodies = JBCheckBox(KEEP_BODIES_TEXT, keepBodiesSetting()).apply {
        toolTipText = RequestsSection.KEEP_BODIES_COMMENT
    }
    internal val todayLabel = JBLabel()
    private val details = RequestDetails()
    internal val detailsPanel: JPanel get() = details.panel
    private val tableScroll = JBScrollPane(table)

    val component: JComponent = JPanel(BorderLayout())

    init {
        columns = RequestsColumn.entries.associateWith { table.columnModel.getColumn(it.ordinal) }
        configureTable()
        configureFilters()

        val clear = ActionLink("Clear") {
            if (confirmClear(component)) {
                toReveal = null
                clearLog()
            }
        }
        // Wraps onto a second line in a narrow tab rather than pushing the last controls out of sight
        val filters = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(GAP), JBUI.scale(GAP))).apply {
            add(senderFilter)
            add(modelFilter)
            add(warningsOnly)
            add(groupBursts)
            add(keepBodies)
            add(clear)
        }
        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyBottom(GAP)
            filters.alignmentX = Component.LEFT_ALIGNMENT
            todayLabel.alignmentX = Component.LEFT_ALIGNMENT
            todayLabel.border = JBUI.Borders.emptyTop(GAP)
            add(filters)
            add(todayLabel)
        }
        val splitter = JBSplitter(true, TABLE_SHARE).apply {
            firstComponent = tableScroll
            secondComponent = JBScrollPane(detailsPanel).apply { border = JBUI.Borders.empty() }
        }
        component.add(header, BorderLayout.NORTH)
        component.add(splitter, BorderLayout.CENTER)

        // Unreachable branch: a running Application always has a message bus
        ApplicationManager.getApplication()?.messageBus?.connect(this)
            // Unreachable branch: MessageBus.connect never returns null
            ?.subscribe(
                RequestLogListener.TOPIC,
                object : RequestLogListener {
                    override fun changed(added: RequestRecord?) = reload()
                    override fun updated() = reload()
                }
            )
        component.addHierarchyListener { event ->
            val shownNow = event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L
            if (shownNow && isShowing(component)) onShown()
        }
        show(emptyList())
        loaded = false
        reload()
    }

    /** Reads the log afresh, off the EDT, and shows it unless a newer read was asked for since. */
    fun reload() {
        val read = latestRead.incrementAndGet()
        background {
            val snapshot = recent()
            edt {
                if (!disposed && read == latestRead.get()) {
                    loaded = true
                    show(snapshot)
                }
            }
        }
    }

    private fun configureTable() {
        table.setShowGrid(false)
        table.tableHeader.reorderingAllowed = false
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.selectionModel.addListSelectionListener { if (!it.valueIsAdjusting && !filling) showDetails() }
        columns.getValue(RequestsColumn.MODEL).cellRenderer = MiddleEllipsisRenderer()
        columns.getValue(RequestsColumn.WARNING).cellRenderer = WarningRenderer()
        columns.getValue(RequestsColumn.COST).cellRenderer =
            DefaultTableCellRenderer().apply { horizontalAlignment = SwingConstants.RIGHT }
        columns.getValue(RequestsColumn.WARNING).resizable = false
        table.columnModel.addColumnModelListener(object : TableColumnModelListener {
            override fun columnMarginChanged(e: ChangeEvent) {
                // Only a drag on the header; the widths layoutColumns sets arrive here too
                if (table.tableHeader.resizingColumn == null) return
                visibleColumns.filter { it.resizable }.forEach { draggedWidths[it] = columns.getValue(it).width }
            }

            override fun columnAdded(e: TableColumnModelEvent) = Unit
            override fun columnRemoved(e: TableColumnModelEvent) = Unit
            override fun columnMoved(e: TableColumnModelEvent) = Unit
            override fun columnSelectionChanged(e: ListSelectionEvent) = Unit
        })
        configureBurstToggles()
        tableScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        tableScroll.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = layoutColumns()
        })
    }

    /** A click on a burst's arrow or a double click on its row opens or closes it; so do Right and Left. */
    private fun configureBurstToggles() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point).takeIf { it >= 0 } ?: return
                val column = table.columnAtPoint(e.point)
                val onArrow = column >= 0 && table.columnModel.getColumn(column) === columns[RequestsColumn.TIME]
                if (if (onArrow) e.clickCount == 1 else e.clickCount == 2) toggle(row)
            }
        })
        fun bind(key: Int, name: String, open: Boolean) {
            table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name)
            table.actionMap.put(
                name,
                object : AbstractAction() {
                    override fun actionPerformed(e: ActionEvent) = setExpanded(table.selectedRow, open)
                }
            )
        }
        bind(KeyEvent.VK_RIGHT, "openBurst", open = true)
        bind(KeyEvent.VK_LEFT, "closeBurst", open = false)
    }

    /** Opens a closed burst at [row], closes an open one, and closes the one a request under it is in. */
    internal fun toggle(row: Int) {
        when (val at = rows.getOrNull(row)) {
            is RequestsRow.Header -> setExpanded(row, !at.expanded)
            is RequestsRow.Member -> setExpanded(row, false)
            else -> Unit
        }
    }

    /** Opens or closes the burst at [row], or the one the request there is in, and selects its row. */
    internal fun setExpanded(row: Int, open: Boolean) {
        val burst = when (val at = rows.getOrNull(row)) {
            is RequestsRow.Header -> at.burst
            is RequestsRow.Member -> at.burst
            else -> return
        }
        if (open) expanded += burst.key else expanded -= burst.key
        applyFilter()
        rows.indexOfFirst { it is RequestsRow.Header && it.burst.key == burst.key }
            // Unreachable branch: applyFilter rebuilt the rows from the same records and filters, so the burst's header
            // is listed
            .takeIf { it >= 0 }
            // Unreachable branch: the burst's header is always found above, so takeIf never yields null
            ?.let { table.selectionModel.setSelectionInterval(it, it) }
    }

    private fun configureFilters() {
        val onChange = { if (!rebuildingFilters) applyFilter() }
        senderFilter.addActionListener { onChange() }
        modelFilter.addActionListener { onChange() }
        warningsOnly.addActionListener { onChange() }
        groupBursts.addActionListener {
            saveGroupBursts(groupBursts.isSelected)
            onChange()
        }
        keepBodies.addActionListener { saveKeepBodies(keepBodies.isSelected) }
    }

    private fun show(snapshot: List<RequestRecord>) {
        records = snapshot
        rebuildingFilters = true
        try {
            refill(senderFilter, ALL_SENDERS, RequestsView.senders(snapshot))
            refill(modelFilter, ALL_MODELS, RequestsView.models(snapshot))
        } finally {
            rebuildingFilters = false
        }
        applyFilter()
        toReveal?.let(::select)
    }

    /**
     * Selects [record] with every filter cleared, so it cannot be hidden by one. A record the tab
     * has not read yet - the balloon can be quicker than the log - is selected when it arrives.
     */
    fun reveal(record: RequestRecord) {
        toReveal = record
        rebuildingFilters = true
        try {
            showEveryone(senderFilter)
            showEveryone(modelFilter)
            warningsOnly.isSelected = false
        } finally {
            rebuildingFilters = false
        }
        applyFilter()
        select(record)
    }

    /** Selects [combo]'s first entry, the one that narrows nothing, which [show] adds when the tab is built. */
    private fun showEveryone(combo: ComboBox<String>) {
        combo.selectedIndex = 0
    }

    private fun select(record: RequestRecord) {
        if (record !in shown) return
        // A request folded into a closed burst is listed only once the burst is opened
        // Unreachable branch: Header.burst is non-null, so ?.takeIf sees null only for a row that is not a Header
        rows.firstNotNullOfOrNull { (it as? RequestsRow.Header)?.burst?.takeIf { burst -> record in burst.records } }
            ?.takeIf { it.key !in expanded }
            ?.let {
                expanded += it.key
                applyFilter()
            }
        // Unreachable branch: record is in shown, so it is listed as a Single or, its burst opened above, as a Member
        val row = rows.indexOfFirst { listedRecord(it) == record }.takeIf { it >= 0 } ?: return
        toReveal = null
        table.selectionModel.setSelectionInterval(row, row)
        table.scrollRectToVisible(table.getCellRect(row, 0, true))
    }

    /** Refills [combo] with [all] and [values], keeping what was selected while it still exists. */
    private fun refill(combo: ComboBox<String>, all: String, values: List<String>) {
        val selected = chosen(combo)
        combo.removeAllItems()
        combo.addItem(all)
        values.forEach(combo::addItem)
        combo.selectedIndex = selected?.let { values.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
    }

    /** What [combo] narrows to, or null for its first entry, which means every one. */
    private fun chosen(combo: ComboBox<String>): String? =
        combo.selectedIndex.takeIf { it > 0 }?.let(combo::getItemAt)

    private fun filter() = RequestFilter(
        sender = chosen(senderFilter),
        model = chosen(modelFilter),
        warningsOnly = warningsOnly.isSelected
    )

    /**
     * Refills the table from [records] through the filters, keeping the selected request when it
     * is still listed. The details are rebuilt once at the end, not for each selection event the
     * refill fires on the way.
     */
    private fun applyFilter() {
        val selected = selectedRow()
        val filter = filter()
        filling = true
        try {
            shown = records.filter(filter::matches)
            rows = RequestBursts.rows(shown, expanded, groupBursts.isSelected)
            tableModel.fireTableDataChanged()
            rowOf(selected)?.let { table.selectionModel.setSelectionInterval(it, it) }
        } finally {
            filling = false
        }
        todayLabel.text = RequestsView.todayLine(RequestsView.today(shown, clock(), zone()))
        cellWidths = measureCells()
        layoutColumns()
        showDetails()
    }

    private fun selectedRow(): RequestsRow? = table.selectedRow.takeIf { it >= 0 }?.let(rows::getOrNull)

    /** The request [row] lists, or null for a burst's header. */
    private fun listedRecord(row: RequestsRow): RequestRecord? = when (row) {
        is RequestsRow.Single -> row.record
        is RequestsRow.Member -> row.record
        is RequestsRow.Header -> null
    }

    /**
     * Where [selected] is listed now: the same request, or the same burst's header - which is also
     * where a request goes that a burst has folded since.
     */
    private fun rowOf(selected: RequestsRow?): Int? {
        selected ?: return null
        val record = listedRecord(selected)
        // Unreachable branch: Header.burst is non-null, so ?.key sees null only when selected is not a Header
        val burstKey = (selected as? RequestsRow.Header)?.burst?.key
        return rows.indexOfFirst { record != null && listedRecord(it) == record }.takeIf { it >= 0 }
            ?: rows.indexOfFirst { row ->
                row is RequestsRow.Header && (row.burst.key == burstKey || record in row.burst.records)
            }.takeIf { it >= 0 }
    }

    /** Each fixed column's width: its widest cell, measured once per refill rather than per resize. */
    private fun measureCells(): Map<RequestsColumn, Int> {
        val metrics = table.getFontMetrics(table.font)
        val padding = JBUI.scale(CELL_PADDING)
        fun widest(column: RequestsColumn, sample: String) =
            // Unreachable branch: the list always holds sample, so maxOf never sees it empty
            (rows.map { text(it, column) } + sample).maxOf(metrics::stringWidth) + padding
        return mapOf(
            RequestsColumn.TIME to widest(RequestsColumn.TIME, TIME_SAMPLE),
            RequestsColumn.SENDER to widest(RequestsColumn.SENDER, "").coerceAtMost(JBUI.scale(SENDER_MAX_WIDTH)),
            RequestsColumn.COST to widest(RequestsColumn.COST, ""),
            RequestsColumn.WARNING to WarningMark.iconWidth + padding
        )
    }

    /**
     * Shows the columns that fit the table's width, so a narrow tool window loses columns rather
     * than scrolling sideways. Each column other than the requested id is as wide as the user
     * dragged it, or else as its widest cell; the requested id takes what is left.
     */
    private fun layoutColumns() {
        val widths = cellWidths + draggedWidths
        val available = tableScroll.viewport.width.takeIf { it > 0 } ?: tableScroll.width
        val modelMin = JBUI.scale(RequestsColumnPolicy.MODEL_MIN_WIDTH)
        val visible = RequestsColumnPolicy.visible(available, modelMin, widths)
        if (visible != visibleColumns) {
            visibleColumns.forEach { table.removeColumn(columns.getValue(it)) }
            visible.forEach { table.addColumn(columns.getValue(it)) }
            visibleColumns = visible
        }
        widths.forEach { (column, width) ->
            columns.getValue(column).apply {
                minWidth = if (column.resizable) JBUI.scale(MIN_DRAGGED_WIDTH) else width
                maxWidth = if (column.resizable) Int.MAX_VALUE else width
                preferredWidth = width
            }
        }
        // The rest, exactly, so the table has no slack to spread over the other columns
        // Unreachable branch: measureCells gives every column but MODEL a width, so widths[it] is never null here
        val others = visible.filter { it != RequestsColumn.MODEL }.sumOf { widths[it] ?: 0 }
        columns.getValue(RequestsColumn.MODEL).preferredWidth = (available - others).coerceAtLeast(modelMin)
        // Fixed widths changed after the table was laid out; lay it out again so the requested id
        // takes what is left now rather than on the next resize.
        table.doLayout()
        table.repaint()
    }

    private fun showDetails() {
        val row = selectedRow()
        val record = row?.let(::listedRecord)
        details.rebuild {
            when {
                !loaded -> Unit
                records.isEmpty() -> line(hint(EMPTY_TEXT))
                row is RequestsRow.Header -> {
                    RequestsView.details(row.burst, zone()).forEach { (label, value) -> fact(label, value) }
                    line(hint(if (row.expanded) BURST_OPEN_TEXT else BURST_CLOSED_TEXT))
                }
                record == null -> line(hint(SELECT_TEXT))
                else -> {
                    RequestsView.details(record, zone()).forEach { (label, value) -> fact(label, value) }
                    line(links(record.reply.generationId))
                    record.bodiesId?.let { id -> line(ActionLink(SHOW_BODIES_TEXT) { openBodies(id) }) }
                }
            }
        }
    }

    /** The tab came on screen: the settings page may have turned keeping bodies on or off meanwhile. */
    private fun onShown() {
        keepBodies.isSelected = keepBodiesSetting()
        applyFilter()
    }

    /** Reads the bodies kept under [id] off the EDT, and shows them - or says they are gone. */
    private fun openBodies(id: String) {
        background {
            val bodies = loadBodies(id)
            edt {
                if (disposed) return@edt
                if (bodies != null) {
                    showBodies(component, bodies)
                } else {
                    sayBodiesGone(component)
                }
            }
        }
    }

    override fun dispose() {
        disposed = true
    }

    private fun text(row: RequestsRow, column: RequestsColumn): String = RequestsView.text(row, column, clock(), zone())

    private inner class RequestsTableModel : AbstractTableModel() {
        override fun getRowCount() = rows.size
        override fun getColumnCount() = RequestsColumn.entries.size
        override fun getColumnName(column: Int) = RequestsColumn.entries[column].title
        override fun getValueAt(row: Int, column: Int): Any = text(rows[row], RequestsColumn.entries[column])
    }

    companion object {
        const val ALL_SENDERS = "All senders"
        const val ALL_MODELS = "All models"
        const val EMPTY_TEXT = "No requests yet. Requests from the chat and from tools using the proxy appear here."
        const val SELECT_TEXT = "Select a request to see its details."
        const val SHOW_BODIES_TEXT = "Show prompt and reply"
        const val KEEP_BODIES_TEXT = "Keep prompt and reply"
        const val BODIES_GONE_TEXT = "This request's prompt and reply are no longer kept."
        const val BURST_CLOSED_TEXT =
            "Open the row to see each request: click its arrow, double-click it, or press Right."
        const val BURST_OPEN_TEXT = "Each request is listed under this row."

        private const val GAP = 4
        private const val CELL_PADDING = 12
        private const val SENDER_MAX_WIDTH = 140

        /** How narrow a user may drag a column, in unscaled pixels. */
        private const val MIN_DRAGGED_WIDTH = 32
        private const val FILTER_MAX_WIDTH = 200
        private const val TABLE_SHARE = 0.6f
        private const val TIME_SAMPLE = "00:00:00"

        /**
         * A filter list no wider than [FILTER_MAX_WIDTH], cutting a long model id in the middle,
         * so one long id cannot push the other controls out of a narrow tab.
         */
        private fun filterCombo(): ComboBox<String> = object : ComboBox<String>() {
            override fun getPreferredSize(): Dimension =
                super.getPreferredSize().let { Dimension(minOf(it.width, JBUI.scale(FILTER_MAX_WIDTH)), it.height) }
        }.apply { renderer = MiddleEllipsisComboRenderer(this, DefaultListCellRenderer()) }

        @ExcludeFromCoverage(MODAL_DIALOG)
        private fun bodiesGoneMessage(parent: JComponent) =
            Messages.showInfoMessage(parent, BODIES_GONE_TEXT, SHOW_BODIES_TEXT)

        @ExcludeFromCoverage(MODAL_DIALOG)
        private fun askToClear(parent: JComponent): Boolean = Messages.showOkCancelDialog(
            parent,
            "Remove every recorded request? This cannot be undone.",
            "Clear Requests",
            "Clear",
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        ) == Messages.OK
    }
}
