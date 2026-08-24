package org.zhavoronkov.openrouter.proxy.routing

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager

@DisplayName("ProviderRoutingInjector Tests")
class ProviderRoutingInjectorTest {

    private val gson = Gson()
    private lateinit var settings: OpenRouterSettings
    private lateinit var routing: ProviderRoutingManager

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        routing = ProviderRoutingManager(settings) {}
    }

    private fun buildRequest(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    @Nested
    @DisplayName("Provider block injection")
    inner class ProviderBlockInjection {

        @Test
        @DisplayName("should inject provider when routing enabled and request omits it")
        fun injectsWhenAbsent() {
            routing.enabled = true
            routing.order = mutableListOf("Anthropic", "OpenAI")

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-001")

            assertTrue(result)
            assertTrue(rawJson.has("provider"))
            val provider = rawJson.getAsJsonObject("provider")
            assertEquals(2, provider.getAsJsonArray("order").size())
            assertEquals("Anthropic", provider.getAsJsonArray("order")[0].asString)
        }

        @Test
        @DisplayName("should NOT inject provider when client already sent it")
        fun preservesClientProvider() {
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")

            val rawJson = buildRequest("""
                {"model":"openai/gpt-4o","messages":[],"provider":{"order":["OpenAI"]}}
            """.trimIndent())

            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-002")

            assertFalse(result)
            // Client's block is preserved verbatim
            val provider = rawJson.getAsJsonObject("provider")
            assertEquals(1, provider.getAsJsonArray("order").size())
            assertEquals("OpenAI", provider.getAsJsonArray("order")[0].asString)
        }

        @Test
        @DisplayName("should NOT inject when routing is disabled")
        fun noInjectionWhenDisabled() {
            routing.enabled = false
            routing.order = mutableListOf("Anthropic")

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-003")

            assertFalse(result)
            assertFalse(rawJson.has("provider"))
        }

        @Test
        @DisplayName("should NOT inject when routing enabled but no fields set")
        fun noInjectionWhenNoFieldsSet() {
            routing.enabled = true
            // All fields at defaults → toPreferences() returns null

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-004")

            assertFalse(result)
            assertFalse(rawJson.has("provider"))
        }
    }

    @Nested
    @DisplayName("Fallback models injection")
    inner class FallbackModelsInjection {

        @Test
        @DisplayName("should inject models when routing enabled and request omits it")
        fun injectsModelsWhenAbsent() {
            routing.enabled = true
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet", "openai/gpt-4o")

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            ProviderRoutingInjector.inject(rawJson, routing, gson, "test-005")

            assertTrue(rawJson.has("models"))
            assertEquals(2, rawJson.getAsJsonArray("models").size())
            assertEquals("anthropic/claude-3.5-sonnet", rawJson.getAsJsonArray("models")[0].asString)
        }

        @Test
        @DisplayName("should NOT inject models when client already sent them")
        fun preservesClientModels() {
            routing.enabled = true
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val rawJson = buildRequest("""
                {"model":"openai/gpt-4o","messages":[],"models":["meta-llama/llama-3.1-70b"]}
            """.trimIndent())

            ProviderRoutingInjector.inject(rawJson, routing, gson, "test-006")

            // Client's models preserved
            assertEquals(1, rawJson.getAsJsonArray("models").size())
            assertEquals("meta-llama/llama-3.1-70b", rawJson.getAsJsonArray("models")[0].asString)
        }

        @Test
        @DisplayName("should NOT inject models when fallbackModels is empty")
        fun noModelsWhenEmpty() {
            routing.enabled = true
            routing.fallbackModels = mutableListOf()

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            ProviderRoutingInjector.inject(rawJson, routing, gson, "test-007")

            assertFalse(rawJson.has("models"))
        }
    }

    @Nested
    @DisplayName("Combined injection scenarios")
    inner class CombinedScenarios {

        @Test
        @DisplayName("should inject both provider and models when both absent")
        fun injectsBoth() {
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val rawJson = buildRequest("""{"model":"openai/gpt-4o","messages":[]}""")
            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-008")

            assertTrue(result)
            assertTrue(rawJson.has("provider"))
            assertTrue(rawJson.has("models"))
        }

        @Test
        @DisplayName("should inject models but skip provider when client sent provider")
        fun injectsModelsSkipsProvider() {
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val rawJson = buildRequest("""
                {"model":"openai/gpt-4o","messages":[],"provider":{"order":["OpenAI"]}}
            """.trimIndent())

            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-009")

            assertTrue(result) // models was injected
            // Provider preserved from client
            assertEquals("OpenAI", rawJson.getAsJsonObject("provider").getAsJsonArray("order")[0].asString)
            // Models injected from settings
            assertTrue(rawJson.has("models"))
            assertEquals("anthropic/claude-3.5-sonnet", rawJson.getAsJsonArray("models")[0].asString)
        }

        @Test
        @DisplayName("should inject provider but skip models when client sent models")
        fun injectsProviderSkipsModels() {
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val rawJson = buildRequest("""
                {"model":"openai/gpt-4o","messages":[],"models":["meta-llama/llama-3.1-70b"]}
            """.trimIndent())

            val result = ProviderRoutingInjector.inject(rawJson, routing, gson, "test-010")

            assertTrue(result) // provider was injected
            assertTrue(rawJson.has("provider"))
            assertEquals("Anthropic", rawJson.getAsJsonObject("provider").getAsJsonArray("order")[0].asString)
            // Models preserved from client
            assertEquals("meta-llama/llama-3.1-70b", rawJson.getAsJsonArray("models")[0].asString)
        }
    }
}
