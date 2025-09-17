package org.zhavoronkov.openrouter.models

import com.intellij.icons.AllIcons
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import javax.swing.Icon

/**
 * Represents the connection status of the OpenRouter plugin
 *
 * @param displayName Human-readable name for the status
 * @param icon Icon to display for this status
 * @param description Detailed description of the status
 */
enum class ConnectionStatus(
    val displayName: String,
    val icon: Icon,
    val description: String
) {
    /** Plugin is connected and ready to use */
    READY("Ready", OpenRouterIcons.SUCCESS, "Connected and ready to use"),

    /** Plugin is in the process of connecting */
    CONNECTING("Connecting...", AllIcons.Process.Step_1, "Establishing connection"),

    /** Connection failed or API error occurred */
    ERROR("Error", OpenRouterIcons.ERROR, "Connection failed or API error"),

    /** API key is not configured */
    NOT_CONFIGURED("Not Configured", AllIcons.RunConfigurations.TestIgnored, "API key not configured"),

    /** No internet connection available */
    OFFLINE("Offline", AllIcons.RunConfigurations.TestSkipped, "No internet connection")
}
