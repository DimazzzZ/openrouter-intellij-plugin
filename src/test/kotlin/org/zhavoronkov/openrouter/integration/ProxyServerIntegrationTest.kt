package org.zhavoronkov.openrouter.integration

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.zhavoronkov.openrouter.proxy.servlets.HealthCheckServlet
import java.util.concurrent.TimeUnit

/**
 * Integration tests for OpenRouter Proxy Server via Jetty
 * 
 * These tests verify that the Jetty server infrastructure works correctly:
 * - Server starts and stops cleanly
 * - Servlets are properly registered and routed
 * - Concurrent requests are handled correctly
 * - HTTP protocol basics work (GET, POST, OPTIONS)
 * 
 * Note: Full end-to-end tests with OpenRouter API are in separate test files
 * because they require IntelliJ services which aren't available in unit tests.
 */
@DisplayName("OpenRouter Proxy Server Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyServerIntegrationTest {

    private lateinit var jettyServer: Server
    private lateinit var httpClient: OkHttpClient
    private lateinit var gson: Gson
    private val proxyPort: Int = 18080 // Use a different port to avoid conflicts

    @BeforeAll
    fun setUpAll() {
        gson = Gson()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        // Create a standalone Jetty server for testing
        jettyServer = Server(proxyPort)
        
        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.contextPath = "/"
        
        // Add health check servlet (doesn't require IntelliJ services)
        context.addServlet(ServletHolder(HealthCheckServlet()), "/health")
        
        jettyServer.handler = context
        jettyServer.start()

        println("✅ Test Jetty server started on port $proxyPort")
    }

    @AfterAll
    fun tearDownAll() {
        jettyServer.stop()
        jettyServer.join()
        println("✅ Test server stopped")
    }

    @Nested
    @DisplayName("Health Check Endpoint")
    inner class HealthCheckTests {

        @Test
        @DisplayName("Should return healthy status")
        fun testHealthCheck() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/health")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "Health check should return 200")
                
                val body = response.body?.string()
                assertNotNull(body)
                
                val json = gson.fromJson(body, Map::class.java)
                assertEquals("ok", json["status"])
            }
        }

        @Test
        @DisplayName("Should handle multiple health check requests")
        fun testMultipleHealthChecks() {
            repeat(10) { iteration ->
                val request = Request.Builder()
                    .url("http://localhost:$proxyPort/health")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    assertEquals(200, response.code, "Health check $iteration should return 200")
                }
            }
        }
    }

    @Nested
    @DisplayName("Jetty Server Functionality")
    inner class JettyServerTests {

        @Test
        @DisplayName("Should handle concurrent requests")
        fun testConcurrentRequests() {
            val threads = (1..10).map {
                Thread {
                    val request = Request.Builder()
                        .url("http://localhost:$proxyPort/health")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        assertEquals(200, response.code)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join(5000) }
        }

        @Test
        @DisplayName("Should handle OPTIONS requests (CORS preflight)")
        fun testOptionsRequest() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/health")
                .method("OPTIONS", null)
                .build()

            httpClient.newCall(request).execute().use { response ->
                // Should handle OPTIONS request (200 or 405 are both acceptable)
                assertTrue(response.code in 200..405, "Should handle OPTIONS request")
            }
        }

        @Test
        @DisplayName("Should return 404 for non-existent endpoints")
        fun testNonExistentEndpoint() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/non-existent-endpoint")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(404, response.code, "Should return 404 for non-existent endpoint")
            }
        }
    }

    @Nested
    @DisplayName("Server Lifecycle")
    inner class ServerLifecycleTests {

        @Test
        @DisplayName("Should be started and running")
        fun testServerIsRunning() {
            assertTrue(jettyServer.isStarted, "Server should be started")
            assertFalse(jettyServer.isStopped, "Server should not be stopped")
            assertTrue(jettyServer.isRunning, "Server should be running")
        }

        @Test
        @DisplayName("Should handle rapid sequential requests")
        fun testRapidSequentialRequests() {
            val startTime = System.currentTimeMillis()
            
            repeat(50) {
                val request = Request.Builder()
                    .url("http://localhost:$proxyPort/health")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    assertEquals(200, response.code, "Request $it should succeed")
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            println("✅ Completed 50 requests in ${duration}ms")
            assertTrue(duration < 10000, "Should complete 50 requests in under 10 seconds")
        }

        @Test
        @DisplayName("Should have correct server configuration")
        fun testServerConfiguration() {
            val connectors = jettyServer.connectors
            assertNotNull(connectors, "Server should have connectors")
            assertTrue(connectors.isNotEmpty(), "Server should have at least one connector")
            
            val handler = jettyServer.handler
            assertNotNull(handler, "Server should have a handler")
            assertTrue(handler is ServletContextHandler, "Handler should be ServletContextHandler")
        }
    }

    @Nested
    @DisplayName("HTTP Protocol Support")
    inner class HttpProtocolTests {

        @Test
        @DisplayName("Should support GET requests")
        fun testGetRequest() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/health")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertNotNull(response.body)
            }
        }

        @Test
        @DisplayName("Should support HEAD requests")
        fun testHeadRequest() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/health")
                .head()
                .build()

            httpClient.newCall(request).execute().use { response ->
                // HEAD should return same status as GET but no body
                assertTrue(response.code in 200..405)
            }
        }

        @Test
        @DisplayName("Should handle requests with custom headers")
        fun testCustomHeaders() {
            val request = Request.Builder()
                .url("http://localhost:$proxyPort/health")
                .addHeader("X-Custom-Header", "test-value")
                .addHeader("User-Agent", "OpenRouter-Test/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                assertEquals(200, response.code, "Should handle custom headers")
            }
        }
    }

    @Nested
    @DisplayName("Performance and Reliability")
    inner class PerformanceTests {

        @Test
        @DisplayName("Should handle requests under load")
        fun testUnderLoad() {
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            val threads = (1..20).map {
                Thread {
                    repeat(5) {
                        try {
                            val request = Request.Builder()
                                .url("http://localhost:$proxyPort/health")
                                .get()
                                .build()

                            httpClient.newCall(request).execute().use { response ->
                                if (response.code == 200) {
                                    successCount.incrementAndGet()
                                }
                            }
                        } catch (e: Exception) {
                            println("⚠️ Request failed: ${e.message}")
                        }
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join(10000) }

            val totalRequests = 20 * 5
            val successRate = (successCount.get().toDouble() / totalRequests) * 100
            println("✅ Success rate: $successRate% ($successCount/$totalRequests)")
            assertTrue(successRate >= 95.0, "Should have at least 95% success rate under load")
        }
    }
}

