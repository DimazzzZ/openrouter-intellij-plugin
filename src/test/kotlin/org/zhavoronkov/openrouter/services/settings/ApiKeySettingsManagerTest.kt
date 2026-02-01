package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("ApiKeySettingsManager Tests")
class ApiKeySettingsManagerTest {

    @Test
    fun `setApiKey should store and retrieve`() {
        val settings = OpenRouterSettings()
        var changes = 0
        val manager = ApiKeySettingsManager(settings) { changes++ }

        manager.setApiKey("sk-or-test")

        assertEquals("sk-or-test", manager.getApiKey())
        assertEquals(1, changes)
    }

    @Test
    fun `validateKeyForCurrentScope should return message when missing`() {
        val settings = OpenRouterSettings(authScope = AuthScope.REGULAR)
        val manager = ApiKeySettingsManager(settings) { }

        val result = manager.validateKeyForCurrentScope()

        assertNotNull(result)
    }
}
