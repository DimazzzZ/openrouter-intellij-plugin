package org.zhavoronkov.openrouter.utils

import com.intellij.openapi.diagnostic.Logger

/**
 * Centralized logging utility for the OpenRouter plugin.
 * Provides configurable debug logging that can be enabled/disabled for development vs production.
 */
object PluginLogger {
    
    private const val PLUGIN_NAME = "OpenRouter"
    
    // Logger instances for different components
    private val serviceLogger = Logger.getInstance("org.zhavoronkov.openrouter.services")
    private val settingsLogger = Logger.getInstance("org.zhavoronkov.openrouter.settings")
    private val statusBarLogger = Logger.getInstance("org.zhavoronkov.openrouter.statusbar")
    private val modelsLogger = Logger.getInstance("org.zhavoronkov.openrouter.models")
    private val startupLogger = Logger.getInstance("org.zhavoronkov.openrouter.startup")
    
    // Debug mode flag - can be controlled via system property
    private val debugEnabled: Boolean by lazy {
        System.getProperty("openrouter.debug", "false").toBoolean() ||
        System.getProperty("idea.log.debug.categories", "").contains("org.zhavoronkov.openrouter")
    }
    
    /**
     * Service-related logging
     */
    object Service {
        fun info(message: String) = serviceLogger.info("[$PLUGIN_NAME] $message")
        fun warn(message: String) = serviceLogger.warn("[$PLUGIN_NAME] $message")
        fun warn(message: String, throwable: Throwable) = serviceLogger.warn("[$PLUGIN_NAME] $message", throwable)
        fun error(message: String) = serviceLogger.error("[$PLUGIN_NAME] $message")
        fun error(message: String, throwable: Throwable) = serviceLogger.error("[$PLUGIN_NAME] $message", throwable)

        fun debug(message: String) {
            if (debugEnabled) {
                // Only log to logger, NOT to console (prevents duplicate logging)
                serviceLogger.info("[$PLUGIN_NAME][DEBUG] $message")
            }
        }

        fun debug(message: String, throwable: Throwable) {
            if (debugEnabled) {
                serviceLogger.info("[$PLUGIN_NAME][DEBUG] $message", throwable)
            }
        }
    }
    
    /**
     * Settings-related logging
     */
    object Settings {
        fun info(message: String) {
            settingsLogger.info("[$PLUGIN_NAME] $message")
        }

        fun warn(message: String) {
            settingsLogger.warn("[$PLUGIN_NAME] $message")
        }

        fun warn(message: String, throwable: Throwable) {
            settingsLogger.warn("[$PLUGIN_NAME] $message", throwable)
        }

        fun error(message: String) {
            settingsLogger.error("[$PLUGIN_NAME] $message")
        }

        fun error(message: String, throwable: Throwable) {
            settingsLogger.error("[$PLUGIN_NAME] $message", throwable)
        }

        fun debug(message: String) {
            if (debugEnabled) {
                settingsLogger.info("[$PLUGIN_NAME][DEBUG] $message")
            }
        }

        fun debug(message: String, throwable: Throwable) {
            if (debugEnabled) {
                settingsLogger.info("[$PLUGIN_NAME][DEBUG] $message", throwable)
            }
        }

        /**
         * Production-safe logging for troubleshooting.
         * Always logs important events regardless of debug mode.
         */
        fun production(message: String) {
            settingsLogger.info("[$PLUGIN_NAME][PROD] $message")
        }
    }
    
    /**
     * Status bar widget logging
     */
    object StatusBar {
        fun info(message: String) = statusBarLogger.info("[$PLUGIN_NAME] $message")
        fun warn(message: String) = statusBarLogger.warn("[$PLUGIN_NAME] $message")
        fun warn(message: String, throwable: Throwable) = statusBarLogger.warn("[$PLUGIN_NAME] $message", throwable)
        fun error(message: String) = statusBarLogger.error("[$PLUGIN_NAME] $message")
        fun error(message: String, throwable: Throwable) = statusBarLogger.error("[$PLUGIN_NAME] $message", throwable)
        
        fun debug(message: String) {
            if (debugEnabled) {
                statusBarLogger.info("[$PLUGIN_NAME][DEBUG] $message")
            }
        }
    }
    
    /**
     * Models/API data logging
     */
    object Models {
        fun info(message: String) = modelsLogger.info("[$PLUGIN_NAME] $message")
        fun warn(message: String) = modelsLogger.warn("[$PLUGIN_NAME] $message")
        fun warn(message: String, throwable: Throwable) = modelsLogger.warn("[$PLUGIN_NAME] $message", throwable)
        fun error(message: String) = modelsLogger.error("[$PLUGIN_NAME] $message")
        fun error(message: String, throwable: Throwable) = modelsLogger.error("[$PLUGIN_NAME] $message", throwable)

        fun debug(message: String) {
            if (debugEnabled) {
                modelsLogger.info("[$PLUGIN_NAME][DEBUG] $message")
            }
        }
    }

    /**
     * Startup activity logging
     */
    object Startup {
        fun info(message: String) = startupLogger.info("[$PLUGIN_NAME] $message")
        fun warn(message: String) = startupLogger.warn("[$PLUGIN_NAME] $message")
        fun warn(message: String, throwable: Throwable) = startupLogger.warn("[$PLUGIN_NAME] $message", throwable)
        fun error(message: String) = startupLogger.error("[$PLUGIN_NAME] $message")
        fun error(message: String, throwable: Throwable) = startupLogger.error("[$PLUGIN_NAME] $message", throwable)

        fun debug(message: String) {
            if (debugEnabled) {
                startupLogger.info("[$PLUGIN_NAME][DEBUG] $message")
            }
        }
    }

    /**
     * Check if debug logging is enabled
     */
    fun isDebugEnabled(): Boolean = debugEnabled

    /**
     * Log debug information about the current logging configuration
     */
    fun logConfiguration() {
        serviceLogger.info("[$PLUGIN_NAME] Debug logging enabled: $debugEnabled")
        if (debugEnabled) {
            serviceLogger.info("[$PLUGIN_NAME] Debug logging configuration:")
            serviceLogger.info("[$PLUGIN_NAME]   - openrouter.debug system property: ${System.getProperty("openrouter.debug", "not set")}")
            serviceLogger.info("[$PLUGIN_NAME]   - idea.log.debug.categories: ${System.getProperty("idea.log.debug.categories", "not set")}")
        }
    }
}
