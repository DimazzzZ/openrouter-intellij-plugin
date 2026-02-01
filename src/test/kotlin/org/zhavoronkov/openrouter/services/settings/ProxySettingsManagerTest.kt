package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("ProxySettingsManager Tests")
class ProxySettingsManagerTest {

    @Test
    fun `setProxyPortRange should update settings`() {
        val settings = OpenRouterSettings()
        var changes = 0
        val manager = ProxySettingsManager(settings) { changes++ }

        manager.setProxyPortRange(2000, 2001)

        assertEquals(2000, manager.getProxyPortRangeStart())
        assertEquals(2001, manager.getProxyPortRangeEnd())
        assertEquals(1, changes)
    }

    @Test
    fun `setProxyPort should reject invalid`() {
        val settings = OpenRouterSettings()
        val manager = ProxySettingsManager(settings) { }

        assertThrows(IllegalArgumentException::class.java) {
            manager.setProxyPort(1)
        }
    }
}
