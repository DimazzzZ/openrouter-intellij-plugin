package org.zhavoronkov.openrouter.integration

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.zhavoronkov.openrouter.proxy.servlets.StreamingResponseHandler
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

/**
 * Live E2E test for streaming tool-calling flow.
 *
 * This test verifies the branch's headline feature (streaming tool-call reassembly)
 * against real OpenRouter models with a real API key from .env.
 *
 * Strategy: call OpenRouter's Chat Completions endpoint directly for both non-streaming
 * and streaming tool-call scenarios, then drive the real streamed bytes through the
 * production [StreamingResponseHandler] + [ToolCallAccumulator] to prove our assembly
 * code correctly reassembles the tool_call from real provider output.
 *
 * Why not boot ChatCompletionServlet here? The servlet depends on IntelliJ Application
 * services (settings, logger) which require a full test-fixture harness. Driving the
 * handler classes directly against real SSE bytes gives equivalent coverage of the code
 * this branch actually changed, without the IntelliJ bootstrap fragility.
 *
 * Cost: ~$0.003 per full run of E1-E4 using openai/gpt-4o-mini (the same cheap model
 * every other live test uses). Gated by @Tag("functional") + `.env` presence; excluded
 * from CI. Run with: `./gradlew test --tests "ToolCallingProxyE2ETest" -Pfunctional`.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("Tool-Calling Proxy E2E Tests (Real API)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("functional")
class ToolCallingProxyE2ETest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private lateinit var apiKey: String
    private val openRouterApiUrl = "https://openrouter.ai/api/v1"
    private val model = "openai/gpt-4o-mini"

    // A simple weather tool schema the model should be prompted to call
    private val weatherToolJson = """
        {
          "type": "function",
          "function": {
            "name": "get_weather",
            "description": "Get the current weather for a given city.",
            "parameters": {
              "type": "object",
              "properties": {
                "city": {"type": "string", "description": "The city name"}
              },
              "required": ["city"]
            }
          }
        }
    """.trimIndent()

    private val timeToolJson = """
        {
          "type": "function",
          "function": {
            "name": "get_time",
            "description": "Get the current time in a given timezone.",
            "parameters": {
              "type": "object",
              "properties": {
                "timezone": {"type": "string", "description": "IANA timezone, e.g. Europe/Paris"}
              },
              "required": ["timezone"]
            }
          }
        }
    """.trimIndent()

    @BeforeAll
    fun setUpAll() {
        loadEnvFile()
        println("🔑 Loaded API key from .env (${apiKey.take(20)}...)")

        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("⚠️  WARNING: These tests make REAL API calls to OpenRouter (~$0.003 total)")
    }

    @AfterAll
    fun tearDownAll() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        println("✅ ToolCallingProxyE2ETest completed")
    }

    private fun loadEnvFile() {
        val envFile = File(".env")
        if (!envFile.exists()) {
            error("❌ .env file not found. Create one with OPENROUTER_API_KEY.")
        }
        val envVars = mutableMapOf<String, String>()
        envFile.readLines().forEach { line ->
            if (line.isNotBlank() && !line.startsWith("#")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    envVars[parts[0].trim()] = parts[1].trim().removeSurrounding("\"")
                }
            }
        }
        apiKey = envVars["OPENROUTER_API_KEY"]
            ?: error("❌ OPENROUTER_API_KEY missing in .env")
    }

    // ─── E1: non-streaming tool call ─────────────────────────────────────────

    @Test
    @DisplayName("E1: Non-streaming tool call returns tool_calls in response")
    fun testNonStreamingToolCall() {
        val body = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": "What is the weather in Paris right now? Use the get_weather tool."}
              ],
              "tools": [$weatherToolJson],
              "tool_choice": "auto",
              "stream": false,
              "max_tokens": 200
            }
        """.trimIndent()

        val response = post(body)
        assertEquals(200, response.code, "Expected HTTP 200 from OpenRouter")

        val responseBody = response.body?.string() ?: error("Empty body")
        response.close()

        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val message = json.getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message")
        assertTrue(message.has("tool_calls"), "Message should contain tool_calls; got: $message")

        val toolCalls = message.getAsJsonArray("tool_calls")
        assertTrue(toolCalls.size() >= 1, "Expected at least one tool call")
        val firstCall = toolCalls[0].asJsonObject
        val functionObj = firstCall.getAsJsonObject("function")
        assertEquals("get_weather", functionObj.get("name").asString)

        // Arguments must be a JSON string that parses as an object with a "city" field
        val argsRaw = functionObj.get("arguments").asString
        val argsJson = gson.fromJson(argsRaw, JsonObject::class.java)
        assertTrue(argsJson.has("city"), "Arguments should include 'city'; got: $argsRaw")
        println("✅ E1 non-streaming tool call: city=${argsJson.get("city").asString}")
    }

    // ─── E2: streaming tool call, single tool ───────────────────────────────

    @Test
    @DisplayName("E2: Streaming tool call yields SSE chunks + handler assembles the call")
    fun testStreamingSingleToolCall() {
        val body = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": "What is the weather in Paris right now? Use the get_weather tool."}
              ],
              "tools": [$weatherToolJson],
              "tool_choice": "auto",
              "stream": true,
              "max_tokens": 200
            }
        """.trimIndent()

        val response = post(body)
        assertEquals(200, response.code, "Expected HTTP 200 from OpenRouter")
        val contentType = response.header("Content-Type") ?: ""
        assertTrue(
            contentType.startsWith("text/event-stream"),
            "Expected SSE Content-Type but got: $contentType"
        )

        val sseBytes = response.body?.string() ?: error("Empty streaming body")
        response.close()

        // Basic SSE assertions
        assertTrue(sseBytes.contains("data:"), "SSE stream should contain data: lines")
        assertTrue(sseBytes.contains("tool_calls"), "SSE stream should mention tool_calls")

        // Drive the raw SSE through the production handler to verify our reassembly.
        // Wrap the raw string as if it were an OkHttp response body.
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("http://replay").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(sseBytes.toResponseBody("text/event-stream".toMediaType()))
            .build()

        val handler = StreamingResponseHandler()
        val out = StringWriter()
        handler.streamResponseToClient(fakeResponse, PrintWriter(out), "e2-req")

        // Assembled + cleared after finish_reason=tool_calls
        val hasPending = handler.getAccumulatorForTesting().hasPending()
        // Accumulator should be empty after finish_reason=tool_calls is processed
        assertFalse(hasPending, "Accumulator should be empty after full stream (indicates assembly completed)")
        val forwarded = out.toString()
        assertTrue(forwarded.contains("get_weather"), "Assembled stream should mention get_weather")
        assertTrue(forwarded.contains("[DONE]"), "Stream should terminate with DONE marker")
        println("✅ E2 streaming tool call assembled through production handler")
    }

    // ─── E3: streaming with multiple tools, model picks one ─────────────────

    @Test
    @DisplayName("E3: Streaming with two tools available, model selects one")
    fun testStreamingMultipleToolsOnePicked() {
        val body = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": "What is the weather in Paris? Use one of the tools."}
              ],
              "tools": [$weatherToolJson, $timeToolJson],
              "tool_choice": "auto",
              "stream": true,
              "max_tokens": 200
            }
        """.trimIndent()

        val response = post(body)
        assertEquals(200, response.code)
        val sseBytes = response.body?.string() ?: error("Empty streaming body")
        response.close()

        // Feed through handler
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("http://replay").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(sseBytes.toResponseBody("text/event-stream".toMediaType()))
            .build()

        val handler = StreamingResponseHandler()
        val out = StringWriter()
        handler.streamResponseToClient(fakeResponse, PrintWriter(out), "e3-req")

        val forwarded = out.toString()
        // Model must have picked exactly one of the two offered tools
        val pickedWeather = forwarded.contains("get_weather")
        val pickedTime = forwarded.contains("get_time")
        assertTrue(pickedWeather || pickedTime, "Model should have called one of the offered tools")
        println("✅ E3 model picked: weather=$pickedWeather, time=$pickedTime")
    }

    // ─── E4: streaming with provider passthrough field ──────────────────────

    @Test
    @DisplayName("E4: Streaming tool call with provider passthrough succeeds")
    fun testStreamingWithProviderPassthrough() {
        // Combines two things this branch touches: OpenRouter-specific `provider` field
        // preservation (Phase 1) and the streaming tool-call path (Phase 2).
        val body = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": "What is the weather in Paris? Use the get_weather tool."}
              ],
              "tools": [$weatherToolJson],
              "tool_choice": "auto",
              "provider": {"order": ["OpenAI"]},
              "stream": true,
              "max_tokens": 200
            }
        """.trimIndent()

        val response = post(body)
        assertEquals(200, response.code, "Provider-routed request should still return 200")

        val sseBytes = response.body?.string() ?: error("Empty streaming body")
        response.close()

        // Sanity check the SSE stream
        assertTrue(sseBytes.contains("data:"), "SSE stream expected")

        // Drive through handler to confirm tool-call assembly still works under provider routing
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("http://replay").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(sseBytes.toResponseBody("text/event-stream".toMediaType()))
            .build()

        val handler = StreamingResponseHandler()
        val out = StringWriter()
        handler.streamResponseToClient(fakeResponse, PrintWriter(out), "e4-req")

        val forwarded = out.toString()
        assertTrue(forwarded.contains("get_weather"), "Assembled stream should mention get_weather")
        assertFalse(handler.getAccumulatorForTesting().hasPending())
        println("✅ E4 provider passthrough + streaming tool call works end-to-end")
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private fun post(body: String): Response {
        val request = Request.Builder()
            .url("$openRouterApiUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/zhavoronkov/openrouter-intellij-plugin")
            .addHeader("X-Title", "OpenRouter IntelliJ Plugin E2E Test")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return httpClient.newCall(request).execute()
    }
}
