package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.proxy.routing.ProviderRoutingInjector
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import java.util.concurrent.TimeUnit

/**
 * End-to-End tests for provider-routing injection through the full HTTP stack.
 *
 * These tests verify the complete flow:
 * 1. Client sends a request (with or without provider/models fields)
 * 2. ProviderRoutingInjector adds settings-level routing when absent
 * 3. OpenRouterRequestBuilder constructs the HTTP request
 * 4. OkHttpClient sends it to the mock upstream (MockWebServer)
 * 5. MockWebServer records what was actually sent
 *
 * This exercises the real production code path without booting the servlet,
 * proving the inject-only-when-absent invariant holds end-to-end.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("Provider Routing E2E Tests (MockWebServer)")
@Tag("functional")
class ProviderRoutingE2ETest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private val gson = Gson()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /**
     * Helper to enqueue a mock response that echoes the request body.
     */
    private fun enqueueEchoResponse() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                    "id":"cmpl-test","object":"chat.completion",
                    "model":"test","choices":[{"message":{"role":"assistant","content":"test"}}]
                    }"""
                )
        )
    }

    /**
     * Helper to send a request and return the recorded request body from MockWebServer.
     */
    private fun sendAndCaptureRequest(jsonBody: String): JsonObject {
        enqueueEchoResponse()

        val request = OpenRouterRequestBuilder.buildPostRequest(
            url = mockWebServer.url("/api/v1/chat/completions").toString(),
            jsonBody = jsonBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = "test-key"
        )

        httpClient.newCall(request).execute().use { response ->
            assertEquals(200, response.code, "Request should succeed")
        }

        val recordedRequest = mockWebServer.takeRequest()
        assertNotNull(recordedRequest, "MockWebServer should have recorded a request")

        val recordedBody = recordedRequest!!.body.readUtf8()
        return gson.fromJson(recordedBody, JsonObject::class.java)
    }

    @Nested
    @DisplayName("Injection invariant: only when absent")
    inner class InjectionInvariant {

        @Test
        @DisplayName("Should inject provider when absent and settings enabled")
        fun injectsProviderWhenAbsent() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic", "OpenAI")
            routing.sort = "price"
            routing.dataCollection = "deny"

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[]}""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-001")
            assertTrue(injected, "Should report that injection occurred")
            assertTrue(incomingJson.has("provider"), "provider block should be present")

            val provider = incomingJson.getAsJsonObject("provider")
            assertEquals(2, provider.getAsJsonArray("order").size())
            assertEquals("price", provider.get("sort").asString)
            assertEquals("deny", provider.get("data_collection").asString)
        }

        @Test
        @DisplayName("Should NOT inject provider when client already sent it")
        fun skipsProviderWhenPresent() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic", "OpenAI")

            val incomingJson = gson.fromJson(
                """{
                    "model":"openai/gpt-4o",
                    "messages":[],
                    "provider":{"order":["OpenAI"]}
                }""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-002")
            assertFalse(injected, "Should report that no injection occurred")

            val provider = incomingJson.getAsJsonObject("provider")
            assertEquals(1, provider.getAsJsonArray("order").size())
            assertEquals("OpenAI", provider.getAsJsonArray("order")[0].asString)
        }

        @Test
        @DisplayName("Should inject fallback models when absent and settings configured")
        fun injectsFallbackModelsWhenAbsent() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet", "meta-llama/llama-3.1-70b")

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[]}""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-003")
            assertTrue(injected, "Should report that injection occurred")
            assertTrue(incomingJson.has("models"), "models array should be present")

            val models = incomingJson.getAsJsonArray("models")
            assertEquals(2, models.size())
            assertEquals("anthropic/claude-3.5-sonnet", models[0].asString)
            assertEquals("meta-llama/llama-3.1-70b", models[1].asString)
        }

        @Test
        @DisplayName("Should NOT inject fallback models when client already sent them")
        fun skipsFallbackModelsWhenPresent() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet", "meta-llama/llama-3.1-70b")

            val incomingJson = gson.fromJson(
                """{
                    "model":"openai/gpt-4o",
                    "messages":[],
                    "models":["openai/gpt-4o-mini"]
                }""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-004")
            assertFalse(injected, "Should report that no injection occurred")

            val models = incomingJson.getAsJsonArray("models")
            assertEquals(1, models.size())
            assertEquals("openai/gpt-4o-mini", models[0].asString)
        }
    }

    @Nested
    @DisplayName("End-to-end HTTP flow")
    inner class EndToEndHttpFlow {

        @Test
        @DisplayName("Should send injected provider block to upstream")
        fun sendsInjectedProviderToUpstream() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.sort = "throughput"

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[{"role":"user","content":"Hi"}]}""",
                JsonObject::class.java
            )

            ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-005")

            val outboundBody = gson.toJson(incomingJson)
            val recordedBody = sendAndCaptureRequest(outboundBody)

            assertTrue(recordedBody.has("provider"), "Upstream should receive provider block")
            val provider = recordedBody.getAsJsonObject("provider")
            assertEquals("Anthropic", provider.getAsJsonArray("order")[0].asString)
            assertEquals("throughput", provider.get("sort").asString)
        }

        @Test
        @DisplayName("Should send injected fallback models to upstream")
        fun sendsInjectedFallbackModelsToUpstream() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.fallbackModels = mutableListOf("meta-llama/llama-3.1-70b", "anthropic/claude-3.5-sonnet")

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[{"role":"user","content":"Hi"}]}""",
                JsonObject::class.java
            )

            ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-006")

            val outboundBody = gson.toJson(incomingJson)
            val recordedBody = sendAndCaptureRequest(outboundBody)

            assertTrue(recordedBody.has("models"), "Upstream should receive models array")
            val models = recordedBody.getAsJsonArray("models")
            assertEquals(2, models.size())
            assertEquals("meta-llama/llama-3.1-70b", models[0].asString)
            assertEquals("anthropic/claude-3.5-sonnet", models[1].asString)
        }

        @Test
        @DisplayName("Should preserve client-sent provider when present")
        fun preservesClientProviderInUpstream() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic", "OpenAI")

            val incomingJson = gson.fromJson(
                """{
                    "model":"openai/gpt-4o",
                    "messages":[],
                    "provider":{"order":["OpenAI"],"allow_fallbacks":false}
                }""",
                JsonObject::class.java
            )

            ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-007")

            val outboundBody = gson.toJson(incomingJson)
            val recordedBody = sendAndCaptureRequest(outboundBody)

            assertTrue(recordedBody.has("provider"), "provider should be present")
            val provider = recordedBody.getAsJsonObject("provider")
            assertEquals(1, provider.getAsJsonArray("order").size())
            assertEquals("OpenAI", provider.getAsJsonArray("order")[0].asString)
            assertEquals(false, provider.get("allow_fallbacks").asBoolean)
        }

        @Test
        @DisplayName("Should preserve client-sent models when present")
        fun preservesClientModelsInUpstream() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet", "meta-llama/llama-3.1-70b")

            val incomingJson = gson.fromJson(
                """{
                    "model":"openai/gpt-4o",
                    "messages":[],
                    "models":["openai/gpt-4o-mini"]
                }""",
                JsonObject::class.java
            )

            ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-008")

            val outboundBody = gson.toJson(incomingJson)
            val recordedBody = sendAndCaptureRequest(outboundBody)

            assertTrue(recordedBody.has("models"), "models should be present")
            val models = recordedBody.getAsJsonArray("models")
            assertEquals(1, models.size())
            assertEquals("openai/gpt-4o-mini", models[0].asString)
        }
    }

    @Nested
    @DisplayName("Complex scenarios")
    inner class ComplexScenarios {

        @Test
        @DisplayName("Should inject both provider and models when both absent")
        fun injectsBothWhenAbsent() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.sort = "price"
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[]}""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-009")
            assertTrue(injected, "Should report injection")
            assertTrue(incomingJson.has("provider"), "provider should be injected")
            assertTrue(incomingJson.has("models"), "models should be injected")
        }

        @Test
        @DisplayName("Should not inject when routing disabled")
        fun skipsInjectionWhenDisabled() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = false
            routing.order = mutableListOf("Anthropic")
            routing.fallbackModels = mutableListOf("anthropic/claude-3.5-sonnet")

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[]}""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-010")
            assertFalse(injected, "Should report no injection when disabled")
            assertFalse(incomingJson.has("provider"), "provider should not be injected")
            assertFalse(incomingJson.has("models"), "models should not be injected")
        }

        @Test
        @DisplayName("Should inject provider but not models when only provider configured")
        fun injectsProviderOnlyWhenModelsEmpty() {
            val routing = ProviderRoutingManager(OpenRouterSettings()) {}
            routing.enabled = true
            routing.order = mutableListOf("Anthropic")
            routing.fallbackModels = mutableListOf() // Empty

            val incomingJson = gson.fromJson(
                """{"model":"openai/gpt-4o","messages":[]}""",
                JsonObject::class.java
            )

            val injected = ProviderRoutingInjector.inject(incomingJson, routing, gson, "test-011")
            assertTrue(injected, "Should report injection of provider")
            assertTrue(incomingJson.has("provider"), "provider should be injected")
            assertFalse(incomingJson.has("models"), "models should not be injected (empty list)")
        }
    }
}
