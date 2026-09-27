package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension

@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Usage Endpoint Tests")
class OpenRouterServiceUsageEndpointsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")
        // Both are stubbed because getCredits() prefers the Management Key and falls back to the
        // API key - see the two tests pinning that order.
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")

        service = OpenRouterService(
            gson = Gson(),
            settingsService = mockSettingsService,
            baseUrlOverride = mockWebServer.url("/api/v1").toString().removeSuffix("/")
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getCredits should parse response`() = runBlocking {
        val response = """
            {
              "data": {
                "total_credits": 100.0,
                "total_usage": 25.0
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.getCredits()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(100.0, success.data.data.totalCredits)
        assertEquals(25.0, success.data.data.totalUsage)
    }

    @Test
    fun `getActivity should parse response`() = runBlocking {
        val response = """
            {
              "data": [
                {
                  "date": "2025-01-01",
                  "model": "openai/gpt-4",
                  "usage": 1.25,
                  "requests": 2,
                  "prompt_tokens": 10,
                  "completion_tokens": 20,
                  "reasoning_tokens": 0
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
        )

        val result = service.getActivity()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(1, success.data.data.size)
        assertEquals("openai/gpt-4", success.data.data.first().model)
    }

    // --- Defect A: a non-2xx body must surface the server's own sentence, never the raw JSON ----

    @Test
    fun `getCredits should surface the server's error message not the raw JSON body on a 403`() = runBlocking {
        val body = """{"error":{"message":"Only management keys can fetch credits for an account","code":403}}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val result = service.getCredits()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals("Only management keys can fetch credits for an account", error.message)
        assertFalse(
            error.message.contains("{"),
            "the raw JSON body must never reach the user-visible message: ${error.message}"
        )
    }

    @Test
    fun `getActivity should surface the server's error message not the raw JSON body on a 403`() = runBlocking {
        val body = """{"error":{"message":"Only management keys can fetch activity for an account","code":403}}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )

        val result = service.getActivity()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals("Only management keys can fetch activity for an account", error.message)
        assertFalse(
            error.message.contains("{"),
            "the raw JSON body must never reach the user-visible message: ${error.message}"
        )
    }

    @Test
    @DisplayName("getCredits authenticates with the Management Key when one is configured")
    fun `getCredits prefers the management key`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"total_credits":10.0,"total_usage":1.0}}""")
        )

        service.getCredits()

        // Regression (2026-09-25): a live account answered 200 for one ordinary API key and 403
        // "Only management keys can fetch credits for an account" for another, so which ordinary
        // keys qualify is not knowable here. Sending the API key first broke the balance for every
        // existing user who had configured a Management Key - the key that always works.
        assertEquals(
            "Bearer pk-test",
            mockWebServer.takeRequest().getHeader("Authorization"),
            "credits must authenticate with the Management Key when one is configured"
        )
    }

    @Test
    @DisplayName("getCredits does not fall back to the API key when no Management Key is set")
    fun `getCredits does not fall back to the api key`() = runBlocking {
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("")

        val result = service.getCredits()

        // A fallback to the ordinary API key was shipped and then removed. /credits is
        // Management-Key-only; a dashboard-created key was measured answering 200, but a key
        // minted through POST /keys answers 403 and the two cannot be told apart through the API.
        // A fallback that works for a minority, silently, makes the same plugin behave differently
        // for two users who configured it identically - so it must not reach the network at all.
        assertEquals(0, mockWebServer.requestCount, "no request may be made without a Management Key")
        assertTrue(result is ApiResult.Error, "expected an error, got $result")
    }
}
