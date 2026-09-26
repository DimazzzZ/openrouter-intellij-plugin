package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Configurable for per-router default parameter values.
 * Appears as a sub-page under Tools → OpenRouter → Router Defaults.
 */
class RouterDefaultsConfigurable : Configurable {

    private var settingsPanel: RouterDefaultsSettingsPanel? = null

    override fun getDisplayName(): String = "Router Defaults"

    override fun createComponent(): JComponent? {
        val panel = RouterDefaultsSettingsPanel()
        settingsPanel = panel
        return panel.createPanel()
    }

    override fun isModified(): Boolean = settingsPanel?.isModified() ?: false

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
