package org.zhavoronkov.openrouter.proxy.routing

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager

@DisplayName("RouterPluginsInjector Tests")
class RouterPluginsInjectorTest {

    private val gson = Gson()
    private lateinit var settings: OpenRouterSettings
    private lateinit var defaults: RouterDefaultsManager

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        defaults = RouterDefaultsManager(settings) {}
    }

    private fun buildRequest(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    @Test
    @DisplayName("injects saved default plugins for a known router when request omits plugins")
    fun injectsWhenAbsent() {
        defaults.set("openrouter/auto", "high")

        val rawJson = buildRequest("""{"model":"openrouter/auto","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-1")

        assertTrue(result)
        val plugins = rawJson.getAsJsonArray("plugins")
        assertEquals(1, plugins.size())
        val entry = plugins[0].asJsonObject
        assertEquals("auto-router", entry.get("id").asString)
        assertEquals("high", entry.get("cost_tier").asString)
    }

    @Test
    @DisplayName("does NOT inject when the client already sent a plugins block")
    fun preservesClientPlugins() {
        defaults.set("openrouter/auto", "high")

        val rawJson = buildRequest(
            """{"model":"openrouter/auto","messages":[],"plugins":[{"id":"custom"}]}"""
        )
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-2")

        assertFalse(result)
        val plugins = rawJson.getAsJsonArray("plugins")
        assertEquals(1, plugins.size())
        assertEquals("custom", plugins[0].asJsonObject.get("id").asString)
    }

    @Test
    @DisplayName("does NOT inject when the request model is not a router")
    fun skipsNonRouter() {
        defaults.set("openrouter/auto", "high")

        val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-3")

        assertFalse(result)
        assertFalse(rawJson.has("plugins"))
    }

    @Test
    @DisplayName("does NOT inject when no default is saved for the router")
    fun skipsWhenNoDefault() {
        val rawJson = buildRequest("""{"model":"openrouter/auto","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-4")

        assertFalse(result)
        assertFalse(rawJson.has("plugins"))
    }

    @Test
    @DisplayName("does NOT inject when the saved default is invalid for the router param")
    fun skipsInvalidSavedValue() {
        // Auto Router is a closed enum; "bogus" is not in its allowed values.
        defaults.set("openrouter/auto", "bogus")

        val rawJson = buildRequest("""{"model":"openrouter/auto","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-5")

        assertFalse(result)
        assertFalse(rawJson.has("plugins"))
    }

    @Test
    @DisplayName("does NOT inject for a router that takes no parameter")
    fun skipsParameterlessRouter() {
        // openrouter/free carries no plugin; a saved value must be ignored.
        defaults.set("openrouter/free", "anything")

        val rawJson = buildRequest("""{"model":"openrouter/free","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-6")

        assertFalse(result)
        assertFalse(rawJson.has("plugins"))
    }

    @Test
    @DisplayName("accepts an editable-enum saved value verbatim (Fusion preset)")
    fun editableEnumPassesThrough() {
        defaults.set("openrouter/fusion", "my-custom-preset")

        val rawJson = buildRequest("""{"model":"openrouter/fusion","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-7")

        assertTrue(result)
        val entry = rawJson.getAsJsonArray("plugins")[0].asJsonObject
        assertEquals("fusion", entry.get("id").asString)
        assertEquals("my-custom-preset", entry.get("preset").asString)
    }

    @Test
    @DisplayName("coerces a float-range saved value to a number (Pareto min_coding_score)")
    fun floatRangeIsCoerced() {
        defaults.set("openrouter/pareto-code", "0.7")

        val rawJson = buildRequest("""{"model":"openrouter/pareto-code","messages":[]}""")
        val result = RouterPluginsInjector.inject(rawJson, defaults, gson, "t-8")

        assertTrue(result)
        val entry = rawJson.getAsJsonArray("plugins")[0].asJsonObject
        assertEquals("pareto-router", entry.get("id").asString)
        assertEquals(0.7, entry.get("min_coding_score").asDouble, 1e-9)
    }
}
