package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener

/**
 * Content for the OpenRouter tool window
 */
class OpenRouterToolWindowContent(
    private val project: Project,
    private val settingsService: OpenRouterSettingsService = OpenRouterSettingsService.getInstance(),
    private val openRouterService: OpenRouterService = OpenRouterService.getInstance()
) : Disposable {

    companion object {
        // UI Dimensions
        private const val MAIN_PANEL_BORDER = 10
    }

    private val mainPanel: JPanel
    private val tabbedPane: JBTabbedPane
    private val statusTab = StatusTabPanel(project, settingsService)

    private val chatPanel = ChatPanel(project, settingsService, openRouterService)

    /**
     * Calls [StatusTabPanel.onActivated] whenever the Status tab becomes the selected tab -
     * [JBTabbedPane] fires a [ChangeListener] on every selection change, including to the OTHER
     * tab, so this checks which one is now selected rather than refreshing unconditionally.
     * Registered as a plain Swing listener rather than through [Disposer] - `removeChangeListener`
     * in [dispose] is enough to tear it down with this panel, and it holds no resource of its own
     * that would otherwise leak.
     */
    private val statusTabActivationListener = ChangeListener {
        if (tabbedPane.selectedComponent === statusTab.component) {
            statusTab.onActivated()
        }
    }

    init {
        // statusTab owns a message-bus subscription of its own (to the shared stats cache); it
        // must be a Disposer CHILD of this content, not merely disposed by a plain method call
        // from this.dispose() - a bare `statusTab.dispose()` call only runs that class's dispose()
        // body, it does not ask the Disposer to walk (and tear down) statusTab's own children.
        Disposer.register(this, statusTab)

        // Register for settings changes
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(
            OpenRouterSettingsListener.TOPIC,
            object : OpenRouterSettingsListener {
                override fun onSettingsChanged() {
                    SwingUtilities.invokeLater {
                        statusTab.refresh()
                        chatPanel.refreshModels()
                    }
                }
            }
        )

        // Create main panel with tabs
        mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(MAIN_PANEL_BORDER)

        // Create tabbed pane
        tabbedPane = JBTabbedPane()

        // Create status panel
        tabbedPane.addTab("Status", statusTab.component)

        // Create chat panel
        tabbedPane.addTab("Chat", chatPanel.getPanel())

        // Select Chat tab by default
        tabbedPane.selectedIndex = 1

        // Refresh-on-activation (Task 11): fires on every selection change including this one,
        // but the listener itself checks which tab is now selected, so this initial event (still
        // selecting Chat) is a no-op.
        tabbedPane.addChangeListener(statusTabActivationListener)

        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    fun getContentPanel(): JPanel = mainPanel

    /** The wired-in [StatusTabPanel], so a test can call [StatusTabPanel.onActivated] directly to
     * observe whether [statusTabActivationListener] already consumed its refresh gate. */
    internal fun getStatusTabForTest(): StatusTabPanel = statusTab

    /** The real [JBTabbedPane], so a test can drive tab selection the way a user would. */
    internal fun getTabbedPaneForTest(): JBTabbedPane = tabbedPane

    override fun dispose() {
        tabbedPane.removeChangeListener(statusTabActivationListener)
        // statusTab is disposed by the Disposer walking the child registered above, not here.
        chatPanel.dispose()
    }
}
