package org.zhavoronkov.openrouter.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager
import org.zhavoronkov.openrouter.services.settings.PresetsManager

/**
 * Platform test for [OpenRouterToolWindowContent].
 *
 * Extends [BasePlatformTestCase] so a real IntelliJ [com.intellij.openapi.project.Project]
 * (with services such as PropertiesComponent registered) is available. The content builds
 * a [ChatPanel] during construction, which reads project-level platform services; a mocked
 * project cannot satisfy those, so the fixture's real project is used instead. The
 * OpenRouter services are still mocked to drive the unconfigured state.
 */
class OpenRouterToolWindowContentPlatformTest : BasePlatformTestCase() {

    fun testUnconfiguredStateSetsLabels() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val openRouterService = mock(OpenRouterService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)

        // ChatPanel (built transitively by OpenRouterToolWindowContent) reads the
        // favorite-models and presets managers during construction. Stub them so
        // panel init does not NPE on the mocked settings service.
        val favoriteModelsManager = mock(FavoriteModelsManager::class.java)
        `when`(favoriteModelsManager.getFavoriteModels()).thenReturn(emptyList())
        `when`(settingsService.favoriteModelsManager).thenReturn(favoriteModelsManager)

        val presetsManager = mock(PresetsManager::class.java)
        `when`(presetsManager.getCustomPresets()).thenReturn(emptyList())
        `when`(settingsService.presetsManager).thenReturn(presetsManager)

        val content = OpenRouterToolWindowContent(project, settingsService, openRouterService)
        try {
            assertEquals("Not configured", content.getStatusTextForTest())
            assertEquals("N/A", content.getQuotaTextForTest())
            assertEquals("N/A", content.getUsageTextForTest())
            assertEquals("N/A", content.getActivityTextForTest())
        } finally {
            content.dispose()
        }
    }
}
