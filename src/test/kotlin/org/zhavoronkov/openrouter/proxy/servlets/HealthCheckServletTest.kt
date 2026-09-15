package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("HealthCheckServlet Tests")
class HealthCheckServletTest {

    private fun mockResponse(): Pair<HttpServletResponse, StringWriter> {
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(resp.writer).thenReturn(PrintWriter(writer))
        return resp to writer
    }

    @Test
    fun `doGet should return ok json on success`() {
        val servlet = HealthCheckServlet()
        val req = mock(HttpServletRequest::class.java)
        val (resp, writer) = mockResponse()
        org.mockito.Mockito.`when`(req.method).thenReturn("GET")

        servlet.service(req, resp)

        org.mockito.Mockito.verify(resp).status = HttpServletResponse.SC_OK
        org.mockito.Mockito.verify(resp).contentType = "application/json"
        assertTrue(writer.toString().contains("\"status\""))
    }

    @Test
    fun `doGet should handle IOException`() {
        val servlet = HealthCheckServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        org.mockito.Mockito.`when`(req.method).thenReturn("GET")
        // First writer access (in try) throws; subsequent access (in catch) succeeds.
        val errorWriter = StringWriter()
        org.mockito.Mockito.`when`(resp.writer)
            .thenThrow(java.io.IOException("write failed"))
            .thenReturn(PrintWriter(errorWriter))

        servlet.service(req, resp)

        org.mockito.Mockito.verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        assertTrue(errorWriter.toString().contains("Health check failed"))
    }

    @Test
    fun `doGet should handle IllegalStateException`() {
        val servlet = HealthCheckServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        org.mockito.Mockito.`when`(req.method).thenReturn("GET")
        val errorWriter = StringWriter()
        org.mockito.Mockito.`when`(resp.writer)
            .thenThrow(IllegalStateException("invalid state"))
            .thenReturn(PrintWriter(errorWriter))

        servlet.service(req, resp)

        org.mockito.Mockito.verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        assertTrue(errorWriter.toString().contains("Health check failed"))
    }

    @Test
    fun `doOptions should handle CORS preflight`() {
        val servlet = HealthCheckServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        org.mockito.Mockito.`when`(req.method).thenReturn("OPTIONS")

        servlet.service(req, resp)

        org.mockito.Mockito.verify(resp).setHeader("Access-Control-Allow-Origin", "*")
        org.mockito.Mockito.verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        org.mockito.Mockito.verify(resp).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        org.mockito.Mockito.verify(resp).status = HttpServletResponse.SC_OK
    }
}
