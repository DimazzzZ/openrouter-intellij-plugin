package org.zhavoronkov.openrouter.proxy

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import jakarta.servlet.DispatcherType
import java.util.EnumSet
import org.zhavoronkov.openrouter.proxy.servlets.ChatCompletionServlet
import org.zhavoronkov.openrouter.proxy.servlets.EnginesServlet
import org.zhavoronkov.openrouter.proxy.servlets.HealthCheckServlet
import org.zhavoronkov.openrouter.proxy.servlets.ModelsServlet
import org.zhavoronkov.openrouter.proxy.servlets.OrganizationServlet
import org.zhavoronkov.openrouter.proxy.servlets.RootServlet
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded HTTP server that provides OpenAI-compatible API endpoints
 * for integration with JetBrains AI Assistant
 */
class OpenRouterProxyServer {

    companion object {
        private const val DEFAULT_PORT = 8080
        private const val MIN_PORT = 8080
        private const val MAX_PORT = 8090
        private const val LOCALHOST = "127.0.0.1"
        private const val RESTART_DELAY_MS = 1000L

        // Singleton instance
        @Volatile
        private var INSTANCE: OpenRouterProxyServer? = null

        fun getInstance(): OpenRouterProxyServer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OpenRouterProxyServer().also { INSTANCE = it }
            }
        }
    }

    private var server: Server? = null
    private var currentPort: Int = 0
    private val isRunning = AtomicBoolean(false)
    private val gson = Gson()

    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()

    /**
     * Starts the proxy server on an available port
     */
    fun start(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            var port = -1
            try {
                if (isRunning.get()) {
                    PluginLogger.Service.debug("Proxy server is already running on port $currentPort")
                    return@supplyAsync true
                }

                port = findAvailablePort()
                if (port == -1) {
                    PluginLogger.Service.error("No available ports found in range $MIN_PORT-$MAX_PORT")
                    return@supplyAsync false
                }

                server = Server(port).apply {
                    handler = createServletHandler()
                }

                server?.start()
                currentPort = port
                isRunning.set(true)

                PluginLogger.Service.info("OpenRouter proxy server started on http://$LOCALHOST:$port")
                PluginLogger.Service.info("AI Assistant can connect to: http://$LOCALHOST:$port")

                true
            } catch (e: java.net.BindException) {
                PluginLogger.Service.error("Port $port is already in use", e)
                isRunning.set(false)
                false
            } catch (e: java.io.IOException) {
                PluginLogger.Service.error("IO error starting proxy server", e)
                isRunning.set(false)
                false
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Server already running or in invalid state", e)
                isRunning.set(false)
                false
            } catch (e: RuntimeException) {
                PluginLogger.Service.error("Runtime error starting proxy server", e)
                isRunning.set(false)
                false
            }
        }
    }

    /**
     * Stops the proxy server
     */
    fun stop(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                if (!isRunning.get()) {
                    PluginLogger.Service.debug("Proxy server is not running")
                    return@supplyAsync true
                }

                server?.stop()
                server?.join()
                server = null
                isRunning.set(false)
                currentPort = 0

                PluginLogger.Service.info("OpenRouter proxy server stopped")
                true
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Server not running or in invalid state", e)
                false
            } catch (e: java.io.IOException) {
                PluginLogger.Service.error("IO error stopping proxy server", e)
                false
            } catch (e: RuntimeException) {
                PluginLogger.Service.error("Runtime error stopping proxy server", e)
                false
            }
        }
    }

    /**
     * Restarts the proxy server
     */
    fun restart(): CompletableFuture<Boolean> {
        return stop().thenCompose { stopSuccess ->
            if (stopSuccess) {
                // Wait a moment before restarting
                Thread.sleep(RESTART_DELAY_MS)
                start()
            } else {
                CompletableFuture.completedFuture(false)
            }
        }
    }

    /**
     * Gets the current server status
     */
    fun getStatus(): ProxyServerStatus {
        return ProxyServerStatus(
            isRunning = isRunning.get(),
            port = if (isRunning.get()) currentPort else null,
            url = if (isRunning.get()) "http://$LOCALHOST:$currentPort" else null,
            isConfigured = settingsService.isConfigured()
        )
    }

    /**
     * Tests if the server is responding correctly
     */
    fun testConnection(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            if (!isRunning.get()) {
                return@supplyAsync false
            }

            try {
                // Simple health check
                val url = "http://$LOCALHOST:$currentPort/health"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: java.net.ConnectException) {
                PluginLogger.Service.error("Connection refused during proxy test", e)
                false
            } catch (e: java.net.SocketTimeoutException) {
                PluginLogger.Service.error("Connection timeout during proxy test", e)
                false
            } catch (e: java.io.IOException) {
                PluginLogger.Service.error("IO error during proxy connection test", e)
                false
            } catch (e: RuntimeException) {
                PluginLogger.Service.error("Runtime error during proxy connection test", e)
                false
            }
        }
    }

    /**
     * Creates the servlet handler with all endpoints
     */
    private fun createServletHandler(): ServletContextHandler {
        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.contextPath = "/"

        // Add servlets
        val rootServlet = ServletHolder(RootServlet(openRouterService))
        val healthServlet = ServletHolder(HealthCheckServlet())
        val modelsServlet = ServletHolder(ModelsServlet(openRouterService))
        val chatServlet = ServletHolder(ChatCompletionServlet(openRouterService))
        val organizationServlet = ServletHolder(OrganizationServlet())
        val enginesServlet = ServletHolder(EnginesServlet())

        context.addServlet(healthServlet, "/health")
        context.addServlet(modelsServlet, "/v1/models")
        context.addServlet(modelsServlet, "/models") // AI Assistant compatibility alias

        context.addServlet(chatServlet, "/chat/completions") // AI Assistant compatibility alias

        context.addServlet(chatServlet, "/v1/chat/completions")
        context.addServlet(organizationServlet, "/v1/organizations")
        context.addServlet(enginesServlet, "/v1/engines")
        context.addServlet(rootServlet, "/")  // Root servlet handles /models and other routes

        // Add CORS filter for browser compatibility
        context.addFilter(CorsFilter::class.java, "/*", null)

        return context
    }

    /**
     * Finds an available port in the specified range
     */
    private fun findAvailablePort(): Int {
        for (port in MIN_PORT..MAX_PORT) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        return -1
    }

    /**
     * Checks if a port is available
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: java.net.BindException) {
            PluginLogger.Service.debug("Port $port is not available (bind exception)")
            false
        } catch (e: java.io.IOException) {
            PluginLogger.Service.debug("Port $port is not available (IO exception)")
            false
        }
    }

    /**
     * Data class representing server status
     */
    data class ProxyServerStatus(
        val isRunning: Boolean,
        val port: Int?,
        val url: String?,
        val isConfigured: Boolean
    )
}
