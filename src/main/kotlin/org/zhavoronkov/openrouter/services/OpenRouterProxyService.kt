package org.zhavoronkov.openrouter.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.CompletableFuture

/**
 * Application-level service for managing the OpenRouter proxy server
 * This service handles the lifecycle of the embedded HTTP server
 */
@Service(Service.Level.APP)
class OpenRouterProxyService {

    companion object {
        fun getInstance(): OpenRouterProxyService {
            return ApplicationManager.getApplication().getService(OpenRouterProxyService::class.java)
        }
    }

    private val proxyServer = OpenRouterProxyServer.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    /**
     * Starts the proxy server if it's not already running
     */
    fun startServer(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                if (!settingsService.isConfigured()) {
                    PluginLogger.Service.warn("Cannot start proxy server: OpenRouter not configured")
                    return@supplyAsync false
                }

                val status = proxyServer.getStatus()
                if (status.isRunning) {
                    PluginLogger.Service.debug("Proxy server is already running on port ${status.port}")
                    return@supplyAsync true
                }

                PluginLogger.Service.info("Starting OpenRouter proxy server...")
                proxyServer.start().get()
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to start proxy server", e)
                false
            }
        }
    }

    /**
     * Stops the proxy server if it's running
     */
    fun stopServer(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val status = proxyServer.getStatus()
                if (!status.isRunning) {
                    PluginLogger.Service.debug("Proxy server is not running")
                    return@supplyAsync true
                }

                PluginLogger.Service.info("Stopping OpenRouter proxy server...")
                proxyServer.stop().get()
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to stop proxy server", e)
                false
            }
        }
    }

    /**
     * Restarts the proxy server
     */
    fun restartServer(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                PluginLogger.Service.info("Restarting OpenRouter proxy server...")
                proxyServer.restart().get()
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to restart proxy server", e)
                false
            }
        }
    }

    /**
     * Gets the current server status
     */
    fun getServerStatus(): OpenRouterProxyServer.ProxyServerStatus {
        return proxyServer.getStatus()
    }

    /**
     * Tests the proxy server connection
     */
    fun testServerConnection(): CompletableFuture<Boolean> {
        return proxyServer.testConnection()
    }

    /**
     * Auto-starts the server if configured and not running
     * This is called during plugin initialization
     */
    fun autoStartIfConfigured(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                if (!settingsService.isConfigured()) {
                    PluginLogger.Service.debug("OpenRouter not configured, skipping auto-start")
                    return@supplyAsync false
                }

                val status = proxyServer.getStatus()
                if (status.isRunning) {
                    PluginLogger.Service.debug("Proxy server already running, no need to auto-start")
                    return@supplyAsync true
                }

                PluginLogger.Service.info("Auto-starting OpenRouter proxy server...")
                startServer().get()
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to auto-start proxy server", e)
                false
            }
        }
    }

    /**
     * Force starts the server regardless of configuration status
     * This is used for testing and development purposes
     */
    fun forceStartServer(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                val status = proxyServer.getStatus()
                if (status.isRunning) {
                    PluginLogger.Service.debug("Proxy server already running, no need to force-start")
                    return@supplyAsync true
                }

                PluginLogger.Service.info("Force-starting OpenRouter proxy server (test mode)...")
                proxyServer.start().get()
            } catch (e: Exception) {
                PluginLogger.Service.error("Failed to force-start proxy server", e)
                false
            }
        }
    }

    /**
     * Gets the proxy URL for AI Assistant configuration
     */
    fun getProxyUrl(): String? {
        val status = proxyServer.getStatus()
        return if (status.isRunning) status.url else null
    }

    /**
     * Gets the proxy port
     */
    fun getProxyPort(): Int? {
        val status = proxyServer.getStatus()
        return if (status.isRunning) status.port else null
    }

    /**
     * Checks if the proxy server is running and configured
     */
    fun isReady(): Boolean {
        val status = proxyServer.getStatus()
        return status.isRunning && status.isConfigured
    }

    /**
     * Gets configuration instructions for AI Assistant
     */
    fun getAIAssistantConfigurationInstructions(): String {
        val status = proxyServer.getStatus()
        return if (status.isRunning) {
            """
            To configure JetBrains AI Assistant to use OpenRouter:
            
            1. Go to Settings > Tools > AI Assistant > Models
            2. In the "Third-party AI providers" section, select "Other OpenAI-compatible service"
            3. Set the URL to: ${status.url}
            4. Leave the API key field empty (authentication is handled by OpenRouter plugin)
            5. Click "Test Connection" to verify
            6. Click "Apply" to save changes
            
            The proxy server is running on port ${status.port}
            """.trimIndent()
        } else {
            "Proxy server is not running. Please start it first."
        }
    }
}
