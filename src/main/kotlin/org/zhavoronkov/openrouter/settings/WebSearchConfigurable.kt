package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Configurable for tuning Web Search.
 * Appears as a sub-page under Tools → OpenRouter → Web Search.
 */
class WebSearchConfigurable : Configurable {

    private var settingsPanel: WebSearchSettingsPanel? = null

    override fun getDisplayName(): String = "Web Search"

    override fun createComponent(): JComponent {
        val panel = WebSearchSettingsPanel()
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
