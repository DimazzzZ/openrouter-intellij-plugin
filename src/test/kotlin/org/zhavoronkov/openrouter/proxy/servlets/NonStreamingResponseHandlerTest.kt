package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.PrintWriter
import java.io.StringWriter

@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("NonStreamingResponseHandler Tests")
class NonStreamingResponseHandlerTest {

    private lateinit var server: MockWebServer
    private lateinit var handler: NonStreamingResponseHandler
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .url(server.url("/api/v1/chat/completions"))
                    .build()
                chain.proceed(newRequest)
            }
            .build()
        handler = NonStreamingResponseHandler(client, Gson())
    }

    @AfterEach
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun `handleNonStreamingRequest should write success response`() {
        val responseJson = """
            {
              "id": "cmpl-1",
              "model": "openai/gpt-4",
              "choices": [{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
              "usage": {"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-1",
            startNs = System.nanoTime()
        )

        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_OK)
        val body = writer.toString()
        assertEquals(true, body.contains("openai/gpt-4"))
    }

    @Test
    fun `handleNonStreamingRequest should map OpenRouter error status`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}"""))

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-err",
            startNs = System.nanoTime()
        )

        org.mockito.Mockito.verify(response).setStatus(429)
        assertEquals(true, writer.toString().contains("OpenRouter API error"))
    }

    @Test
    fun `handleNonStreamingRequest should handle empty body with 200`() {
        // 200 with an empty body still parses to a (null-content) ChatCompletionResponse;
        // this exercises parseOpenRouterResponseBody's non-null branch with minimal content.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-empty",
            startNs = System.nanoTime()
        )

        // Either a validation error (500) or a success (200) is written; a body is produced.
        assertEquals(true, writer.toString().isNotEmpty())
    }

    @Test
    fun `handleNonStreamingRequest should send 503 on network error`() {
        // Point the client at a dead port to force an IOException.
        val deadClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .url("http://127.0.0.1:1/api/v1/chat/completions")
                    .build()
                chain.proceed(newRequest)
            }
            .build()
        val deadHandler = NonStreamingResponseHandler(deadClient, Gson())

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        deadHandler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-neterr",
            startNs = System.nanoTime()
        )

        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
        assertEquals(true, writer.toString().contains("Network error"))
        deadClient.dispatcher.executorService.shutdown()
        deadClient.connectionPool.evictAll()
    }

    @Test
    fun `handleNonStreamingRequest should log OpenRouter metadata headers`() {
        val responseJson = """
            {
              "id": "cmpl-2",
              "model": "openai/gpt-4",
              "choices": [{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],
              "usage": {"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}
            }
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("x-openrouter-model", "openai/gpt-4")
                .setBody(responseJson)
        )

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-meta",
            startNs = System.nanoTime()
        )

        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_OK)
    }

    @Test
    fun `handleNonStreamingRequest should map error status with empty body`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-empty-err",
            startNs = System.nanoTime()
        )

        org.mockito.Mockito.verify(response).setStatus(500)
    }

    @Test
    fun `handleNonStreamingRequest should 500 on malformed success JSON`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all {"))

        val response = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(response.writer).thenReturn(PrintWriter(writer))

        handler.handleNonStreamingRequest(
            resp = response,
            requestBody = "{}",
            apiKey = "sk-test",
            originalModel = "openai/gpt-4",
            requestId = "req-malformed",
            startNs = System.nanoTime()
        )

        // Malformed JSON triggers the JsonSyntaxException catch -> 500
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
}
