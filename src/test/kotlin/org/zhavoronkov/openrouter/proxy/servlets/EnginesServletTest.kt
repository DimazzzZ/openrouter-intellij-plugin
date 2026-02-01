package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("EnginesServlet Tests")
class EnginesServletTest {

    @Test
    fun `doGet should return engines list`() {
        val servlet = EnginesServlet()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(req.getHeader("Authorization")).thenReturn("Bearer sk-test")
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        `when`(req.method).thenReturn("GET")

        servlet.service(req, resp)

        assertTrue(writer.toString().contains("gpt-4"))
    }
}
