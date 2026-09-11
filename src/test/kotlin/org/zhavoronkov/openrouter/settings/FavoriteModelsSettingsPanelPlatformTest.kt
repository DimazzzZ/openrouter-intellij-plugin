package org.zhavoronkov.openrouter.settings

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.SearchTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.services.settings.FavoriteModelsManager
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.CATALOG
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GPT4O
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.GROK
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsFixtures.SONNET
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState
import org.zhavoronkov.openrouter.settings.favorites.FavoriteModelsPageState.Mode
import org.zhavoronkov.openrouter.settings.favorites.VariantLegend
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane

/**
 * Platform test for the single-table Favorite Models page. The panel is built with
 * `autoLoad = false` and fed a fixture catalog, so no network is touched.
 */
class FavoriteModelsSettingsPanelPlatformTest : BasePlatformTestCase() {

    private companion object {
        const val PAGE_WIDTH = 900
        const val PAGE_HEIGHT = 800
        const val ICON_SIZE = 16
    }

    private lateinit var manager: FavoriteModelsManager
    private lateinit var state: FavoriteModelsPageState
    private var panel: FavoriteModelsSettingsPanel? = null

    override fun setUp() {
        super.setUp()
        manager = FavoriteModelsManager(OpenRouterSettings()) {}
        manager.setFavoriteModels(listOf(SONNET.id, GPT4O.id))
        state = FavoriteModelsPageState()
    }

    override fun tearDown() {
        try {
            panel?.let { Disposer.dispose(it) }
        } finally {
            super.tearDown()
        }
    }

    private fun createPanel(configured: Boolean = true): JComponent {
        val created = FavoriteModelsSettingsPanel(
            favoriteModelsManager = manager,
            isConfigured = { configured },
            state = state,
            autoLoad = false,
        )
        panel = created
        val root = created.createPanel()
        if (configured) created.onCatalogLoaded(CATALOG)
        return root
    }

    private fun table(root: JComponent): JBTable =
        UIUtil.findComponentOfType(root, JBTable::class.java) ?: error("table not found")

    /** Lays out the whole hierarchy without a native peer, so sizes are real. */
    private fun forceLayout(component: Component) {
        if (component is Container) {
            component.doLayout()
            component.components.forEach { forceLayout(it) }
        }
    }

    private fun statusLabel(root: JComponent): JLabel =
        UIUtil.findComponentsOfType(root, JLabel::class.java).first { it.name == "favoritesStatusLabel" }

    fun testWarningBannerWhenKeyMissing() {
        val root = createPanel(configured = false)

        val openSettings = UIUtil.findComponentsOfType(root, JButton::class.java).find { it.text == "Open Settings" }
        assertNotNull("Open Settings button should be shown", openSettings)
        val search = UIUtil.findComponentOfType(root, SearchTextField::class.java)
        assertTrue("Page body should be hidden without a key", search == null || !search.isVisible)
        assertFalse(panel!!.isModified())
    }

    fun testTableHasFiveColumns() {
        val root = createPanel()
        val model = table(root).model

        val names = (0 until model.columnCount).map(model::getColumnName)
        assertEquals(listOf("★", "Model", "Context", "Input", "Output"), names)
        assertEquals(CATALOG.size, model.rowCount)
    }

    fun testCheckboxToggleMarksModifiedAndApplies() {
        val root = createPanel()
        val model = table(root).model
        val grokRow = (0 until model.rowCount).first { model.getValueAt(it, 1) == GROK.id }
        assertFalse(panel!!.isModified())

        model.setValueAt(true, grokRow, 0)

        assertTrue(panel!!.isModified())
        assertEquals(true, model.getValueAt(grokRow, 0))

        panel!!.apply()

        assertEquals(listOf(SONNET.id, GPT4O.id, GROK.id), manager.getFavoriteModels())
        assertFalse(panel!!.isModified())
    }

    fun testFavoritesOnlyShowsStoredOrderAndDisablesSorting() {
        val root = createPanel()
        val table = table(root)
        assertNotNull("Catalog mode should be sortable", table.rowSorter)

        state.mode = Mode.FAVORITES_ONLY

        val ids = (0 until table.model.rowCount).map { table.model.getValueAt(it, 1) }
        assertEquals(listOf(SONNET.id, GPT4O.id), ids)
        assertNull("Favorites-only mode must not sort", table.rowSorter)
    }

    fun testSearchEnterIsConsumedAndAppliesImmediately() {
        val root = createPanel()
        val editor = UIUtil.findComponentOfType(root, SearchTextField::class.java)!!.textEditor
        editor.text = "grok"
        val enter = KeyEvent(editor, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n')

        // The component is not showing, so the focus manager would swallow a dispatched event;
        // drive the registered listeners directly instead.
        editor.keyListeners.forEach { it.keyPressed(enter) }

        assertTrue("Enter must be consumed so the Settings dialog does not close", enter.isConsumed)
        assertEquals("grok", state.criteria.searchText)
        assertEquals(2, table(root).model.rowCount)
    }

    fun testEmptyStateTextWhenNoMatches() {
        val root = createPanel()

        state.criteria = ModelFilterCriteria(searchText = "zzz-nothing")

        val table = table(root)
        assertEquals(0, table.model.rowCount)
        assertTrue(
            "Empty text was: ${table.emptyText.text}",
            table.emptyText.text.contains("No models match", ignoreCase = true)
        )
    }

    fun testTableFillsAvailableVerticalSpace() {
        val root = createPanel()

        root.setSize(PAGE_WIDTH, PAGE_HEIGHT)
        forceLayout(root)

        val scrollPane = UIUtil.findComponentOfType(root, JScrollPane::class.java)!!
        assertTrue(
            "Table should take the page's spare height, but got ${scrollPane.height} of $PAGE_HEIGHT",
            scrollPane.height >= PAGE_HEIGHT / 2
        )
    }

    fun testVariantHelpTooltipCannotFlickerUnderTheCursor() {
        val icon = VariantLegend.createLabel()
        icon.setSize(ICON_SIZE, ICON_SIZE)
        val cursor = Point(ICON_SIZE / 2, ICON_SIZE / 2)

        val shortTip = VariantLegend.ALIGNMENT.getPointFor(icon, Dimension(300, 60), cursor)
        val tallTip = VariantLegend.ALIGNMENT.getPointFor(icon, Dimension(300, 900), cursor)

        // HELP_BUTTON, the ContextHelpLabel default, returns -popupHeight above the
        // icon. A tall popup then lands off the top of the screen, the platform
        // clamps it back over the icon, and the owner flips between mouseExited and
        // mouseEntered forever (IDEA-330235).
        assertEquals(
            "Tooltip position must not depend on its height, or a tall tooltip gets clamped over the cursor",
            shortTip,
            tallTip
        )
        assertTrue(
            "Tooltip must open below the icon, clear of the cursor, but y was ${'$'}{shortTip.y}",
            shortTip.y >= icon.height
        )
    }

    fun testPageShowsTheVariantHelpIcon() {
        val root = createPanel()

        assertNotNull(
            "The variant legend help icon should be on the page",
            UIUtil.findComponentOfType(root, ContextHelpLabel::class.java)
        )
    }

    fun testStatusLabelMirrorsState() {
        val root = createPanel()
        val label = statusLabel(root)
        assertEquals(state.statusText(), label.text)

        state.setFavorite(GROK.id, true)

        assertEquals(state.statusText(), label.text)
        assertTrue(label.text.contains("3 favorites"))
    }
}
