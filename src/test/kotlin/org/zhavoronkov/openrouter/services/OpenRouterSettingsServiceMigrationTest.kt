package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("OpenRouter Settings Service Migration Tests")
class OpenRouterSettingsServiceMigrationTest {

    @Test
    fun `loadState should promote authScope when provisioning key exists`() {
        val service = OpenRouterSettingsService()
        val state = OpenRouterSettings(
            authScope = AuthScope.REGULAR,
            provisioningKey = "pk-test"
        )

        service.loadState(state)

        assertEquals(AuthScope.EXTENDED, service.getState().authScope)
        assertNotNull(service.apiKeyManager)
    }
}
