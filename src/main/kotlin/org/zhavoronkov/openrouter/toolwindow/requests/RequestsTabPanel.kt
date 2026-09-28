package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestLogService
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.warning
import org.zhavoronkov.openrouter.toolwindow.chat.CHAT_WARNING_FOREGROUND
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsis
import org.zhavoronkov.openrouter.toolwindow.composer.MiddleEllipsisComboRenderer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
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
 */
class RequestsTabPanel(
    private val recent: () -> List<RequestRecord> = { RequestLogService.getInstance().recent() },
    private val clearLog: () -> Unit = { RequestLogService.getInstance().clear() },
    private val confirmClear: (JComponent) -> Boolean = ::askToClear,
    private val background: (Runnable) -> Unit = { ApplicationManager.getApplication().executeOnPooledThread(it) },
    private val edt: (Runnable) -> Unit = { ApplicationManager.getApplication().invokeLater(it, ModalityState.any()) },
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) : Disposable {

    private var records: List<RequestRecord> = emptyList()
    private var shown: List<RequestRecord> = emptyList()
    private var loaded = false

    @Volatile
    private var disposed = false

    private val latestRead = AtomicInteger()
    private var rebuildingFilters = false
    private var filling = false
    private var detailRow = 0
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
    internal val todayLabel = JBLabel()
    internal val detailsPanel = JPanel(GridBagLayout())
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
        detailsPanel.border = JBUI.Borders.empty(GAP)
        val splitter = JBSplitter(true, TABLE_SHARE).apply {
            firstComponent = tableScroll
            secondComponent = JBScrollPane(detailsPanel).apply { border = JBUI.Borders.empty() }
        }
        component.add(header, BorderLayout.NORTH)
        component.add(splitter, BorderLayout.CENTER)

        ApplicationManager.getApplication()?.messageBus?.connect(this)
            ?.subscribe(
                RequestLogListener.TOPIC,
                object : RequestLogListener {
                    override fun changed(added: RequestRecord?) = reload()
                    override fun updated() = reload()
                }
            )
        component.addHierarchyListener { event ->
            val shownNow = event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L
            if (shownNow && component.isShowing) applyFilter()
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
        tableScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        tableScroll.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = layoutColumns()
        })
    }

    private fun configureFilters() {
        val onChange = { if (!rebuildingFilters) applyFilter() }
        senderFilter.addActionListener { onChange() }
        modelFilter.addActionListener { onChange() }
        warningsOnly.addActionListener { onChange() }
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

    /** Selects [combo]'s first entry, the one that narrows nothing - when it has one yet. */
    private fun showEveryone(combo: ComboBox<String>) {
        if (combo.itemCount > 0) combo.selectedIndex = 0
    }

    private fun select(record: RequestRecord) {
        val row = shown.indexOf(record).takeIf { it >= 0 } ?: return
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
        val selected = selectedRecord()
        val filter = filter()
        filling = true
        try {
            shown = records.filter(filter::matches)
            tableModel.fireTableDataChanged()
            shown.indexOf(selected).takeIf { it >= 0 }?.let { table.selectionModel.setSelectionInterval(it, it) }
        } finally {
            filling = false
        }
        todayLabel.text = RequestsView.todayLine(RequestsView.today(shown, clock(), zone()))
        cellWidths = measureCells()
        layoutColumns()
        showDetails()
    }

    private fun selectedRecord(): RequestRecord? = table.selectedRow.takeIf { it >= 0 }?.let(shown::getOrNull)

    /** Each fixed column's width: its widest cell, measured once per refill rather than per resize. */
    private fun measureCells(): Map<RequestsColumn, Int> {
        val metrics = table.getFontMetrics(table.font)
        val padding = JBUI.scale(CELL_PADDING)
        fun widest(column: RequestsColumn, sample: String) =
            (shown.map { text(it, column) } + sample).maxOf(metrics::stringWidth) + padding
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
        val others = visible.filter { it != RequestsColumn.MODEL }.sumOf { widths[it] ?: 0 }
        columns.getValue(RequestsColumn.MODEL).preferredWidth = (available - others).coerceAtLeast(modelMin)
        // Fixed widths changed after the table was laid out; lay it out again so the requested id
        // takes what is left now rather than on the next resize.
        table.doLayout()
        table.repaint()
    }

    private fun showDetails() {
        detailsPanel.removeAll()
        detailRow = 0
        val record = selectedRecord()
        when {
            !loaded -> Unit
            records.isEmpty() -> addLine(hint(EMPTY_TEXT))
            record == null -> addLine(hint(SELECT_TEXT))
            else -> {
                RequestsView.details(record, zone()).forEach { (label, value) -> addFact(label, value) }
                addLine(links(record.reply.generationId))
            }
        }
        addFiller()
        detailsPanel.revalidate()
        detailsPanel.repaint()
    }

    private fun addFact(label: String, value: String) {
        val row = detailRow++
        detailsPanel.add(
            hint(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insets(0, 0, ROW_GAP, GAP * 2)
            }
        )
        detailsPanel.add(
            JBLabel(value).apply { setCopyable(true) },
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insetsBottom(ROW_GAP)
            }
        )
    }

    /** A component across both columns, below everything added so far. */
    private fun addLine(line: JComponent) {
        detailsPanel.add(
            line,
            GridBagConstraints().apply {
                gridx = 0
                gridy = detailRow++
                gridwidth = 2
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insetsTop(ROW_GAP)
            }
        )
    }

    private fun addFiller() {
        detailsPanel.add(
            JPanel().apply { isOpaque = false },
            GridBagConstraints().apply {
                gridx = 0
                gridy = detailRow++
                gridwidth = 2
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
        )
    }

    /**
     * OpenRouter's logs, where the request can be found by its generation id - its docs name no
     * address that opens one generation directly - and the id to search for, when it is known.
     */
    private fun links(generationId: String?): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        add(ActionLink("Open logs on openrouter.ai") { BrowserUtil.browse(RequestsView.LOGS_URL) })
        generationId?.let { id ->
            add(JBLabel("  "))
            add(ActionLink("Copy generation id") { CopyPasteManager.getInstance().setContents(StringSelection(id)) })
        }
    }

    private fun hint(text: String) = JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

    override fun dispose() {
        disposed = true
    }

    private fun text(record: RequestRecord, column: RequestsColumn): String = when (column) {
        RequestsColumn.TIME -> RequestsView.time(record, clock(), zone())
        RequestsColumn.SENDER -> record.sender
        RequestsColumn.MODEL -> record.requestedModel
        RequestsColumn.COST -> RequestsView.cost(record)
        RequestsColumn.WARNING -> record.warning.orEmpty()
    }

    private inner class RequestsTableModel : AbstractTableModel() {
        override fun getRowCount() = shown.size
        override fun getColumnCount() = RequestsColumn.entries.size
        override fun getColumnName(column: Int) = RequestsColumn.entries[column].title
        override fun getValueAt(row: Int, column: Int): Any = text(shown[row], RequestsColumn.entries[column])
    }

    /** The requested id, cut in the middle to the column's width so both author and model show. */
    private class MiddleEllipsisRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val full = value?.toString().orEmpty()
            val room = table.columnModel.getColumn(column).width - insets.left - insets.right
            val metrics = getFontMetrics(font)
            text = MiddleEllipsis.fit(full, room, metrics::stringWidth)
            toolTipText = full.takeIf { text != it }
            return this
        }
    }

    /** The warning mark: an icon, with the reason as its tooltip, or nothing for a normal request. */
    private class WarningRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
            val reason = value?.toString().orEmpty()
            icon = WarningMark.takeIf { reason.isNotEmpty() }
            toolTipText = reason.ifEmpty { null }
            horizontalAlignment = SwingConstants.CENTER
            return this
        }
    }

    /**
     * A filled triangle in the chat's warning colour, so a request that went wrong reads as the same
     * kind of thing as the warning under a chat reply, and stands out in a column of plain text.
     */
    private object WarningMark : Icon {
        private const val SIZE = 10

        override fun getIconWidth() = JBUI.scale(SIZE)
        override fun getIconHeight() = JBUI.scale(SIZE)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = CHAT_WARNING_FOREGROUND
                val size = iconWidth
                g2.fillPolygon(intArrayOf(x, x + size / 2, x + size), intArrayOf(y + size, y, y + size), 3)
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        const val ALL_SENDERS = "All senders"
        const val ALL_MODELS = "All models"
        const val EMPTY_TEXT = "No requests yet. Requests from the chat and from tools using the proxy appear here."
        const val SELECT_TEXT = "Select a request to see its details."

        private const val GAP = 4
        private const val ROW_GAP = 2
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
