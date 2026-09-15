package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("RootServlet Tests")
class RootServletTest {

    @Test
    fun `doGet should return api info`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(req.requestURI).thenReturn("/")
        `when`(req.servletPath).thenReturn("/")
        `when`(req.pathInfo).thenReturn(null)
        `when`(resp.writer).thenReturn(PrintWriter(writer))
        `when`(req.method).thenReturn("GET")

        servlet.service(req, resp)

        val body = writer.toString()
        assertTrue(body.contains("OpenRouter AI Assistant Proxy"))
        assertTrue(body.contains("/v1/chat/completions"))
        verify(resp).setHeader("Access-Control-Allow-Origin", "*")
    }

    @Test
    fun `doGet should handle IOException`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.requestURI).thenReturn("/")
        `when`(req.servletPath).thenReturn("/")
        `when`(req.pathInfo).thenReturn(null)
        `when`(req.method).thenReturn("GET")
        val errorWriter = StringWriter()
        `when`(resp.writer)
            .thenThrow(java.io.IOException("boom"))
            .thenReturn(PrintWriter(errorWriter))

        servlet.service(req, resp)

        verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        assertTrue(errorWriter.toString().contains("Internal server error"))
    }

    @Test
    fun `doGet should handle IllegalStateException`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.requestURI).thenReturn("/")
        `when`(req.servletPath).thenReturn("/")
        `when`(req.pathInfo).thenReturn(null)
        `when`(req.method).thenReturn("GET")
        val errorWriter = StringWriter()
        `when`(resp.writer)
            .thenThrow(IllegalStateException("bad state"))
            .thenReturn(PrintWriter(errorWriter))

        servlet.service(req, resp)

        verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        assertTrue(errorWriter.toString().contains("Internal server error"))
    }

    @Test
    fun `doOptions should handle models path via requestURI`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.method).thenReturn("OPTIONS")

        servlet.service(req, resp)

        verify(resp).setHeader("Access-Control-Max-Age", "3600")
        verify(resp).status = HttpServletResponse.SC_OK
    }

    @Test
    fun `doOptions should handle models path via servletPath`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.requestURI).thenReturn("/something")
        `when`(req.servletPath).thenReturn("/models")
        `when`(req.method).thenReturn("OPTIONS")

        servlet.service(req, resp)

        verify(resp).setHeader("Access-Control-Max-Age", "3600")
        verify(resp).status = HttpServletResponse.SC_OK
    }

    @Test
    fun `doOptions should handle CORS for other paths`() {
        val servlet = RootServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.requestURI).thenReturn("/")
        `when`(req.servletPath).thenReturn("/")
        `when`(req.method).thenReturn("OPTIONS")

        servlet.service(req, resp)

        verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        verify(resp).status = HttpServletResponse.SC_OK
    }
}
