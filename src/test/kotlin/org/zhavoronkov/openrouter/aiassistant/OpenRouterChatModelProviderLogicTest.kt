package org.zhavoronkov.openrouter.aiassistant

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for the pure-logic surface of [OpenRouterChatModelProvider]:
 * request body creation, response parsing, token estimation and streaming flags.
 *
 * The injected constructor supplies a mocked [OpenRouterSettingsService].
 *
 * Note: `makeOpenRouterRequest` (and therefore the network paths of
 * `sendChatRequest` / `sendCompletionRequest` when configured) are not covered
 * here because they open a real OkHttp connection to openrouter.ai. Those paths
 * are network-bound and belong to functional/integration testing, not the fast
 * unit `:test` task. The "not configured" short-circuit branches ARE covered.
 */
@DisplayName("OpenRouter Chat Model Provider Logic Tests")
class OpenRouterChatModelProviderLogicTest {

    private val gson = Gson()

    private fun provider(configured: Boolean = true, apiKey: String = "sk-test"): OpenRouterChatModelProvider {
        val svc = mock(OpenRouterSettingsService::class.java)
        whenever(svc.isConfigured()).thenReturn(configured)
        whenever(svc.getApiKey()).thenReturn(apiKey)
        return OpenRouterChatModelProvider(svc)
    }

    @Nested
    @DisplayName("estimateTokenCount")
    inner class EstimateTokenCountTests {
        @Test
        fun `estimates ~4 chars per token`() {
            assertEquals(25, provider().estimateTokenCount("x".repeat(100)))
        }

        @Test
        fun `returns at least 1 for short text`() {
            assertEquals(1, provider().estimateTokenCount("hi"))
        }

        @Test
        fun `returns at least 1 for empty text`() {
            assertEquals(1, provider().estimateTokenCount(""))
        }
    }

    @Nested
    @DisplayName("supportsStreaming")
    inner class SupportsStreamingTests {
        @Test
        fun `always returns true`() {
            assertTrue(provider().supportsStreaming("openai/gpt-4o"))
            assertTrue(provider().supportsStreaming("any/model"))
        }
    }

    @Nested
    @DisplayName("createChatRequestBody")
    inner class CreateChatRequestBodyTests {
        @Test
        fun `serializes model, messages, and params`() {
            val body = provider().createChatRequestBody(
                modelId = "openai/gpt-4o",
                messages = listOf(ChatMessage("user", "Hello")),
                maxTokens = 500,
                temperature = 0.5,
                stream = false
            )
            val json = gson.fromJson(body, JsonObject::class.java)

            assertEquals("openai/gpt-4o", json.get("model").asString)
            assertEquals(500, json.get("max_tokens").asInt)
            assertEquals(0.5, json.get("temperature").asDouble)
            assertFalse(json.get("stream").asBoolean)

            val messages = json.getAsJsonArray("messages")
            assertEquals(1, messages.size())
            val msg = messages[0].asJsonObject
            assertEquals("user", msg.get("role").asString)
            assertEquals("Hello", msg.get("content").asString)
        }

        @Test
        fun `serializes multiple messages preserving order`() {
            val body = provider().createChatRequestBody(
                modelId = "openai/gpt-4o",
                messages = listOf(
                    ChatMessage("system", "You are helpful"),
                    ChatMessage("user", "Hi"),
                    ChatMessage("assistant", "Hello!")
                ),
                maxTokens = 100,
                temperature = 0.7,
                stream = true
            )
            val json = gson.fromJson(body, JsonObject::class.java)
            val messages = json.getAsJsonArray("messages")

            assertEquals(3, messages.size())
            assertEquals("system", messages[0].asJsonObject.get("role").asString)
            assertEquals("user", messages[1].asJsonObject.get("role").asString)
            assertEquals("assistant", messages[2].asJsonObject.get("role").asString)
            assertTrue(json.get("stream").asBoolean)
        }
    }

    @Nested
    @DisplayName("parseOpenRouterResponse")
    inner class ParseResponseTests {
        @Test
        fun `error when responseBody is null`() {
            val r = provider().parseOpenRouterResponse(null)
            assertFalse(r.isSuccess())
            assertNotNull(r.error)
        }

        @Test
        fun `error when responseBody is blank`() {
            val r = provider().parseOpenRouterResponse("   ")
            assertFalse(r.isSuccess())
        }

        @Test
        fun `success extracts content from first choice`() {
            val body = """
                {"choices":[{"message":{"role":"assistant","content":"Hello there"}}]}
            """.trimIndent()
            val r = provider().parseOpenRouterResponse(body)
            assertTrue(r.isSuccess())
            assertEquals("Hello there", r.content)
        }

        @Test
        fun `error object in response is surfaced`() {
            val body = """{"error":{"message":"Rate limit exceeded"}}"""
            val r = provider().parseOpenRouterResponse(body)
            assertFalse(r.isSuccess())
            assertEquals("Rate limit exceeded", r.error)
        }

        @Test
        fun `error object without message uses fallback`() {
            val body = """{"error":{"code":429}}"""
            val r = provider().parseOpenRouterResponse(body)
            assertFalse(r.isSuccess())
            assertEquals("Unknown error", r.error)
        }

        @Test
        fun `no choices yields error`() {
            val body = """{"choices":[]}"""
            val r = provider().parseOpenRouterResponse(body)
            assertFalse(r.isSuccess())
        }

        @Test
        fun `choice without content yields error`() {
            val body = """{"choices":[{"message":{"role":"assistant"}}]}"""
            val r = provider().parseOpenRouterResponse(body)
            assertFalse(r.isSuccess())
        }

        @Test
        fun `malformed json yields parsing error`() {
            val r = provider().parseOpenRouterResponse("{not valid json")
            assertFalse(r.isSuccess())
            assertNotNull(r.error)
        }
    }

    @Nested
    @DisplayName("parseResponseJson")
    inner class ParseResponseJsonTests {
        @Test
        fun `extracts content from valid JsonObject`() {
            val json = gson.fromJson(
                """{"choices":[{"message":{"content":"parsed"}}]}""",
                JsonObject::class.java
            )
            val r = provider().parseResponseJson(json)
            assertTrue(r.isSuccess())
            assertEquals("parsed", r.content)
        }
    }

    @Nested
    @DisplayName("parseValidResponse")
    inner class ParseValidResponseTests {
        @Test
        fun `accepts a valid JsonObject body`() {
            val r = provider().parseValidResponse(
                """{"choices":[{"message":{"content":"ok"}}]}"""
            )
            assertTrue(r.isSuccess())
            assertEquals("ok", r.content)
        }

        @Test
        fun `error when body has neither error nor choices`() {
            val r = provider().parseValidResponse("{}")
            assertFalse(r.isSuccess())
        }
    }

    @Nested
    @DisplayName("Not-configured short circuits")
    inner class NotConfiguredTests {
        @Test
        fun `sendChatRequest returns error when not configured`() {
            val r = provider(configured = false).sendChatRequest(
                "openai/gpt-4o",
                listOf(ChatMessage("user", "hi"))
            ).get()
            assertFalse(r.isSuccess())
            assertEquals("OpenRouter not configured", r.error)
        }

        @Test
        fun `sendCompletionRequest returns error when not configured`() {
            val r = provider(configured = false).sendCompletionRequest(
                "openai/gpt-4o",
                "complete this"
            ).get()
            assertFalse(r.isSuccess())
            assertNull(r.content)
            assertNotNull(r.error)
        }
    }
}
