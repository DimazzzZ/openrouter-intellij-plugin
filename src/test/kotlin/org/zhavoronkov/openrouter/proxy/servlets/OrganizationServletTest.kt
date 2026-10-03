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

@DisplayName("OrganizationServlet Tests")
class OrganizationServletTest {

    @Test
    fun `doGet should return organization info`() {
        val servlet = OrganizationServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(req.getHeader("Authorization")).thenReturn("Bearer sk-test")
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        `when`(req.method).thenReturn("GET")

        servlet.service(req, resp)

        assertTrue(writer.toString().contains("OpenRouter Proxy"))
    }

    @Test
    fun `a preflight request is answered with the methods it allows`() {
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        `when`(req.method).thenReturn("OPTIONS")

        OrganizationServlet().service(req, resp)

        verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        verify(resp).status = HttpServletResponse.SC_OK
    }
}
