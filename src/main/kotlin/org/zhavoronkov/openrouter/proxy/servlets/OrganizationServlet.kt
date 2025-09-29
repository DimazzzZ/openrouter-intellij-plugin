package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator

/**
 * Servlet that handles OpenAI organization endpoint (/v1/organizations)
 * Provides organization information for OpenAI API compatibility
 */
class OrganizationServlet : HttpServlet() {
    private val gson = Gson()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            PluginLogger.Service.debug("Organization endpoint called")
            
            // Validate authorization header (strict OpenAI compatibility)
            val authHeader = req.getHeader("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                PluginLogger.Service.debug("Missing or invalid Authorization header")
                sendAuthErrorResponse(resp)
                return
            }
            
            val apiKey = authHeader.substring(7) // Remove "Bearer " prefix
            if (apiKey.isBlank()) {
                PluginLogger.Service.debug("Empty API key provided")
                sendAuthErrorResponse(resp)
                return
            }
            
            PluginLogger.Service.debug("Valid Authorization header provided")

            // Set response headers
            resp.contentType = "application/json"
            resp.setHeader("Access-Control-Allow-Origin", "*")
            resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")

            // Create mock organization response (OpenAI format)
            val organizationResponse = mapOf(
                "object" to "organization",
                "id" to "org-openrouter-proxy",
                "name" to "OpenRouter Proxy",
                "description" to "OpenRouter AI Assistant Integration",
                "personal" to false,
                "default" to true,
                "role" to "owner",
                "created" to 1677610602
            )

            resp.writer.write(gson.toJson(organizationResponse))
            PluginLogger.Service.debug("Organization response sent successfully")

        } catch (e: Exception) {
            PluginLogger.Service.error("Error in organization endpoint", e)
            sendErrorResponse(resp, "Internal server error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // Handle CORS preflight requests
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }

    private fun sendAuthErrorResponse(resp: HttpServletResponse) {
        resp.status = HttpServletResponse.SC_UNAUTHORIZED
        resp.contentType = "application/json"
        
        val errorResponse = ResponseTranslator.createAuthErrorResponse()
        resp.writer.write(gson.toJson(errorResponse))
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.status = statusCode
        resp.contentType = "application/json"
        
        val errorResponse = ResponseTranslator.createErrorResponse(
            message = message,
            type = when (statusCode) {
                HttpServletResponse.SC_BAD_REQUEST -> "invalid_request_error"
                HttpServletResponse.SC_REQUEST_TIMEOUT -> "timeout_error"
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE -> "request_too_large"
                HttpServletResponse.SC_SERVICE_UNAVAILABLE -> "service_unavailable"
                else -> "internal_error"
            }
        )
        
        resp.writer.write(gson.toJson(errorResponse))
    }
}
