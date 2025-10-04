package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Configurable for OpenRouter Favorite Models settings
 * This appears as a sub-page under Tools -> OpenRouter -> Favorite Models
 *
 * Uses compact UI DSL v2 panel with provisioning key guard
 */
class FavoriteModelsConfigurable : Configurable {

    private var settingsPanel: FavoriteModelsSettingsPanel? = null

    override fun getDisplayName(): String = "Favorite Models"

    override fun createComponent(): JComponent? {
        val panel = FavoriteModelsSettingsPanel()
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
