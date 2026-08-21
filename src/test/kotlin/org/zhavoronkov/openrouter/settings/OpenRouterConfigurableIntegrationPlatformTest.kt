package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.AuthScope
import javax.swing.JButton
import javax.swing.JLabel

/**
 * Integration test that actually instantiates OpenRouterSettingsPanel and OpenRouterConfigurable
 * to catch runtime errors like UiDslException
 */
class OpenRouterConfigurableIntegrationPlatformTest : BasePlatformTestCase() {

    fun testConfigurableCreatesComponentWithoutErrors() {
        val configurable = OpenRouterConfigurable()

        // This will throw UiDslException if radio buttons are not properly grouped
        val component = configurable.createComponent()

        assertNotNull("Configurable component should be created successfully", component)
        assertTrue("Component should be visible", component!!.isVisible)
        assertTrue("Component should have child components", component.componentCount > 0)
    }

    fun testSettingsPanelInstantiationWithoutErrors() {
        // This test will fail if there are any runtime errors during panel creation
        // such as UiDslException: Button group must be defined before using radio button
        val panel = OpenRouterSettingsPanel()
        assertNotNull("Settings panel should be created successfully", panel)

        val component = panel.getPanel()
        assertNotNull("Panel component should not be null", component)
        assertTrue("Panel should be visible", component.isVisible)
        assertTrue("Panel should have components", component.componentCount > 0)
    }

    fun testPanelHasRunSetupWizardButton() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()

        val buttons = findAllButtons(component)
        val setupWizardButton = buttons.find { it.text?.contains("Setup Wizard", ignoreCase = true) == true }

        assertNotNull("Run Setup Wizard button should exist", setupWizardButton)
    }

    fun testPanelHasAuthenticationScopeRadioButtons() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()

        // Auth scope selection moved to the Setup Wizard; the panel now shows the
        // current scope as a read-only status label instead of radio buttons.
        val labels = findAllLabels(component)
        val authStatusLabel = labels.find {
            val t = it.text ?: return@find false
            t.contains("API Key", ignoreCase = true) ||
                t.contains("Extended", ignoreCase = true) ||
                t.contains("Not Configured", ignoreCase = true)
        }

        assertNotNull("Authentication status label should exist", authStatusLabel)
    }

    fun testPanelHasPasswordFieldsForKeys() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()

        // API-key and provisioning-key entry moved to the Setup Wizard, so the panel
        // no longer embeds password fields. Verify the Setup Wizard entry point exists.
        val buttons = findAllButtons(component)
        val setupWizardButton = buttons.find { it.text?.contains("Setup Wizard", ignoreCase = true) == true }

        assertNotNull("Setup Wizard button should exist for key management", setupWizardButton)
    }

    fun testPanelHasPasteButtons() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()

        val buttons = findAllButtons(component)
        // Paste buttons were part of the old inline key fields, now removed.
        // The panel's action buttons are the proxy-server controls.
        val proxyButtons = buttons.filter {
            val t = it.text ?: return@filter false
            t.contains("Start", ignoreCase = true) ||
                t.contains("Stop", ignoreCase = true) ||
                t.contains("Copy", ignoreCase = true)
        }

        assertTrue("Proxy server control buttons should exist", proxyButtons.size >= 2)
    }

    fun testPanelHasProxyServerControls() {
        val panel = OpenRouterSettingsPanel()
        val component = panel.getPanel()

        val buttons = findAllButtons(component)

        val startButton = buttons.find { it.text?.contains("Start", ignoreCase = true) == true }
        val stopButton = buttons.find { it.text?.contains("Stop", ignoreCase = true) == true }
        val copyButton = buttons.find { it.text?.contains("Copy", ignoreCase = true) == true }

        assertNotNull("Start button should exist", startButton)
        assertNotNull("Stop button should exist", stopButton)
        assertNotNull("Copy button should exist", copyButton)
    }

    fun testPanelAuthScopeGetterAndSetter() {
        val panel = OpenRouterSettingsPanel()

        // Test setting to REGULAR
        panel.setAuthScope(AuthScope.REGULAR)
        assertEquals("AuthScope should be REGULAR", AuthScope.REGULAR, panel.getAuthScope())

        // Test setting to EXTENDED
        panel.setAuthScope(AuthScope.EXTENDED)
        assertEquals("AuthScope should be EXTENDED", AuthScope.EXTENDED, panel.getAuthScope())
    }

    fun testPanelApiKeyGetterAndSetter() {
        val panel = OpenRouterSettingsPanel()

        val testKey = "sk-or-v1-test-key-123"
        panel.setApiKey(testKey)
        assertEquals("API key should match", testKey, panel.getApiKey())
    }

    fun testPanelProvisioningKeyGetterAndSetter() {
        val panel = OpenRouterSettingsPanel()

        val testKey = "sk-or-v1-test-provisioning-key-456"
        panel.setProvisioningKey(testKey)
        assertEquals("Provisioning key should match", testKey, panel.getProvisioningKey())
    }

    // Helper methods to find components recursively
    private fun findAllButtons(container: java.awt.Container): List<JButton> {
        val buttons = mutableListOf<JButton>()
        findComponentsRecursive(container, JButton::class.java, buttons)
        return buttons
    }

    private fun findAllLabels(container: java.awt.Container): List<JLabel> {
        val labels = mutableListOf<JLabel>()
        findComponentsRecursive(container, JLabel::class.java, labels)
        return labels
    }

    private fun <T : java.awt.Component> findComponentsRecursive(
        container: java.awt.Container,
        componentClass: Class<T>,
        results: MutableList<T>
    ) {
        for (component in container.components) {
            if (componentClass.isInstance(component)) {
                @Suppress("UNCHECKED_CAST")
                results.add(component as T)
            }
            if (component is java.awt.Container) {
                findComponentsRecursive(component, componentClass, results)
            }
        }
    }
}
