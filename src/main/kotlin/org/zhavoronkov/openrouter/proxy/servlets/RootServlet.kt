package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.services.OpenRouterService

/**
 * Servlet that handles root endpoint (/)
 * Provides basic API information for OpenAI API compatibility
 */
class RootServlet(
    private val openRouterService: OpenRouterService
) : HttpServlet() {
    private val gson = Gson()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val requestURI = req.requestURI
            val servletPath = req.servletPath
            val pathInfo = req.pathInfo

            PluginLogger.Service.info("🧪 Root servlet - URI: '$requestURI', servletPath: '$servletPath', pathInfo: '$pathInfo'")

            // Handle /models requests by creating a models response directly
            if (requestURI == "/models" || servletPath == "/models") {
                PluginLogger.Service.info("🧪 Root servlet handling /models request directly")
                handleModelsRequest(req, resp)
                return
            }

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
                    "/models",  // AI Assistant compatibility alias
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
            handleModelsOptions(req, resp)
            return
        }

        // Handle CORS preflight requests for other paths
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun handleModelsRequest(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            PluginLogger.Service.info("🧪 Creating models response directly in RootServlet")

            // Set response headers
            resp.contentType = "application/json"
            resp.setHeader("Access-Control-Allow-Origin", "*")
            resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
            resp.setHeader("Access-Control-Max-Age", "3600")

            // Create the same models response as ModelsServlet
            val modelsResponse = createCoreModelsResponse()
            resp.writer.write(gson.toJson(modelsResponse))

            PluginLogger.Service.info("🧪 Models response sent successfully from RootServlet")

        } catch (e: Exception) {
            PluginLogger.Service.error("Error handling /models request in RootServlet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write(gson.toJson(mapOf("error" to "Internal server error")))
        }
    }

    private fun handleModelsOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.setHeader("Access-Control-Max-Age", "3600")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun createCoreModelsResponse(): Map<String, Any> {
        // Return models with FULL OpenRouter format (provider/model)
        // This is critical - OpenRouter API requires full model names with provider prefix
        val models = listOf(
            createModel("openai/gpt-4", "GPT-4"),
            createModel("openai/gpt-4-turbo", "GPT-4 Turbo"),
            createModel("openai/gpt-3.5-turbo", "GPT-3.5 Turbo"),
            createModel("openai/gpt-4o", "GPT-4o"),
            createModel("openai/gpt-4o-mini", "GPT-4o Mini")
        )

        return mapOf(
            "object" to "list",
            "data" to models
        )
    }

    private fun createModel(id: String, name: String): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "object" to "model",
            "created" to 1687882411,
            "owned_by" to "openai",
            "permission" to listOf(createDefaultPermission()),
            "root" to id,
            "parent" to null
        )
    }

    private fun createDefaultPermission(): Map<String, Any?> {
        return mapOf(
            "id" to "modelperm-${System.currentTimeMillis()}",
            "object" to "model_permission",
            "created" to 1687882411,
            "allow_create_engine" to false,
            "allow_sampling" to true,
            "allow_logprobs" to true,
            "allow_search_indices" to false,
            "allow_view" to true,
            "allow_fine_tuning" to false,
            "organization" to "*",
            "group" to null,
            "is_blocking" to false
        )
    }
}
