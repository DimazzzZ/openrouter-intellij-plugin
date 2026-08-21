package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.AuthScope
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JSpinner

/**
 * Comprehensive UI tests for OpenRouterSettingsPanel
 * Tests verify that all UI components are present and functional
 */
class OpenRouterSettingsPanelUIPlatformTest : BasePlatformTestCase() {

    private lateinit var settingsPanel: OpenRouterSettingsPanel

    override fun setUp() {
        super.setUp()
        settingsPanel = OpenRouterSettingsPanel()
    }

    fun testPanelCreation() {
        val panel = settingsPanel.getPanel()
        assertNotNull("Settings panel should not be null", panel)
    }

    fun testRunSetupWizardButtonExists() {
        val panel = settingsPanel.getPanel()
        val button = findComponentByText(panel, JButton::class.java, "Run Setup Wizard")
        assertNotNull("Run Setup Wizard button should exist", button)
    }

    fun testAuthenticationScopeRadioButtonsExist() {
        val panel = settingsPanel.getPanel()

        // Auth scope selection moved to the Setup Wizard; the panel now shows the
        // current scope as a read-only status label instead of radio buttons.
        val labels = findAllComponents(panel, JLabel::class.java)
        val authStatusLabel = labels.find {
            it.text.contains("API Key", ignoreCase = true) ||
                it.text.contains("Extended", ignoreCase = true) ||
                it.text.contains("Not Configured", ignoreCase = true)
        }
        assertNotNull("Authentication status label should exist", authStatusLabel)
    }

    fun testApiKeyFieldExists() {
        val panel = settingsPanel.getPanel()

        // API key input moved to the Setup Wizard; verify the wizard entry point exists.
        val setupButton = findComponentByText(panel, JButton::class.java, "Setup Wizard")
        assertNotNull(
            "Setup Wizard button should exist for API key management",
            setupButton
        )
    }

    fun testProvisioningKeyFieldExists() {
        val panel = settingsPanel.getPanel()

        // Provisioning key input moved to the Setup Wizard; verify the wizard entry point.
        val setupButton = findComponentByText(panel, JButton::class.java, "Setup Wizard")
        assertNotNull(
            "Setup Wizard button should exist for provisioning key management",
            setupButton
        )
    }

    fun testPasteButtonsExist() {
        val panel = settingsPanel.getPanel()

        // Paste buttons were part of the old inline key fields, now removed.
        // The panel's action buttons are the proxy-server controls.
        val buttons = findAllComponents(panel, JButton::class.java)
        val proxyButtons = buttons.filter {
            it.text.contains("Start", ignoreCase = true) ||
                it.text.contains("Stop", ignoreCase = true) ||
                it.text.contains("Copy", ignoreCase = true)
        }
        assertTrue("Proxy server control buttons should exist", proxyButtons.size >= 2)
    }

    fun testGeneralSettingsComponentsExist() {
        val panel = settingsPanel.getPanel()

        // Check for refresh interval spinner
        val spinners = findAllComponents(panel, JSpinner::class.java)
        assertTrue("Spinners should exist for settings", spinners.isNotEmpty())

        // Check for checkboxes
        val checkboxes = findAllComponents(panel, JCheckBox::class.java)
        assertTrue("Checkboxes should exist for settings", checkboxes.isNotEmpty())
    }

    fun testProxyServerComponentsExist() {
        val panel = settingsPanel.getPanel()

        val startButton = findComponentByText(panel, JButton::class.java, "Start")
        val stopButton = findComponentByText(panel, JButton::class.java, "Stop")
        val copyButton = findComponentByText(panel, JButton::class.java, "Copy")

        assertNotNull("Start Proxy Server button should exist", startButton)
        assertNotNull("Stop Proxy Server button should exist", stopButton)
        assertNotNull("Copy Proxy URL button should exist", copyButton)
    }

    fun testProxyStatusLabelExists() {
        val panel = settingsPanel.getPanel()
        val labels = findAllComponents(panel, JLabel::class.java)

        val statusLabel = labels.find {
            it.text.contains("Status") || it.text.contains("Stopped") || it.text.contains("Running")
        }
        assertNotNull("Proxy status label should exist", statusLabel)
    }

    fun testSetAuthScopeUpdatesUI() {
        // Set to REGULAR
        settingsPanel.setAuthScope(AuthScope.REGULAR)
        assertEquals("AuthScope should be REGULAR", AuthScope.REGULAR, settingsPanel.getAuthScope())

        // Set to EXTENDED
        settingsPanel.setAuthScope(AuthScope.EXTENDED)
        assertEquals("AuthScope should be EXTENDED", AuthScope.EXTENDED, settingsPanel.getAuthScope())
    }

    fun testApiKeyGetterAndSetter() {
        val testKey = "test-api-key-123"
        settingsPanel.setApiKey(testKey)
        assertEquals("API key should match", testKey, settingsPanel.getApiKey())
    }

    fun testProvisioningKeyGetterAndSetter() {
        val testKey = "test-provisioning-key-456"
        settingsPanel.setProvisioningKey(testKey)
        assertEquals("Provisioning key should match", testKey, settingsPanel.getProvisioningKey())
    }

    fun testAutoRefreshGetterAndSetter() {
        settingsPanel.setAutoRefresh(true)
        assertTrue("Auto refresh should be enabled", settingsPanel.isAutoRefreshEnabled())

        settingsPanel.setAutoRefresh(false)
        assertFalse("Auto refresh should be disabled", settingsPanel.isAutoRefreshEnabled())
    }

    fun testRefreshIntervalGetterAndSetter() {
        val testInterval = 120
        settingsPanel.setRefreshInterval(testInterval)
        assertEquals("Refresh interval should match", testInterval, settingsPanel.getRefreshInterval())
    }

    fun testShowCostsGetterAndSetter() {
        settingsPanel.setShowCosts(true)
        assertTrue("Show costs should be enabled", settingsPanel.shouldShowCosts())

        settingsPanel.setShowCosts(false)
        assertFalse("Show costs should be disabled", settingsPanel.shouldShowCosts())
    }

    fun testDefaultMaxTokensGetterAndSetter() {
        settingsPanel.setDefaultMaxTokensEnabled(true)
        assertTrue("Default max tokens should be enabled", settingsPanel.isDefaultMaxTokensEnabled())

        val testTokens = 4000
        settingsPanel.setDefaultMaxTokens(testTokens)
        assertEquals("Default max tokens should match", testTokens, settingsPanel.getDefaultMaxTokens())

        settingsPanel.setDefaultMaxTokensEnabled(false)
        assertFalse("Default max tokens should be disabled", settingsPanel.isDefaultMaxTokensEnabled())
    }

    fun testProxySettingsGettersAndSetters() {
        settingsPanel.setProxyAutoStart(true)
        assertTrue("Proxy auto start should be enabled", settingsPanel.getProxyAutoStart())

        settingsPanel.setUseSpecificPort(true)
        assertTrue("Use specific port should be enabled", settingsPanel.getUseSpecificPort())

        val testPort = 9090
        settingsPanel.setProxyPort(testPort)
        assertEquals("Proxy port should match", testPort, settingsPanel.getProxyPort())

        val testRangeStart = 8880
        val testRangeEnd = 8899
        settingsPanel.setProxyPortRangeStart(testRangeStart)
        settingsPanel.setProxyPortRangeEnd(testRangeEnd)
        assertEquals("Proxy port range start should match", testRangeStart, settingsPanel.getProxyPortRangeStart())
        assertEquals("Proxy port range end should match", testRangeEnd, settingsPanel.getProxyPortRangeEnd())
    }

    // Helper methods to find components
    private fun <T : Component> findComponentByText(container: Container, componentClass: Class<T>, text: String): T? {
        return findAllComponentsByText(container, componentClass, text).firstOrNull()
    }

    private fun <T : Component> findAllComponentsByText(
        container: Container,
        componentClass: Class<T>,
        text: String
    ): List<T> {
        val components = mutableListOf<T>()
        findComponentsByTextRecursive(container, componentClass, text, components)
        return components
    }

    private fun <T : Component> findComponentsByTextRecursive(
        container: Container,
        componentClass: Class<T>,
        text: String,
        results: MutableList<T>
    ) {
        for (component in container.components) {
            if (componentClass.isInstance(component)) {
                @Suppress("UNCHECKED_CAST")
                val typedComponent = component as T
                val componentText = when (typedComponent) {
                    is JButton -> typedComponent.text
                    is JLabel -> typedComponent.text
                    is JRadioButton -> typedComponent.text
                    is JCheckBox -> typedComponent.text
                    else -> null
                }
                if (componentText != null && componentText.contains(text, ignoreCase = true)) {
                    results.add(typedComponent)
                }
            }
            if (component is Container) {
                findComponentsByTextRecursive(component, componentClass, text, results)
            }
        }
    }

    private fun <T : Component> findAllComponents(container: Container, componentClass: Class<T>): List<T> {
        val components = mutableListOf<T>()
        findComponentsRecursive(container, componentClass, components)
        return components
    }

    private fun <T : Component> findComponentsRecursive(
        container: Container,
        componentClass: Class<T>,
        results: MutableList<T>
    ) {
        for (component in container.components) {
            if (componentClass.isInstance(component)) {
                @Suppress("UNCHECKED_CAST")
                results.add(component as T)
            }
            if (component is Container) {
                findComponentsRecursive(component, componentClass, results)
            }
        }
    }
}
