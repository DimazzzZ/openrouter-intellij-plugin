package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("ModelsServlet Tests")
class ModelsServletTest {

    @Test
    fun `doGet returns curated favorites`() {
        val openRouterService = mock(OpenRouterService::class.java)
        val favoritesProvider = { listOf("openai/gpt-4") }
        val servlet = ModelsServlet(openRouterService, favoritesProvider)

        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(req.getParameter("mode")).thenReturn("curated")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertTrue(writer.toString().contains("openai/gpt-4"))
    }
}
