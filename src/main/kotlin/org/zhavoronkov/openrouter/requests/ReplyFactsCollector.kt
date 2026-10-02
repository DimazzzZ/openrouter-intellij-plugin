package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Gathers [ReplyFacts] from an OpenAI chat-completions response - a whole body, or each chunk of
 * a stream in turn.
 *
 * Both carry the same field names, but a stream spreads them out: the id and model on every chunk,
 * the finish reason on the last content chunk, the usage on a final chunk of its own. Each chunk
 * observed adds what it carries, so a stream and a whole body end with the same facts.
 */
class ReplyFactsCollector {

    private var facts = ReplyFacts()

    /** Adds what [json] - a whole response, or one chunk of a stream - carries to the facts so far. */
    fun observe(json: JsonObject) {
        val usage = json.objectOrNull("usage")
        facts = facts.copy(
            generationId = json.stringOrNull("id") ?: facts.generationId,
            answeringModel = json.stringOrNull("model") ?: facts.answeringModel,
            provider = json.stringOrNull("provider") ?: facts.provider,
            finishReason = firstFinishReason(json) ?: facts.finishReason,
            promptTokens = usage?.intOrNull("prompt_tokens") ?: facts.promptTokens,
            completionTokens = usage?.intOrNull("completion_tokens") ?: facts.completionTokens,
            cost = usage?.doubleOrNull("cost") ?: facts.cost,
            // A live reply names it server_tool_use_details; the older name is still read
            webSearches = (usage?.objectOrNull("server_tool_use_details") ?: usage?.objectOrNull("server_tool_use"))
                ?.intOrNull("web_search_requests")
                ?: facts.webSearches
        )
    }

    /** The facts gathered from every response or chunk observed so far. */
    fun facts(): ReplyFacts = facts

    private fun firstFinishReason(json: JsonObject): String? =
        json.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull { it.isJsonObject }?.asJsonObject?.stringOrNull("finish_reason")

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.primitiveOrNull()?.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.primitiveOrNull()?.takeIf { it.isNumber }?.asInt

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.primitiveOrNull()?.takeIf { it.isNumber }?.asDouble

    private fun JsonElement.primitiveOrNull() = takeIf { it.isJsonPrimitive }?.asJsonPrimitive
}
