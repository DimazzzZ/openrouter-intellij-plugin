package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("ModelsServlet Tests")
class ModelsServletTest {

    private fun createDoGetRequest(mode: String = "curated"): Pair<HttpServletRequest, StringWriter> {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn(mode)
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        return Pair(req, writer)
    }

    private fun executeServlet(
        favorites: List<String> = listOf("openai/gpt-4"),
        presets: List<String> = listOf(),
        mode: String = "curated"
    ): String {
        val openRouterService = mock(OpenRouterService::class.java)
        val servlet = ModelsServlet(openRouterService, { favorites }, { presets })
        val (req, writer) = createDoGetRequest(mode)
        val resp = mock(HttpServletResponse::class.java)
        `when`(resp.writer).thenReturn(PrintWriter(writer))
        servlet.doGet(req, resp)
        return writer.toString()
    }

    @Test
    fun `doGet returns curated favorites`() {
        val result = executeServlet(favorites = listOf("openai/gpt-4"))
        assertTrue(result.contains("openai/gpt-4"), "Response should contain the favorite model")
    }

    @Nested
    @DisplayName("Preset support")
    inner class PresetTests {

        @Test
        fun `doGet includes built-in presets in response`() {
            val presets = listOf("openrouter/auto", "openrouter/free")
            val result = executeServlet(presets = presets, favorites = listOf("openai/gpt-4"))
            assertTrue(result.contains("openrouter/auto"), "Response should contain openrouter/auto preset")
            assertTrue(result.contains("openrouter/free"), "Response should contain openrouter/free preset")
        }

        @Test
        fun `doGet includes custom presets in response`() {
            val presets = listOf("@preset/email-copywriter")
            val result = executeServlet(presets = presets, favorites = listOf("openai/gpt-4"))
            assertTrue(result.contains("@preset/email-copywriter"), "Response should contain custom preset")
        }

        @Test
        fun `doGet returns presets before favorites`() {
            val presets = listOf("openrouter/auto")
            val favorites = listOf("openai/gpt-4")
            val result = executeServlet(presets = presets, favorites = favorites)
            val presetIndex = result.indexOf("openrouter/auto")
            val favoriteIndex = result.indexOf("openai/gpt-4")
            assertTrue(presetIndex < favoriteIndex, "Preset should appear before favorite in response")
        }

        @Test
        fun `doGet returns only favorites when no presets configured`() {
            val result = executeServlet(presets = listOf(), favorites = listOf("openai/gpt-4"))
            assertTrue(result.contains("openai/gpt-4"), "Response should contain favorite model")
            assertFalse(result.contains("openrouter/auto"), "Response should not contain preset when none configured")
        }

        @Test
        fun `doGet returns default favorites when no favorites and no presets`() {
            val result = executeServlet(presets = listOf(), favorites = listOf())
            assertTrue(result.contains("openai/gpt-4o"), "Response should contain default favorite gpt-4o")
            assertTrue(result.contains("anthropic/claude-3.5-sonnet"), "Response should contain default favorite claude")
        }

        @Test
        fun `doGet returns both presets and defaults when no favorites`() {
            val presets = listOf("openrouter/auto")
            val result = executeServlet(presets = presets, favorites = listOf())
            assertTrue(result.contains("openrouter/auto"), "Response should contain preset")
            assertTrue(result.contains("openai/gpt-4o"), "Response should contain default favorite")
        }
    }
}
