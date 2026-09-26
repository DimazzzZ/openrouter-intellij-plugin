package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
    private val statusTab = StatusTabPanel(project, settingsService, openRouterService)

    private val chatPanel = ChatPanel(project, settingsService, openRouterService)

    init {
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

        mainPanel.add(tabbedPane, BorderLayout.CENTER)
    }

    fun getContentPanel(): JPanel = mainPanel

    override fun dispose() {
        statusTab.dispose()
    }
}
