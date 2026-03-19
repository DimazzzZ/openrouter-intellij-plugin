package org.zhavoronkov.openrouter.proxy

import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.proxy.servlets.EnginesServlet
import org.zhavoronkov.openrouter.proxy.servlets.HealthCheckServlet
import org.zhavoronkov.openrouter.proxy.servlets.ModelsServlet
import org.zhavoronkov.openrouter.proxy.servlets.OrganizationServlet
import org.zhavoronkov.openrouter.proxy.servlets.RootServlet
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ProxySettingsManager
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * Comprehensive tests for OpenRouterProxyServer
 * These tests cover server lifecycle, servlet configuration, port management,
 * and HTTP integration to ensure safe migration to Jetty 12
 */
@DisplayName("OpenRouterProxyServer Tests")
class OpenRouterProxyServerTest {

    private lateinit var proxyServer: OpenRouterProxyServer
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockOpenRouterService: OpenRouterService
    private lateinit var mockProxyManager: ProxySettingsManager

    @BeforeEach
    fun setUp() {
        proxyServer = OpenRouterProxyServer()
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        mockOpenRouterService = mock(OpenRouterService::class.java)
        mockProxyManager = mock(ProxySettingsManager::class.java)

        `when`(mockSettingsService.proxyManager).thenReturn(mockProxyManager)
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
        `when`(mockSettingsService.getApiKey()).thenReturn("sk-or-v1-test-key-12345")

        // Default port range configuration
        `when`(mockProxyManager.getProxyPort()).thenReturn(0) // Auto-select
        `when`(mockProxyManager.getProxyPortRangeStart()).thenReturn(18880)
        `when`(mockProxyManager.getProxyPortRangeEnd()).thenReturn(18899)

        proxyServer.setDependenciesForTests(mockOpenRouterService, mockSettingsService)

        // Set up a test-safe servlet handler factory that doesn't depend on IntelliJ services
        proxyServer.setServletHandlerFactoryForTests {
            createTestServletHandler()
        }
    }

    @AfterEach
    fun tearDown() {
        // Ensure server is stopped after each test
        try {
            proxyServer.stop().get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Ignore errors during cleanup
        }
    }

    /**
     * Creates a servlet handler for testing that doesn't depend on IntelliJ services.
     * This mirrors the production createServletHandler() but uses test-safe dependencies.
     */
    private fun createTestServletHandler(): ServletContextHandler {
        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.contextPath = "/"

        // Add servlets with test-safe dependencies
        val rootServlet = ServletHolder(RootServlet())
        val healthServlet = ServletHolder(HealthCheckServlet())
        // Use a custom favorites provider that doesn't call OpenRouterSettingsService.getInstance()
        val modelsServlet = ServletHolder(
            ModelsServlet(
                mockOpenRouterService,
                favoriteModelsProvider = { listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet") }
            )
        )
        val organizationServlet = ServletHolder(OrganizationServlet())
        val enginesServlet = ServletHolder(EnginesServlet())

        context.addServlet(healthServlet, "/health")
        context.addServlet(modelsServlet, "/v1/models")
        context.addServlet(modelsServlet, "/models")
        context.addServlet(organizationServlet, "/v1/organizations")
        context.addServlet(enginesServlet, "/v1/engines")
        context.addServlet(rootServlet, "/")

        // Add CORS filter
        context.addFilter(CorsFilter::class.java, "/*", null)

        return context
    }

    @Nested
    @DisplayName("Server Status Tests")
    inner class ServerStatusTests {

        @Test
        @DisplayName("getStatus should reflect running flag when server is not started")
        fun testGetStatusWhenNotRunning() {
            val status = proxyServer.getStatus()

            assertFalse(status.isRunning, "Server should not be running initially")
            assertNull(status.port, "Port should be null when not running")
            assertNull(status.url, "URL should be null when not running")
            assertTrue(status.isConfigured, "Should be configured when API key is set")
        }

        @Test
        @DisplayName("getStatus should show not configured when API key is missing")
        fun testGetStatusNotConfigured() {
            `when`(mockSettingsService.isConfigured()).thenReturn(false)

            val status = proxyServer.getStatus()

            assertFalse(status.isConfigured, "Should not be configured when API key is missing")
        }
    }

    @Nested
    @DisplayName("Server Lifecycle Tests")
    inner class ServerLifecycleTests {

        @Test
        @DisplayName("start should start server and update status")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testServerStart() {
            val result = proxyServer.start().get(5, TimeUnit.SECONDS)

            assertTrue(result, "Server should start successfully")

            val status = proxyServer.getStatus()
            assertTrue(status.isRunning, "Server should be running after start")
            assertNotNull(status.port, "Port should be set after start")
            assertNotNull(status.url, "URL should be set after start")
            assertTrue(status.port!! in 18880..18899, "Port should be in configured range")
        }

        @Test
        @DisplayName("start should be idempotent when already running")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testServerStartIdempotent() {
            // Start first time
            val firstResult = proxyServer.start().get(5, TimeUnit.SECONDS)
            assertTrue(firstResult, "First start should succeed")

            val firstPort = proxyServer.getStatus().port

            // Start second time
            val secondResult = proxyServer.start().get(5, TimeUnit.SECONDS)
            assertTrue(secondResult, "Second start should also succeed (idempotent)")

            val secondPort = proxyServer.getStatus().port
            assertEquals(firstPort, secondPort, "Port should remain the same")
        }

        @Test
        @DisplayName("stop should stop server and clear status")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testServerStop() {
            // Start server first
            proxyServer.start().get(5, TimeUnit.SECONDS)
            assertTrue(proxyServer.getStatus().isRunning, "Server should be running")

            // Stop server
            val result = proxyServer.stop().get(5, TimeUnit.SECONDS)

            assertTrue(result, "Server should stop successfully")

            val status = proxyServer.getStatus()
            assertFalse(status.isRunning, "Server should not be running after stop")
            assertNull(status.port, "Port should be null after stop")
            assertNull(status.url, "URL should be null after stop")
        }

        @Test
        @DisplayName("stop should be idempotent when not running")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testServerStopIdempotent() {
            // Server is not running
            assertFalse(proxyServer.getStatus().isRunning, "Server should not be running")

            // Stop anyway
            val result = proxyServer.stop().get(5, TimeUnit.SECONDS)

            assertTrue(result, "Stop should succeed even when not running")
        }

        @Test
        @DisplayName("restart should stop and start server")
        @Timeout(15, unit = TimeUnit.SECONDS)
        fun testServerRestart() {
            // Start server first
            proxyServer.start().get(5, TimeUnit.SECONDS)
            assertTrue(proxyServer.getStatus().isRunning, "Server should be running initially")

            // Restart server
            val result = proxyServer.restart().get(10, TimeUnit.SECONDS)

            assertTrue(result, "Server should restart successfully")
            assertTrue(proxyServer.getStatus().isRunning, "Server should be running after restart")
            assertNotNull(proxyServer.getStatus().port, "Port should be set after restart")
        }
    }

    @Nested
    @DisplayName("Port Selection Tests")
    inner class PortSelectionTests {

        @Test
        @DisplayName("should use specific port when configured")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testSpecificPortConfiguration() {
            val specificPort = 18888
            `when`(mockProxyManager.getProxyPort()).thenReturn(specificPort)

            val result = proxyServer.start().get(5, TimeUnit.SECONDS)

            assertTrue(result, "Server should start successfully")
            assertEquals(specificPort, proxyServer.getStatus().port, "Should use specific port")
        }

        @Test
        @DisplayName("should fall back to range when specific port is unavailable")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testFallbackToRangeWhenPortUnavailable() {
            val specificPort = 18888

            // Occupy the specific port
            ServerSocket(specificPort).use {
                `when`(mockProxyManager.getProxyPort()).thenReturn(specificPort)

                val result = proxyServer.start().get(5, TimeUnit.SECONDS)

                assertTrue(result, "Server should start successfully on alternative port")
                val actualPort = proxyServer.getStatus().port
                assertNotNull(actualPort, "Port should be set")
                assertTrue(
                    actualPort != specificPort && actualPort in 18880..18899,
                    "Should use port from range when specific port is unavailable"
                )
            }
        }

        @Test
        @DisplayName("should fail when no ports available in range")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testFailWhenNoPortsAvailable() {
            // Configure a very small range
            `when`(mockProxyManager.getProxyPort()).thenReturn(0)
            `when`(mockProxyManager.getProxyPortRangeStart()).thenReturn(18890)
            `when`(mockProxyManager.getProxyPortRangeEnd()).thenReturn(18891)

            // Occupy both ports in range
            val socket1 = ServerSocket(18890)
            val socket2 = ServerSocket(18891)

            try {
                val result = proxyServer.start().get(5, TimeUnit.SECONDS)

                assertFalse(result, "Server should fail to start when no ports available")
                assertFalse(proxyServer.getStatus().isRunning, "Server should not be running")
            } finally {
                socket1.close()
                socket2.close()
            }
        }

        @Test
        @DisplayName("should select from range when port is 0 (auto-select)")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testAutoSelectFromRange() {
            `when`(mockProxyManager.getProxyPort()).thenReturn(0)
            `when`(mockProxyManager.getProxyPortRangeStart()).thenReturn(18880)
            `when`(mockProxyManager.getProxyPortRangeEnd()).thenReturn(18899)

            val result = proxyServer.start().get(5, TimeUnit.SECONDS)

            assertTrue(result, "Server should start successfully")
            val port = proxyServer.getStatus().port
            assertNotNull(port, "Port should be set")
            assertTrue(port in 18880..18899, "Port should be within configured range")
        }
    }

    @Nested
    @DisplayName("HTTP Integration Tests")
    inner class HttpIntegrationTests {

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        @Test
        @DisplayName("health endpoint should respond with OK")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testHealthEndpoint() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val port = proxyServer.getStatus().port!!

            val request = Request.Builder()
                .url("http://127.0.0.1:$port/health")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "Health endpoint should return 200")
                val body = response.body?.string() ?: ""
                assertTrue(body.contains("status"), "Response should contain status field")
            }
        }

        @Test
        @DisplayName("root endpoint should respond")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testRootEndpoint() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val port = proxyServer.getStatus().port!!

            val request = Request.Builder()
                .url("http://127.0.0.1:$port/")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "Root endpoint should return 200")
            }
        }

        @Test
        @DisplayName("models endpoint should respond")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testModelsEndpoint() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val port = proxyServer.getStatus().port!!

            val request = Request.Builder()
                .url("http://127.0.0.1:$port/v1/models")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "Models endpoint should return 200")
                val body = response.body?.string() ?: ""
                assertTrue(body.contains("data"), "Response should contain data field")
            }
        }

        @Test
        @DisplayName("testConnection should return true when server is running")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testTestConnectionWhenRunning() {
            proxyServer.start().get(5, TimeUnit.SECONDS)

            val result = proxyServer.testConnection().get(5, TimeUnit.SECONDS)

            assertTrue(result, "testConnection should return true when server is running")
        }

        @Test
        @DisplayName("testConnection should return false when server is not running")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testTestConnectionWhenNotRunning() {
            val result = proxyServer.testConnection().get(5, TimeUnit.SECONDS)

            assertFalse(result, "testConnection should return false when server is not running")
        }

        @Test
        @DisplayName("CORS headers should be present on OPTIONS request")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testCorsHeaders() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val port = proxyServer.getStatus().port!!

            val request = Request.Builder()
                .url("http://127.0.0.1:$port/v1/models")
                .method("OPTIONS", null)
                .header("Origin", "http://localhost:3000")
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "OPTIONS request should return 200")
                // CorsFilter adds these headers
                assertNotNull(
                    response.header("Access-Control-Allow-Origin"),
                    "Should have CORS Allow-Origin header"
                )
            }
        }
    }

    @Nested
    @DisplayName("Servlet Registration Tests")
    inner class ServletRegistrationTests {

        @Test
        @DisplayName("all required endpoints should be accessible")
        @Timeout(15, unit = TimeUnit.SECONDS)
        fun testAllEndpointsAccessible() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val port = proxyServer.getStatus().port!!

            val httpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            // List of endpoints that should be registered
            val endpoints = listOf(
                "/health" to "GET",
                "/" to "GET",
                "/v1/models" to "GET",
                "/models" to "GET", // AI Assistant compatibility alias
                "/v1/organizations" to "GET",
                "/v1/engines" to "GET"
            )

            for ((path, method) in endpoints) {
                val request = Request.Builder()
                    .url("http://127.0.0.1:$port$path")
                    .method(method, null)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    assertTrue(
                        response.code in 200..299 || response.code == 401,
                        "Endpoint $path should be accessible, got ${response.code}"
                    )
                }
            }
        }
    }

    @Nested
    @DisplayName("URL Building Tests")
    inner class UrlBuildingTests {

        @Test
        @DisplayName("buildProxyUrl should construct correct URL")
        fun testBuildProxyUrl() {
            val url = OpenRouterProxyServer.buildProxyUrl(8888)

            assertEquals("http://127.0.0.1:8888/v1/", url, "URL should be correctly formatted")
        }

        @Test
        @DisplayName("getStatus URL should match buildProxyUrl format")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun testStatusUrlFormat() {
            proxyServer.start().get(5, TimeUnit.SECONDS)
            val status = proxyServer.getStatus()

            assertNotNull(status.url, "URL should be set")
            assertEquals(
                OpenRouterProxyServer.buildProxyUrl(status.port!!),
                status.url,
                "Status URL should match buildProxyUrl format"
            )
        }
    }
}
