package org.zhavoronkov.openrouter.requests

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.delay

/**
 * Whether a reply's own `provider` field says who served the request.
 *
 * Measured against OpenRouter: when a request offers one of OpenRouter's server tools - web
 * search, say - the reply names "OpenAI" whatever served it, Llama on Together included, while
 * the generation record names the real provider. A preset can carry such a tool without the
 * request showing it, and so can the legacy web plugin and the `:online` variant, so a request
 * with any of them is not believed; its provider is read from the generation record instead.
 */
object ReplyProvider {

    /**
     * Whether [request]'s reply names its provider truly. A preset it names is judged by its
     * config as [presetConfig] knows it, by slug; a preset that is not known is not believed.
     */
    fun trusted(request: JsonObject, presetConfig: (slug: String) -> JsonObject? = { null }): Boolean =
        mayNameProvider(request) && presetSlugs(request)?.all { slug ->
            presetConfig(slug)?.let(::mayNameProvider) == true
        } == true

    private fun mayNameProvider(fields: JsonObject): Boolean = !offersServerTool(fields) && !usesLegacySearch(fields)

    private fun offersServerTool(request: JsonObject): Boolean =
        request.arrayOrEmpty("tools").any { it.stringField("type")?.startsWith(SERVER_TOOL_PREFIX) == true }

    /**
     * Every preset [request] names: in its model id, and in its own `preset` field, prefixed or
     * not - or null when that field is not a string, which names nothing a copy can look up.
     */
    private fun presetSlugs(request: JsonObject): List<String>? {
        val field = request.get("preset")
        val fieldSlug = request.stringOrNull("preset")?.removePrefix(PRESET_MARKER)
        if (field != null && fieldSlug == null) return null
        val modelSlug = request.stringOrNull("model")?.takeIf { PRESET_MARKER in it }?.substringAfter(PRESET_MARKER)
        return listOfNotNull(modelSlug, fieldSlug)
    }

    private fun usesLegacySearch(request: JsonObject): Boolean =
        request.stringOrNull("model")?.endsWith(ONLINE_SUFFIX) == true ||
            request.arrayOrEmpty("plugins").any { it.stringField("id") == WEB_PLUGIN }

    private fun JsonObject.arrayOrEmpty(key: String): List<JsonElement> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonElement.stringField(key: String): String? =
        takeIf { it.isJsonObject }?.asJsonObject?.stringOrNull(key)

    private const val SERVER_TOOL_PREFIX = "openrouter:"
    private const val PRESET_MARKER = "@preset/"
    private const val ONLINE_SUFFIX = ":online"
    private const val WEB_PLUGIN = "web"
}

/**
 * Reads which provider served a generation from OpenRouter's generation record, which appears a
 * few seconds after the reply: asked once after each of [delaysMillis], until it names one.
 */
class GenerationProviderLookup(
    private val fetch: suspend (generationId: String) -> String?,
    private val delaysMillis: List<Long> = DEFAULT_DELAYS
) {
    /** The provider, or null when the record has not named one after the last try. */
    suspend fun providerOf(generationId: String): String? {
        for (wait in delaysMillis) {
            delay(wait)
            fetch(generationId)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    companion object {
        /** About half a minute in all; measured, the record was there within about ten seconds. */
        val DEFAULT_DELAYS = listOf(2_000L, 4_000L, 8_000L, 16_000L)
    }
}
