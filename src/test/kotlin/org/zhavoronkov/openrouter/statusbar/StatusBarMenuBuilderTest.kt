package org.zhavoronkov.openrouter.statusbar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.ConnectionStatus

@DisplayName("StatusBarMenuBuilder Tests")
class StatusBarMenuBuilderTest {

    @Test
    fun `buildMenuItems should show login when not configured`() {
        val items = StatusBarMenuBuilder.buildMenuItems(
            connectionStatus = ConnectionStatus.NOT_CONFIGURED,
            isConfigured = false,
            authScope = AuthScope.REGULAR
        )

        val texts = items.map { it.text }
        assertTrue(texts.contains(OpenRouterStatusBarWidget.LOGIN_MENU_TEXT))
    }

    @Test
    fun `buildMenuItems should enable quota when extended`() {
        val items = StatusBarMenuBuilder.buildMenuItems(
            connectionStatus = ConnectionStatus.READY,
            isConfigured = true,
            authScope = AuthScope.EXTENDED
        )

        val quotaItem = items.first { it.text == OpenRouterStatusBarWidget.QUOTA_MENU_TEXT }
        assertTrue(quotaItem.isActionEnabled)
    }

    @Test
    fun `buildMenuItems should disable quota when regular`() {
        val items = StatusBarMenuBuilder.buildMenuItems(
            connectionStatus = ConnectionStatus.READY,
            isConfigured = true,
            authScope = AuthScope.REGULAR
        )

        val quotaItem = items.first { it.text.contains("Monitoring Disabled") }
        assertEquals(false, quotaItem.isActionEnabled)
    }
}
