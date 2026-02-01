package org.zhavoronkov.openrouter.proxy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.ProxySettingsManager

@DisplayName("OpenRouterProxyServer Tests")
class OpenRouterProxyServerTest {

    @Test
    fun `getStatus should reflect running flag`() {
        val proxyServer = OpenRouterProxyServer()
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val proxyManager = mock(ProxySettingsManager::class.java)
        `when`(settingsService.proxyManager).thenReturn(proxyManager)
        `when`(settingsService.isConfigured()).thenReturn(true)
        proxyServer.setDependenciesForTests(mock(OpenRouterService::class.java), settingsService)

        val status = proxyServer.getStatus()

        assertFalse(status.isRunning)
        assertTrue(status.isConfigured)
    }
}
