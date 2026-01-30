@file:Suppress("TooGenericExceptionCaught")

package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Servlet that handles root endpoint (/)
 * Provides basic API information for OpenAI API compatibility
 */
class RootServlet : HttpServlet() {
    private val gson = Gson()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val requestURI = req.requestURI
            val servletPath = req.servletPath
            val pathInfo = req.pathInfo

            PluginLogger.Service.info(
                "🧪 Root servlet - URI: '$requestURI', servletPath: '$servletPath', pathInfo: '$pathInfo'"
            )

            // Note: /models requests are now handled exclusively by ModelsServlet
            // This prevents duplicate endpoint handling and routing conflicts

            PluginLogger.Service.debug("Root endpoint called for non-models path")

            // Set response headers
            resp.contentType = "application/json"
            resp.setHeader("Access-Control-Allow-Origin", "*")
            resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")

            // Create API info response
            val apiInfo = mapOf(
                "message" to "OpenRouter AI Assistant Proxy",
                "version" to "1.0.0",
                "debug_path_info" to mapOf(
                    "requestURI" to requestURI,
                    "servletPath" to servletPath,
                    "pathInfo" to pathInfo
                ),
                "endpoints" to listOf(
                    "/v1/models",
                    "/models", // AI Assistant compatibility alias
                    "/v1/chat/completions",
                    "/v1/engines",
                    "/v1/organizations",
                    "/health"
                ),
                "compatible_with" to "OpenAI API v1",
                "proxy_for" to "OpenRouter.ai"
            )

            resp.writer.write(gson.toJson(apiInfo))
            PluginLogger.Service.debug("Root response sent successfully")
        } catch (e: Exception) {
            PluginLogger.Service.error("Error in root endpoint", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write(gson.toJson(mapOf("error" to "Internal server error")))
        }
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestURI = req.requestURI

        // Handle /models OPTIONS requests
        if (requestURI == "/models" || req.servletPath == "/models") {
            PluginLogger.Service.info("🧪 Root servlet handling /models OPTIONS request")
            handleModelsOptions(resp)
            return
        }

        // Handle CORS preflight requests for other paths
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    // Note: handleModelsRequest method removed - /models is now handled exclusively by ModelsServlet

    private fun handleModelsOptions(resp: HttpServletResponse) {
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.setHeader("Access-Control-Max-Age", "3600")
        resp.status = HttpServletResponse.SC_OK
    }

    // Note: createCoreModelsResponse method removed - models are now handled exclusively by ModelsServlet

    // Note: createModel and createDefaultPermission methods removed -
    // models are now handled exclusively by ModelsServlet
}
