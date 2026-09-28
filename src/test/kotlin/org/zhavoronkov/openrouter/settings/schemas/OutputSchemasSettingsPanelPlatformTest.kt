package org.zhavoronkov.openrouter.settings.schemas

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.OutputSchema
import org.zhavoronkov.openrouter.services.settings.OutputSchemasManager
import org.zhavoronkov.openrouter.settings.OutputSchemasConfigurable

/**
 * The Output Schemas page, driven through its add, edit and delete actions and read back through
 * the Settings layer. The modal editor is stood in for by [nextEdit], since a test cannot show a
 * modal dialog; what the editor itself accepts is pinned in [OutputSchemaEditorPlatformTest].
 */
class OutputSchemasSettingsPanelPlatformTest : BasePlatformTestCase() {

    private lateinit var settings: OpenRouterSettings
    private lateinit var manager: OutputSchemasManager
    private lateinit var page: OutputSchemasSettingsPanel

    /** What the stand-in editor returns next; null is the user pressing Cancel. */
    private var nextEdit: OutputSchema? = null
    private val editorCalls = mutableListOf<Pair<OutputSchema?, List<String>>>()

    private val features = OutputSchema(
        name = "features",
        strict = true,
        schema = """{"type":"object","properties":{"a":{}}}"""
    )
    private val summary = OutputSchema("summary", strict = false, schema = """{"type":"object"}""")

    override fun setUp() {
        super.setUp()
        settings = OpenRouterSettings()
        manager = OutputSchemasManager(settings) {}
        page = OutputSchemasSettingsPanel(manager) { _, initial, taken ->
            editorCalls += initial to taken
            nextEdit
        }
    }

    private fun names(): List<String> = page.model.items.map { it.name }

    private fun cell(row: Int, column: Int): Any? = page.table.getValueAt(row, column)

    fun testThePageListsSavedSchemasByName() {
        manager.replaceAll(listOf(features, summary))

        page.createPanel()

        assertEquals(listOf("features", "summary"), names())
        val columns = (0 until page.table.columnCount).map(page.table::getColumnName)
        assertEquals(listOf("Name", "Strict", "Fields"), columns)
        assertEquals("features", cell(0, 0))
        assertEquals("Yes", cell(0, 1))
        assertEquals("1", cell(0, 2))
        assertEquals("No", cell(1, 1))
        assertFalse("opening the page must not count as a change", page.isModified())
    }

    fun testAddingASchemaStagesItUntilApplied() {
        page.createPanel()
        nextEdit = features

        page.add()

        assertEquals(listOf("features"), names())
        assertTrue(page.isModified())
        assertEquals("nothing is stored before apply", emptyList<OutputSchema>(), manager.all())
        page.apply()
        assertEquals(listOf(features), manager.all())
        assertFalse(page.isModified())
    }

    fun testCancellingTheEditorAddsNothing() {
        page.createPanel()
        nextEdit = null

        page.add()

        assertEquals(emptyList<String>(), names())
        assertFalse(page.isModified())
    }

    fun testEditingReplacesTheSchemaInPlace() {
        manager.replaceAll(listOf(features, summary))
        page.createPanel()
        page.table.setRowSelectionInterval(0, 0)
        nextEdit = features.copy(name = "feature-list", strict = false)

        page.editSelected()

        assertEquals(listOf("feature-list", "summary"), names())
        assertEquals("the edited schema is handed to the editor", features, editorCalls.single().first)
        assertEquals(
            "its own name must not count as taken, or it could never be saved unchanged",
            listOf("summary"),
            editorCalls.single().second
        )
    }

    fun testANewSchemaMayNotReuseAnyExistingName() {
        manager.replaceAll(listOf(features, summary))
        page.createPanel()
        nextEdit = null

        page.add()

        assertEquals(listOf("features", "summary"), editorCalls.single().second)
    }

    fun testDeletingRemovesTheSelectedSchema() {
        manager.replaceAll(listOf(features, summary))
        page.createPanel()
        page.table.setRowSelectionInterval(0, 0)

        page.removeSelected()
        page.apply()

        assertEquals(listOf(summary), manager.all())
    }

    fun testResetDiscardsStagedEdits() {
        manager.replaceAll(listOf(features))
        page.createPanel()
        page.table.setRowSelectionInterval(0, 0)
        page.removeSelected()

        page.reset()

        assertEquals(listOf("features"), names())
        assertFalse(page.isModified())
    }

    /**
     * What the IDE does on shutdown and startup: the settings object serialised, written out as XML
     * text, read back and deserialised. Going through the text matters - a line break inside an
     * attribute value survives an in-memory element but is folded into a space by an XML parser
     * unless the writer escaped it, and a schema body is full of line breaks.
     */
    fun testSavedSchemasSurviveARestart() {
        val multiline = OutputSchema(
            name = "multi-line",
            strict = false,
            schema = "{\n\t\"type\": \"object\",\r\n  \"description\": \"a <b> & \\\"c\\\" \\n d\"\n}"
        )
        manager.replaceAll(listOf(features, multiline))

        val written = JDOMUtil.write(XmlSerializer.serialize(settings))
        val restarted = XmlSerializer.deserialize(JDOMUtil.load(written), OpenRouterSettings::class.java)

        assertEquals(listOf(features, multiline), OutputSchemasManager(restarted) {}.all())
    }

    fun testTheRegisteredPageOpensWithoutAChange() {
        val configurable = OutputSchemasConfigurable()
        try {
            assertEquals("Output Schemas", configurable.displayName)
            assertNotNull(configurable.createComponent())
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
