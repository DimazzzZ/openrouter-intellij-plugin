package org.zhavoronkov.openrouter.proxy.servlets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RequestBuilder Tests")
class RequestBuilderTest {

    @Test
    fun `parseRequestBody should return JsonObject`() {
        val builder = RequestBuilder()
        val result = builder.parseRequestBody("{\"model\":\"gpt\"}", "req-1")

        assertNotNull(result)
    }

    @Test
    fun `parseRequestBody should return null on invalid json`() {
        // build.gradle.kts sets openrouter.testMode=true for the unit test JVM, so
        // PluginLogger.Service.error routes to .debug (see PluginLogger.kt). The
        // JsonSyntaxException catch is genuine off-platform-reachable COVER logic.
        val builder = RequestBuilder()
        val result = builder.parseRequestBody("{ not valid json", "req-invalid")
        assertNull(result, "malformed JSON should degrade to null, not throw")
    }

    @Test
    fun `parseRequestBody returns null for a JSON array (not an object)`() {
        val builder = RequestBuilder()
        assertNull(builder.parseRequestBody("[1,2,3]", "req-array"))
    }

    @Test
    fun `buildOpenRouterRequest targets chat completions with POST`() {
        val builder = RequestBuilder()
        val req = builder.buildOpenRouterRequest("{\"model\":\"gpt-4\"}", "sk-or-test-key")

        assertEquals("https://openrouter.ai/api/v1/chat/completions", req.url.toString())
        assertEquals("POST", req.method)
    }

    @Test
    fun `buildOpenRouterRequest carries the API key as a Bearer token`() {
        val builder = RequestBuilder()
        val req = builder.buildOpenRouterRequest("{}", "sk-or-abc123")

        assertEquals("Bearer sk-or-abc123", req.header("Authorization"))
    }

    @Test
    fun `buildOpenRouterRequest sends the JSON body verbatim`() {
        val builder = RequestBuilder()
        val body = "{\"model\":\"anthropic/claude-3\",\"messages\":[]}"
        val req = builder.buildOpenRouterRequest(body, "sk-or-x")

        val buffer = okio.Buffer()
        req.body!!.writeTo(buffer)
        assertEquals(body, buffer.readUtf8())
        assertTrue(
            req.body!!.contentType().toString().startsWith("application/json"),
            "expected application/json content type, was: ${req.body!!.contentType()}"
        )
    }
}
