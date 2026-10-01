package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.models.ResponseFormats
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.utils.asStringOrNull

/**
 * Applies the Web Search tuning saved on the Web Search settings page to a web search a Consumer
 * asked for itself, so it gets the engine, result count, domains and mode the user chose, the way
 * the plugin's own chat does.
 *
 * Two request shapes carry a web search. The current one is OpenRouter's server tool,
 * `tools: [{"type": "openrouter:web_search", "parameters": {...}}]`, whose settings live under
 * `parameters`. The deprecated one is the `web` plugin, `plugins: [{"id": "web", ...}]`, whose
 * settings sit beside its id and spell the two domain lists differently. OpenRouter still honours
 * the plugin and a Consumer may still send it, so both are tuned, each in its own spelling.
 *
 * Only fills in what the Consumer left out, following the other proxy injectors: a key already
 * present is the Consumer's decision and is kept as it was sent. And it never starts a search: a
 * search is charged, so a request without one is left alone. The saved mode belongs to the saved
 * engine; when the Consumer chose a different engine, the saved mode is not added, since it would
 * be meaningless to that engine or refused by it.
 */
object WebSearchTuningInjector {

    private val gson = Gson()

    /** @return true when at least one key was added to a web search the Consumer asked for. */
    fun inject(body: JsonObject, tuning: WebSearchSettings): Boolean {
        var added = false
        entries(body, TOOLS_KEY, ResponseFormats::isWebSearchTool).forEach { tool ->
            val parameters = tool.get(PARAMETERS_KEY)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            if (fill(parameters, tuning.toolParameters(), tuning)) {
                tool.add(PARAMETERS_KEY, parameters)
                added = true
            }
        }
        entries(body, PLUGINS_KEY) { it.get(ID_KEY)?.asStringOrNull() == WEB_PLUGIN_ID }.forEach { plugin ->
            if (fill(plugin, tuning.legacyPluginParams(), tuning)) added = true
        }
        return added
    }

    private fun entries(body: JsonObject, key: String, matches: (JsonObject) -> Boolean): List<JsonObject> =
        body.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }?.map { it.asJsonObject }?.filter(matches)
            .orEmpty()

    /** Adds every key of [params] that [target] lacks; returns whether it added any. */
    private fun fill(target: JsonObject, params: Map<String, Any>, tuning: WebSearchSettings): Boolean {
        val consumerEngine = target.get(ENGINE_KEY)?.asStringOrNull()
        val engineDiffers = consumerEngine != null && consumerEngine != tuning.engine?.apiName
        var added = false
        for ((key, value) in params) {
            if (target.has(key) || (key == MODE_KEY && engineDiffers)) continue
            target.add(key, gson.toJsonTree(value))
            added = true
        }
        return added
    }

    private const val TOOLS_KEY = "tools"
    private const val PARAMETERS_KEY = "parameters"
    private const val PLUGINS_KEY = "plugins"
    private const val ID_KEY = "id"
    private const val ENGINE_KEY = "engine"
    private const val MODE_KEY = "mode"
    private const val WEB_PLUGIN_ID = "web"
}
