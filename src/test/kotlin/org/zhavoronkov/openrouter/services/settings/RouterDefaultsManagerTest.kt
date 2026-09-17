package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

@DisplayName("RouterDefaultsManager Tests")
class RouterDefaultsManagerTest {

    private lateinit var settings: OpenRouterSettings
    private var notifications = 0
    private lateinit var manager: RouterDefaultsManager

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        notifications = 0
        manager = RouterDefaultsManager(settings) { notifications++ }
    }

    @Test
    @DisplayName("set stores a value and notifies once")
    fun setStores() {
        manager.set("openrouter/auto", "high")
        assertEquals("high", manager.get("openrouter/auto"))
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("setting the same value again does not notify")
    fun idempotentSet() {
        manager.set("openrouter/auto", "high")
        manager.set("openrouter/auto", "high")
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("blank value clears an existing default and notifies")
    fun blankClears() {
        manager.set("openrouter/auto", "high")
        manager.set("openrouter/auto", "")
        assertNull(manager.get("openrouter/auto"))
        assertFalse(settings.routerDefaults.containsKey("openrouter/auto"))
        assertEquals(2, notifications)
    }

    @Test
    @DisplayName("clearing an already-absent default does not notify")
    fun clearAbsentNoNotify() {
        manager.set("openrouter/auto", null)
        assertEquals(0, notifications)
    }

    @Test
    @DisplayName("all() returns only set values, in catalog order")
    fun allSnapshot() {
        manager.set("openrouter/pareto-code", "0.5")
        manager.set("openrouter/auto", "low")
        val all = manager.all()
        assertEquals(2, all.size)
        // Catalog order: auto precedes pareto-code
        assertEquals(listOf("openrouter/auto", "openrouter/pareto-code"), all.keys.toList())
    }

    @Test
    @DisplayName("replaceAll drops blanks and notifies on change")
    fun replaceAll() {
        manager.replaceAll(mapOf("openrouter/auto" to "high", "openrouter/fusion" to ""))
        assertEquals("high", manager.get("openrouter/auto"))
        assertNull(manager.get("openrouter/fusion"))
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("replaceAll with an identical map does not notify")
    fun replaceAllIdempotent() {
        manager.set("openrouter/auto", "high")
        val before = notifications
        manager.replaceAll(mapOf("openrouter/auto" to "high"))
        assertEquals(before, notifications)
    }
}
