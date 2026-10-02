package org.zhavoronkov.openrouter.toolwindow.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import org.zhavoronkov.openrouter.models.ChatMessage
import java.awt.Container
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * The Web Search checkbox in the send-parameters popup: it is there, it says what it costs, what it
 * says reaches the request, and it stays as the user left it.
 */
class ChatParamsPopupWebSearchPlatformTest : BasePlatformTestCase() {

    private lateinit var webSearch: JBCheckBox
    private lateinit var popup: ChatParamsPopup

    override fun setUp() {
        super.setUp()
        webSearch = JBCheckBox()
        popup = ChatParamsPopup(
            ComboBox(ChatExchange.REASONING_CHOICES.toTypedArray()),
            ComboBox(ChatExchange.VERBOSITY_CHOICES.toTypedArray()),
            ComboBox(arrayOf("")),
            webSearch
        )
    }

    private fun buildForm(): JComponent {
        val method = ChatParamsPopup::class.java.getDeclaredMethod("buildForm")
        method.isAccessible = true
        return method.invoke(popup) as JComponent
    }

    private fun <T> descendants(root: java.awt.Component, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(c: java.awt.Component) {
            if (type.isInstance(c)) found += type.cast(c)
            if (c is Container) c.components.forEach(::walk)
        }
        walk(root)
        return found
    }

    private fun toolsSent(): String? {
        val messages = listOf(ChatMessage(role = "user", content = JsonPrimitive("hi")))
        val tools = ChatExchange.buildRequest("openai/gpt-5.2", messages, popup.requestOptions()).tools
        return tools?.let { Gson().toJsonTree(it).toString() }
    }

    fun testTheFormOffersWebSearchAndSaysItCostsPerRequest() {
        val form = buildForm()

        val labels = descendants(form, JLabel::class.java).map { it.text }
        assertTrue("expected a 'Web search:' row label, got $labels", labels.contains("Web search:"))
        val box = descendants(form, JCheckBox::class.java).singleOrNull()
        assertSame("the form must show the checkbox whose state the request reads", webSearch, box)
        assertTrue(
            "the checkbox must say that ticking it adds a per-request cost, got '${box!!.text}'",
            box.text.contains("charged per search")
        )
    }

    fun testTickingTheBoxOffersTheWebSearchToolToTheModel() {
        assertNull("an unticked box must send no web search tool", toolsSent())

        webSearch.isSelected = true

        assertEquals(
            JsonParser.parseString("""[{"type":"openrouter:web_search"}]"""),
            JsonParser.parseString(toolsSent())
        )
    }

    /**
     * The popup's form is rebuilt every time it opens, so this is what keeps the toggle between
     * sends: the same checkbox is re-parented into each new form, carrying its state with it.
     */
    fun testTheToggleKeepsItsStateAcrossSendsAndReopening() {
        webSearch.isSelected = true
        buildForm()
        toolsSent()
        val reopened = buildForm()

        assertTrue(
            "reopening the popup must show the box still ticked",
            descendants(reopened, JCheckBox::class.java).single().isSelected
        )
        assertTrue("a second send must search the web too", popup.requestOptions().webSearch)
    }

    fun testALeftOnToggleShowsOnTheGearBadge() {
        assertFalse(popup.hasNonDefaultSelection())

        webSearch.isSelected = true

        assertTrue(
            "a search is charged, so a ticked box must badge the gear",
            popup.hasNonDefaultSelection()
        )
        assertEquals("Web search", popup.activeSummary())
    }
}
