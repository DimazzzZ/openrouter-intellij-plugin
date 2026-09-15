package org.zhavoronkov.openrouter.utils

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension
import java.io.IOException

@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OkHttpExtensions Tests")
class OkHttpExtensionsTest {

    private val gson = Gson()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().build()
    }

    @AfterEach
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    private fun buildResponse(
        code: Int = 200,
        body: String = "{}",
        contentType: String = "application/json"
    ): Response {
        val responseBody = body.toResponseBody(contentType.toMediaType())
        return Response.Builder()
            .request(Request.Builder().url("http://test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(responseBody)
            .build()
    }

    @Test
    @DisplayName("toApiResult parses a successful JSON body")
    fun successful() {
        val response = buildResponse(body = "{\"data\":\"test\"}")

        val result = response.toApiResult<Map<String, String>>(gson)

        assertTrue(result is ApiResult.Success)
        assertEquals(200, (result as ApiResult.Success).statusCode)
    }

    @Test
    @DisplayName("toApiResult extracts OpenRouter error.message on a non-2xx body")
    fun errorWithStructuredMessage() {
        val response = buildResponse(code = 401, body = "{\"error\":{\"message\":\"API key invalid\"}}")

        val result = response.toApiResult<String>(gson)

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(401, error.statusCode)
        assertEquals("API key invalid", error.message)
    }

    @Test
    @DisplayName("toApiResult falls back to the raw body when the error is plain text")
    fun errorWithPlainTextBody() {
        val response = buildResponse(code = 500, body = "Internal Server Error")

        val result = response.toApiResult<String>(gson)

        val error = result as ApiResult.Error
        assertEquals(500, error.statusCode)
        assertEquals("Internal Server Error", error.message)
    }

    @Test
    @DisplayName("toApiResult reports parse errors on a 2xx body")
    fun jsonParseErrorOnSuccess() {
        val response = buildResponse(body = "not valid json")

        val result = response.toApiResult<Map<String, String>>(gson)

        val error = result as ApiResult.Error
        assertEquals(200, error.statusCode)
        assertTrue(error.message.contains("Failed to parse response"))
    }

    @Test
    @DisplayName("toApiResult falls back to raw body when the error body is unparseable JSON")
    fun jsonParseErrorOnError() {
        val response = buildResponse(code = 400, body = "definitely not json")

        val result = response.toApiResult<String>(gson)

        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
        assertEquals("definitely not json", error.message)
    }

    @Test
    @DisplayName("toApiResult uses response.message when an error body is blank")
    fun errorWithBlankBody() {
        val response = buildResponse(code = 404, body = "")

        val result = response.toApiResult<String>(gson)

        val error = result as ApiResult.Error
        assertEquals(404, error.statusCode)
        assertEquals("Error", error.message)
    }

    @Test
    @DisplayName("toApiResult trims leading whitespace before decoding")
    fun trimsLeadingWhitespace() {
        val response = buildResponse(body = "   {\"data\":\"test\"}")

        val result = response.toApiResult<Map<String, String>>(gson)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    @DisplayName("await resumes with the HTTP response on a successful call (onResponse)")
    fun awaitResumesOnResponse() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("pong"))
        val call = client.newCall(Request.Builder().url(server.url("/ping")).build())

        val response = runBlocking { call.await() }

        response.use {
            assertEquals(200, it.code)
            assertEquals("pong", it.body?.string())
        }
    }

    @Test
    @DisplayName("await propagates an IOException when the connection is dropped (onFailure)")
    fun awaitResumesWithExceptionOnFailure() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val call = client.newCall(Request.Builder().url(server.url("/boom")).build())

        // OkHttp resolves the callback on a real IO thread, so bridge back with
        // runBlocking; runTest's virtual clock does not drive that dispatcher here.
        assertThrows(IOException::class.java) {
            runBlocking { call.await() }
        }
    }

    @Test
    @DisplayName("await honours cancellation and invokes the cancel hook")
    fun awaitCancellationCancelsCall() = runTest {
        val call = client.newCall(Request.Builder().url(server.url("/never")).build())
        call.cancel()

        assertThrows(IOException::class.java) {
            runBlocking { call.await() }
        }
        assertTrue(call.isCanceled())
    }
}
