package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.WebSearchSettingsManager
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings page for tuning Web Search: which engine, how many results, which domains to include or
 * exclude, and which mode. Applied to every message sent with the chat's Web search box ticked,
 * and filled into any web search a Consumer request asks for itself; whether a request
 * searches at all stays the sender's choice.
 *
 * Every control starts at "leave it to OpenRouter", and [WebSearchSettings.pluginParams] leaves
 * out whatever is still there, so visiting this page is never required for a search to work.
 *
 * Mode is gated on the engine, because only Exa and Parallel accept one and each accepts its own
 * set. Changing the engine rebuilds the mode list; a mode the new engine does not take goes back
 * to Default in front of the user, rather than being kept here and silently dropped from the
 * request.
 *
 * The controls are internal so a platform test can drive them the way a user would.
 */
class WebSearchSettingsPanel(
    private val manager: WebSearchSettingsManager = OpenRouterSettingsService.getInstance().webSearchManager
) : SettingsPage {

    internal val engine = ComboBox(choices(WebSearchEngine.entries.map { it.apiName }))
    internal val maxResults = JBIntSpinner(
        WebSearchSettings.DEFAULT_MAX_RESULTS,
        WebSearchSettings.MIN_MAX_RESULTS,
        WebSearchSettings.MAX_MAX_RESULTS
    )
    internal val includeDomains = JBTextField()
    internal val excludeDomains = JBTextField()
    internal val mode = ComboBox<String>()
    internal val modeComment = JLabel()

    init {
        engine.renderer = textListCellRenderer<String?> { name ->
            // Unreachable branch: displayName is a non-null String, so only a name no engine has renders as AUTOMATIC
            name?.let { WebSearchEngine.fromApiName(it)?.displayName ?: AUTOMATIC }
        }
        mode.renderer = textListCellRenderer<String?> { if (it == UNSET) DEFAULT else it }
        engine.addActionListener { refreshModes() }
        refreshModes()
    }

    override fun createPanel(): JPanel {
        reset()
        return panel {
            row {
                comment(
                    "Applied to every chat message sent with Web search on, and to any Consumer " +
                        "request that asks for a web search itself. Anything left at its default is left " +
                        "out of the request, so OpenRouter decides it."
                )
            }.topGap(TopGap.MEDIUM)
            row("Engine:") { cell(engine).comment("Automatic lets OpenRouter pick the search backend.") }
            row("Results:") {
                cell(maxResults).comment("OpenRouter's default is ${WebSearchSettings.DEFAULT_MAX_RESULTS}.")
            }
            row("Include domains:") {
                cell(includeDomains).align(AlignX.FILL)
                    .comment("Only search these. Separate with commas or spaces; wildcards like *.jetbrains.com work.")
            }
            row("Exclude domains:") {
                cell(excludeDomains).align(AlignX.FILL)
                    .comment("Never return results from these. Separated the same way.")
            }
            row("Mode:") { cell(mode) }
            row("") { cell(modeComment) }
        }
    }

    /** What the page currently says, as the settings it would store. */
    internal fun snapshot(): WebSearchSettings = WebSearchSettings(
        engine = chosenEngine(),
        maxResults = maxResults.number,
        includeDomains = WebSearchSettings.parseDomains(includeDomains.text),
        excludeDomains = WebSearchSettings.parseDomains(excludeDomains.text),
        mode = chosen(mode)
    )

    override fun isModified(): Boolean = snapshot() != manager.current()

    override fun apply() = manager.replace(snapshot())

    override fun reset() {
        val stored = manager.current()
        // Unreachable branch: apiName is a non-null String, so only a stored engine of null selects UNSET
        engine.selectedItem = stored.engine?.apiName ?: UNSET
        refreshModes()
        mode.selectedItem = stored.mode ?: UNSET
        maxResults.number = stored.maxResults
        includeDomains.text = stored.includeDomains.joinToString(", ")
        excludeDomains.text = stored.excludeDomains.joinToString(", ")
    }

    private fun chosenEngine(): WebSearchEngine? = WebSearchEngine.fromApiName(chosen(engine))

    private fun chosen(combo: ComboBox<String>): String? = (combo.selectedItem as? String)?.takeIf { it != UNSET }

    /**
     * Rebuilds the mode list for the engine now chosen. An engine's own default is not listed
     * beside Default, since choosing it and choosing nothing send the same request.
     */
    private fun refreshModes() {
        val current = chosenEngine()
        val modes = current?.selectableModes.orEmpty()
        val kept = chosen(mode)?.takeIf { it in modes } ?: UNSET
        mode.model = choices(modes)
        mode.selectedItem = kept
        mode.isEnabled = modes.isNotEmpty()
        modeComment.text = if (current == null || modes.isEmpty()) {
            NO_MODE_TEXT
        } else {
            "Default is ${current.displayName}'s own, ${current.defaultMode}."
        }
    }

    internal companion object {
        /** How both combos spell "nothing chosen" internally; never stored or sent. */
        const val UNSET = ""
        const val AUTOMATIC = "Automatic"
        const val DEFAULT = "Default"

        val NO_MODE_TEXT = "Only the " +
            WebSearchEngine.withModes.joinToString(" and ") { it.displayName } + " engines take a mode."

        /** [values] behind a leading "nothing chosen" entry. */
        fun choices(values: List<String>) = DefaultComboBoxModel((listOf(UNSET) + values).toTypedArray())
    }
}
