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
import org.mockito.Mockito.mock
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("NonStreamingResponseHandler Tests")
class NonStreamingResponseHandlerTest {

    private lateinit var server: MockWebServer
    private lateinit var handler: NonStreamingResponseHandler

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
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
}
