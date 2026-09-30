package org.zhavoronkov.openrouter.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.presets.PresetCopy
import org.zhavoronkov.openrouter.presets.PresetCopyService
import org.zhavoronkov.openrouter.presets.PresetEntry
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.settings.presets.PresetDialog
import org.zhavoronkov.openrouter.settings.presets.PresetDraft
import org.zhavoronkov.openrouter.settings.presets.PresetWriter
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Duration
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel

/**
 * Settings → OpenRouter → Presets: the user's OpenRouter presets, from the plugin's copy of them,
 * with New and Edit opening a dialog that shows only what a preset sets.
 *
 * Presets live on OpenRouter; saving writes one through the API - an existing slug gets a new
 * version - and reads the copy again. OpenRouter has no delete endpoint, so Delete says so and
 * opens its presets page. Nothing is staged here, so the page is never "modified".
 */
class PresetsSettingsPanel(
    private val copy: () -> PresetCopy = { PresetCopyService.getInstance().copy },
    private val isConfigured: () -> Boolean = { OpenRouterSettingsService.getInstance().isConfigured() },
    private val schemas: () -> List<OutputSchema> = {
        OpenRouterSettingsService.getInstance().outputSchemasManager.all()
    },
    /** Writes a preset; the error message, or null when it was saved. */
    private val save: suspend (PresetDraft) -> String? = PresetWriter::save,
    private val edit: (JPanel, PresetDraft, Boolean, List<String>, List<OutputSchema>) -> PresetDraft? =
        { parent, draft, isNew, taken, saved -> PresetDialog.edit(parent, draft, isNew, taken, saved) },
    private val askSlug: () -> String? = ::promptSlug,
    private val confirmDelete: (String) -> Boolean = ::confirmOpeningSite,
    private val openSite: () -> Unit = { BrowserUtil.browse(PRESETS_URL) },
    private val clock: () -> Long = System::currentTimeMillis,
    /** The proxy lists every preset as a model of its own, from this list of slugs. */
    private val listSlugs: (List<String>) -> Unit = {
        OpenRouterSettingsService.getInstance().presetsManager.setCustomPresets(it)
    },
    private val autoRefresh: Boolean = true,
    /** Says that the preset [slug] names cannot be edited, since what it sets could not be read. */
    private val tellUnreadable: (String) -> Unit = ::showUnreadable
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    internal val listModel = DefaultListModel<PresetEntry>()
    internal val list = JBList(listModel).apply {
        cellRenderer = PresetRenderer()
        emptyText.text = "No presets yet"
    }
    internal val status = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }
    private lateinit var root: JPanel

    fun createPanel(): JPanel {
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) editSelected()
            }
        })
        val decorated = ToolbarDecorator.createDecorator(list)
            .setAddAction { createPreset() }
            .setEditAction { editSelected() }
            .setRemoveAction { deleteSelected() }
            .disableUpDownActions()
            .createPanel()
        root = panel {
            row {
                comment(
                    "Presets are OpenRouter's named request settings. Pair one with a model in Favorite Models " +
                        "and a Consumer that can only pick a model gets them."
                )
            }
            row { cell(decorated).align(AlignX.FILL).align(AlignY.FILL) }.resizableRow()
            row { cell(status) }
        }.apply { border = JBUI.Borders.empty(PANEL_BORDER) }
        show()
        if (autoRefresh && isConfigured()) refresh()
        return root
    }

    /** Reads the presets again and shows them; the copy is shown meanwhile. */
    internal fun refresh() {
        scope.launch {
            val read = withContext(Dispatchers.IO) { copy().refresh() }
            show(readFailed = !read)
        }
    }

    internal fun show(readFailed: Boolean = false) {
        val snapshot = copy().snapshot()
        val selected = list.selectedValue?.slug
        listModel.clear()
        snapshot?.presets?.sortedBy { it.slug }?.forEach(listModel::addElement)
        selected?.let { slug -> (0 until listModel.size).firstOrNull { listModel[it].slug == slug } }
            ?.let(list::setSelectedIndex)
        status.text = statusText(snapshot?.readAtMillis, snapshot?.presets?.size ?: 0, readFailed)
        snapshot?.let { listSlugs(it.presets.map(PresetEntry::slug)) }
    }

    private fun statusText(readAt: Long?, count: Int, readFailed: Boolean): String = when {
        !isConfigured() -> "Add your API key on the OpenRouter page to read your presets."
        readAt == null && readFailed -> "Could not read your presets from OpenRouter."
        readAt == null -> "Reading your presets…"
        readFailed -> "Could not reach OpenRouter; showing the presets read ${ago(readAt)}."
        else -> "${if (count == 1) "1 preset" else "$count presets"}, read ${ago(readAt)}."
    }

    private fun ago(millis: Long): String {
        val minutes = Duration.ofMillis(clock() - millis).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < MINUTES_PER_HOUR -> "$minutes min ago"
            else -> "${minutes / MINUTES_PER_HOUR} h ago"
        }
    }

    private fun takenSlugs(): List<String> = (0 until listModel.size).map { listModel[it].slug }

    internal fun createPreset() {
        val slug = askSlug() ?: return
        val draft = edit(root, PresetDraft.empty(slug), true, takenSlugs(), schemas()) ?: return
        store(draft)
    }

    internal fun editSelected() {
        val entry = list.selectedValue ?: return
        // Saving writes a whole new version, so a preset whose config is not known would be wiped
        if (entry.config == null) {
            tellUnreadable(entry.slug)
            return
        }
        val draft = edit(root, PresetDraft.of(entry), false, takenSlugs(), schemas()) ?: return
        store(draft)
    }

    internal fun deleteSelected() {
        val entry = list.selectedValue ?: return
        if (confirmDelete(entry.slug)) openSite()
    }

    private fun store(draft: PresetDraft) {
        scope.launch {
            val error = save(draft)
            if (error != null) {
                Messages.showErrorDialog(root, "Could not save the preset: $error", "Save Preset")
                return@launch
            }
            withContext(Dispatchers.IO) { copy().refresh() }
            show()
        }
    }

    // Every change is written to OpenRouter when its dialog closes; nothing waits for Apply
    @Suppress("FunctionOnlyReturningConstant")
    fun isModified(): Boolean = false

    fun apply() = Unit

    fun reset() = Unit

    override fun dispose() {
        scope.cancel()
    }

    /** A preset's slug, then what it sets. */
    private class PresetRenderer : ColoredListCellRenderer<PresetEntry>() {
        override fun customizeCellRenderer(
            list: JList<out PresetEntry>,
            value: PresetEntry?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            value ?: return
            append(value.slug)
            val sets = if (value.config == null) "what it sets could not be read" else PresetDraft.of(value).summary
            append("  $sets", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    companion object {
        const val PRESETS_URL = "https://openrouter.ai/settings/presets"
        private const val PANEL_BORDER = 10
        private const val MINUTES_PER_HOUR = 60

        private fun promptSlug(): String? = Messages.showInputDialog(
            "Slug for the new preset (lower-case letters, digits and hyphens), e.g. web-json:",
            "New Preset",
            null
        )?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        private fun showUnreadable(slug: String) = Messages.showWarningDialog(
            "What '$slug' sets could not be read from OpenRouter, and saving it would replace it with " +
                "an empty preset. Open Settings again to read your presets once more, or edit it on openrouter.ai.",
            "Edit Preset"
        )

        private fun confirmOpeningSite(slug: String): Boolean = Messages.showOkCancelDialog(
            "OpenRouter deletes presets only on its site. Open your presets there to delete '$slug'?",
            "Delete Preset",
            "Open openrouter.ai",
            Messages.getCancelButton(),
            null
        ) == Messages.OK
    }
}
