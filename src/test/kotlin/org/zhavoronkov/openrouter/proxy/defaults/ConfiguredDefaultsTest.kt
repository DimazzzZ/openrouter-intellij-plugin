package org.zhavoronkov.openrouter.proxy.defaults

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.WebSearchSettings
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.services.settings.RouterDefaultsManager

@DisplayName("ConfiguredDefaults")
class ConfiguredDefaultsTest {

    private val gson = Gson()

    private fun settings(defaultMaxTokens: Int): ConfiguredDefaults.Settings {
        val stored = OpenRouterSettings()
        return ConfiguredDefaults.Settings(
            defaultMaxTokens = defaultMaxTokens,
            routing = ProviderRoutingManager(stored) {},
            routerDefaults = RouterDefaultsManager(stored) {},
            webSearch = WebSearchSettings(),
            schemas = emptyList()
        )
    }

    private fun apply(json: String, defaultMaxTokens: Int, presetFields: Set<String>?): JsonObject =
        JsonParser.parseString(json).asJsonObject.also {
            ConfiguredDefaults.apply(it, { settings(defaultMaxTokens) }, gson, "t", presetFields)
        }

    @Test
    @DisplayName("a plain model's request without max tokens gets the default")
    fun addsDefaultMaxTokens() {
        assertEquals(512, apply("""{"model":"m"}""", 512, emptySet()).get("max_tokens").asInt)
    }

    @Test
    @DisplayName("a request's own max tokens are kept")
    fun keepsOwnMaxTokens() {
        assertEquals(64, apply("""{"model":"m","max_tokens":64}""", 512, emptySet()).get("max_tokens").asInt)
    }

    @Test
    @DisplayName("no default is added when none is configured")
    fun noDefaultConfigured() {
        assertFalse(apply("""{"model":"m"}""", 0, emptySet()).has("max_tokens"))
    }

    @Test
    @DisplayName("no default is added over a preset that sets max tokens, or a preset that is not known")
    fun presetSetsMaxTokens() {
        assertFalse(apply("""{"model":"m"}""", 512, setOf("max_tokens")).has("max_tokens"))
        assertFalse(apply("""{"model":"m"}""", 512, null).has("max_tokens"))
    }

    @Test
    @DisplayName("settings that cannot be read leave the request as it came")
    fun unreadableSettings() {
        val body = JsonParser.parseString("""{"model":"m"}""").asJsonObject

        ConfiguredDefaults.apply(body, { error("not readable") }, gson, "t", emptySet())

        assertEquals("""{"model":"m"}""", body.toString())
    }
}
