package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * A settings page as its [PageConfigurable] drives it: build the component, report whether it
 * differs from what is stored, store it, or show what is stored again. Nothing to release unless a
 * page says otherwise.
 */
interface SettingsPage : Disposable {
    fun createPanel(): JComponent
    fun isModified(): Boolean
    fun apply()
    fun reset()

    override fun dispose() = Unit
}

/**
 * The Configurable for one [SettingsPage] under Tools → OpenRouter: [displayName] is the page's
 * title in the Settings tree, and [createPage] builds a fresh page each time the Settings dialog
 * opens it.
 */
abstract class PageConfigurable<P : SettingsPage>(
    private val displayName: String,
    private val createPage: () -> P
) : Configurable {

    private var page: P? = null

    override fun getDisplayName(): String = displayName

    override fun createComponent(): JComponent = createPage().also { page = it }.createPanel()

    override fun isModified(): Boolean = page?.isModified() ?: false

    override fun apply() {
        page?.apply()
    }

    override fun reset() {
        page?.reset()
    }

    override fun disposeUIResources() {
        page?.let { Disposer.dispose(it) }
        page = null
    }
}
