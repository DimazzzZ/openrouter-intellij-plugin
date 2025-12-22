package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Centralized logging utility for SetupWizardDialog
 * Provides controlled logging with different levels and reduces log pollution
 */
object SetupWizardLogger {
    private const val TAG = "[OpenRouter]"

    /**
     * Log important events that should always be visible
     */
    fun info(message: String) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            PluginLogger.Service.info("$TAG $message")
        }
    }

    /**
     * Log debug information (only shown in debug mode)
     */
    fun debug(message: String) {
        if (SetupWizardConfig.LOGGING_ENABLED && SetupWizardConfig.DEBUG_LOGGING_ENABLED) {
            PluginLogger.Service.debug("$TAG $message")
        }
    }

    /**
     * Log warnings for potential issues
     */
    fun warn(message: String) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            PluginLogger.Service.warn("$TAG $message")
        }
    }

    /**
     * Log errors that need attention
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            if (throwable != null) {
                PluginLogger.Service.error("$TAG $message", throwable)
            } else {
                PluginLogger.Service.error("$TAG $message")
            }
        }
    }

    /**
     * Log PKCE flow events
     */
    fun logPkceEvent(event: String, details: String? = null) {
        val message = "PKCE: $event${details?.let { " - $it" } ?: ""}"
        if (SetupWizardConfig.DEBUG_LOGGING_ENABLED) {
            PluginLogger.Service.debug("$TAG $message")
        } else {
            PluginLogger.Service.info("$TAG $message")
        }
    }

    /**
     * Log validation events
     */
    fun logValidationEvent(event: String, details: String? = null) {
        val message = "Validation: $event${details?.let { " - $it" } ?: ""}"
        PluginLogger.Service.info("$TAG $message")
    }

    /**
     * Log model loading events
     */
    fun logModelLoadingEvent(event: String, details: String? = null) {
        val message = "Models: $event${details?.let { " - $it" } ?: ""}"
        PluginLogger.Service.info("$TAG $message")
    }
}
