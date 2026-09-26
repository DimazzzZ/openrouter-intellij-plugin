package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import org.zhavoronkov.openrouter.proxy.routing.RouterCatalog
import org.zhavoronkov.openrouter.proxy.routing.RouterDefinition
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

/**
 * Settings panel for managing persisted per-router default parameter values.
 *
 * Renders one control per router in [RouterCatalog] that takes a parameter,
 * using the same shape as the chat UI's router-param combo (closed enums are
 * non-editable; editable enums and float ranges accept free text). Routers
 * without a parameter are listed as a read-only note.
 *
 * A blank selection means "no default"; the proxy will then let OpenRouter
 * apply its own default for that router.
 */
class RouterDefaultsSettingsPanel : Disposable {

    private val settingsService = OpenRouterSettingsService.getInstance()

    // One combo per parameterised router, keyed by model slug. Order matches
    // RouterCatalog so the UI is stable and driven by the catalog.
    private val combos: Map<String, ComboBox<String>> = RouterCatalog.all
        .filter { it.param != null }
        .associate { def -> def.modelSlug to buildCombo(def) }

    private var initial: Map<String, String> = emptyMap()

    private fun buildCombo(def: RouterDefinition): ComboBox<String> {
        val param = def.param!!
        val combo = ComboBox(DefaultComboBoxModel(param.suggestions.toTypedArray()))
        // Editable enums and float ranges accept free text; closed enums do not.
        combo.isEditable = param.freeText
        combo.toolTipText = param.description
        return combo
    }

    fun createPanel(): JPanel {
        loadSettings()
        return panel {
            row {
                comment(
                    "Saved defaults are injected into proxy requests that target an " +
                        "openrouter/* router and omit `plugins`. Leave a value blank to " +
                        "let OpenRouter apply its own default."
                )
            }.topGap(TopGap.MEDIUM)

            for (def in RouterCatalog.all) {
                val param = def.param
                if (param == null) {
                    row("${def.displayName}:") {
                        comment("No tunable parameter")
                    }
                    continue
                }
                // For free-type params, flag the field as editable so the
                // caret-editable control isn't mistaken for a closed dropdown.
                val editableHint = if (param.freeText) " (editable)" else ""
                row("${def.displayName} (${param.label.lowercase()})$editableHint:") {
                    cell(combos.getValue(def.modelSlug))
                        // Surface the accepted-values description inline instead of
                        // hiding it in the combo tooltip (IntelliJ .comment() renders
                        // this as a gray sub-label).
                        .comment(param.description)
                }
            }
        }
    }

    private fun loadSettings() {
        val saved = settingsService.routerDefaultsManager.all()
        combos.forEach { (slug, combo) ->
            combo.selectedItem = saved[slug] ?: ""
        }
        initial = snapshot()
    }

    private fun snapshot(): Map<String, String> = combos.mapValues { (_, c) ->
        (c.selectedItem as? String).orEmpty().trim()
    }

    fun isModified(): Boolean = snapshot() != initial

    fun apply() {
        val values = snapshot()
        settingsService.routerDefaultsManager.replaceAll(values)
        initial = values
    }

    fun reset() {
        loadSettings()
    }

    override fun dispose() {
        // No special cleanup needed
    }
}
