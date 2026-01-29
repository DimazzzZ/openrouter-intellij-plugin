package org.zhavoronkov.openrouter.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.zhavoronkov.openrouter.proxy.OpenRouterProxyServer
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Application-level service for managing the OpenRouter proxy server
 * This service handles the lifecycle of the embedded HTTP server
 *
 * Implements Disposable for dynamic plugin support - ensures proper cleanup
 * when plugin is unloaded/disabled without IDE restart
 */
@Service(Service.Level.APP)
class OpenRouterProxyService : Disposable {

    companion object {
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10L

        fun getInstance(): OpenRouterProxyService {
            return ApplicationManager.getApplication().getService(OpenRouterProxyService::class.java)
        }
    }

    private val proxyServer = OpenRouterProxyServer.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    // Track active async operations for cleanup
    private val activeTasks = ConcurrentHashMap<String, CompletableFuture<*>>()

    /**
     * Starts the proxy server if it's not already running
     */
    fun startServer(): CompletableFuture<Boolean> {
        val future = CompletableFuture.supplyAsync {
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
            } catch (e: java.net.BindException) {
                PluginLogger.Service.error("Failed to start proxy server - port already in use", e)
                false
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Service.error("Proxy server startup failed due to internal error", e)
                false
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Service.error("Proxy server startup timed out", e)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Cannot start proxy server - invalid state", e)
                false
            }
        }

        // Track task for cleanup
        trackTask("startServer", future)
        return future
    }

    /**
     * Stops the proxy server if it's running
     */
    fun stopServer(): CompletableFuture<Boolean> {
        val future = CompletableFuture.supplyAsync {
            try {
                val status = proxyServer.getStatus()
                if (!status.isRunning) {
                    PluginLogger.Service.debug("Proxy server is not running")
                    return@supplyAsync true
                }

                PluginLogger.Service.info("Stopping OpenRouter proxy server...")
                proxyServer.stop().get()
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Service.error("Proxy server shutdown failed due to internal error", e)
                false
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Service.error("Proxy server shutdown timed out", e)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Cannot stop proxy server - invalid state", e)
                false
            }
        }

        // Track task for cleanup
        trackTask("stopServer", future)
        return future
    }

    /**
     * Restarts the proxy server
     */
    fun restartServer(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                PluginLogger.Service.info("Restarting OpenRouter proxy server...")
                proxyServer.restart().get()
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Service.error("Proxy server restart failed due to internal error", e)
                false
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Service.error("Proxy server restart timed out", e)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Cannot restart proxy server - invalid state", e)
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
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Service.error("Auto-start failed due to proxy server error", e)
                false
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Service.error("Auto-start timed out", e)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Auto-start failed - invalid proxy server state", e)
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
            } catch (e: java.net.BindException) {
                PluginLogger.Service.error("Force-start failed - port already in use", e)
                false
            } catch (e: java.util.concurrent.ExecutionException) {
                PluginLogger.Service.error("Force-start failed due to internal server error", e)
                false
            } catch (e: java.util.concurrent.TimeoutException) {
                PluginLogger.Service.error("Force-start timed out", e)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Force-start failed - invalid server state", e)
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

    /**
     * Track async task for cleanup on dispose
     */
    private fun trackTask(taskId: String, future: CompletableFuture<*>) {
        activeTasks[taskId] = future
        future.whenComplete { _, _ -> activeTasks.remove(taskId) }
    }

    /**
     * Dispose method for dynamic plugin support
     * Ensures all resources are cleaned up when plugin is unloaded
     */
    override fun dispose() {
        PluginLogger.Service.info("Disposing OpenRouterProxyService - cleaning up resources")

        try {
            // Cancel all active async tasks
            val taskCount = activeTasks.size
            if (taskCount > 0) {
                PluginLogger.Service.debug("Cancelling $taskCount active tasks")
                activeTasks.values.forEach { it.cancel(true) }
                activeTasks.clear()
            }

            // Stop proxy server if running
            val status = proxyServer.getStatus()
            if (status.isRunning) {
                PluginLogger.Service.info("Stopping proxy server during plugin disposal")
                try {
                    proxyServer.stop().get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    PluginLogger.Service.info("Proxy server stopped successfully")
                } catch (e: java.util.concurrent.ExecutionException) {
                    PluginLogger.Service.error("Error stopping proxy server during disposal", e)
                } catch (e: java.util.concurrent.TimeoutException) {
                    PluginLogger.Service.error("Error stopping proxy server during disposal", e)
                } catch (e: IllegalStateException) {
                    PluginLogger.Service.error("Error stopping proxy server during disposal", e)
                } catch (e: RuntimeException) {
                    PluginLogger.Service.error("Error stopping proxy server during disposal", e)
                }
            }

            PluginLogger.Service.info("OpenRouterProxyService disposed successfully")
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error during OpenRouterProxyService disposal", e)
        } catch (e: RuntimeException) {
            PluginLogger.Service.error("Error during OpenRouterProxyService disposal", e)
        }
    }
}
