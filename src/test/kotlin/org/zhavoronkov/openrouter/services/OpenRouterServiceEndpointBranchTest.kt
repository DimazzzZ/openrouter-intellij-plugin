package org.zhavoronkov.openrouter.services

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.testing.OkHttpLeakSafeExtension

/**
 * Branch coverage for the provisioning-key-authenticated and public endpoints of
 * [OpenRouterService]. Each endpoint has up to four arms: blank-key guard (where
 * present), successful parse, JSON parse error on a 2xx body, and a non-2xx HTTP
 * error. Existing per-endpoint tests exercise mostly the happy path; this class fills
 * the error / parse / blank-key arms flagged by Kover.
 */
@Tag("functional")
@ExtendWith(OkHttpLeakSafeExtension::class)
@DisplayName("OpenRouter Service Endpoint Branch Tests")
class OpenRouterServiceEndpointBranchTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockApiKeyManager: org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager
    private lateinit var service: OpenRouterService

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")
        mockApiKeyManager = mock(org.zhavoronkov.openrouter.services.settings.ApiKeySettingsManager::class.java)
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn("sk-or-test")
        `when`(mockSettingsService.apiKeyManager).thenReturn(mockApiKeyManager)
        service = OpenRouterService(
            gson = Gson(),
            settingsService = mockSettingsService,
            baseUrlOverride = mockWebServer.url("/api/v1").toString().removeSuffix("/")
        )
    }

    @AfterEach
    fun tearDown() {
        service.dispose()
        mockWebServer.shutdown()
    }

    private fun enqueue(code: Int, body: String) {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body)
        )
    }

    private fun blankKey() {
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("")
    }

    @Test
    @DisplayName("getApiKeysList returns Error when provisioning key is blank, without a request")
    fun apiKeysListBlankKey() = runBlocking {
        val result = service.getApiKeysList("")
        assertTrue(result is ApiResult.Error)
        assertEquals("Provisioning key is required", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("getApiKeysList maps a non-2xx to an Error carrying the status code")
    fun apiKeysListHttpError() = runBlocking {
        enqueue(401, """{"error":{"message":"unauthorized"}}""")
        val result = service.getApiKeysList("pk-test")
        assertTrue(result is ApiResult.Error)
        assertEquals(401, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("getApiKeysList parses a 2xx list body into Success")
    fun apiKeysListSuccess() = runBlocking {
        enqueue(200, """{"data":[]}""")
        val result = service.getApiKeysList("pk-test")
        assertTrue(result is ApiResult.Success)
    }

    @Test
    @DisplayName("getQuotaInfo returns Error when no provisioning key is configured")
    fun quotaInfoBlankKey() = runBlocking {
        blankKey()
        val result = service.getQuotaInfo()
        assertTrue(result is ApiResult.Error)
        assertEquals("No provisioning key configured", (result as ApiResult.Error).message)
    }

    @Test
    @DisplayName("getQuotaInfo propagates an underlying keys-list Error")
    fun quotaInfoPropagatesError() = runBlocking {
        enqueue(500, """{"error":"boom"}""")
        val result = service.getQuotaInfo()
        assertTrue(result is ApiResult.Error)
    }

    @Test
    @DisplayName("getQuotaInfo sums enabled keys and treats zero limit as unlimited")
    fun quotaInfoSuccessUnlimited() = runBlocking {
        enqueue(
            200,
            """{"data":[{"name":"a","label":"a","limit":null,"usage":3.0,"disabled":false,"created_at":"t","updated_at":null,"hash":"h"},{"name":"b","label":"b","limit":10.0,"usage":2.0,"disabled":true,"created_at":"t","updated_at":null,"hash":"h2"}]}"""
        )
        val result = service.getQuotaInfo()
        assertTrue(result is ApiResult.Success)
        val quota = (result as ApiResult.Success).data
        assertEquals(3.0, quota.used)
        assertEquals(0.0, quota.total)
        assertEquals(Double.MAX_VALUE, quota.remaining)
    }

    @Test
    @DisplayName("getQuotaInfo computes remaining when a positive limit exists")
    fun quotaInfoSuccessWithLimit() = runBlocking {
        enqueue(
            200,
            """{"data":[{"name":"a","label":"a","limit":10.0,"usage":4.0,"disabled":false,"created_at":"t","updated_at":null,"hash":"h"}]}"""
        )
        val result = service.getQuotaInfo()
        assertTrue(result is ApiResult.Success)
        val quota = (result as ApiResult.Success).data
        assertEquals(10.0, quota.total)
        assertEquals(4.0, quota.used)
        assertEquals(6.0, quota.remaining)
    }

    @Test
    @DisplayName("getKeyInfo summarizes enabled keys with a limit into Success")
    fun keyInfoSuccessWithLimit() = runBlocking {
        enqueue(
            200,
            """{"data":[{"name":"a","label":"a","limit":10.0,"usage":4.0,"disabled":false,"created_at":"t","updated_at":null,"hash":"h"}]}"""
        )
        val result = service.getKeyInfo()
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data.data
        assertEquals(4.0, data.usage)
        assertEquals(10.0, data.limit)
    }

    @Test
    @DisplayName("getKeyInfo reports a null limit when the total limit is zero")
    fun keyInfoSuccessNoLimit() = runBlocking {
        enqueue(
            200,
            """{"data":[{"name":"a","label":"a","limit":null,"usage":1.0,"disabled":false,"created_at":"t","updated_at":null,"hash":"h"}]}"""
        )
        val result = service.getKeyInfo()
        assertTrue(result is ApiResult.Success)
        assertEquals(null, (result as ApiResult.Success).data.data.limit)
    }

    @Test
    @DisplayName("getKeyInfo propagates a keys-list Error")
    fun keyInfoError() = runBlocking {
        enqueue(403, """{"error":"forbidden"}""")
        val result = service.getKeyInfo()
        assertTrue(result is ApiResult.Error)
        assertEquals(403, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("createApiKey parses a 2xx body into Success")
    fun createApiKeySuccess() = runBlocking {
        enqueue(
            200,
            """{"data":{"name":"n","label":"l","limit":5.0,"usage":0.0,"disabled":false,"created_at":"t","updated_at":null,"hash":"h"},"key":"sk-or-new"}"""
        )
        val result = service.createApiKey("n", 5.0)
        assertTrue(result is ApiResult.Success)
        assertEquals("sk-or-new", (result as ApiResult.Success).data.key)
    }

    @Test
    @DisplayName("createApiKey maps a malformed 2xx body to a parse Error carrying the status code")
    fun createApiKeyParseError() = runBlocking {
        enqueue(200, "{ not json")
        val result = service.createApiKey("n")
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to parse response", (result as ApiResult.Error).message)
        assertEquals(200, result.statusCode)
    }

    @Test
    @DisplayName("createApiKey maps a non-2xx to an Error, default message when body blank")
    fun createApiKeyHttpErrorBlankBody() = runBlocking {
        enqueue(400, "")
        val result = service.createApiKey("n")
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to create API key", (result as ApiResult.Error).message)
        assertEquals(400, result.statusCode)
    }

    @Test
    @DisplayName("createApiKey surfaces a non-blank error body verbatim")
    fun createApiKeyHttpErrorWithBody() = runBlocking {
        enqueue(400, "quota exceeded")
        val result = service.createApiKey("n")
        assertTrue(result is ApiResult.Error)
        assertEquals("quota exceeded", (result as ApiResult.Error).message)
    }

    @Test
    @DisplayName("deleteApiKey parses a 2xx body into Success")
    fun deleteApiKeySuccess() = runBlocking {
        enqueue(200, """{"deleted":true}""")
        val result = service.deleteApiKey("hash123")
        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.deleted)
    }

    @Test
    @DisplayName("deleteApiKey maps a malformed 2xx body to a parse Error")
    fun deleteApiKeyParseError() = runBlocking {
        enqueue(200, "{ broken")
        val result = service.deleteApiKey("hash123")
        assertTrue(result is ApiResult.Error)
        assertEquals(200, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("deleteApiKey maps a non-2xx to an Error with a default message when body blank")
    fun deleteApiKeyHttpErrorBlank() = runBlocking {
        enqueue(404, "")
        val result = service.deleteApiKey("hash123")
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to delete API key", (result as ApiResult.Error).message)
        assertEquals(404, result.statusCode)
    }

    @Test
    @DisplayName("getCredits returns Error when no provisioning key configured, without a request")
    fun creditsBlankKey() = runBlocking {
        blankKey()
        val result = service.getCredits()
        assertTrue(result is ApiResult.Error)
        assertEquals("No provisioning key configured", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("getCredits parses a 2xx body into Success")
    fun creditsSuccess() = runBlocking {
        enqueue(200, """{"data":{"total_credits":100.0,"total_usage":40.0}}""")
        val result = service.getCredits()
        assertTrue(result is ApiResult.Success)
        assertEquals(100.0, (result as ApiResult.Success).data.data.totalCredits)
    }

    @Test
    @DisplayName("getCredits maps a malformed 2xx body to a parse Error")
    fun creditsParseError() = runBlocking {
        enqueue(200, "{ nope")
        val result = service.getCredits()
        assertTrue(result is ApiResult.Error)
        assertEquals(200, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("getCredits maps a non-2xx to an Error with a default message when body blank")
    fun creditsHttpErrorBlank() = runBlocking {
        enqueue(500, "")
        val result = service.getCredits()
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to fetch credits", (result as ApiResult.Error).message)
        assertEquals(500, result.statusCode)
    }

    @Test
    @DisplayName("getActivity returns Error when no provisioning key configured, without a request")
    fun activityBlankKey() = runBlocking {
        blankKey()
        val result = service.getActivity()
        assertTrue(result is ApiResult.Error)
        assertEquals("No provisioning key configured", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("getActivity parses a 2xx body into Success")
    fun activitySuccess() = runBlocking {
        enqueue(200, """{"data":[]}""")
        val result = service.getActivity()
        assertTrue(result is ApiResult.Success)
    }

    @Test
    @DisplayName("getActivity maps a malformed 2xx body to a parse Error")
    fun activityParseError() = runBlocking {
        enqueue(200, "{ bad")
        val result = service.getActivity()
        assertTrue(result is ApiResult.Error)
        assertEquals(200, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("getActivity maps a non-2xx to an Error with a default message when body blank")
    fun activityHttpErrorBlank() = runBlocking {
        enqueue(502, "")
        val result = service.getActivity()
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to fetch activity", (result as ApiResult.Error).message)
        assertEquals(502, result.statusCode)
    }

    @Test
    @DisplayName("getProviders parses a 2xx body into Success (public endpoint, no auth)")
    fun providersSuccess() = runBlocking {
        enqueue(200, """{"data":[]}""")
        val result = service.getProviders()
        assertTrue(result is ApiResult.Success)
        assertEquals(0, (result as ApiResult.Success).data.data.size)
    }

    @Test
    @DisplayName("getProviders maps a malformed 2xx body to a parse Error")
    fun providersParseError() = runBlocking {
        enqueue(200, "{ broken")
        val result = service.getProviders()
        assertTrue(result is ApiResult.Error)
        assertEquals(200, (result as ApiResult.Error).statusCode)
        assertNotNull(result.throwable)
    }

    @Test
    @DisplayName("getProviders maps a non-2xx to an Error with a default message when body blank")
    fun providersHttpErrorBlank() = runBlocking {
        enqueue(503, "")
        val result = service.getProviders()
        assertTrue(result is ApiResult.Error)
        assertEquals("Failed to fetch providers", (result as ApiResult.Error).message)
        assertEquals(503, result.statusCode)
    }

    @Test
    @DisplayName("getModelsCount surfaces a non-blank error body verbatim on non-2xx")
    fun modelsCountHttpErrorWithBody() = runBlocking {
        enqueue(429, "rate limited")
        val result = service.getModelsCount()
        assertTrue(result is ApiResult.Error)
        assertEquals("rate limited", (result as ApiResult.Error).message)
        assertEquals(429, result.statusCode)
    }

    @Test
    @DisplayName("exchangeAuthCode maps a malformed 2xx body to a parse Error carrying the message")
    fun exchangeParseError() = runBlocking {
        enqueue(200, "{ not json")
        val result = service.exchangeAuthCode("code", "verifier")
        assertTrue(result is ApiResult.Error)
        assertEquals(200, (result as ApiResult.Error).statusCode)
        assertTrue(result.message.startsWith("Failed to parse response"))
    }

    @Test
    @DisplayName("exchangeAuthCode maps a non-2xx with blank body to a default HTTP error message")
    fun exchangeHttpErrorBlank() = runBlocking {
        enqueue(400, "")
        val result = service.exchangeAuthCode("code", "verifier")
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).statusCode)
        assertTrue(result.message.contains("HTTP 400"))
    }

    @Test
    @DisplayName("getApiKeysList maps a network failure to an Error carrying the cause")
    fun apiKeysListNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getApiKeysList("pk-test")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("createApiKey maps a network failure to an Error carrying the cause")
    fun createApiKeyNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.createApiKey("n")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("deleteApiKey maps a network failure to an Error carrying the cause")
    fun deleteApiKeyNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.deleteApiKey("hash123")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("getCredits maps a network failure to an Error carrying the cause")
    fun creditsNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getCredits()
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("getActivity maps a network failure to an Error carrying the cause")
    fun activityNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getActivity()
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("getProviders maps a network failure to an Error carrying the cause")
    fun providersNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getProviders()
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("getGenerationStats maps a network failure to an Error carrying the cause")
    fun generationStatsNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getGenerationStats("gen-1")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("exchangeAuthCode maps a network failure to an Error carrying the cause")
    fun exchangeNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.exchangeAuthCode("code", "verifier")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("testApiKey returns Error when the key is blank, without a request")
    fun testApiKeyBlank() = runBlocking {
        val result = service.testApiKey("")
        assertTrue(result is ApiResult.Error)
        assertEquals("API key is required", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("testApiKey returns Success(true) on a 2xx response")
    fun testApiKeySuccess() = runBlocking {
        enqueue(200, """{"data":{"label":"k"}}""")
        val result = service.testApiKey("sk-or-test")
        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    @DisplayName("testApiKey parses a structured error message from a non-2xx body")
    fun testApiKeyErrorParsed() = runBlocking {
        enqueue(401, """{"error":{"message":"bad key"}}""")
        val result = service.testApiKey("sk-bad")
        assertTrue(result is ApiResult.Error)
        assertEquals("bad key", (result as ApiResult.Error).message)
        assertEquals(401, result.statusCode)
    }

    @Test
    @DisplayName("testApiKey falls back to a synthetic message when the error body is unparseable")
    fun testApiKeyErrorFallback() = runBlocking {
        enqueue(401, "not json at all")
        val result = service.testApiKey("sk-bad")
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.contains("HTTP 401"))
    }

    @Test
    @DisplayName("testConnection delegates to testApiKey with the configured key")
    fun testConnectionSuccess() = runBlocking {
        enqueue(200, """{"data":{"label":"k"}}""")
        val result = service.testConnection()
        assertTrue(result is ApiResult.Success)
    }

    @Test
    @DisplayName("getPreset returns Error when no API key is configured, without a request")
    fun getPresetBlankKey() = runBlocking {
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn(null)
        val result = service.getPreset("slug")
        assertTrue(result is ApiResult.Error)
        assertEquals("No API key configured", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("getPreset maps a network failure to an Error carrying the cause")
    fun getPresetNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.getPreset("slug")
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("getPreset falls back to the HTTP status message on a non-2xx blank body")
    fun getPresetHttpErrorBlank() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        val result = service.getPreset("slug")
        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("createOrUpdatePreset returns Error when no API key is configured, without a request")
    fun createPresetBlankKey() = runBlocking {
        `when`(mockApiKeyManager.getStoredApiKey()).thenReturn(null)
        val result = service.createOrUpdatePreset("slug", emptyMap(), null)
        assertTrue(result is ApiResult.Error)
        assertEquals("No API key configured", (result as ApiResult.Error).message)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    @DisplayName("createOrUpdatePreset maps a network failure to an Error carrying the cause")
    fun createPresetNetworkError() = runBlocking {
        mockWebServer.shutdown()
        val result = service.createOrUpdatePreset("slug", emptyMap(), null)
        assertTrue(result is ApiResult.Error)
        assertNotNull((result as ApiResult.Error).throwable)
    }

    @Test
    @DisplayName("createOrUpdatePreset falls back to the HTTP status message on a non-2xx blank body")
    fun createPresetHttpErrorBlank() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))
        val result = service.createOrUpdatePreset("slug", emptyMap(), "sys")
        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).statusCode)
    }

    @Test
    @DisplayName("getApiKeysList no-arg overload delegates to the configured provisioning key")
    fun apiKeysListNoArgDelegates() = runBlocking {
        enqueue(200, """{"data":[]}""")
        val result = service.getApiKeysList()
        assertTrue(result is ApiResult.Success)
    }

    @Test
    @DisplayName("isConfigured delegates to the settings service")
    fun isConfiguredDelegates() {
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
        assertTrue(service.isConfigured())
    }
}
