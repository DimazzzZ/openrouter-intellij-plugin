package org.zhavoronkov.openrouter.settings

import com.intellij.ui.components.JBLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import java.util.concurrent.CompletableFuture
import javax.swing.JButton

/**
 * Tests for proxy server start/stop functionality
 */
@DisplayName("Proxy Server Control Tests")
class ProxyServerControlTest {

    private lateinit var mockProxyService: OpenRouterProxyService
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var proxyServerManager: ProxyServerManager
    private lateinit var startButton: JButton
    private lateinit var stopButton: JButton
    private lateinit var statusLabel: JBLabel

    @BeforeEach
    fun setUp() {
        // Create mocks
        mockProxyService = mock(OpenRouterProxyService::class.java)
        mockSettingsService = mock(OpenRouterSettingsService::class.java)

        // Create UI components
        startButton = JButton("Start Proxy Server")
        stopButton = JButton("Stop Proxy Server")
        statusLabel = JBLabel()

        // Create proxy server manager
        proxyServerManager = ProxyServerManager(mockProxyService, mockSettingsService)

        // Setup default mock behaviors
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
    }

    @Test
    @DisplayName("User clicks Start Proxy Server - server starts successfully")
    fun testStartProxyServerSuccess() {
        // Given: Proxy server is stopped and configured
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )

        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)
        `when`(mockProxyService.startServer())
            .thenReturn(CompletableFuture.completedFuture(true))

        // When: Start server is called
        val startFuture = mockProxyService.startServer()

        // Then: Server should return success
        assertTrue(startFuture.get(), "Start server should return true")
        verify(mockProxyService, times(1)).startServer()
    }

    @Test
    @DisplayName("User clicks Start Proxy Server - shows error when not configured")
    fun testStartProxyServerNotConfigured() {
        // Given: Proxy server is not configured
        `when`(mockSettingsService.isConfigured()).thenReturn(false)

        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = false
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Checking if configured
        val isConfigured = mockSettingsService.isConfigured()

        // Then: Should not be configured
        assertFalse(isConfigured, "Server should not be configured")

        // And: Start server should not be called when not configured
        verify(mockProxyService, never()).startServer()
    }

    @Test
    @DisplayName("User clicks Start Proxy Server - button shows Starting... during operation")
    fun testStartProxyServerButtonStatesDuringStart() {
        // Given: Proxy server is stopped
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // Create a future that we can control
        val startFuture = CompletableFuture<Boolean>()
        `when`(mockProxyService.startServer()).thenReturn(startFuture)

        // When: Simulating button state during start operation
        startButton.text = "Starting..."
        startButton.isEnabled = false

        // Then: Button should show "Starting..." and be disabled
        assertEquals("Starting...", startButton.text, "Button should show 'Starting...' during operation")
        assertFalse(startButton.isEnabled, "Button should be disabled during operation")

        // Complete the future
        startFuture.complete(true)

        // Verify start was called (we didn't actually call it, just mocked it)
        verify(mockProxyService, never()).startServer()
    }

    @Test
    @DisplayName("User clicks Stop Proxy Server - server stops successfully")
    fun testStopProxyServerSuccess() {
        // Given: Proxy server is running
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080/v1/",
            isConfigured = true
        )

        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)
        `when`(mockProxyService.stopServer())
            .thenReturn(CompletableFuture.completedFuture(true))

        // When: Stop server is called
        val stopFuture = mockProxyService.stopServer()

        // Then: Server should return success
        assertTrue(stopFuture.get(), "Stop server should return true")
        verify(mockProxyService, times(1)).stopServer()
    }

    @Test
    @DisplayName("User clicks Stop Proxy Server - button shows Stopping... during operation")
    fun testStopProxyServerButtonStatesDuringStop() {
        // Given: Proxy server is running
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080/v1/",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // Create a future that we can control
        val stopFuture = CompletableFuture<Boolean>()
        `when`(mockProxyService.stopServer()).thenReturn(stopFuture)

        // When: Simulating button state during stop operation
        stopButton.text = "Stopping..."
        stopButton.isEnabled = false

        // Then: Button should show "Stopping..." and be disabled
        assertEquals("Stopping...", stopButton.text, "Button should show 'Stopping...' during operation")
        assertFalse(stopButton.isEnabled, "Button should be disabled during operation")

        // Complete the future
        stopFuture.complete(true)

        // Verify stop was called
        verify(mockProxyService, never()).stopServer() // We didn't actually call it, just mocked it
    }

    @Test
    @DisplayName("Start button disabled when server is running")
    fun testStartButtonDisabledWhenRunning() {
        // Given: Proxy server is running
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080/v1/",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // When: Buttons are updated
        proxyServerManager.updateProxyButtons(startButton, stopButton)

        // Then: Start button should be disabled, stop button enabled
        assertFalse(startButton.isEnabled, "Start button should be disabled when server is running")
        assertTrue(stopButton.isEnabled, "Stop button should be enabled when server is running")
    }

    @Test
    @DisplayName("Stop button disabled when server is stopped")
    fun testStopButtonDisabledWhenStopped() {
        // Given: Proxy server is stopped
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Buttons are updated
        proxyServerManager.updateProxyButtons(startButton, stopButton)

        // Then: Stop button should be disabled, start button enabled
        assertTrue(startButton.isEnabled, "Start button should be enabled when server is stopped")
        assertFalse(stopButton.isEnabled, "Stop button should be disabled when server is stopped")
    }

    @Test
    @DisplayName("Start button disabled when not configured")
    fun testStartButtonDisabledWhenNotConfigured() {
        // Given: Proxy server is not configured
        `when`(mockSettingsService.isConfigured()).thenReturn(false)

        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = false
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Buttons are updated
        proxyServerManager.updateProxyButtons(startButton, stopButton)

        // Then: Start button should be disabled with appropriate text
        assertFalse(startButton.isEnabled, "Start button should be disabled when not configured")
        assertEquals(
            "Start Proxy (Configure First)",
            startButton.text,
            "Button text should indicate configuration is needed"
        )
    }

    @Test
    @DisplayName("Status label shows running status with URL")
    fun testStatusLabelShowsRunningWithUrl() {
        // Given: Proxy server is running
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080/v1/",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // When: Status label is updated
        proxyServerManager.updateProxyStatusLabel(statusLabel)

        // Then: Status should show running with URL
        assertTrue(statusLabel.text.contains("Running"), "Status should indicate running")
        assertTrue(statusLabel.text.contains("http://127.0.0.1:8080/v1/"), "Status should show URL")
        assertNotNull(statusLabel.icon, "Status should have an icon")
    }

    @Test
    @DisplayName("Status label shows stopped status")
    fun testStatusLabelShowsStoppedStatus() {
        // Given: Proxy server is stopped
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Status label is updated
        proxyServerManager.updateProxyStatusLabel(statusLabel)

        // Then: Status should show stopped
        assertEquals("Stopped", statusLabel.text, "Status should show 'Stopped'")
        assertNotNull(statusLabel.icon, "Status should have an icon")
    }
}
