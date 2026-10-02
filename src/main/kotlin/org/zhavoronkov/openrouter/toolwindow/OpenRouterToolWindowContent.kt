package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.unseenWarning
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsNavigator
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsTabPanel
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
        private const val REQUESTS_TITLE = "Requests"
    }

    private val mainPanel: JPanel
    private val tabbedPane: JBTabbedPane
    private val statusTab = StatusTabPanel(project, settingsService)

    private val chatPanel = ChatPanel(project, settingsService, openRouterService)

    private val requestsTab = RequestsTabPanel()

    /** Consumer warnings recorded while the Requests tab was out of sight, shown in its title. */
    private var unseenWarnings = 0
    private var disposed = false

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
        if (tabbedPane.selectedComponent === requestsTab.component) {
            unseenWarnings = 0
            updateRequestsTitle()
        }
    }

    init {
        // statusTab owns a message-bus subscription of its own (to the shared stats cache); it
        // must be a Disposer CHILD of this content, not merely disposed by a plain method call
        // from this.dispose() - a bare `statusTab.dispose()` call only runs that class's dispose()
        // body, it does not ask the Disposer to walk (and tear down) statusTab's own children.
        Disposer.register(this, statusTab)
        Disposer.register(this, requestsTab)

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

        // A Consumer's warning is counted on the Requests tab until the tab is looked at; clearing
        // the log clears the count with it
        connection.subscribe(
            RequestLogListener.TOPIC,
            RequestLogListener { added ->
                ApplicationManager.getApplication().invokeLater({ onRequestRecorded(added) }, ModalityState.any())
            }
        )
        project.messageBus.connect(this).subscribe(
            RequestsNavigator.TOPIC,
            RequestsNavigator { record ->
                tabbedPane.selectedComponent = requestsTab.component
                requestsTab.reveal(record)
            }
        )

        // Create main panel with tabs
        mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(MAIN_PANEL_BORDER)

        // Create tabbed pane
        tabbedPane = JBTabbedPane()

        // Chat first, what it and the Consumers sent next, the account last
        tabbedPane.addTab("Chat", chatPanel.getPanel())
        tabbedPane.addTab(REQUESTS_TITLE, requestsTab.component)
        tabbedPane.addTab("Status", statusTab.component)

        // Select Chat tab by default
        tabbedPane.selectedIndex = 0

        // Refresh-on-activation: the listener is added AFTER `selectedIndex = 0` above,
        // so Swing's own initial-selection bookkeeping has already happened by the time it is
        // attached - no ChangeEvent fires for this line at all, let alone one the listener would
        // need to no-op on. It only ever fires for a later, real selection change.
        tabbedPane.addChangeListener(statusTabActivationListener)

        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    fun getContentPanel(): JPanel = mainPanel

    private fun onRequestRecorded(added: RequestRecord?) {
        when {
            disposed -> return
            added == null -> unseenWarnings = 0
            added.unseenWarning == null || requestsTab.component.isShowing -> return
            else -> unseenWarnings++
        }
        updateRequestsTitle()
    }

    private fun updateRequestsTitle() {
        val index = tabbedPane.indexOfComponent(requestsTab.component).takeIf { it >= 0 } ?: return
        tabbedPane.setTitleAt(index, if (unseenWarnings > 0) "$REQUESTS_TITLE ($unseenWarnings)" else REQUESTS_TITLE)
    }

    /** The wired-in [StatusTabPanel], so a test can call [StatusTabPanel.onActivated] directly to
     * observe whether [statusTabActivationListener] already consumed its refresh gate. */
    internal fun getStatusTabForTest(): StatusTabPanel = statusTab

    /** The real [JBTabbedPane], so a test can drive tab selection the way a user would. */
    internal fun getTabbedPaneForTest(): JBTabbedPane = tabbedPane

    override fun dispose() {
        disposed = true
        tabbedPane.removeChangeListener(statusTabActivationListener)
        // statusTab is disposed by the Disposer walking the child registered above, not here.
        chatPanel.dispose()
    }
}
