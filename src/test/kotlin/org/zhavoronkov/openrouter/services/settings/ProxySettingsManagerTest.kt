package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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

    private fun manager(): ProxySettingsManager =
        ProxySettingsManager(OpenRouterSettings()) { }.apply { setProxyPortRange(2000, 3000) }

    @ParameterizedTest(name = "start {0}")
    @ValueSource(ints = [1023, 65536, 3001])
    fun `setProxyPortRangeStart rejects a port out of range or past the end`(port: Int) {
        val manager = manager()

        assertThrows(IllegalArgumentException::class.java) { manager.setProxyPortRangeStart(port) }
        assertEquals(2000, manager.getProxyPortRangeStart())
    }

    @ParameterizedTest(name = "end {0}")
    @ValueSource(ints = [1023, 65536, 1999])
    fun `setProxyPortRangeEnd rejects a port out of range or before the start`(port: Int) {
        val manager = manager()

        assertThrows(IllegalArgumentException::class.java) { manager.setProxyPortRangeEnd(port) }
        assertEquals(3000, manager.getProxyPortRangeEnd())
    }

    @Test
    fun `the range ends move one at a time within their bounds`() {
        val manager = manager()

        manager.setProxyPortRangeStart(2500)
        manager.setProxyPortRangeEnd(2500)

        assertEquals(2500, manager.getProxyPortRangeStart())
        assertEquals(2500, manager.getProxyPortRangeEnd())
    }

    @ParameterizedTest(name = "{0}..{1}")
    @CsvSource("1023, 2000", "65536, 65536", "2000, 1023", "2000, 65536", "3000, 2000")
    fun `setProxyPortRange rejects ends out of range or out of order`(start: Int, end: Int) {
        val manager = manager()

        assertThrows(IllegalArgumentException::class.java) { manager.setProxyPortRange(start, end) }
        assertEquals(2000, manager.getProxyPortRangeStart())
    }
}
