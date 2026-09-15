package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Behavioural unit tests for [OpenRouterProxyService].
 *
 * The service exposes a [OpenRouterProxyService.setDependenciesForTests] seam that swaps the
 * platform-bound providers (OpenRouterProxyServer.getInstance / OpenRouterSettingsService.getInstance)
 * for mocks, so the full lifecycle surface is unit-testable off-platform.
 *
 * ## Platform-bound surface NOT covered here (see TESTING.md "Platform-bound coverage exclusions")
 *   - getInstance() companion (ApplicationManager.getService)
 *   - the java.net.BindException / TimeoutException catch arms in startServer/forceStartServer
 *     (need a real bound socket / real timeout; the ExecutionException + IllegalStateException
 *     arms ARE covered via mocked failed futures / thenThrow)
 *   - dispose()'s TimeoutException branch on proxyServer.stop().get(timeout)
 */
@DisplayName("OpenRouter Proxy Service Tests")
class OpenRouterProxyServiceTest {

    private lateinit var service: OpenRouterProxyService
    private lateinit var mockProxyServer: OpenRouterProxyServer
    private lateinit var mockSettingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        service = OpenRouterProxyService()
        mockProxyServer = Mockito.mock(OpenRouterProxyServer::class.java)
        mockSettingsService = Mockito.mock(OpenRouterSettingsService::class.java)
        service.setDependenciesForTests(mockProxyServer, mockSettingsService)
    }

    private fun status(running: Boolean, port: Int? = 8081, configured: Boolean = true) =
        OpenRouterProxyServer.ProxyServerStatus(
            isRunning = running,
            port = if (running) port else null,
            url = if (running) "http://127.0.0.1:$port/v1/" else null,
            isConfigured = configured
        )

    @Nested
    @DisplayName("Status Helpers")
    inner class StatusHelpers {

        @Test
        fun `should return proxy url when running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            assertEquals("http://127.0.0.1:8081/v1/", service.getProxyUrl())
            assertEquals(8081, service.getProxyPort())
            assertTrue(service.isReady())
        }

        @Test
        fun `should return null proxy url when stopped`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            assertNull(service.getProxyUrl())
            assertNull(service.getProxyPort())
            assertFalse(service.isReady())
        }

        @Test
        fun `should report not ready when unconfigured`() {
            Mockito.`when`(mockProxyServer.getStatus())
                .thenReturn(status(running = true, configured = false))
            assertFalse(service.isReady())
        }

        @Test
        fun `getServerStatus delegates to proxy server`() {
            val s = status(running = true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(s)
            assertEquals(s, service.getServerStatus())
        }
    }

    @Nested
    @DisplayName("Connection Helpers")
    inner class ConnectionHelpers {

        @Test
        fun `testServerConnection should delegate to proxy server`() {
            val future = CompletableFuture.completedFuture(true)
            Mockito.`when`(mockProxyServer.testConnection()).thenReturn(future)
            assertTrue(service.testServerConnection().get())
        }
    }

    @Nested
    @DisplayName("Lifecycle: startServer")
    inner class StartServer {

        @Test
        fun `startServer returns false when not configured`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(false)
            assertFalse(service.startServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `startServer returns true when already running`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true, port = 8080))
            assertTrue(service.startServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `startServer delegates to proxyServer_start when not running`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            Mockito.`when`(mockProxyServer.start()).thenReturn(CompletableFuture.completedFuture(true))
            assertTrue(service.startServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `startServer returns false when proxyServer_start throws ExecutionException`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            val failed = CompletableFuture<Boolean>()
            failed.completeExceptionally(RuntimeException("boom"))
            Mockito.`when`(mockProxyServer.start()).thenReturn(failed)
            assertFalse(service.startServer().get(2, TimeUnit.SECONDS))
        }

        @Test
        fun `startServer returns false when proxyServer_start throws IllegalStateException`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            Mockito.`when`(mockProxyServer.start()).thenThrow(IllegalStateException("bad"))
            assertFalse(service.startServer().get(2, TimeUnit.SECONDS))
        }
    }

    @Nested
    @DisplayName("Lifecycle: stopServer")
    inner class StopServer {

        @Test
        fun `stopServer returns true when not running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            assertTrue(service.stopServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `stopServer delegates to proxyServer_stop when running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            Mockito.`when`(mockProxyServer.stop()).thenReturn(CompletableFuture.completedFuture(true))
            assertTrue(service.stopServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `stopServer returns false when stop throws ExecutionException`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            val failed = CompletableFuture<Boolean>()
            failed.completeExceptionally(RuntimeException("boom"))
            Mockito.`when`(mockProxyServer.stop()).thenReturn(failed)
            assertFalse(service.stopServer().get(2, TimeUnit.SECONDS))
        }

        @Test
        fun `stopServer returns false when stop throws IllegalStateException`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            Mockito.`when`(mockProxyServer.stop()).thenThrow(IllegalStateException("bad"))
            assertFalse(service.stopServer().get(2, TimeUnit.SECONDS))
        }
    }

    @Nested
    @DisplayName("Lifecycle: restartServer")
    inner class RestartServer {

        @Test
        fun `restartServer delegates to proxyServer_restart on success`() {
            Mockito.`when`(mockProxyServer.restart()).thenReturn(CompletableFuture.completedFuture(true))
            assertTrue(service.restartServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `restartServer returns false when restart throws ExecutionException`() {
            val failed = CompletableFuture<Boolean>()
            failed.completeExceptionally(RuntimeException("boom"))
            Mockito.`when`(mockProxyServer.restart()).thenReturn(failed)
            assertFalse(service.restartServer().get(2, TimeUnit.SECONDS))
        }

        @Test
        fun `restartServer returns false when restart throws IllegalStateException`() {
            Mockito.`when`(mockProxyServer.restart()).thenThrow(IllegalStateException("bad"))
            assertFalse(service.restartServer().get(2, TimeUnit.SECONDS))
        }
    }

    @Nested
    @DisplayName("Lifecycle: autoStartIfConfigured")
    inner class AutoStart {

        @Test
        fun `returns false when not configured`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(false)
            assertFalse(service.autoStartIfConfigured().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `returns true when already running`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            assertTrue(service.autoStartIfConfigured().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `calls startServer when configured and not running`() {
            Mockito.`when`(mockSettingsService.isConfigured()).thenReturn(true)
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            Mockito.`when`(mockProxyServer.start()).thenReturn(CompletableFuture.completedFuture(true))
            assertTrue(service.autoStartIfConfigured().get(2, TimeUnit.SECONDS))
        }
    }

    @Nested
    @DisplayName("Lifecycle: forceStartServer")
    inner class ForceStart {

        @Test
        fun `returns true when already running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            assertTrue(service.forceStartServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `starts server when not running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            Mockito.`when`(mockProxyServer.start()).thenReturn(CompletableFuture.completedFuture(true))
            assertTrue(service.forceStartServer().get(1, TimeUnit.SECONDS))
        }

        @Test
        fun `returns false when start throws ExecutionException`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            val failed = CompletableFuture<Boolean>()
            failed.completeExceptionally(RuntimeException("boom"))
            Mockito.`when`(mockProxyServer.start()).thenReturn(failed)
            assertFalse(service.forceStartServer().get(2, TimeUnit.SECONDS))
        }

        @Test
        fun `returns false when start throws IllegalStateException`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            Mockito.`when`(mockProxyServer.start()).thenThrow(IllegalStateException("bad"))
            assertFalse(service.forceStartServer().get(2, TimeUnit.SECONDS))
        }
    }

    @Nested
    @DisplayName("Configuration instructions")
    inner class ConfigInstructions {

        @Test
        fun `returns detailed instructions when server is running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            val text = service.getAIAssistantConfigurationInstructions()
            assertTrue(text.contains("http://127.0.0.1:8081/v1/"))
            assertTrue(text.contains("8081"))
            assertTrue(text.contains("Third-party AI providers"))
        }

        @Test
        fun `returns short message when server is stopped`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            val text = service.getAIAssistantConfigurationInstructions()
            assertTrue(text.contains("not running"))
        }
    }

    @Nested
    @DisplayName("dispose")
    inner class Dispose {

        @Test
        fun `dispose stops the server when it is running`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            Mockito.`when`(mockProxyServer.stop()).thenReturn(CompletableFuture.completedFuture(true))
            service.dispose()
            Mockito.verify(mockProxyServer).stop()
        }

        @Test
        fun `dispose is a no-op for the server when it is stopped`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = false))
            service.dispose()
            Mockito.verify(mockProxyServer, Mockito.never()).stop()
        }

        @Test
        fun `dispose swallows ExecutionException from stop`() {
            Mockito.`when`(mockProxyServer.getStatus()).thenReturn(status(running = true))
            val failed = CompletableFuture<Boolean>()
            failed.completeExceptionally(RuntimeException("boom"))
            Mockito.`when`(mockProxyServer.stop()).thenReturn(failed)
            service.dispose() // must not throw
        }
    }
}
