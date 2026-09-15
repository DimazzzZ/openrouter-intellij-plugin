package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("OpenAIBaseServlet Tests")
class OpenAIBaseServletTest {

    /** Concrete subclass exposing the protected members for direct testing. */
    private class TestServlet : OpenAIBaseServlet() {
        fun extractKey(resp: HttpServletResponse, req: HttpServletRequest) = validateAndExtractApiKey(resp, req)
        fun cors(resp: HttpServletResponse, methods: String = "GET, OPTIONS") = setCORSHeaders(resp, methods)
        fun authError(resp: HttpServletResponse) = sendAuthErrorResponse(resp)
        fun errorResponse(resp: HttpServletResponse, message: String, code: Int) = sendErrorResponse(resp, message, code)
        fun run(handler: () -> Unit, resp: HttpServletResponse, ctx: String) = handleRequest(handler, resp, ctx)
    }

    private fun responseWithWriter(): Pair<HttpServletResponse, StringWriter> {
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))
        return resp to writer
    }

    @Test
    fun `validateAndExtractApiKey returns key for valid Bearer header`() {
        val servlet = TestServlet()
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getHeader("Authorization")).thenReturn("Bearer sk-or-valid")
        val resp = mock(HttpServletResponse::class.java)

        assertEquals("sk-or-valid", servlet.extractKey(resp, req))
    }

    @Test
    fun `validateAndExtractApiKey returns null when header missing`() {
        val servlet = TestServlet()
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getHeader("Authorization")).thenReturn(null)
        val (resp, writer) = responseWithWriter()

        assertNull(servlet.extractKey(resp, req))
        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
        assertTrue(writer.toString().isNotEmpty())
    }

    @Test
    fun `validateAndExtractApiKey returns null when scheme not Bearer`() {
        val servlet = TestServlet()
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getHeader("Authorization")).thenReturn("Basic abc")
        val (resp, _) = responseWithWriter()

        assertNull(servlet.extractKey(resp, req))
        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    @Test
    fun `validateAndExtractApiKey returns null when key blank`() {
        val servlet = TestServlet()
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getHeader("Authorization")).thenReturn("Bearer    ")
        val (resp, _) = responseWithWriter()

        assertNull(servlet.extractKey(resp, req))
        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    @Test
    fun `setCORSHeaders sets all headers`() {
        val servlet = TestServlet()
        val resp = mock(HttpServletResponse::class.java)

        servlet.cors(resp, "POST, OPTIONS")

        verify(resp).setHeader("Access-Control-Allow-Origin", "*")
        verify(resp).setHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
        verify(resp).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    @Test
    fun `handleOptionsRequest sets CORS and 200`() {
        val servlet = TestServlet()
        val resp = mock(HttpServletResponse::class.java)

        servlet.handleOptionsRequest(resp, "PUT, OPTIONS")

        verify(resp).setHeader("Access-Control-Allow-Methods", "PUT, OPTIONS")
        verify(resp).status = HttpServletResponse.SC_OK
    }

    @Test
    fun `setCORSHeaders uses default allowed methods`() {
        val servlet = TestServlet()
        val resp = mock(HttpServletResponse::class.java)
        servlet.cors(resp)
        verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
    }

    @Test
    fun `handleOptionsRequest uses default allowed methods`() {
        val servlet = TestServlet()
        val resp = mock(HttpServletResponse::class.java)
        servlet.handleOptionsRequest(resp)
        verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        verify(resp).status = HttpServletResponse.SC_OK
    }

    @Test
    fun `sendAuthErrorResponse writes 401 body`() {
        val servlet = TestServlet()
        val (resp, writer) = responseWithWriter()

        servlet.authError(resp)

        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
        assertTrue(writer.toString().isNotEmpty())
    }

    @Test
    fun `sendErrorResponse maps each status code to a type`() {
        val servlet = TestServlet()
        val codes = listOf(
            HttpServletResponse.SC_BAD_REQUEST,
            HttpServletResponse.SC_REQUEST_TIMEOUT,
            HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
            HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        )
        for (code in codes) {
            val (resp, writer) = responseWithWriter()
            servlet.errorResponse(resp, "msg-" + code, code)
            verify(resp).status = code
            assertTrue(writer.toString().isNotEmpty())
        }
    }

    @Test
    fun `handleRequest runs handler on success`() {
        val servlet = TestServlet()
        val resp = mock(HttpServletResponse::class.java)
        var ran = false

        servlet.run({ ran = true }, resp, "ctx")

        assertTrue(ran)
    }

    @Test
    fun `handleRequest maps IOException to 503`() {
        val servlet = TestServlet()
        val (resp, _) = responseWithWriter()

        servlet.run({ throw IOException("net") }, resp, "ctx")

        verify(resp).status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
    }

    @Test
    fun `handleRequest maps JsonSyntaxException to 400`() {
        val servlet = TestServlet()
        val (resp, _) = responseWithWriter()

        servlet.run({ throw JsonSyntaxException("bad json") }, resp, "ctx")

        verify(resp).status = HttpServletResponse.SC_BAD_REQUEST
    }

    @Test
    fun `handleRequest maps IllegalStateException to 500`() {
        val servlet = TestServlet()
        val (resp, _) = responseWithWriter()

        servlet.run({ throw IllegalStateException("state") }, resp, "ctx")

        verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }

    @Test
    fun `handleRequest maps IllegalArgumentException to 400`() {
        val servlet = TestServlet()
        val (resp, _) = responseWithWriter()

        servlet.run({ throw IllegalArgumentException("arg") }, resp, "ctx")

        verify(resp).status = HttpServletResponse.SC_BAD_REQUEST
    }
}
