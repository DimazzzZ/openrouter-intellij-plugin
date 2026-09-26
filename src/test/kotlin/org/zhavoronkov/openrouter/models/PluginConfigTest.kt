package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * PluginConfig is a flat OpenRouter plugins[] element: a mandatory `id`
 * plus router-specific params that OpenRouter expects at the SAME object
 * level as id (not nested). The class carries a JsonAdapter so ANY plain
 * Gson() — the service uses a bare one — flattens params correctly.
 */
@DisplayName("PluginConfig serialization")
class PluginConfigTest {

    private val gson = Gson()

    @Test
    @DisplayName("flattens params to the top level next to id")
    fun flattensParams() {
        val cfg = PluginConfig(id = "auto-router", params = mapOf("cost_tier" to "medium"))
        val json = JsonParser.parseString(gson.toJson(cfg)).asJsonObject
        assertEquals("auto-router", json.get("id").asString)
        assertEquals("medium", json.get("cost_tier").asString)
        assertFalse(json.has("params")) // never leak the wrapper key
    }

    @Test
    @DisplayName("emits only id when params is empty")
    fun idOnly() {
        val cfg = PluginConfig(id = "fusion", params = emptyMap())
        val json = JsonParser.parseString(gson.toJson(cfg)).asJsonObject
        assertEquals("fusion", json.get("id").asString)
        assertEquals(1, json.entrySet().size)
    }

    @Test
    @DisplayName("preserves numeric param types (float score stays a number)")
    fun numericParam() {
        val cfg = PluginConfig(id = "pareto-router", params = mapOf("min_coding_score" to 0.8))
        val json = JsonParser.parseString(gson.toJson(cfg)).asJsonObject
        assertEquals(0.8, json.get("min_coding_score").asDouble)
        assertTrue(json.get("min_coding_score").asJsonPrimitive.isNumber)
    }

    @Test
    @DisplayName("serializes as an element of a ChatCompletionRequest plugins array")
    fun withinRequest() {
        val req = ChatCompletionRequest(
            model = "openrouter/auto",
            messages = emptyList(),
            plugins = listOf(PluginConfig("auto-router", mapOf("cost_tier" to "high")))
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject
        val plugins = json.getAsJsonArray("plugins")
        assertEquals(1, plugins.size())
        val first = plugins[0].asJsonObject
        assertEquals("auto-router", first.get("id").asString)
        assertEquals("high", first.get("cost_tier").asString)
    }

    @Test
    @DisplayName("skips null-valued params (never emits a JSON null field)")
    fun skipsNullParams() {
        val cfg = PluginConfig(id = "auto-router", params = mapOf("cost_tier" to null, "kept" to "x"))
        val json = JsonParser.parseString(gson.toJson(cfg)).asJsonObject
        assertFalse(json.has("cost_tier")) // dropped, not null
        assertEquals("x", json.get("kept").asString)
    }

    @Test
    @DisplayName("omits the plugins field entirely when the request has none")
    fun omitsPluginsWhenNull() {
        val req = ChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = emptyList(),
            plugins = null
        )
        val json = JsonParser.parseString(gson.toJson(req)).asJsonObject
        assertFalse(json.has("plugins")) // non-routers must not send an empty/null plugins array
    }
}
