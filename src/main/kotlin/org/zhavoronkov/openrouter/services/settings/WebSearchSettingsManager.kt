package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings

/**
 * Manages the persisted Web Search tuning, following the same wrap-and-notify pattern as
 * [RouterDefaultsManager].
 *
 * The stored fields are raw strings and numbers; [current] is where they are read into a
 * [WebSearchSettings], and that reading is forgiving on purpose. An engine this build does not
 * know - from a newer version, or a settings file edited by hand - falls back to letting
 * OpenRouter choose, a mode the engine does not take reads as its default, and a result count
 * outside the page's bounds is brought inside them. A bad stored value costs one setting rather
 * than every search failing, and [current] always describes exactly what would be sent.
 */
class WebSearchSettingsManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    /** The stored tuning, read forgivingly: exactly what a search would be sent with. */
    fun current(): WebSearchSettings {
        val read = WebSearchSettings(
            engine = WebSearchEngine.fromApiName(settings.webSearchEngine),
            maxResults = settings.webSearchMaxResults
                .coerceIn(WebSearchSettings.MIN_MAX_RESULTS, WebSearchSettings.MAX_MAX_RESULTS),
            includeDomains = settings.webSearchIncludeDomains.toList(),
            excludeDomains = settings.webSearchExcludeDomains.toList(),
            mode = settings.webSearchMode
        )
        return read.copy(mode = read.effectiveMode)
    }

    /**
     * Store [value], notifying only when the stored fields actually change. Compared field by field
     * against what is stored rather than against [current], so storing a clean value over one
     * [current] had to forgive replaces it instead of being mistaken for no change.
     */
    fun replace(value: WebSearchSettings) {
        val engine = value.engine?.apiName.orEmpty()
        val mode = value.effectiveMode.orEmpty()
        val unchanged = settings.webSearchEngine == engine &&
            settings.webSearchMaxResults == value.maxResults &&
            settings.webSearchIncludeDomains == value.includeDomains &&
            settings.webSearchExcludeDomains == value.excludeDomains &&
            settings.webSearchMode == mode
        if (unchanged) return
        settings.webSearchEngine = engine
        settings.webSearchMaxResults = value.maxResults
        settings.webSearchIncludeDomains = value.includeDomains.toMutableList()
        settings.webSearchExcludeDomains = value.excludeDomains.toMutableList()
        settings.webSearchMode = mode
        onStateChanged()
    }
}
