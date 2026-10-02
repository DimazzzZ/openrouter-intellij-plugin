package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The Requests tab's details area: a two-column list of facts, label and value, with lines across
 * both columns below them, rebuilt whole for each selection and kept to the top of the area.
 */
class RequestDetails {

    val panel = JPanel(GridBagLayout()).apply { border = JBUI.Borders.empty(GAP) }

    private var row = 0

    /** Replaces everything shown with what [build] adds. */
    fun rebuild(build: RequestDetails.() -> Unit) {
        panel.removeAll()
        row = 0
        build()
        addFiller()
        panel.revalidate()
        panel.repaint()
    }

    /** A fact: its [label] in the first column, its [value] - selectable, to copy - in the second. */
    fun fact(label: String, value: String) {
        val at = row++
        panel.add(
            hint(label),
            GridBagConstraints().apply {
                gridx = 0
                gridy = at
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insets(0, 0, ROW_GAP, GAP * 2)
            }
        )
        panel.add(
            JBLabel(value).apply { setCopyable(true) },
            GridBagConstraints().apply {
                gridx = 1
                gridy = at
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insetsBottom(ROW_GAP)
            }
        )
    }

    /** A component across both columns, below everything added so far. */
    fun line(line: JComponent) {
        panel.add(
            line,
            GridBagConstraints().apply {
                gridx = 0
                gridy = row++
                gridwidth = 2
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.FIRST_LINE_START
                insets = JBUI.insetsTop(ROW_GAP)
            }
        )
    }

    private fun addFiller() {
        panel.add(
            JPanel().apply { isOpaque = false },
            GridBagConstraints().apply {
                gridx = 0
                gridy = row++
                gridwidth = 2
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            }
        )
    }

    companion object {
        const val OPEN_LOG_TEXT = "Open log on openrouter.ai"

        private const val GAP = 4
        private const val ROW_GAP = 2

        /** [text] in the colour the IDE gives secondary text. */
        fun hint(text: String) = JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

        /**
         * The request's own log on openrouter.ai and its generation id to copy - or nothing, for a
         * request with no generation to open: one refused or failed before OpenRouter answered.
         */
        fun links(generationId: String?): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            generationId ?: return@apply
            add(ActionLink(OPEN_LOG_TEXT) { BrowserUtil.browse(RequestsView.logUrl(generationId)) })
            add(JBLabel("  "))
            add(
                ActionLink("Copy generation id") {
                    CopyPasteManager.getInstance().setContents(StringSelection(generationId))
                }
            )
        }
    }
}
