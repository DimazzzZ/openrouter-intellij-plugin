package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

@DisplayName("OpenRouterToolWindowContent Tests")
class OpenRouterToolWindowContentTest {

    @Test
    fun `unconfigured state should set labels`() {
        val project = mock(Project::class.java)
        val settingsService = mock(OpenRouterSettingsService::class.java)
        val openRouterService = mock(OpenRouterService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)

        val content = OpenRouterToolWindowContent(project, settingsService, openRouterService)

        assertEquals("Not configured", content.getStatusTextForTest())
        assertEquals("N/A", content.getQuotaTextForTest())
        assertEquals("N/A", content.getUsageTextForTest())
        assertEquals("N/A", content.getActivityTextForTest())
    }
}
