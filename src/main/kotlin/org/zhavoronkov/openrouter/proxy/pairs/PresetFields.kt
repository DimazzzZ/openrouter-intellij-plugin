package org.zhavoronkov.openrouter.proxy.pairs

import com.google.gson.JsonObject

/**
 * The preset wins over the Consumer. OpenRouter merges a preset under the request, so a field the
 * Consumer sends overrides the preset's; for a pair the proxy removes those fields first, so the
 * pair does what it was picked for.
 */
object PresetFields {

    /**
     * Never removed: the conversation, the model the pair names, and tools - OpenRouter unions a
     * request's tools with the preset's, which keeps a Consumer's own tools beside a web search.
     */
    private val KEPT = setOf("messages", "model", "tools", "stream", "stream_options")

    private const val PRESET = "preset"

    /**
     * Removes from [body] every field [config] sets, and a `preset` of the Consumer's own, which
     * would name a second preset beside the pair's; returns the names removed, in the body's order.
     */
    fun strip(body: JsonObject, config: JsonObject): List<String> {
        val removed = body.keySet().filter { (it in config.keySet() && it !in KEPT) || it == PRESET }
        removed.forEach(body::remove)
        return removed
    }
}
