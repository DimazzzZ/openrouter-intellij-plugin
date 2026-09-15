package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
import org.zhavoronkov.openrouter.services.OpenRouterService
import java.io.PrintWriter
import java.io.StringWriter

@DisplayName("ModelsServlet Tests")
class ModelsServletTest {

    @BeforeEach
    fun resetStaticCache() {
        // ModelsServlet caches "all" results in a static companion map with a 15-min TTL.
        // Reset it between tests so error/exception fallbacks are exercised deterministically.
        val companion = ModelsServlet::class.java
        val cacheField = companion.getDeclaredField("modelsCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (cacheField.get(null) as MutableMap<String, *>).clear()
        val tsField = companion.getDeclaredField("cacheTimestamp").apply { isAccessible = true }
        (tsField.get(null) as java.util.concurrent.atomic.AtomicLong).set(0)
    }

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

    @Test
    fun `doGet all mode fetches from OpenRouterService and caches`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        val models = OpenRouterModelsResponse(
            data = listOf(
                OpenRouterModelInfo(id = "openai/gpt-4o", name = "GPT-4o", created = 1L),
                OpenRouterModelInfo(id = "anthropic/claude-3.5-sonnet", name = "Claude", created = 2L),
                OpenRouterModelInfo(id = "x-ai/grok:free", name = "Grok", created = 3L),
                OpenRouterModelInfo(id = "bare-model", name = "Bare", created = 4L)
            )
        )
        `when`(openRouterService.getModels()).thenReturn(ApiResult.Success(models, 200))

        val servlet = ModelsServlet(openRouterService, { emptyList() }, { emptyList() })
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("all")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        val body = writer.toString()
        assertTrue(body.contains("openai/gpt-4o"))
        assertTrue(body.contains("x-ai/grok:free"))
        assertTrue(body.contains("bare-model"))
    }

    @Test
    fun `doGet all mode falls back to curated on ApiResult Error`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        `when`(openRouterService.getModels()).thenReturn(ApiResult.Error("boom", 500))

        val servlet = ModelsServlet(openRouterService, { listOf("openai/gpt-4o-mini") }, { emptyList() })
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("all")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertTrue(writer.toString().contains("openai/gpt-4o-mini"))
    }

    @Test
    fun `doGet search mode with blank search returns curated`() {
        val openRouterService = mock(OpenRouterService::class.java)
        val servlet = ModelsServlet(openRouterService, { listOf("openai/gpt-4") }, { emptyList() })
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("search")
        `when`(req.getParameter("search")).thenReturn(null)
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.getHeader("Authorization")).thenReturn("Bearer sk-preview-1234567890abcdef")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertTrue(writer.toString().contains("openai/gpt-4"))
    }

    @Test
    fun `doGet search mode with keyword filters by id`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        val models = OpenRouterModelsResponse(
            data = listOf(
                OpenRouterModelInfo(id = "openai/gpt-4o", name = "GPT-4o", created = 1L),
                OpenRouterModelInfo(id = "anthropic/claude", name = "Claude", created = 2L)
            )
        )
        `when`(openRouterService.getModels()).thenReturn(ApiResult.Success(models, 200))
        val servlet = ModelsServlet(openRouterService, { emptyList() }, { emptyList() })

        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("search")
        `when`(req.getParameter("search")).thenReturn("claude")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertTrue(writer.toString().contains("anthropic/claude"))
    }

    @Test
    fun `doGet all mode filters by provider and limit`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        val models = OpenRouterModelsResponse(
            data = listOf(
                OpenRouterModelInfo(id = "openai/gpt-4o", name = "GPT-4o", created = 1L),
                OpenRouterModelInfo(id = "openai/gpt-4o-mini", name = "GPT-4o Mini", created = 2L),
                OpenRouterModelInfo(id = "anthropic/claude", name = "Claude", created = 3L)
            )
        )
        `when`(openRouterService.getModels()).thenReturn(ApiResult.Success(models, 200))
        val servlet = ModelsServlet(openRouterService, { emptyList() }, { emptyList() })

        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("all")
        `when`(req.getParameter("provider")).thenReturn("openai")
        `when`(req.getParameter("limit")).thenReturn("1")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertFalse(writer.toString().contains("anthropic/claude"))
    }

    @Test
    fun `doOptions sets CORS headers and 200`() {
        val servlet = ModelsServlet(mock(OpenRouterService::class.java), { emptyList() }, { emptyList() })
        val req = mock(HttpServletRequest::class.java)
        `when`(req.method).thenReturn("OPTIONS")
        val resp = mock(HttpServletResponse::class.java)

        servlet.service(req, resp)

        verify(resp).setHeader("Access-Control-Allow-Origin", "*")
        verify(resp).setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        verify(resp).status = HttpServletResponse.SC_OK
    }

    @Test
    fun `doGet falls back to curated on IllegalStateException from service getModels`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        `when`(openRouterService.getModels()).thenThrow(IllegalStateException("boom"))
        val servlet = ModelsServlet(openRouterService, { listOf("openai/gpt-4o") }, { emptyList() })

        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("all")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())

        val resp = mock(HttpServletResponse::class.java)
        val writer = StringWriter()
        `when`(resp.writer).thenReturn(PrintWriter(writer))

        servlet.doGet(req, resp)

        assertTrue(writer.toString().contains("openai/gpt-4o"))
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
            assertTrue(
                result.contains("anthropic/claude-3.5-sonnet"),
                "Response should contain default favorite claude"
            )
        }

        @Test
        fun `doGet returns both presets and defaults when no favorites`() {
            val presets = listOf("openrouter/auto")
            val result = executeServlet(presets = presets, favorites = listOf())
            assertTrue(result.contains("openrouter/auto"), "Response should contain preset")
            assertTrue(result.contains("openai/gpt-4o"), "Response should contain default favorite")
        }
    }

    @Test
    fun `doGet all mode second call hits the cache`() = kotlinx.coroutines.runBlocking {
        val openRouterService = mock(OpenRouterService::class.java)
        val models = OpenRouterModelsResponse(
            data = listOf(OpenRouterModelInfo(id = "openai/gpt-4o", name = "GPT-4o", created = 1L))
        )
        `when`(openRouterService.getModels()).thenReturn(ApiResult.Success(models, 200))
        val servlet = ModelsServlet(openRouterService, { emptyList() }, { emptyList() })

        fun makeReq(): HttpServletRequest {
            val req = mock(HttpServletRequest::class.java)
            `when`(req.getParameter("mode")).thenReturn("all")
            `when`(req.getHeader("User-Agent")).thenReturn("test")
            `when`(req.requestURI).thenReturn("/models")
            `when`(req.remoteAddr).thenReturn("127.0.0.1")
            `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())
            return req
        }
        fun makeResp(): Pair<HttpServletResponse, StringWriter> {
            val resp = mock(HttpServletResponse::class.java)
            val writer = StringWriter()
            `when`(resp.writer).thenReturn(PrintWriter(writer))
            return resp to writer
        }

        val (resp1, w1) = makeResp()
        servlet.doGet(makeReq(), resp1)
        val (resp2, w2) = makeResp()
        servlet.doGet(makeReq(), resp2)

        // Second call served from cache; getModels invoked exactly once.
        org.mockito.Mockito.verify(openRouterService, org.mockito.Mockito.times(1)).getModels()
        assertTrue(w2.toString().contains("openai/gpt-4o"))
    }

    @Test
    fun `doGet outer catch handles IOException from writer`() {
        val servlet = ModelsServlet(mock(OpenRouterService::class.java), { listOf("openai/gpt-4o") }, { emptyList() })
        val req = mock(HttpServletRequest::class.java)
        `when`(req.getParameter("mode")).thenReturn("curated")
        `when`(req.getHeader("User-Agent")).thenReturn("test")
        `when`(req.requestURI).thenReturn("/models")
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.headerNames).thenReturn(java.util.Collections.emptyEnumeration())
        val resp = mock(HttpServletResponse::class.java)
        val errorWriter = StringWriter()
        `when`(resp.writer)
            .thenThrow(java.io.IOException("write boom"))
            .thenReturn(PrintWriter(errorWriter))

        servlet.doGet(req, resp)

        org.mockito.Mockito.verify(resp).status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }
}
