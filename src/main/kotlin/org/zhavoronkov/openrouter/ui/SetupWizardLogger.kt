package org.zhavoronkov.openrouter.ui

import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Centralized logging utility for SetupWizardDialog
 * Provides controlled logging with different levels and reduces log pollution
 */
object SetupWizardLogger {
    /**
     * Log important events that should always be visible
     */
    fun info(message: String) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            PluginLogger.Service.info(message)
        }
    }

    /**
     * Log debug information (only shown in debug mode)
     */
    fun debug(message: String) {
        if (SetupWizardConfig.DEBUG_LOGGING_ENABLED) {
            PluginLogger.Service.debug(message)
        }
    }

    /**
     * Log warnings for potential issues
     */
    fun warn(message: String) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            PluginLogger.Service.warn(message)
        }
    }

    /**
     * Log errors that need attention
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (SetupWizardConfig.LOGGING_ENABLED) {
            if (throwable != null) {
                PluginLogger.Service.error(message, throwable)
            } else {
                PluginLogger.Service.error(message)
            }
        }
    }

    /**
     * Log PKCE flow events
     */
    fun logPkceEvent(event: String, details: String? = null) {
        val message = "PKCE: $event${details?.let { " - $it" } ?: ""}"
        if (SetupWizardConfig.DEBUG_LOGGING_ENABLED) {
            PluginLogger.Service.debug(message)
        } else {
            PluginLogger.Service.info(message)
        }
    }

    /**
     * Log validation events
     */
    fun logValidationEvent(event: String, details: String? = null) {
        val message = "Validation: $event${details?.let { " - $it" } ?: ""}"
        PluginLogger.Service.info(message)
    }

    /**
     * Log model loading events
     */
    fun logModelLoadingEvent(event: String, details: String? = null) {
        val message = "Models: $event${details?.let { " - $it" } ?: ""}"
        PluginLogger.Service.info(message)
    }
}
