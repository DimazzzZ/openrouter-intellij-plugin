package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.proxy.pairs.PresetFields
import org.zhavoronkov.openrouter.proxy.routing.ProviderRoutingInjector
import org.zhavoronkov.openrouter.proxy.routing.RouterPluginsInjector
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * The plugin-configured defaults a Consumer's request gets on its way through the proxy - default
 * max tokens, Provider Routing and its fallback models, a router's saved parameters, the Web
 * Search tuning, a saved Output Schema by name - each only where the request does not already
 * say. A client's own `provider` or `models[]` block is never overwritten or merged into.
 *
 * `presetFields` are the fields a pair's preset sets, which no default is added over: OpenRouter
 * lets a request's field override the preset's. It is null for a pair whose preset is not known,
 * which gets none of these defaults - only the Web Search tuning, into a search the Consumer asked
 * for itself - and empty for a plain model.
 */
object ConfiguredDefaults {

    /** What the user configured that a Consumer's request may get. */
    data class Settings(
        /** The max tokens a request without its own gets, or 0 for none. */
        val defaultMaxTokens: Int,
        val routing: ProviderRoutingManager,
        val routerDefaults: RouterDefaultsManager,
        val webSearch: WebSearchSettings,
        val schemas: List<OutputSchema>
    )

    /**
     * Adds to [rawJson] the defaults [settings] holds. [settings] is read here, so a request is sent
     * without defaults rather than failing when they cannot be read.
     */
    fun apply(
        rawJson: JsonObject,
        settings: () -> Settings,
        gson: Gson,
        requestId: String,
        presetFields: Set<String>?
    ) {
        fun presetSets(field: String) = presetFields == null || field in presetFields
        try {
            val configured = settings()
            val defaultMaxTokens = configured.defaultMaxTokens
            if (defaultMaxTokens > 0 && !rawJson.has("max_tokens") && !presetSets("max_tokens")) {
                rawJson.addProperty("max_tokens", defaultMaxTokens)
            }

            // Provider Routing is one block with its fallback models; a preset that sets either
            // replaces the global routing whole
            if (presetFields == null || PresetFields.setsRouting(presetFields)) {
                PluginLogger.Service.debug("[Chat-$requestId] The preset sets routing; global routing defaults skipped")
            } else {
                // Global provider routing (invariant: only when absent)
                ProviderRoutingInjector.inject(
                    rawJson = rawJson,
                    routing = configured.routing,
                    gson = gson,
                    requestId = requestId
                )
            }
            if (!presetSets("plugins")) {
                // The saved per-router default plugins block (invariant: only when the request
                // omits `plugins` and targets a known router)
                RouterPluginsInjector.inject(
                    rawJson = rawJson,
                    defaults = configured.routerDefaults,
                    gson = gson,
                    requestId = requestId
                )
            }

            // The user's Web Search tuning, into a web search the Consumer asked for itself (invariant:
            // only keys the entry lacks, and never a search the Consumer did not ask for)
            if (WebSearchTuningInjector.inject(rawJson, configured.webSearch)) {
                PluginLogger.Service.debug("[Chat-$requestId] Applied saved Web Search tuning")
            }

            // A saved Output Schema, for a json_schema response format that names one and carries
            // no schema of its own (invariant: a Consumer's own schema is never replaced)
            if (!presetSets("response_format") && SavedSchemaInjector.inject(rawJson, configured.schemas)) {
                PluginLogger.Service.debug("[Chat-$requestId] Applied a saved Output Schema by name")
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The settings may not be readable in a test environment (getService can raise
            // IllegalStateException or a class-loading Error); a broad catch is intentional so
            // defaults are simply skipped rather than failing the request.
            PluginLogger.Service.debug("Could not apply configured defaults: ${e.message}")
        }
    }
}
