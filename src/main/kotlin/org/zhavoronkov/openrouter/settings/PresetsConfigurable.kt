package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Configurable for OpenRouter Presets settings
 * This appears as a sub-page under Tools -> OpenRouter -> Presets
 */
class PresetsConfigurable : Configurable {

    private var settingsPanel: PresetsSettingsPanel? = null

    override fun getDisplayName(): String = "Presets"

    override fun createComponent(): JComponent? {
        val panel = PresetsSettingsPanel()
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
