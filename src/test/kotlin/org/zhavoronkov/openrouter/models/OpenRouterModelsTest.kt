package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
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
}
