package org.zhavoronkov.openrouter.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel

/**
 * Platform test for [StatusTabPanel].
 *
 * Extends [BasePlatformTestCase] so a real IntelliJ [com.intellij.openapi.project.Project]
 * is available for the panel's "Configure" button, which opens the settings dialog for
 * that project. The OpenRouter services are still mocked to drive the unconfigured state.
 */
class OpenRouterToolWindowContentPlatformTest : BasePlatformTestCase() {

    fun testUnconfiguredStateSetsLabels() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val openRouterService = mock(OpenRouterService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)

        val statusTab = StatusTabPanel(project, settingsService, openRouterService)
        try {
            assertEquals("Not configured", statusTab.getStatusTextForTest())
            assertEquals("N/A", statusTab.getQuotaTextForTest())
            assertEquals("N/A", statusTab.getUsageTextForTest())
            assertEquals("N/A", statusTab.getActivityTextForTest())
        } finally {
            statusTab.dispose()
        }
    }
}
