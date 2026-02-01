package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture

@DisplayName("OpenRouter Proxy Service Tests")
class OpenRouterProxyServiceTest {

    private lateinit var service: OpenRouterProxyService
    private lateinit var mockProxyServer: OpenRouterProxyServer
    private lateinit var mockSettingsService: OpenRouterSettingsService

    private fun initService() {
        service = OpenRouterProxyService()
        mockProxyServer = mock(OpenRouterProxyServer::class.java)
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        service.setDependenciesForTests(mockProxyServer, mockSettingsService)
    }

    @Nested
    @DisplayName("Status Helpers")
    inner class StatusHelpers {

        @Test
        fun `should return proxy url when running`() {
            initService()
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = true,
                port = 8081,
                url = "http://127.0.0.1:8081/v1/",
                isConfigured = true
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            assertEquals("http://127.0.0.1:8081/v1/", service.getProxyUrl())
            assertEquals(8081, service.getProxyPort())
            assertTrue(service.isReady())
        }

        @Test
        fun `should return null proxy url when stopped`() {
            initService()
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = false,
                port = null,
                url = null,
                isConfigured = true
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            assertNull(service.getProxyUrl())
            assertNull(service.getProxyPort())
            assertFalse(service.isReady())
        }

        @Test
        fun `should report not ready when unconfigured`() {
            initService()
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = true,
                port = 8081,
                url = "http://127.0.0.1:8081/v1/",
                isConfigured = false
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            assertFalse(service.isReady())
        }
    }

    @Nested
    @DisplayName("Connection Helpers")
    inner class ConnectionHelpers {

        @Test
        fun `testServerConnection should delegate to proxy server`() {
            initService()
            val future = CompletableFuture.completedFuture(true)
            `when`(mockProxyServer.testConnection()).thenReturn(future)

            val result = service.testServerConnection().get()

            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("Lifecycle Helpers")
    inner class LifecycleHelpers {

        @Test
        fun `startServer should not start when not configured`() {
            initService()
            `when`(mockSettingsService.isConfigured()).thenReturn(false)

            val result = service.startServer().get(1, TimeUnit.SECONDS)

            assertFalse(result)
        }

        @Test
        fun `startServer should skip when already running`() {
            initService()
            `when`(mockSettingsService.isConfigured()).thenReturn(true)
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = true,
                port = 8080,
                url = "http://localhost:8080/v1",
                isConfigured = true
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            val result = service.startServer().get(1, TimeUnit.SECONDS)

            assertTrue(result)
        }

        @Test
        fun `stopServer should return true when already stopped`() {
            initService()
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = false,
                port = null,
                url = null,
                isConfigured = true
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            val result = service.stopServer().get(1, TimeUnit.SECONDS)

            assertTrue(result)
        }

        @Test
        fun `autoStartIfConfigured should return false when not configured`() {
            initService()
            `when`(mockSettingsService.isConfigured()).thenReturn(false)

            val result = service.autoStartIfConfigured().get(1, TimeUnit.SECONDS)

            assertFalse(result)
        }

        @Test
        fun `forceStartServer should skip when running`() {
            initService()
            val status = OpenRouterProxyServer.ProxyServerStatus(
                isRunning = true,
                port = 8080,
                url = "http://localhost:8080/v1",
                isConfigured = true
            )
            `when`(mockProxyServer.getStatus()).thenReturn(status)

            val result = service.forceStartServer().get(1, TimeUnit.SECONDS)

            assertTrue(result)
        }
    }
}
