package org.zhavoronkov.openrouter.services.settings

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings

class ProviderRoutingManagerTest {

    private lateinit var settings: OpenRouterSettings
    private lateinit var manager: ProviderRoutingManager
    private var changeCount = 0

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        changeCount = 0
        manager = ProviderRoutingManager(settings) { changeCount++ }
    }

    @Test
    fun `enabled defaults to false`() {
        assertFalse(manager.enabled)
    }

    @Test
    fun `setting fields notifies state change`() {
        manager.enabled = true
        manager.order = mutableListOf("Anthropic")
        assertEquals(2, changeCount)
        assertTrue(manager.enabled)
        assertEquals(listOf("Anthropic"), manager.order)
    }

    @Test
    fun `toPreferences returns null when nothing is set`() {
        assertNull(manager.toPreferences())
    }

    @Test
    fun `toPreferences returns null when only allowFallbacks is default true`() {
        manager.allowFallbacks = true // default
        assertNull(manager.toPreferences())
    }

    @Test
    fun `toPreferences captures order`() {
        manager.order = mutableListOf("Anthropic", "OpenAI")
        val prefs = manager.toPreferences()
        assertEquals(listOf("Anthropic", "OpenAI"), prefs?.order)
    }

    @Test
    fun `toPreferences captures allowFallbacks false`() {
        manager.allowFallbacks = false
        val prefs = manager.toPreferences()
        assertEquals(false, prefs?.allowFallbacks)
    }

    @Test
    fun `toPreferences omits allowFallbacks when true`() {
        manager.order = mutableListOf("Anthropic")
        manager.allowFallbacks = true
        val prefs = manager.toPreferences()
        assertNull(prefs?.allowFallbacks)
    }

    @Test
    fun `toPreferences captures all fields`() {
        manager.order = mutableListOf("Anthropic")
        manager.allowFallbacks = false
        manager.sort = "price"
        manager.requireParameters = true
        manager.dataCollection = "deny"
        manager.quantizations = mutableListOf("int4")
        manager.only = mutableListOf("Anthropic")
        manager.ignore = mutableListOf("OpenAI")

        val prefs = manager.toPreferences()
        assertEquals(listOf("Anthropic"), prefs?.order)
        assertEquals(false, prefs?.allowFallbacks)
        assertEquals("price", prefs?.sort)
        assertEquals(true, prefs?.requireParameters)
        assertEquals("deny", prefs?.dataCollection)
        assertEquals(listOf("int4"), prefs?.quantizations)
        assertEquals(listOf("Anthropic"), prefs?.only)
        assertEquals(listOf("OpenAI"), prefs?.ignore)
    }

    @Test
    fun `buildProviderJson returns null when nothing set`() {
        assertNull(manager.buildProviderJson(Gson()))
    }

    @Test
    fun `buildProviderJson emits only set fields`() {
        manager.sort = "throughput"
        val json = manager.buildProviderJson(Gson())
        assertTrue(json!!.has("sort"))
        assertFalse(json.has("order"))
        assertEquals("throughput", json.get("sort").asString)
    }

    @Test
    fun `settings persist through the underlying data class`() {
        manager.enabled = true
        manager.sort = "latency"
        // Verify the underlying settings object was mutated (XML persistence source of truth)
        assertTrue(settings.providerRoutingEnabled)
        assertEquals("latency", settings.providerSort)
    }
}
