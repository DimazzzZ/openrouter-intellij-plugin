package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration test that actually instantiates OpenRouterSettingsPanel
 * to catch runtime errors like UiDslException
 */
class OpenRouterSettingsPanelIntegrationTest : BasePlatformTestCase() {

    fun testPanelInstantiation() {
        // This test will fail if there are any runtime errors during panel creation
        // such as UiDslException: Button group must be defined before using radio button
        val panel = OpenRouterSettingsPanel()
        assertNotNull("Settings panel should be created successfully", panel)
        
        val component = panel.getPanel()
        assertNotNull("Panel component should not be null", component)
    }

    fun testPanelIsVisible() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()
        
        // The panel should be visible by default
        assertTrue("Panel should be visible", component.isVisible)
    }

    fun testPanelHasComponents() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()
        
        // The panel should have child components
        assertTrue("Panel should have components", component.componentCount > 0)
    }
}
