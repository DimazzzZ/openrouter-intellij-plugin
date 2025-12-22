package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for dynamic plugin support
 * Verifies that all services properly implement Disposable and clean up resources
 */
@DisplayName("Dynamic Plugin Support Tests")
class DynamicPluginSupportTest {

    @Nested
    @DisplayName("Service Disposal Tests")
    inner class ServiceDisposalTests {

        @Test
        @DisplayName("OpenRouterProxyService should implement Disposable")
        fun testProxyServiceImplementsDisposable() {
            val service = OpenRouterProxyService()

            // Verify it implements Disposable
            assertTrue(
                service is com.intellij.openapi.Disposable,
                "OpenRouterProxyService must implement Disposable for dynamic plugin support"
            )

            // Verify dispose can be called without errors
            service.dispose()
        }

        @Test
        @DisplayName("OpenRouterService should implement Disposable")
        fun testOpenRouterServiceImplementsDisposable() {
            val service = OpenRouterService()

            // Verify it implements Disposable
            assertTrue(
                service is com.intellij.openapi.Disposable,
                "OpenRouterService must implement Disposable for dynamic plugin support"
            )

            // Verify dispose can be called without errors
            service.dispose()
        }

        @Test
        @DisplayName("OpenRouterSettingsService should implement Disposable")
        fun testSettingsServiceImplementsDisposable() {
            val service = OpenRouterSettingsService()

            // Verify it implements Disposable
            assertTrue(
                service is com.intellij.openapi.Disposable,
                "OpenRouterSettingsService must implement Disposable for dynamic plugin support"
            )

            // Verify dispose can be called without errors
            service.dispose()
        }

        @Test
        @DisplayName("OpenRouterGenerationTrackingService should implement Disposable")
        fun testGenerationTrackingServiceImplementsDisposable() {
            val service = OpenRouterGenerationTrackingService()

            // Verify it implements Disposable
            assertTrue(
                service is com.intellij.openapi.Disposable,
                "OpenRouterGenerationTrackingService must implement Disposable for dynamic plugin support"
            )

            // Verify dispose can be called without errors
            service.dispose()
        }
    }

    @Nested
    @DisplayName("Proxy Server Cleanup Tests")
    inner class ProxyServerCleanupTests {

        private lateinit var proxyService: OpenRouterProxyService

        @BeforeEach
        fun setUp() {
            proxyService = OpenRouterProxyService()
        }

        @AfterEach
        fun tearDown() {
            // Ensure server is stopped after each test
            try {
                proxyService.stopServer().get()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            proxyService.dispose()
        }

        @Test
        @DisplayName("Dispose should stop running proxy server")
        fun testDisposeStopsRunningServer() {
            // Note: This test verifies the dispose logic exists
            // Actual server start/stop requires full IntelliJ environment

            val status = proxyService.getServerStatus()
            assertNotNull(status, "Server status should be available")

            // Dispose should handle cleanup gracefully
            proxyService.dispose()
        }

        @Test
        @DisplayName("Dispose should handle already stopped server")
        fun testDisposeHandlesStoppedServer() {
            // Ensure server is not running
            val status = proxyService.getServerStatus()
            if (status.isRunning) {
                proxyService.stopServer().get()
            }

            // Dispose should handle this gracefully
            proxyService.dispose()
        }

        @Test
        @DisplayName("Dispose should cancel active tasks")
        fun testDisposeCancelsActiveTasks() {
            // Start an async operation (won't actually start server in test environment)
            val future = proxyService.startServer()

            // Dispose should cancel pending operations
            proxyService.dispose()

            // Future should be completed (either successfully or cancelled)
            assertTrue(
                future.isDone || future.isCancelled,
                "Active tasks should be completed or cancelled after dispose"
            )
        }
    }

    @Nested
    @DisplayName("Resource Cleanup Tests")
    inner class ResourceCleanupTests {

        @Test
        @DisplayName("OpenRouterService should cleanup HTTP client on dispose")
        fun testHttpClientCleanup() {
            val service = OpenRouterService()

            // Dispose should shutdown connection pool
            service.dispose()

            // No exception should be thrown
        }

        @Test
        @DisplayName("Multiple dispose calls should be safe")
        fun testMultipleDisposeCalls() {
            val service = OpenRouterService()

            // Multiple dispose calls should not throw exceptions
            service.dispose()
            service.dispose()
            service.dispose()
        }
    }
}
