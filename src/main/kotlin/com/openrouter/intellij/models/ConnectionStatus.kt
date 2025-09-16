package com.openrouter.intellij.models

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Represents the connection status of the OpenRouter plugin
 */
enum class ConnectionStatus(
    val displayName: String,
    val icon: Icon,
    val description: String
) {
    READY("Ready", AllIcons.RunConfigurations.TestPassed, "Connected and ready to use"),
    CONNECTING("Connecting...", AllIcons.Process.Step_1, "Establishing connection"),
    ERROR("Error", AllIcons.RunConfigurations.TestError, "Connection failed or API error"),
    NOT_CONFIGURED("Not Configured", AllIcons.RunConfigurations.TestIgnored, "API key not configured"),
    OFFLINE("Offline", AllIcons.RunConfigurations.TestSkipped, "No internet connection")
}
