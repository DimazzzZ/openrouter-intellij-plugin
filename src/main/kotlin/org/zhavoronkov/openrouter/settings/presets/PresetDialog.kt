package org.zhavoronkov.openrouter.settings.presets

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.settings.ProviderRoutingForm
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

/**
 * The modal editor for one preset. OK is disabled while the preset has a problem, re-decided on
 * every change, and the editor's status line says what it is.
 */
class PresetDialog(
    parent: Component,
    draft: PresetDraft,
    isNew: Boolean,
    takenSlugs: List<String>,
    schemas: List<OutputSchema>
) : DialogWrapper(parent, true) {

    internal val editor = PresetEditor(draft, isNew, takenSlugs, schemas, ::editRouting)

    init {
        title = if (isNew) "New Preset" else "Edit Preset ${draft.slug}"
        init()
        editor.onChanged = ::refresh
        refresh()
    }

    private fun refresh() {
        isOKActionEnabled = editor.problem() == null
        pack()
    }

    /** Fixed width, so a long summary or prompt wraps or truncates rather than scrolling sideways. */
    override fun createCenterPanel(): JComponent = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(WIDTH), super.getPreferredSize().height)
    }.apply { add(editor.component, BorderLayout.CENTER) }

    override fun getPreferredFocusedComponent(): JComponent = editor.slug

    public override fun doValidate(): ValidationInfo? = editor.problem()?.let { ValidationInfo(it) }

    private fun editRouting(current: ProviderRoutingPreferences): ProviderRoutingPreferences? {
        val dialog = RoutingDialog(contentPanel, current)
        return if (dialog.showAndGet()) dialog.form.value() else null
    }

    /**
     * The Provider Routing form on its own, for one preset's `provider` block. It scrolls: the
     * form's three provider lists alone are taller than a laptop screen.
     */
    internal class RoutingDialog(parent: Component, current: ProviderRoutingPreferences) : DialogWrapper(parent, true) {
        val form = ProviderRoutingForm().apply { show(current) }

        init {
            title = "Preset Provider Routing"
            init()
        }

        override fun createCenterPanel(): JComponent = content(form)

        companion object {
            /** [form] in a scroll pane no taller than [ROUTING_HEIGHT], so the dialog fits the screen. */
            fun content(form: ProviderRoutingForm): JComponent = JBScrollPane(panel { form.addTo(this) }).apply {
                border = JBUI.Borders.empty()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                preferredSize = JBDimension(ROUTING_WIDTH, ROUTING_HEIGHT)
            }
        }
    }

    companion object {
        const val WIDTH = 480
        const val ROUTING_WIDTH = 560
        const val ROUTING_HEIGHT = 560

        /** Shows the dialog and returns the preset it describes, or null when it was cancelled. */
        fun edit(
            parent: Component,
            draft: PresetDraft,
            isNew: Boolean,
            takenSlugs: List<String>,
            schemas: List<OutputSchema>
        ): PresetDraft? {
            val dialog = PresetDialog(parent, draft, isNew, takenSlugs, schemas)
            return if (dialog.showAndGet()) dialog.editor.result() else null
        }
    }
}
