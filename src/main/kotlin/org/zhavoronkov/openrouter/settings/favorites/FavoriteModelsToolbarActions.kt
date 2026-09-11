package org.zhavoronkov.openrouter.settings.favorites

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import org.zhavoronkov.openrouter.settings.ModelPresets
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.Mode
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.Capability
import org.zhavoronkov.openrouter.utils.ModelProviderUtils.ContextRange
import javax.swing.JComponent

/**
 * Toolbar actions for the Favorite Models page. Every action reads and writes
 * [FavoriteModelsPageState] directly; the panel only supplies callbacks for
 * things that live in Swing (selection, catalog reload, clearing the search box).
 */

/** Star toggle: shows favorites in stored order and enables reordering. */
internal class FavoritesOnlyToggleAction(private val state: FavoriteModelsPageState) :
    ToggleAction(
        "Favorites only",
        "Show only favorite models, in the order AI Assistant lists them",
        AllIcons.Nodes.Favorite,
    ),
    DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = state.mode == Mode.FAVORITES_ONLY

    override fun setSelected(e: AnActionEvent, selected: Boolean) {
        state.mode = if (selected) Mode.FAVORITES_ONLY else Mode.CATALOG
    }
}

/** Single-choice drop-down filter (Provider / Context / Variant) rendered as a combo button. */
internal class ChoiceFilterAction<T>(
    private val label: String,
    private val state: FavoriteModelsPageState,
    private val options: () -> List<T>,
    private val current: () -> T,
    private val isDefault: (T) -> Boolean,
    private val display: (T) -> String,
    private val onPick: (T) -> Unit,
) : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val value = current()
        e.presentation.text = if (isDefault(value)) label else "$label: ${display(value)}"
        e.presentation.isEnabled = state.mode == Mode.CATALOG
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        options().forEach { option ->
            group.add(object : ToggleAction(display(option)), DumbAware {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun isSelected(e: AnActionEvent): Boolean = current() == option
                override fun setSelected(e: AnActionEvent, selected: Boolean) {
                    if (selected) onPick(option)
                }
            })
        }
        return group
    }

    companion object {
        fun provider(state: FavoriteModelsPageState): ChoiceFilterAction<String?> = ChoiceFilterAction(
            label = "Provider",
            state = state,
            options = { listOf<String?>(null) + state.providers() },
            current = { state.criteria.provider },
            isDefault = { it == null },
            display = { it ?: "All providers" },
            onPick = { state.criteria = state.criteria.copy(provider = it) },
        )

        fun context(state: FavoriteModelsPageState): ChoiceFilterAction<ContextRange> = ChoiceFilterAction(
            label = "Context",
            state = state,
            options = { ContextRange.entries },
            current = { state.criteria.contextRange },
            isDefault = { it == ContextRange.ANY },
            display = { it.displayName },
            onPick = { state.criteria = state.criteria.copy(contextRange = it) },
        )

        fun variant(state: FavoriteModelsPageState): ChoiceFilterAction<VariantFilter> = ChoiceFilterAction(
            label = "Variant",
            state = state,
            options = { VariantFilter.entries },
            current = { state.criteria.variant },
            isDefault = { it == VariantFilter.ANY },
            display = { it.displayName },
            onPick = { state.criteria = state.criteria.copy(variant = it) },
        )
    }
}

/** Multi-select capability filter; the popup stays open while ticking. */
internal class CapabilitiesFilterAction(private val state: FavoriteModelsPageState) : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val count = state.criteria.capabilities.size
        e.presentation.text = if (count == 0) "Capabilities" else "Capabilities ($count)"
        e.presentation.isEnabled = state.mode == Mode.CATALOG
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        FILTERABLE_CAPABILITIES.forEach { capability ->
            group.add(object : ToggleAction(capability.displayName), DumbAware {
                init {
                    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Always
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun isSelected(e: AnActionEvent): Boolean = capability in state.criteria.capabilities
                override fun setSelected(e: AnActionEvent, selected: Boolean) {
                    val updated = if (selected) {
                        state.criteria.capabilities + capability
                    } else {
                        state.criteria.capabilities - capability
                    }
                    state.criteria = state.criteria.copy(capabilities = updated)
                }
            })
        }
        return group
    }

    companion object {
        val FILTERABLE_CAPABILITIES = listOf(
            Capability.VISION,
            Capability.AUDIO,
            Capability.TOOLS,
            Capability.IMAGE_GENERATION,
            Capability.REASONING,
        )
    }
}

/** Text-only toolbar action, visible only while a filter or search text is active. */
internal class ClearFiltersAction(
    private val state: FavoriteModelsPageState,
    private val onClear: () -> Unit,
) : DumbAwareAction("Clear filters", "Reset all filters and the search text", null) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = state.mode == Mode.CATALOG && state.criteria.hasAnyInput()
    }

    override fun actionPerformed(e: AnActionEvent) = onClear()
}

/** Drop-down of preset bundles; picking one appends its catalog-present models to the favorites. */
internal class PresetsAction(private val onPreset: (String) -> Unit) : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.text = "Presets"
        e.presentation.description = "Add a bundle of models to the favorites"
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup =
        presetsGroup(onPreset)

    companion object {
        fun presetsGroup(onPreset: (String) -> Unit): DefaultActionGroup {
            val group = DefaultActionGroup()
            ModelPresets.ALL_PRESETS.forEach { preset ->
                group.add(object : DumbAwareAction(preset.name, preset.description, null) {
                    override fun actionPerformed(e: AnActionEvent) = onPreset(preset.name)
                })
            }
            return group
        }
    }
}

internal class RefreshCatalogAction(
    private val isLoading: () -> Boolean,
    private val onRefresh: () -> Unit,
) : DumbAwareAction("Refresh Models", "Reload the model catalog from OpenRouter", AllIcons.Actions.Refresh) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !isLoading()
    }

    override fun actionPerformed(e: AnActionEvent) = onRefresh()
}

/** Move Up / Move Down for favorites-only mode; the panel owns selection. */
internal class MoveFavoriteAction(
    private val state: FavoriteModelsPageState,
    private val up: Boolean,
    private val selectedRow: () -> Int,
    private val onMoved: (Int) -> Unit,
) : DumbAwareAction(
    if (up) "Move Up" else "Move Down",
    "Reorder favorites; the order is used by AI Assistant",
    if (up) AllIcons.Actions.MoveUp else AllIcons.Actions.MoveDown,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val row = selectedRow()
        val last = state.favorites.lastIndex
        e.presentation.isEnabled = state.canReorder() && row >= 0 && if (up) row > 0 else row < last
    }

    override fun actionPerformed(e: AnActionEvent) {
        val row = selectedRow()
        val target = if (up) state.moveUp(row) else state.moveDown(row)
        target?.let(onMoved)
    }
}
