package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OpenRouterModels Data Class Tests")
class OpenRouterModelsTest {

    private val gson = Gson()

    @Nested
    @DisplayName("ReasoningConfig Tests")
    inner class ReasoningConfigTests {

        @Test
        @DisplayName("should default all fields to null")
        fun testDefaults() {
            val config = ReasoningConfig()

            assertNull(config.effort)
            assertNull(config.maxTokens)
            assertNull(config.exclude)
            assertNull(config.enabled)
        }

        @Test
        @DisplayName("should set all fields")
        fun testSetAllFields() {
            val config = ReasoningConfig(
                effort = "high",
                maxTokens = 2000,
                exclude = false,
                enabled = true
            )

            assertEquals("high", config.effort)
            assertEquals(2000, config.maxTokens)
            assertEquals(false, config.exclude)
            assertEquals(true, config.enabled)
        }

        @Test
        @DisplayName("should serialize to JSON correctly")
        fun testSerialization() {
            val config = ReasoningConfig(effort = "medium", exclude = true)
            val json = gson.toJson(config)

            assertTrue(json.contains("\"effort\":\"medium\""))
            assertTrue(json.contains("\"exclude\":true"))
            assertFalse(json.contains("\"max_tokens\""), "Null fields should not appear in JSON")
        }

        @Test
        @DisplayName("should deserialize from JSON correctly")
        fun testDeserialization() {
            val json = """{"effort":"low","max_tokens":1000,"enabled":true}"""
            val config = gson.fromJson(json, ReasoningConfig::class.java)

            assertEquals("low", config.effort)
            assertEquals(1000, config.maxTokens)
            assertEquals(true, config.enabled)
            assertNull(config.exclude)
        }
    }

    @Nested
    @DisplayName("ChatCompletionRequest Tests")
    inner class ChatCompletionRequestTests {

        @Test
        @DisplayName("should default reasoning and verbosity to null")
        fun testDefaults() {
            val request = ChatCompletionRequest(
                model = "openai/gpt-4o",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi")))
            )

            assertNull(request.reasoning)
            assertNull(request.verbosity)
        }

        @Test
        @DisplayName("should accept reasoning and verbosity")
        fun testWithReasoningAndVerbosity() {
            val request = ChatCompletionRequest(
                model = "openai/o3-mini",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Think"))),
                reasoning = ReasoningConfig(effort = "high"),
                verbosity = "low"
            )

            assertNotNull(request.reasoning)
            assertEquals("high", request.reasoning?.effort)
            assertEquals("low", request.verbosity)
        }

        @Test
        @DisplayName("should serialize reasoning and verbosity to JSON")
        fun testSerialization() {
            val request = ChatCompletionRequest(
                model = "openai/o3-mini",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                reasoning = ReasoningConfig(effort = "medium", maxTokens = 3000),
                verbosity = "high"
            )

            val json = gson.toJson(request)

            assertTrue(json.contains("\"reasoning\""))
            assertTrue(json.contains("\"effort\":\"medium\""))
            assertTrue(json.contains("\"max_tokens\":3000"))
            assertTrue(json.contains("\"verbosity\":\"high\""))
        }

        @Test
        @DisplayName("should serialize null reasoning/verbosity as absent in JSON")
        fun testNullFieldsNotInJson() {
            val request = ChatCompletionRequest(
                model = "openai/gpt-4o",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi")))
            )

            val json = gson.toJson(request)

            assertFalse(json.contains("\"reasoning\""), "Null reasoning should not appear")
            assertFalse(json.contains("\"verbosity\""), "Null verbosity should not appear")
        }

        @Test
        @DisplayName("should deserialize reasoning and verbosity from JSON")
        fun testDeserialization() {
            val json = """
                {
                    "model": "openai/o3-mini",
                    "messages": [{"role": "user", "content": "Hi"}],
                    "reasoning": {"effort": "xhigh", "exclude": false},
                    "verbosity": "max"
                }
            """.trimIndent()

            val request = gson.fromJson(json, ChatCompletionRequest::class.java)

            assertNotNull(request.reasoning)
            assertEquals("xhigh", request.reasoning?.effort)
            assertEquals(false, request.reasoning?.exclude)
            assertEquals("max", request.verbosity)
        }
    }

    @Nested
    @DisplayName("ProviderRoutingPreferences Serialization Tests")
    inner class ProviderRoutingPreferencesTests {

        @Test
        @DisplayName("should serialize only set fields (no nulls)")
        fun testOnlySetFieldsSerialized() {
            val prefs = ProviderRoutingPreferences(
                order = listOf("Anthropic", "OpenAI"),
                allowFallbacks = false
            )

            val json = gson.toJson(prefs)

            assertTrue(json.contains("\"order\""))
            assertTrue(json.contains("\"allow_fallbacks\""))
            assertFalse(json.contains("\"sort\""), "Null sort should not appear")
            assertFalse(json.contains("\"require_parameters\""), "Null require_parameters should not appear")
            assertFalse(json.contains("\"data_collection\""), "Null data_collection should not appear")
            assertFalse(json.contains("\"quantizations\""), "Null quantizations should not appear")
            assertFalse(json.contains("\"only\""), "Null only should not appear")
            assertFalse(json.contains("\"ignore\""), "Null ignore should not appear")
        }

        @Test
        @DisplayName("should serialize all fields when all are set")
        fun testAllFieldsSerialized() {
            val prefs = ProviderRoutingPreferences(
                order = listOf("Anthropic"),
                allowFallbacks = true,
                sort = "price",
                requireParameters = true,
                dataCollection = "deny",
                quantizations = listOf("int4", "fp16"),
                only = listOf("Anthropic"),
                ignore = listOf("OpenAI")
            )

            val json = gson.toJson(prefs)

            assertTrue(json.contains("\"order\""))
            assertTrue(json.contains("\"allow_fallbacks\":true"))
            assertTrue(json.contains("\"sort\":\"price\""))
            assertTrue(json.contains("\"require_parameters\":true"))
            assertTrue(json.contains("\"data_collection\":\"deny\""))
            assertTrue(json.contains("\"quantizations\""))
            assertTrue(json.contains("\"only\""))
            assertTrue(json.contains("\"ignore\""))
        }

        @Test
        @DisplayName("should produce empty object when all fields are null")
        fun testEmptyPreferences() {
            val prefs = ProviderRoutingPreferences()
            val json = gson.toJson(prefs)
            assertEquals("{}", json)
        }

        @Test
        @DisplayName("should deserialize from JSON correctly")
        fun testDeserialization() {
            val json = """
                {
                    "order": ["Anthropic", "OpenAI"],
                    "allow_fallbacks": false,
                    "sort": "throughput",
                    "data_collection": "allow",
                    "quantizations": ["fp8"]
                }
            """.trimIndent()

            val prefs = gson.fromJson(json, ProviderRoutingPreferences::class.java)

            assertEquals(listOf("Anthropic", "OpenAI"), prefs.order)
            assertEquals(false, prefs.allowFallbacks)
            assertEquals("throughput", prefs.sort)
            assertNull(prefs.requireParameters)
            assertEquals("allow", prefs.dataCollection)
            assertEquals(listOf("fp8"), prefs.quantizations)
            assertNull(prefs.only)
            assertNull(prefs.ignore)
        }

        @Test
        @DisplayName("ChatCompletionRequest should serialize provider and models when set")
        fun testRequestWithProvider() {
            val request = ChatCompletionRequest(
                model = "openai/gpt-4o",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi"))),
                provider = ProviderRoutingPreferences(order = listOf("Anthropic")),
                models = listOf("anthropic/claude-3.5-sonnet", "openai/gpt-4o")
            )

            val json = gson.toJson(request)

            assertTrue(json.contains("\"provider\""))
            assertTrue(json.contains("\"order\""))
            assertTrue(json.contains("\"Anthropic\""))
            assertTrue(json.contains("\"models\""))
            assertTrue(json.contains("anthropic/claude-3.5-sonnet"))
        }

        @Test
        @DisplayName("ChatCompletionRequest should not serialize provider and models when null")
        fun testRequestWithoutProvider() {
            val request = ChatCompletionRequest(
                model = "openai/gpt-4o",
                messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("Hi")))
            )

            val json = gson.toJson(request)

            assertFalse(json.contains("\"provider\""), "Null provider should not appear")
            assertFalse(json.contains("\"models\""), "Null models should not appear")
        }
    }
}
