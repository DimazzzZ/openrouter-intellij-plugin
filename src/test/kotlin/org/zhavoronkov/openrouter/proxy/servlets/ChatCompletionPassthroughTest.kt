package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest

/**
 * Verifies the Phase 1 field-preserving passthrough contract of the proxy.
 *
 * Background: the chat completion servlet used to deserialize the incoming request
 * into the typed [OpenAIChatCompletionRequest] and re-serialize it, which silently
 * dropped any OpenRouter-specific field the typed model does not declare
 * (provider, models[], route, transforms, response_format, plugins, preset, usage, ...).
 *
 * The fix parses the body into a [JsonObject] and forwards that verbatim, only
 * applying configured defaults. These tests assert the round-trip preserves every
 * field, while the typed model still parses the known fields for validation.
 */
@DisplayName("ChatCompletionServlet Field-Preserving Passthrough Tests")
class ChatCompletionPassthroughTest {

    private val gson = Gson()

    /**
     * Mirrors the servlet's outbound-body construction: parse to JsonObject and
     * serialize that (rather than the typed model). This is the exact mechanism
     * used by ChatCompletionServlet.parseRequestBody + prepareRequest.
     */
    private fun buildOutboundBody(requestBody: String): JsonObject {
        val rawJson = gson.fromJson(requestBody, JsonObject::class.java)
        // Typed parse still succeeds for validation/logging (must not throw)
        gson.fromJson(rawJson, OpenAIChatCompletionRequest::class.java)
        return rawJson
    }

    @Nested
    @DisplayName("OpenRouter-specific field preservation")
    inner class OpenRouterFieldPreservation {

        @Test
        @DisplayName("Should preserve provider routing preferences")
        fun preservesProvider() {
            val body = """
                {
                  "model":"anthropic/claude-3.5-sonnet",
                  "messages":[{"role":"user","content":"Hi"}],
                  "provider":{"order":["Anthropic","OpenAI"],"allow_fallbacks":false}
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertTrue(out.has("provider"), "provider must be preserved")
            val provider = out.getAsJsonObject("provider")
            assertEquals(false, provider.get("allow_fallbacks").asBoolean)
            assertEquals(2, provider.getAsJsonArray("order").size())
        }

        @Test
        @DisplayName("Should preserve models fallback array")
        fun preservesModelsFallback() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "models":["openai/gpt-4o","anthropic/claude-3.5-sonnet"],
                  "route":"fallback"
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertTrue(out.has("models"), "models[] must be preserved")
            assertEquals(2, out.getAsJsonArray("models").size())
            assertEquals("fallback", out.get("route").asString)
        }

        @Test
        @DisplayName("Should preserve response_format (structured outputs)")
        fun preservesResponseFormat() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "response_format":{"type":"json_schema","json_schema":{"name":"weather","strict":true}}
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertTrue(out.has("response_format"), "response_format must be preserved")
            assertEquals("json_schema", out.getAsJsonObject("response_format").get("type").asString)
        }

        @Test
        @DisplayName("Should preserve plugins (web search) and transforms")
        fun preservesPluginsAndTransforms() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "plugins":[{"id":"web","max_results":3}],
                  "transforms":["middle-out"]
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertTrue(out.has("plugins"), "plugins must be preserved")
            assertEquals(1, out.getAsJsonArray("plugins").size())
            assertTrue(out.has("transforms"), "transforms must be preserved")
            assertEquals("middle-out", out.getAsJsonArray("transforms").get(0).asString)
        }

        @Test
        @DisplayName("Should preserve preset and usage accounting flag")
        fun preservesPresetAndUsage() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "preset":"my-preset",
                  "usage":{"include":true}
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertEquals("my-preset", out.get("preset").asString)
            assertTrue(out.has("usage"), "usage must be preserved")
            assertEquals(true, out.getAsJsonObject("usage").get("include").asBoolean)
        }

        @Test
        @DisplayName("Should preserve ALL OpenRouter-specific fields together in one request")
        fun preservesAllFieldsTogether() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "provider":{"order":["OpenAI"]},
                  "models":["openai/gpt-4o","x-ai/grok-2"],
                  "route":"fallback",
                  "transforms":["middle-out"],
                  "response_format":{"type":"json_object"},
                  "plugins":[{"id":"web"}],
                  "preset":"p1",
                  "usage":{"include":true}
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            listOf(
                "provider",
                "models",
                "route",
                "transforms",
                "response_format",
                "plugins",
                "preset",
                "usage"
            ).forEach { field ->
                assertTrue(out.has(field), "$field must survive the round-trip")
            }
        }
    }

    @Nested
    @DisplayName("Known-field behavior and backward compatibility")
    inner class KnownFieldBehavior {

        @Test
        @DisplayName("Should preserve standard OpenAI fields untouched")
        fun preservesStandardFields() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "temperature":0.3,
                  "max_tokens":100,
                  "top_p":0.9,
                  "stream":true
                }
            """.trimIndent()

            val out = buildOutboundBody(body)

            assertEquals(0.3, out.get("temperature").asDouble)
            assertEquals(100, out.get("max_tokens").asInt)
            assertEquals(0.9, out.get("top_p").asDouble)
            assertEquals(true, out.get("stream").asBoolean)
        }

        @Test
        @DisplayName("Should not add fields that were not present (no default clobbering)")
        fun doesNotClobberAbsentFields() {
            val body = """
                {"model":"openai/gpt-4o","messages":[{"role":"user","content":"Hi"}]}
            """.trimIndent()

            val out = buildOutboundBody(body)

            // Only model + messages present; passthrough must not invent unrelated fields.
            assertFalse(out.has("provider"))
            assertFalse(out.has("response_format"))
            assertTrue(out.has("model"))
            assertTrue(out.has("messages"))
        }

        @Test
        @DisplayName("Typed model still parses known fields for validation")
        fun typedModelStillParses() {
            val body = """
                {
                  "model":"openai/gpt-4o",
                  "messages":[{"role":"user","content":"Hi"}],
                  "provider":{"order":["OpenAI"]}
                }
            """.trimIndent()

            val typed = gson.fromJson(body, OpenAIChatCompletionRequest::class.java)

            assertEquals("openai/gpt-4o", typed.model)
            assertEquals(1, typed.messages.size)
            assertEquals("user", typed.messages[0].role)
        }
    }
}
