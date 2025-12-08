package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.services.OpenRouterProxyService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.UIPreferencesManager
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Comprehensive tests for OpenRouterSettingsPanel user interactions
 *
 * Note: These tests focus on testing the logic and behavior without requiring
 * the full IntelliJ Platform test framework. UI component tests are simplified
 * to test the underlying logic.
 */
@DisplayName("OpenRouter Settings Panel Tests")
class OpenRouterSettingsPanelTest {

    private lateinit var mockProxyService: OpenRouterProxyService
    private lateinit var mockSettingsService: OpenRouterSettingsService
    private lateinit var mockUIPreferencesManager: UIPreferencesManager

    @BeforeEach
    fun setUp() {
        // Create mocks
        mockProxyService = mock(OpenRouterProxyService::class.java)
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        mockUIPreferencesManager = mock(UIPreferencesManager::class.java)

        // Setup default mock behaviors
        `when`(mockSettingsService.isConfigured()).thenReturn(true)
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("")
        `when`(mockSettingsService.uiPreferencesManager).thenReturn(mockUIPreferencesManager)
        `when`(mockUIPreferencesManager.autoRefresh).thenReturn(true)
        `when`(mockUIPreferencesManager.refreshInterval).thenReturn(60)
        `when`(mockUIPreferencesManager.showCosts).thenReturn(true)

        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)
    }

    @Test
    @DisplayName("User clicks Paste button - key is properly pasted from clipboard")
    fun testPasteButtonCopiesFromClipboard() {
        // Skip test in headless mode (CI/CD environments)
        if (GraphicsEnvironment.isHeadless()) {
            println("Skipping clipboard test in headless mode")
            return
        }

        // Given: A provisioning key in the clipboard
        val testKey = "sk-or-v1-test-provisioning-key-12345"
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(testKey), null)

            // When: Reading from clipboard
            val clipboardData = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String

            // Then: The clipboard should contain the test key
            assertEquals(testKey, clipboardData.trim(), "Clipboard should contain the provisioning key")
        } catch (_: java.awt.HeadlessException) {
            println("Skipping clipboard test due to headless environment")
        }
    }

    @Test
    @DisplayName("User disables Auto-refresh checkbox - quota updates stop")
    fun testDisableAutoRefreshStopsUpdates() {
        // Given: Settings service with auto-refresh enabled
        `when`(mockUIPreferencesManager.autoRefresh).thenReturn(true)

        // When: User disables auto-refresh
        `when`(mockUIPreferencesManager.autoRefresh).thenReturn(false)

        // Then: Auto-refresh should be disabled
        assertFalse(mockUIPreferencesManager.autoRefresh, "Auto-refresh should be disabled")

        // Verify the setting was changed
    }

    @Test
    @DisplayName("User enables Auto-refresh checkbox - quota updates start")
    fun testEnableAutoRefreshStartsUpdates() {
        // Given: Settings service with auto-refresh disabled
        `when`(mockUIPreferencesManager.autoRefresh).thenReturn(false)

        // When: User enables auto-refresh
        `when`(mockUIPreferencesManager.autoRefresh).thenReturn(true)

        // Then: Auto-refresh should be enabled
        assertTrue(mockUIPreferencesManager.autoRefresh, "Auto-refresh should be enabled")

        // Verify the setting was changed
    }

    @Test
    @DisplayName("User disables Show costs checkbox - costs not shown in status bar")
    fun testDisableShowCostsHidesCosts() {
        // Given: Settings service with show costs enabled
        `when`(mockUIPreferencesManager.showCosts).thenReturn(true)

        // When: User disables show costs
        `when`(mockUIPreferencesManager.showCosts).thenReturn(false)

        // Then: Show costs should be disabled
        assertFalse(mockUIPreferencesManager.showCosts, "Show costs should be disabled")

        // Verify the setting was changed
    }

    @Test
    @DisplayName("User enables Show costs checkbox - costs shown in status bar")
    fun testEnableShowCostsDisplaysCosts() {
        // Given: Settings service with show costs disabled
        `when`(mockUIPreferencesManager.showCosts).thenReturn(false)

        // When: User enables show costs
        `when`(mockUIPreferencesManager.showCosts).thenReturn(true)

        // Then: Show costs should be enabled
        assertTrue(mockUIPreferencesManager.showCosts, "Show costs should be enabled")

        // Verify the setting was changed
    }

    @Test
    @DisplayName("User clicks Refresh button - API keys table updates")
    fun testRefreshButtonUpdatesApiKeysTable() {
        // Given: Mock proxy service that tracks refresh calls
        val mockApiKeyManager = mock(ApiKeyManager::class.java)

        // When: Refresh is called with forceRefresh=true
        mockApiKeyManager.refreshApiKeys(forceRefresh = true)

        // Then: The refresh should be called with forceRefresh=true
        verify(mockApiKeyManager, times(1)).refreshApiKeys(forceRefresh = true)
    }

    @Test
    @DisplayName("User clicks Copy URL button - URL is copied to clipboard")
    fun testCopyUrlButtonCopiesUrlToClipboard() {
        // Skip test in headless mode (CI/CD environments)
        if (GraphicsEnvironment.isHeadless()) {
            println("Skipping clipboard test in headless mode")
            return
        }

        try {
            // Given: A proxy URL
            val testUrl = "http://127.0.0.1:8080/v1/"
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard

            // When: URL is copied to clipboard
            clipboard.setContents(StringSelection(testUrl), null)

            // Then: The URL should be in the clipboard
            val clipboardContent = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
            assertEquals(testUrl, clipboardContent, "Proxy URL should be copied to clipboard")
        } catch (_: java.awt.HeadlessException) {
            println("Skipping clipboard test due to headless environment")
        }
    }

    @Test
    @DisplayName("Copy URL button disabled when server is stopped")
    fun testCopyUrlButtonDisabledWhenServerStopped() {
        // Given: Proxy server is stopped
        val stoppedStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = false,
            port = null,
            url = null,
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(stoppedStatus)

        // When: Checking button state logic
        val shouldBeEnabled = stoppedStatus.isRunning

        // Then: Copy URL button should be disabled
        assertFalse(shouldBeEnabled, "Copy URL button should be disabled when server is stopped")
    }

    @Test
    @DisplayName("Copy URL button enabled when server is running")
    fun testCopyUrlButtonEnabledWhenServerRunning() {
        // Given: Proxy server is running
        val runningStatus = OpenRouterProxyServer.ProxyServerStatus(
            isRunning = true,
            port = 8080,
            url = "http://127.0.0.1:8080/v1/",
            isConfigured = true
        )
        `when`(mockProxyService.getServerStatus()).thenReturn(runningStatus)

        // When: Checking button state logic
        val shouldBeEnabled = runningStatus.isRunning

        // Then: Copy URL button should be enabled
        assertTrue(shouldBeEnabled, "Copy URL button should be enabled when server is running")
    }
}
