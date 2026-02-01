package org.zhavoronkov.openrouter.statusbar

import com.intellij.icons.AllIcons
import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.ConnectionStatus
import javax.swing.Icon

/**
 * Builds status bar menu entries in a testable, UI-agnostic way.
 */
object StatusBarMenuBuilder {

    data class MenuItem(
        val text: String,
        val icon: Icon? = null,
        val isActionEnabled: Boolean = false
    )

    fun buildMenuItems(
        connectionStatus: ConnectionStatus,
        isConfigured: Boolean,
        authScope: AuthScope
    ): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        items.add(
            MenuItem(
                OpenRouterStatusBarWidget.STATUS_MENU_TEXT + connectionStatus.displayName,
                connectionStatus.icon
            )
        )

        val isExtended = authScope == AuthScope.EXTENDED
        val quotaText = if (isExtended) {
            OpenRouterStatusBarWidget.QUOTA_MENU_TEXT
        } else {
            "${OpenRouterStatusBarWidget.QUOTA_MENU_TEXT} (Monitoring Disabled)"
        }
        items.add(MenuItem(quotaText, AllIcons.General.Information, isConfigured && isExtended))

        if (isConfigured) {
            items.add(MenuItem(OpenRouterStatusBarWidget.LOGOUT_MENU_TEXT, AllIcons.Actions.Exit, true))
        } else {
            items.add(MenuItem(OpenRouterStatusBarWidget.LOGIN_MENU_TEXT, AllIcons.Actions.Execute, true))
        }

        items.add(MenuItem(OpenRouterStatusBarWidget.SETTINGS_MENU_TEXT, AllIcons.General.Settings, true))
        items.add(MenuItem(OpenRouterStatusBarWidget.DOCUMENTATION_MENU_TEXT, AllIcons.Actions.Help, true))
        items.add(MenuItem(OpenRouterStatusBarWidget.FEEDBACK_MENU_TEXT, AllIcons.Vcs.Vendors.Github, true))
        return items
    }
}
