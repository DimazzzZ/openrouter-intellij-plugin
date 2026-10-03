package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel

@DisplayName("PageConfigurable")
class PageConfigurableTest {

    private class FakePage : SettingsPage {
        var modified = false
        val calls = mutableListOf<String>()

        override fun createPanel(): JComponent = JPanel().also { calls += "create" }
        override fun isModified(): Boolean = modified
        override fun apply() {
            calls += "apply"
        }

        override fun reset() {
            calls += "reset"
        }

        override fun dispose() {
            calls += "dispose"
        }
    }

    private val pages = mutableListOf<FakePage>()
    private val configurable = object : PageConfigurable<FakePage>("Fake", { FakePage().also { pages += it } }) {}

    @Test
    fun `before the page is built there is nothing to store, show or release`() {
        assertFalse(configurable.isModified())

        configurable.apply()
        configurable.reset()
        configurable.disposeUIResources()

        assertTrue(pages.isEmpty())
    }

    @Test
    fun `a built page is asked whether it changed, stored, shown again and released`() {
        configurable.createComponent()
        val page = pages.single()

        page.modified = true
        assertTrue(configurable.isModified())
        configurable.apply()
        configurable.reset()
        configurable.disposeUIResources()

        assertEquals(listOf("create", "apply", "reset", "dispose"), page.calls)
        assertFalse(configurable.isModified(), "a released page is no longer asked")
    }

    @Test
    fun `each opening of the Settings dialog builds a fresh page`() {
        configurable.createComponent()
        configurable.disposeUIResources()
        configurable.createComponent()

        assertEquals(2, pages.size)
        assertEquals("Fake", configurable.displayName)
    }
}
