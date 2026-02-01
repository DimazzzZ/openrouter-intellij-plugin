package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("UIPreferencesManager Tests")
class UIPreferencesManagerTest {

    @Test
    fun `setters update settings and notify`() {
        val settings = OpenRouterSettings()
        var changes = 0
        val manager = UIPreferencesManager(settings) { changes++ }

        manager.autoRefresh = false
        manager.refreshInterval = 30
        manager.showCosts = false
        manager.trackGenerations = false
        manager.maxTrackedGenerations = 10
        manager.defaultMaxTokens = 123

        assertEquals(false, manager.autoRefresh)
        assertEquals(30, manager.refreshInterval)
        assertEquals(false, manager.showCosts)
        assertEquals(false, manager.trackGenerations)
        assertEquals(10, manager.maxTrackedGenerations)
        assertEquals(123, manager.defaultMaxTokens)
        assertEquals(6, changes)
    }
}
