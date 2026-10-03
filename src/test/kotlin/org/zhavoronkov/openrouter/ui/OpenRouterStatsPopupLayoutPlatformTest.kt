package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JScrollPane

class OpenRouterStatsPopupLayoutPlatformTest : BasePlatformTestCase() {

    /** Recent Models scrolls inside the dialog instead of being cut off at its bottom edge. */
    fun testALongRecentModelsListScrollsWithinTheDialog() {
        lateinit var popup: OpenRouterStatsPopup
        ApplicationManager.getApplication().invokeAndWait { popup = OpenRouterStatsPopup(project) }
        try {
            val root = centerPanel(popup)
            val models = UIUtil.findComponentsOfType(root, JBLabel::class.java)
                .single { it.text.orEmpty().contains("Recent Models:") }
            val scroll = UIUtil.getParentOfType(JScrollPane::class.java, models)
            assertNotNull("Recent Models sits in a scroll pane", scroll)

            models.text = OpenRouterStatsPopup.buildModelsWithSpendHtmlList(
                (1..40).map { OpenRouterStatsPopup.ModelWithSpend("author/model-$it", 0.01, "2026-10-03") }
            )
            root.setSize(root.preferredSize)
            layOut(root)

            assertTrue(
                "the list is taller than its share of the dialog: ${models.preferredSize.height} vs ${scroll!!.height}",
                models.preferredSize.height > scroll.viewport.height
            )
            assertTrue("so the scroll pane offers a vertical scroll bar", scroll.verticalScrollBar.isVisible)
            assertTrue("and the dialog keeps its size", root.height <= root.preferredSize.height)
        } finally {
            ApplicationManager.getApplication().invokeAndWait { Disposer.dispose(popup.disposable) }
        }
    }

    /** The dialog's content, as DialogWrapper asks for it; headless, the dialog has no window to hold it. */
    private fun centerPanel(popup: OpenRouterStatsPopup): JComponent =
        OpenRouterStatsPopup::class.java.getDeclaredMethod("createCenterPanel")
            .apply { isAccessible = true }
            .invoke(popup) as JComponent

    private fun layOut(component: Component) {
        component.doLayout()
        (component as? Container)?.components?.forEach(::layOut)
    }
}
