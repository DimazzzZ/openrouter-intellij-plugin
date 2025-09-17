package org.zhavoronkov.openrouter.models

import com.intellij.icons.AllIcons
import org.zhavoronkov.openrouter.icons.OpenRouterIcons
import javax.swing.Icon

/**
 * Represents the connection status of the OpenRouter plugin
 */
enum class ConnectionStatus(
    val displayName: String,
    val icon: Icon,
    val description: String
) {
    READY("Ready", OpenRouterIcons.SUCCESS, "Connected and ready to use"),
    CONNECTING("Connecting...", AllIcons.Process.Step_1, "Establishing connection"),
    ERROR("Error", OpenRouterIcons.ERROR, "Connection failed or API error"),
    NOT_CONFIGURED("Not Configured", AllIcons.RunConfigurations.TestIgnored, "API key not configured"),
    OFFLINE("Offline", AllIcons.RunConfigurations.TestSkipped, "No internet connection")
}
