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

    @Test
    fun `doGet should return ok json`() {
        val servlet = HealthCheckServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        org.mockito.Mockito.`when`(resp.writer).thenReturn(PrintWriter(writer))

        org.mockito.Mockito.`when`(req.method).thenReturn("GET")

        servlet.service(req, resp)

        org.mockito.Mockito.verify(resp).setStatus(HttpServletResponse.SC_OK)
        assertTrue(writer.toString().contains("\"status\""))
    }
}
