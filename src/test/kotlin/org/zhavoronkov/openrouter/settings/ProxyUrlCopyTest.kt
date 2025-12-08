package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.OpenRouterProxyService

/**
 * Tests for proxy URL display and copy functionality
 */
@DisplayName("Proxy URL Copy Tests")
class ProxyUrlCopyTest {

    @Test
    @DisplayName("Should display full URL when proxy is running")
    fun testDisplaysFullUrlWhenRunning() {
        // Given: Mock proxy service with running status
        val mockProxyService = mock(OpenRouterProxyService::class.java)
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // When: Status label is updated
        val proxyManager = ProxyServerManager(mockProxyService, mock())
        val statusLabel = com.intellij.ui.components.JBLabel()
        proxyManager.updateProxyStatusLabel(statusLabel)

        // Then: Should display full URL
        assertTrue(
            statusLabel.text.contains("http://127.0.0.1:8080"),
            "Status should contain full URL"
        )
        assertTrue(
            statusLabel.text.contains("Running"),
            "Status should indicate running state"
        )
    }

    @Test
    @DisplayName("Should show stopped status when proxy is not running")
    fun testShowsStoppedWhenNotRunning() {
        // Given: Mock proxy service with stopped status
        val mockProxyService = mock(OpenRouterProxyService::class.java)
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Status label is updated
        val proxyManager = ProxyServerManager(mockProxyService, mock())
        val statusLabel = com.intellij.ui.components.JBLabel()
        proxyManager.updateProxyStatusLabel(statusLabel)

        // Then: Should show stopped status
        assertEquals("Stopped", statusLabel.text)
    }

    @Test
    @DisplayName("Should format URL with HTML for better display")
    fun testFormatsUrlWithHtml() {
        // Given: Mock proxy service with running status
        val mockProxyService = mock(OpenRouterProxyService::class.java)
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8081,
            url = "http://127.0.0.1:8081",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // When: Status label is updated
        val proxyManager = ProxyServerManager(mockProxyService, mock())
        val statusLabel = com.intellij.ui.components.JBLabel()
        proxyManager.updateProxyStatusLabel(statusLabel)

        // Then: Should use HTML formatting
        assertTrue(
            statusLabel.text.startsWith("<html>"),
            "Should use HTML formatting"
        )
        assertTrue(
            statusLabel.text.contains("<code>"),
            "Should use code formatting for URL"
        )
        assertTrue(
            statusLabel.text.contains("<b>Running:</b>"),
            "Should bold the status text"
        )
    }
}
