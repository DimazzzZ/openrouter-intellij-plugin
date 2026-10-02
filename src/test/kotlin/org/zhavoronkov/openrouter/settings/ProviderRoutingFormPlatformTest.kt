package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.ProviderRoutingPreferences
import org.zhavoronkov.openrouter.services.settings.ProviderRoutingManager
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import javax.swing.DefaultListModel
import javax.swing.JComponent

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

    private fun listWith(vararg items: String, selected: Int = -1): JBList<String> {
        val model = DefaultListModel<String>().apply { items.forEach(::addElement) }
        return JBList(model).apply { if (selected >= 0) selectedIndex = selected }
    }

    private fun JBList<String>.items(): List<String> = (0 until model.size).map { model.getElementAt(it) }

    fun testMovingKeepsTheEntrySelectedAndStopsAtTheEnds() {
        val list = listWith("A", "B", "C", selected = 0)

        ProviderRoutingForm.move(list, +1)
        assertEquals(listOf("B", "A", "C"), list.items())
        assertEquals(1, list.selectedIndex)

        ProviderRoutingForm.move(list, -1)
        ProviderRoutingForm.move(list, -1)
        assertEquals("the top entry does not move further up", listOf("A", "B", "C"), list.items())

        list.clearSelection()
        ProviderRoutingForm.move(list, +1)
        assertEquals("nothing selected, nothing moved", listOf("A", "B", "C"), list.items())
    }

    fun testRemovingTakesOnlyTheSelectedEntry() {
        val list = listWith("A", "B", selected = 1)

        ProviderRoutingForm.removeSelected(list)
        assertEquals(listOf("A"), list.items())

        ProviderRoutingForm.removeSelected(list)
        assertEquals("nothing selected, nothing removed", listOf("A"), list.items())
    }

    fun testAComboWithNothingChosenIsReadAsNotSet() {
        val form = ProviderRoutingForm()
        form.show(everything)

        form.sort.selectedItem = null
        form.dataCollection.selectedItem = null

        assertNull(form.value().sort)
        assertNull(form.value().dataCollection)
    }

    /** The add buttons of the form's three lists, in the order the form lays them out. */
    private fun addButtons(form: ProviderRoutingForm): List<AnActionButton> {
        val root = panel { form.addTo(this) }
        return UIUtil.findComponentsOfType(root, JComponent::class.java)
            .mapNotNull { ToolbarDecorator.findAddButton(it) }
            .distinct()
    }

    fun testAddingOffersOnlyTheProvidersNotListedYet() {
        var offered: List<String> = emptyList()
        val form = ProviderRoutingForm { _, _, options -> offered = options; options.first() }
        form.show(ProviderRoutingPreferences(order = listOf("OpenAI")))

        addButtons(form).first().actionPerformed(TestActionEvent.createTestEvent())

        assertFalse("a listed provider is not offered again", "OpenAI" in offered)
        assertEquals(listOf("OpenAI", offered.first()), form.value().order)
    }

    fun testAFullListOffersNothingAndOnlyTheOrderListSaysSo() {
        var asked = 0
        val form = ProviderRoutingForm { _, _, _ -> asked++; null }
        val all = ModelProviderUtils.KNOWN_PROVIDERS.values.toList()
        form.show(ProviderRoutingPreferences(order = all, only = all))
        var warned = 0
        TestDialogManager.setTestDialog { warned++; Messages.OK }
        try {
            addButtons(form).forEach { it.actionPerformed(TestActionEvent.createTestEvent()) }
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }

        assertEquals("only the list with room asks", 1, asked)
        assertEquals("only the order list warns that it is full", 1, warned)
    }
}
