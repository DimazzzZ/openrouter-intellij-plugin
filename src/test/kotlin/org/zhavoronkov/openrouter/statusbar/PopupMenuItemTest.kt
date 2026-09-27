package org.zhavoronkov.openrouter.statusbar

import com.intellij.openapi.ui.popup.ListPopupStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [PopupMenuItem] is a top-level class sharing `OpenRouterStatusBarWidget.kt` with the widget it
 * feeds, but it is not itself a widget: it decides whether an entry opens a submenu and what that
 * submenu shows. That decision is ordinary logic and runs under the fast headless `test` task.
 */
@DisplayName("PopupMenuItem")
class PopupMenuItemTest {

    @Test
    @DisplayName("a plain entry has no submenu step")
    fun `a plain entry has no submenu`() {
        val item = PopupMenuItem(text = "Refresh")

        assertNull(item.createSubmenu())
    }

    @Test
    @DisplayName("an entry flagged as a submenu but holding no children still has no submenu step")
    fun `an empty submenu is not a submenu`() {
        val item = PopupMenuItem(text = "Models", hasSubmenu = true, submenuItems = emptyList())

        assertNull(item.createSubmenu())
    }

    @Test
    @DisplayName("children without the submenu flag do not open a submenu either")
    fun `children without the flag do not open a submenu`() {
        val item = PopupMenuItem(
            text = "Models",
            hasSubmenu = false,
            submenuItems = listOf(PopupMenuItem(text = "gpt-4o"))
        )

        assertNull(item.createSubmenu())
    }

    @Test
    @DisplayName("a flagged entry with children opens a step titled after it, listing its children")
    fun `a populated submenu opens a step`() {
        val child = PopupMenuItem(text = "gpt-4o")
        val item = PopupMenuItem(text = "Models", hasSubmenu = true, submenuItems = listOf(child))

        val step = item.createSubmenu() as? ListPopupStep<PopupMenuItem>

        assertNotNull(step)
        assertEquals("Models", step!!.title)
        assertEquals(listOf(child), step.values)
        assertEquals("gpt-4o", step.getTextFor(child))
        assertNull(step.getIconFor(child))
    }

    @Test
    @DisplayName("choosing a child runs its action and ends the popup")
    fun `choosing a child runs its action`() {
        var ran = false
        val child = PopupMenuItem(text = "gpt-4o", action = { ran = true })
        val step = PopupMenuItem(text = "Models", hasSubmenu = true, submenuItems = listOf(child)).createSubmenu()

        val next = step!!.onChosen(child, true)

        assertTrue(ran, "the chosen entry's action must run")
        assertNull(next, "choosing a leaf must end the popup rather than opening another step")
    }

    @Test
    @DisplayName("choosing a child that carries no action is a no-op, not a crash")
    fun `choosing an actionless child is a no-op`() {
        val child = PopupMenuItem(text = "separator-ish", action = null)
        val step = PopupMenuItem(text = "Models", hasSubmenu = true, submenuItems = listOf(child)).createSubmenu()

        assertNull(step!!.onChosen(child, true))
    }
}
