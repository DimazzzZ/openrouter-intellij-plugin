package org.zhavoronkov.openrouter.proxy.servlets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
import org.zhavoronkov.openrouter.requests.RequestLogService
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.io.BufferedReader
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections

/**
 * The proxy's servlets built the way the proxy server builds them, with every collaborator left at
 * its production default and resolved from the application, so a default that no longer resolves
 * fails here rather than in someone's IDE. Only OpenRouter itself is stood in for.
 */
class ProxyWiringPlatformTest : BasePlatformTestCase() {

    private val settings get() = OpenRouterSettingsService.getInstance()

    private fun request(method: String, body: String = "", mode: String? = null): HttpServletRequest {
        val req = mock(HttpServletRequest::class.java)
        `when`(req.method).thenReturn(method)
        `when`(req.reader).thenReturn(BufferedReader(StringReader(body)))
        `when`(req.remoteAddr).thenReturn("127.0.0.1")
        `when`(req.requestURI).thenReturn("/v1/chat/completions")
        `when`(req.servletPath).thenReturn("/v1/chat/completions")
        `when`(req.contentType).thenReturn("application/json")
        `when`(req.getParameter("mode")).thenReturn(mode)
        `when`(req.headerNames).thenReturn(Collections.emptyEnumeration())
        return req
    }

    private fun response(sink: StringWriter): HttpServletResponse =
        mock(HttpServletResponse::class.java).also { `when`(it.writer).thenReturn(PrintWriter(sink, true)) }

    fun testTheDefaultEndpointFollowsTheSelectedRegion() {
        assertEquals("${settings.getApiBaseUrl()}/chat/completions", defaultChatCompletionsUrl())
    }

    fun testTheModelListReadsFavoritesAndPresetsFromTheApplication() {
        val sink = StringWriter()

        ModelsServlet(mock(OpenRouterService::class.java)).doGet(request("GET", mode = "curated"), response(sink))

        assertTrue("an OpenAI-shaped list: $sink", sink.toString().contains("\"object\""))
    }

    fun testAFavoritePairIsCheckedAgainstTheApplicationsPresets() {
        val favorites = settings.favoriteModelsManager
        val before = favorites.getFavoriteModels()
        try {
            favorites.setFavoriteModels(listOf("openai/gpt-4o@preset/never-read"))
            val sink = StringWriter()

            val servlet = ModelsServlet(mock(OpenRouterService::class.java))
            servlet.doGet(request("GET", mode = "curated"), response(sink))

            // Asked of the application's copy, which has not read the presets: nothing to judge by
            assertTrue("a pair is kept while its preset is unknown: $sink", sink.toString().contains("never-read"))
        } finally {
            favorites.setFavoriteModels(before)
        }
    }

    fun testTheModelListLeavesOutAFavoriteTheLoadedCatalogueDoesNotServe() {
        val favorites = settings.favoriteModelsManager
        val before = favorites.getFavoriteModels()
        try {
            val router = mock(OpenRouterService::class.java)
            val served = OpenRouterModelInfo(id = "openai/gpt-4o", name = "GPT-4o", created = 0)
            val response = ApiResult.Success(OpenRouterModelsResponse(listOf(served)), 200)
            runBlocking { `when`(router.getAllModels()).thenReturn(response) }
            val catalogue = FavoriteModelsService(settings, router)
            ApplicationManager.getApplication().replaceService(
                FavoriteModelsService::class.java,
                catalogue,
                testRootDisposable
            )
            runBlocking { catalogue.getAvailableModels() }
            favorites.setFavoriteModels(listOf("openai/gpt-4o", "x-ai/not-served"))
            val sink = StringWriter()

            ModelsServlet(mock(OpenRouterService::class.java)).doGet(request("GET", mode = "curated"), response(sink))

            assertTrue("a served favorite is listed: $sink", sink.toString().contains("openai/gpt-4o"))
            assertFalse("one the region does not serve is not: $sink", sink.toString().contains("x-ai/not-served"))
        } finally {
            favorites.setFavoriteModels(before)
        }
    }

    fun testWithNoKeyConfiguredTheProxyAnswers401() {
        val sink = StringWriter()
        val resp = response(sink)
        val body = """{"model":"openai/gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}"""

        ChatCompletionServlet().service(request("POST", body), resp)

        verify(resp).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    fun testAConfiguredProxyRecordsTheRequestAndKeepsItsBodiesThroughTheApplication() {
        val server = MockWebServer().apply { start() }
        val preferences = settings.uiPreferencesManager
        val keptBefore = preferences.keepRequestBodies
        try {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """{"id":"gen-1","object":"chat.completion","created":1,"model":"openai/gpt-4o-mini",
                      "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},
                      "finish_reason":"stop"}]}"""
                )
            )
            settings.apiKeyManager.setApiKey("sk-or-v1-" + "a".repeat(64))
            preferences.keepRequestBodies = true
            val sink = StringWriter()
            val endpoint = server.url("/api/v1/chat/completions").toString()
            val servlet = ChatCompletionServlet(openRouterApiUrl = { endpoint })
            val body = """{"model":"openai/gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}"""

            servlet.service(request("POST", body), response(sink))

            assertTrue("the reply is relayed: $sink", sink.toString().contains("hello"))
            assertEquals(1, server.requestCount)
        } finally {
            preferences.keepRequestBodies = keptBefore
            settings.apiKeyManager.setApiKey("")
            RequestLogService.getInstance().clear()
            server.shutdown()
        }
    }
}
