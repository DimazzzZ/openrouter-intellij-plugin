package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Configurable for Provider Routing settings.
 * Appears as a sub-page under Tools → OpenRouter → Provider Routing.
 */
class ProviderRoutingConfigurable : Configurable {

    private var settingsPanel: ProviderRoutingSettingsPanel? = null

    override fun getDisplayName(): String = "Provider Routing"

    override fun createComponent(): JComponent? {
        val panel = ProviderRoutingSettingsPanel()
        settingsPanel = panel
        return panel.createPanel()
    }

    override fun isModified(): Boolean {
        return settingsPanel?.isModified() ?: false
    }

    override fun apply() {
        settingsPanel?.apply()
    }

    override fun reset() {
        settingsPanel?.reset()
    }

    override fun disposeUIResources() {
        settingsPanel?.let { Disposer.dispose(it) }
        settingsPanel = null
    }
}
