package org.zhavoronkov.openrouter.models

/**
 * A backend OpenRouter's web plugin can search with, and the modes it takes.
 *
 * Every fact about an engine lives here, so the settings page and the request cannot disagree
 * about what is valid. Taken from OpenRouter's web search guide
 * (`/docs/guides/features/plugins/web-search.md`): only Exa and Parallel document a mode, each has
 * its own set, and each names one of them as its default.
 *
 * [apiName] is OpenRouter's spelling and what is stored in settings; [modes] lists every mode the
 * engine accepts, [defaultMode] among them, in the order the guide lists them.
 */
enum class WebSearchEngine(
    val apiName: String,
    val displayName: String,
    val modes: List<String> = emptyList(),
    val defaultMode: String? = null
) {
    NATIVE("native", "Native"),
    EXA("exa", "Exa", listOf("instant", "fast", "auto", "deep-lite", "deep", "deep-reasoning"), "auto"),
    FIRECRAWL("firecrawl", "Firecrawl"),
    PARALLEL("parallel", "Parallel", listOf("turbo", "fast", "basic", "advanced"), "basic"),
    PERPLEXITY("perplexity", "Perplexity");

    /** The modes worth choosing: every one but the default, which choosing nothing already gets. */
    val selectableModes: List<String> get() = modes - setOfNotNull(defaultMode)

    companion object {
        fun fromApiName(name: String?): WebSearchEngine? = entries.firstOrNull { it.apiName == name }

        /** The engines that take a mode, for telling the user which ones do. */
        val withModes: List<WebSearchEngine> get() = entries.filter { it.modes.isNotEmpty() }
    }
}

/**
 * How a Web Search is tuned: a decision made once on the Web Search settings page, applied to every
 * message that has Web Search turned on. Whether a message searches at all is the chat's own
 * per-message toggle, not this.
 *
 * Every field defaults to leaving the choice to OpenRouter, and [pluginParams] leaves out anything
 * still at its default, so a configuration nobody has touched sends the bare `{"id": "web"}`
 * entry. [engine] null means OpenRouter picks the backend. [mode] null means the engine's own
 * default, and a mode only means anything for an engine that takes one.
 */
data class WebSearchSettings(
    val engine: WebSearchEngine? = null,
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val includeDomains: List<String> = emptyList(),
    val excludeDomains: List<String> = emptyList(),
    val mode: String? = null
) {

    /**
     * [mode] when it is one [engine] accepts and not the engine's own default, else null. A mode
     * can outlive a change of engine, and sent anyway it would be meaningless to the new engine or
     * refused by it; the default is left out like every other default.
     */
    val effectiveMode: String? get() = mode?.takeIf { it in engine?.selectableModes.orEmpty() }

    /** The tuning keys for the `web` plugin entry, in OpenRouter's spelling, defaults left out. */
    fun pluginParams(): Map<String, Any> = buildMap {
        engine?.let { put("engine", it.apiName) }
        if (maxResults != DEFAULT_MAX_RESULTS) put("max_results", maxResults)
        if (includeDomains.isNotEmpty()) put("include_domains", includeDomains)
        if (excludeDomains.isNotEmpty()) put("exclude_domains", excludeDomains)
        effectiveMode?.let { put("mode", it) }
    }

    companion object {
        /** OpenRouter's own default result count, per its web search guide. */
        const val DEFAULT_MAX_RESULTS = 5

        /**
         * The settings page's bounds for the result count. OpenRouter's guide documents no limit;
         * these keep the control to counts a person would pick, since every result adds to the
         * cost of the search.
         */
        const val MIN_MAX_RESULTS = 1
        const val MAX_MAX_RESULTS = 20

        /**
         * A domain list as a person types or pastes it: separated by commas or whitespace, with
         * blanks and repeats dropped. Wildcards such as `*.jetbrains.com` are passed through as
         * typed; OpenRouter's guide documents wildcard support for both domain lists.
         */
        fun parseDomains(typed: String?): List<String> =
            typed.orEmpty()
                .split(',', ' ', '\t', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}
