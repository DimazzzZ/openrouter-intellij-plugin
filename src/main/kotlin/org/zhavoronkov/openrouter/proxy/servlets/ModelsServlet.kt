package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.proxy.models.OpenAIModelsResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIModel
import org.zhavoronkov.openrouter.proxy.models.OpenAIPermission
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.utils.PluginLogger
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit

/**
 * Servlet that provides OpenAI-compatible /v1/models endpoint
 */
class ModelsServlet(
    private val openRouterService: OpenRouterService
) : HttpServlet() {

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }

    private val gson = Gson()

    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val userAgent = req.getHeader("User-Agent") ?: "unknown"
            PluginLogger.Service.debug("Models request received from ${req.remoteAddr}, User-Agent: $userAgent")

            // Log all headers for debugging AI Assistant integration
            val headers = req.headerNames.asSequence().associateWith { req.getHeader(it) }
            PluginLogger.Service.debug("Request headers: $headers")

            // Allow unauthenticated requests for model discovery (common practice for AI clients)
            val authHeader = req.getHeader("Authorization")
            if (authHeader != null) {
                PluginLogger.Service.debug("Authorization header provided: ${authHeader.take(20)}...")
            } else {
                PluginLogger.Service.debug("No Authorization header - allowing unauthenticated model discovery for AI Assistant compatibility")
            }

            // TESTING: Return a curated list of core OpenAI models for AI Assistant compatibility
            // This bypasses OpenRouter API calls and provides immediate compatibility
            val openAIModelsResponse = createCoreModelsResponse()

            PluginLogger.Service.info("🧪 TESTING: Returning curated models list for AI Assistant compatibility - NEW CODE ACTIVE")
            PluginLogger.Service.info("🧪 TESTING: Model count: ${openAIModelsResponse.data.size}")
            
            PluginLogger.Service.info("🧪 TESTING: Returning ${openAIModelsResponse.data.size} models from NEW CODE")
            
            resp.contentType = "application/json"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(gson.toJson(openAIModelsResponse))
            
        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.error("Models request timed out", e)
            sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
        } catch (e: Exception) {
            PluginLogger.Service.error("Models request failed", e)
            sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    public override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // Handle CORS preflight requests
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun createCoreModelsResponse(): OpenAIModelsResponse {
        // Return core OpenAI models that AI Assistant recognizes and supports
        val coreModels = listOf(
            OpenAIModel(
                id = "gpt-4",
                created = 1687882411,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4-turbo",
                created = 1712361441,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4-turbo",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-3.5-turbo",
                created = 1677610602,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-3.5-turbo",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4o",
                created = 1715367049,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4o",
                parent = null
            ),
            OpenAIModel(
                id = "gpt-4o-mini",
                created = 1721172741,
                owned_by = "openai",
                permission = listOf(createDefaultPermission()),
                root = "gpt-4o-mini",
                parent = null
            )
        )

        return OpenAIModelsResponse(
            `object` = "list",
            data = coreModels
        )
    }

    private fun createDefaultPermission(): OpenAIPermission {
        return OpenAIPermission(
            id = "perm-chatcmpl-${System.currentTimeMillis()}",
            created = System.currentTimeMillis() / 1000,
            allow_create_engine = false,
            allow_sampling = true,
            allow_logprobs = true,
            allow_search_indices = false,
            allow_view = true,
            allow_fine_tuning = false,
            organization = "*",
            is_blocking = false
        )
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.status = statusCode
        resp.contentType = "application/json"

        val errorResponse = ResponseTranslator.createErrorResponse(
            message = message,
            type = when (statusCode) {
                HttpServletResponse.SC_REQUEST_TIMEOUT -> "timeout_error"
                HttpServletResponse.SC_SERVICE_UNAVAILABLE -> "service_unavailable"
                else -> "internal_error"
            }
        )

        resp.writer.write(gson.toJson(errorResponse))
    }

    private fun sendAuthErrorResponse(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_UNAUTHORIZED
        resp.contentType = "application/json"

        val errorResponse = ResponseTranslator.createAuthErrorResponse()
        resp.writer.write(gson.toJson(errorResponse))
    }
}
