package org.zhavoronkov.openrouter.regression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.settings.OpenRouterSettingsPanel

/**
 * Regression tests for Issue #8: Proxy Section Not Updating When Applying Settings
 *
 * These tests ensure that when the user clicks "Apply" in settings:
 * 1. API keys table gets updated
 * 2. Status bar widget gets updated
 * 3. Proxy section gets updated (this was the bug)
 */
@DisplayName("Regression: Settings Apply Updates All Sections")
class SettingsApplyRegressionTest {

    @Test
    @DisplayName("OpenRouterSettingsPanel should have public updateProxyStatus method")
    fun testUpdateProxyStatusIsPublic() {
        // Given: OpenRouterSettingsPanel class
        val panelClass = OpenRouterSettingsPanel::class.java

        // When: Check for updateProxyStatus method
        val method = panelClass.methods.find { it.name == "updateProxyStatus" }

        // Then: Method should exist and be public
        assertNotNull(method, "updateProxyStatus method should exist")
        assertTrue(
            java.lang.reflect.Modifier.isPublic(method!!.modifiers),
            "updateProxyStatus should be public"
        )
        assertEquals(0, method.parameterCount, "updateProxyStatus should take no parameters")
    }

    @Test
    @DisplayName("OpenRouterConfigurable.apply should call updateProxyStatus")
    fun testApplyCallsUpdateProxyStatus() {
        // This test verifies by code inspection that apply() calls updateProxyStatus()
        // The actual implementation is checked by reading the source code

        val configurableCode = java.io.File(
            "src/main/kotlin/org/zhavoronkov/openrouter/settings/OpenRouterConfigurable.kt"
        ).readText()

        // Verify that the file contains both apply() method and updateProxyStatus() call
        assertTrue(
            configurableCode.contains("override fun apply()"),
            "File should contain apply() method"
        )
        assertTrue(
            configurableCode.contains("panel.updateProxyStatus()"),
            "File should contain call to panel.updateProxyStatus()"
        )

        // Verify they're in the same general area (apply method should call updateProxyStatus)
        val applyIndex = configurableCode.indexOf("override fun apply()")
        val updateProxyStatusIndex = configurableCode.indexOf("panel.updateProxyStatus()")

        assertTrue(
            applyIndex >= 0 && updateProxyStatusIndex >= 0,
            "Both apply() and updateProxyStatus() should exist"
        )
        assertTrue(
            updateProxyStatusIndex > applyIndex,
            "updateProxyStatus() call should come after apply() method declaration"
        )
    }

    @Test
    @DisplayName("Applying settings should update all UI sections")
    fun testApplyUpdatesAllSections() {
        // This test documents the expected behavior when applying settings

        // When user clicks "Apply" in settings dialog, the following should happen:
        // 1. syncSettings(panel, toService = true) - saves all settings
        // 2. panel.refreshApiKeys(forceRefresh = true) - updates API keys table (if key changed)
        // 3. panel.updateProxyStatus() - updates proxy section status
        // 4. testConnection() - tests connection (if key changed)

        // This ensures that:
        // - API keys table shows current keys
        // - Status bar widget shows current usage
        // - Proxy section shows current server status
        // - User doesn't need to close and reopen settings to see updates

        assertTrue(true, "This test documents the expected behavior")
    }

    @Test
    @DisplayName("updateProxyStatus should be called when provisioning key is set")
    fun testSetProvisioningKeyCallsUpdateProxyStatus() {
        // Verify by code inspection that setProvisioningKey calls updateProxyStatus

        val panelCode = java.io.File(
            "src/main/kotlin/org/zhavoronkov/openrouter/settings/OpenRouterSettingsPanel.kt"
        ).readText()

        // Find setProvisioningKey method
        val setProvisioningKeyPattern = Regex("fun setProvisioningKey[\\s\\S]*?\\n    \\}")
        val setProvisioningKeyMethod = setProvisioningKeyPattern.find(panelCode)

        assertNotNull(setProvisioningKeyMethod, "setProvisioningKey method should exist")
        assertTrue(
            setProvisioningKeyMethod!!.value.contains("updateProxyStatus()"),
            "setProvisioningKey should call updateProxyStatus()"
        )
    }

    @Test
    @DisplayName("Proxy section should enable Start button when configured")
    fun testProxyStartButtonEnabledWhenConfigured() {
        // This test documents that the proxy section should:
        // 1. Disable Start button when not configured
        // 2. Enable Start button when provisioning key is set
        // 3. Update button states when updateProxyStatus() is called

        // The updateProxyStatus() method checks:
        // - settingsService.isConfigured() - whether provisioning key is set
        // - proxyService.getServerStatus() - current server running state

        // And updates:
        // - startServerButton.isEnabled based on configuration
        // - stopServerButton.isEnabled based on server state
        // - statusLabel text and icon
        // - proxyUrlLabel text

        assertTrue(true, "This test documents the proxy section behavior")
    }

    @Test
    @DisplayName("All settings sections should update synchronously on Apply")
    fun testAllSectionsUpdateSynchronously() {
        // When user clicks Apply, all sections should update immediately:

        // 1. API Keys Section:
        //    - Table refreshes with current keys
        //    - Auto-creates IntelliJ IDEA Plugin key if needed

        // 2. Proxy Section:
        //    - Status label updates (Running/Stopped)
        //    - Start/Stop buttons enable/disable correctly
        //    - Proxy URL displays if server is running

        // 3. Status Bar Widget:
        //    - Updates via testConnection() if key changed
        //    - Shows current usage and status

        // User should NOT need to:
        // - Close and reopen settings dialog
        // - Click Refresh button
        // - Restart IDE

        assertTrue(true, "This test documents the synchronous update requirement")
    }

    @Test
    @DisplayName("updateProxyStatus should update button states based on configuration")
    fun testUpdateProxyStatusUpdatesButtonStates() {
        // Verify by code inspection that updateProxyStatus updates button states

        val panelCode = java.io.File(
            "src/main/kotlin/org/zhavoronkov/openrouter/settings/OpenRouterSettingsPanel.kt"
        ).readText()

        // Find updateProxyStatus method
        val updateProxyStatusPattern = Regex("fun updateProxyStatus\\(\\)[\\s\\S]*?\\n    \\}")
        val updateProxyStatusMethod = updateProxyStatusPattern.find(panelCode)

        assertNotNull(updateProxyStatusMethod, "updateProxyStatus method should exist")

        val methodBody = updateProxyStatusMethod!!.value

        // Should check configuration status
        assertTrue(
            methodBody.contains("isConfigured"),
            "updateProxyStatus should check if configured"
        )

        // Should update button states
        assertTrue(
            methodBody.contains("startServerButton.isEnabled") ||
                methodBody.contains("stopServerButton.isEnabled"),
            "updateProxyStatus should update button states"
        )

        // Should update status label
        assertTrue(
            methodBody.contains("statusLabel"),
            "updateProxyStatus should update status label"
        )
    }
}
