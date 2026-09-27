package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.ChatCompletionRequest
import org.zhavoronkov.openrouter.models.ChatMessage
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Drives [OpenRouterService] into each transport failure its `handleNetworkError` distinguishes,
 * and into the blank-body fallbacks its error mapping falls back on.
 *
 * Every case here is hermetic: the exception type is chosen by injecting a client (a [Dns] that
 * refuses to resolve, an [Interceptor] that throws, a short read timeout against a server that
 * never answers) or by pointing at a loopback port whose server has already been shut down. No
 * test reaches a real host, and none depends on wall-clock duration beyond its own read timeout.
 *
 * This complements `OpenRouterServiceNetworkErrorTest`, which asserts the shape of the message
 * strings without calling the service.
 */
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Transport Failure Tests")
class OpenRouterServiceTransportFailureTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private val clients = mutableListOf<OkHttpClient>()
    private val services = mutableListOf<OpenRouterService>()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")
        val mockApiKeyManager = mock(org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager::class.java)
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.apiKeyManager).thenReturn(mockApiKeyManager)
    }

    @AfterEach
    fun tearDown() {
        services.forEach { it.dispose() }
        clients.forEach {
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        runCatching { mockWebServer.shutdown() }
    }

    private fun baseUrl() = mockWebServer.url("/api/v1").toString().removeSuffix("/")

    private fun serviceWith(
        client: OkHttpClient = OkHttpClient.Builder().build(),
        baseUrl: String = baseUrl()
    ): OpenRouterService {
        clients += client
        return OpenRouterService(
            gson = Gson(),
            client = client,
            settingsService = mockSettingsService,
            baseUrlOverride = baseUrl
        ).also { services += it }
    }

    private fun enqueue(code: Int, body: String) {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    private fun chatRequest(content: String = "hi") = ChatCompletionRequest(
        model = "openai/gpt-4o-mini",
        messages = listOf(ChatMessage(role = "user", content = JsonPrimitive(content)))
    )

    @Nested
    @DisplayName("Transport failures are reported, never thrown")
    inner class TransportFailures {

        @Test
        @DisplayName("a hostname that will not resolve is reported as an error")
        fun unresolvableHost() = runBlocking {
            val client = OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String) =
                        throw UnknownHostException("no such host (injected)")
                })
                .build()

            val result = serviceWith(client).testApiKey("sk-or-test")

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
        }

        @Test
        @DisplayName("a server that accepts the connection and never answers is reported as an error")
        fun readTimeout() = runBlocking {
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient.Builder()
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

            val result = serviceWith(client).testApiKey("sk-or-test")

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
        }

        @Test
        @DisplayName("a refused connection is reported as an error")
        fun connectionRefused() = runBlocking {
            // Take the port the server was listening on, then free it: a loopback connect to a
            // closed port is refused immediately, with no traffic leaving the machine.
            val deadUrl = baseUrl()
            mockWebServer.shutdown()

            val result = serviceWith(baseUrl = deadUrl).testApiKey("sk-or-test")

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
        }

        @Test
        @DisplayName("an IO failure of no recognised kind is still reported as an error")
        fun genericIoFailure() = runBlocking {
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("injected transport failure") })
                .build()

            val result = serviceWith(client).testApiKey("sk-or-test")

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
            assertEquals("injected transport failure", (result as ApiResult.Error).message)
        }

        @Test
        @DisplayName("a chat completion whose transport fails is reported, not thrown")
        fun chatCompletionTransportFailure() = runBlocking {
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("injected transport failure") })
                .build()

            val result = serviceWith(client).createChatCompletion(chatRequest())

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
            assertEquals("injected transport failure", (result as ApiResult.Error).message)
        }

        @Test
        @DisplayName("a public-endpoint fetch whose transport fails is reported, not thrown")
        fun publicEndpointTransportFailure() = runBlocking {
            val client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("injected transport failure") })
                .build()

            val result = serviceWith(client).getModels()

            assertTrue(result is ApiResult.Error, "expected a reported error, got: $result")
        }
    }

    @Nested
    @DisplayName("Blank and unparseable error bodies still yield a usable message")
    inner class ErrorBodyFallbacks {

        @Test
        @DisplayName("a chat completion rejected with an empty body falls back to the status line")
        fun chatCompletionBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody(""))

            val result = serviceWith().createChatCompletion(chatRequest())

            val error = result as ApiResult.Error
            assertEquals(503, error.statusCode)
            assertTrue(error.message.isNotBlank(), "a blank body must not produce a blank message")
        }

        @Test
        @DisplayName("credits rejected with a body that is not JSON falls back to the raw body")
        fun creditsUnparseableErrorBody() = runBlocking {
            enqueue(500, "upstream exploded, not json")

            val result = serviceWith().getCredits()

            val error = result as ApiResult.Error
            assertEquals(500, error.statusCode)
            assertEquals("upstream exploded, not json", error.message)
        }

        @Test
        @DisplayName("credits rejected with an empty body falls back to the caller's default message")
        fun creditsBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(""))

            val result = serviceWith().getCredits()

            val error = result as ApiResult.Error
            assertEquals(500, error.statusCode)
            assertEquals("Failed to fetch credits", error.message)
        }

        @Test
        @DisplayName("credits rejected with a JSON body whose error.message is blank falls back too")
        fun creditsBlankErrorMessage() = runBlocking {
            enqueue(500, """{"error":{"message":"   "}}""")

            val result = serviceWith().getCredits()

            val error = result as ApiResult.Error
            assertEquals("""{"error":{"message":"   "}}""", error.message)
        }

        @Test
        @DisplayName("an API key rejected with a JSON body carrying no message reads as Invalid API key")
        fun testApiKeyWithoutErrorMessage() = runBlocking {
            enqueue(401, "{}")

            val result = serviceWith().testApiKey("sk-or-test")

            val error = result as ApiResult.Error
            assertEquals(401, error.statusCode)
            assertEquals("Invalid API key", error.message)
        }

        @Test
        @DisplayName("an API key rejected with a body that is not an object at all still reads as Invalid API key")
        fun testApiKeyWithNullErrorEnvelope() = runBlocking {
            enqueue(401, "null")

            val result = serviceWith().testApiKey("sk-or-test")

            assertEquals("Invalid API key", (result as ApiResult.Error).message)
        }

        @Test
        @DisplayName("an API key rejected with an error object carrying no message reads as Invalid API key")
        fun testApiKeyWithEmptyErrorObject() = runBlocking {
            enqueue(401, """{"error":{}}""")

            val result = serviceWith().testApiKey("sk-or-test")

            assertEquals("Invalid API key", (result as ApiResult.Error).message)
        }

        @Test
        @DisplayName("an auth-code exchange rejected with an empty body names the status code")
        fun exchangeAuthCodeBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(""))

            val result = serviceWith().exchangeAuthCode("code-1", "verifier-1")

            val error = result as ApiResult.Error
            assertEquals(400, error.statusCode)
            assertEquals("Failed to exchange auth code (HTTP 400)", error.message)
        }

        @Test
        @DisplayName("listing presets rejected with an empty body falls back to the status line")
        fun presetsBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody(""))

            val result = serviceWith().getPresets()

            val error = result as ApiResult.Error
            assertEquals(503, error.statusCode)
            assertTrue(error.message.isNotBlank(), "a blank body must not produce a blank message")
        }

        @Test
        @DisplayName("models rejected with an empty body falls back to a named default message")
        fun modelsBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(502).setBody(""))

            val result = serviceWith().getModels()

            val error = result as ApiResult.Error
            assertEquals(502, error.statusCode)
            assertEquals("Failed to fetch models", error.message)
        }

        @Test
        @DisplayName("providers rejected with an empty body falls back to a named default message")
        fun providersBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(502).setBody(""))

            val result = serviceWith().getProviders()

            val error = result as ApiResult.Error
            assertEquals("Failed to fetch providers", error.message)
        }

        @Test
        @DisplayName("models count rejected with an empty body falls back to a named default message")
        fun modelsCountBlankErrorBody() = runBlocking {
            mockWebServer.enqueue(MockResponse().setResponseCode(502).setBody(""))

            val result = serviceWith().getModelsCount()

            val error = result as ApiResult.Error
            assertEquals("Failed to fetch models count", error.message)
        }
    }

    @Nested
    @DisplayName("Outgoing request logging")
    inner class OutgoingLogging {

        @Test
        @DisplayName("a body past the preview length is still sent whole, only its log line is truncated")
        fun longBodyIsSentWhole() = runBlocking {
            val longContent = "x".repeat(PREVIEW_LENGTH * 2)
            mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody(""))

            serviceWith().createChatCompletion(chatRequest(longContent))

            val recorded = mockWebServer.takeRequest()
            assertTrue(
                recorded.body.readUtf8().contains(longContent),
                "the preview length governs the log line only, never the request body"
            )
        }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 300L
        const val PREVIEW_LENGTH = 500
    }
}
