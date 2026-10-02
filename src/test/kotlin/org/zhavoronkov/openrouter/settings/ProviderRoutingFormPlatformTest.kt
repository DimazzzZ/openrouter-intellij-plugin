package org.zhavoronkov.openrouter.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager

/**
 * The provider routing form a preset's routing and the Provider Routing page share, and the page still
 * storing what it always stored through it.
 */
class ProviderRoutingFormPlatformTest : BasePlatformTestCase() {

    private val everything = ProviderRoutingPreferences(
        order = listOf("Anthropic", "OpenAI"),
        allowFallbacks = false,
        sort = "latency",
        requireParameters = true,
        dataCollection = "deny",
        quantizations = listOf("fp8", "bf16"),
        only = listOf("Anthropic"),
        ignore = listOf("DeepInfra")
    )

    fun testPreferencesReadBackAsTheyWereShown() {
        val form = ProviderRoutingForm()

        form.show(everything)

        assertEquals(everything, form.value())
    }

    fun testFieldsAtOpenRouterDefaultsAreReadAsNotSet() {
        val form = ProviderRoutingForm()

        form.show(null)

        assertEquals(ProviderRoutingPreferences(), form.value())
        assertTrue("fallbacks default to allowed", form.allowFallbacks.isSelected)
    }

    fun testThePageStoresWhatTheFormSays() {
        val settings = OpenRouterSettings()
        val manager = ProviderRoutingManager(settings) {}
        val page = ProviderRoutingSettingsPanel(manager)
        page.createPanel()

        page.form.show(everything)
        assertTrue(page.isModified())
        page.apply()

        assertEquals(everything, manager.toPreferences())
        assertFalse("applied is no longer modified", page.isModified())
    }

    fun testThePageOpensShowingWhatIsStored() {
        val settings = OpenRouterSettings()
        val manager = ProviderRoutingManager(settings) {}
        manager.sort = "price"
        manager.order = mutableListOf("Groq")

        val page = ProviderRoutingSettingsPanel(manager)
        page.createPanel()

        assertEquals(ProviderRoutingPreferences(order = listOf("Groq"), sort = "price"), page.form.value())
        assertFalse(page.isModified())
    }
}
