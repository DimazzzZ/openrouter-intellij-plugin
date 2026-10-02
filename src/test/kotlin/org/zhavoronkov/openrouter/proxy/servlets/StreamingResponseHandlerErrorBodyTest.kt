package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Error bodies whose shape is not `{"error":{"message":"..."}}`: a stream that carries one instead
 * of chunks, a chunk whose error is not an object, and a refusal answered before any stream. Each
 * must still end in an error chunk the client can show.
 */
@DisplayName("StreamingResponseHandler error bodies of another shape")
class StreamingResponseHandlerErrorBodyTest {

    private fun response(code: Int, body: String?): Response = Response.Builder()
        .request(Request.Builder().url("http://localhost").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("ERR")
        .apply { body?.let { body(it.toResponseBody("application/json".toMediaType())) } }
        .build()

    private fun streamed(body: String): String {
        val out = StringWriter()
        StreamingResponseHandler().streamResponseToClient(response(200, body), PrintWriter(out), "shape")
        return out.toString()
    }

    private fun refused(code: Int, body: String?): Pair<HttpServletResponse, String> {
        val resp = mock(HttpServletResponse::class.java)
        val out = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(out))
        StreamingResponseHandler().handleStreamingErrorResponse(
            StreamingResponseHandler.StreamingErrorContext(response(code, body), resp, "refused")
        )
        return resp to out.toString()
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["""{"error":"bare"}""", """{"error":{"message":7}}""", """{"error":{}}"""])
    @DisplayName("a JSON body sent in place of a stream, without a readable message, gets the generic explanation")
    fun nonSseJsonOfAnotherShape(body: String) {
        val out = streamed(body)

        assertTrue(out.contains("Unexpected response format from model"), "got: $out")
        assertTrue(out.contains("data: [DONE]"), "the stream is closed: $out")
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["data: {\"error\":7}", "data: {\"error\":[\"overloaded\"]}"])
    @DisplayName("a chunk whose error is neither a message nor an object is still told as an error")
    fun errorChunkOfAnotherShape(line: String) {
        val out = streamed("$line\n\ndata: [DONE]\n\n")

        assertTrue(out.contains("Unknown error from model"), "got: $out")
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            """401 | {"detail":"no error field"} | Authentication failed: | no error field""",
            """418 | {"error":"bare"}             | bare                   | error""",
            """402 | {"error":{"message":7}}      | Insufficient credits:  | message""",
            """429 | ''                           | Rate limit exceeded:   | Rate"""
        ]
    )
    @DisplayName("a refusal whose body carries no readable message quotes the body itself")
    fun refusalWithoutMessage(code: Int, body: String, prefix: String, fragment: String) {
        val (resp, out) = refused(code, body)

        verify(resp).status = code
        assertTrue(out.contains(prefix), "expected '$prefix' in: $out")
        assertTrue(out.contains(fragment), "expected '$fragment' in: $out")
        assertTrue(out.contains("data: [DONE]"), "the stream is closed: $out")
    }

    @Test
    @DisplayName("a refusal with no body at all is told by its status")
    fun refusalWithoutBody() {
        val (resp, out) = refused(503, null)

        verify(resp).status = 503
        assertTrue(out.contains("OpenRouter service is temporarily unavailable"), "got: $out")
    }
}
