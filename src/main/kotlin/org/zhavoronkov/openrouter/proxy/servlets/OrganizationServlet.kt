package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Servlet that handles OpenAI organization endpoint (/v1/organizations)
 * Provides organization information for OpenAI API compatibility
 */
class OrganizationServlet : OpenAIBaseServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest({
            PluginLogger.Service.debug("Organization endpoint called")

            // Validate authorization and extract API key
            val apiKey = validateAndExtractApiKey(resp, req) ?: return@handleRequest

            // Set response headers
            resp.contentType = "application/json"
            setCORSHeaders(resp, "GET, OPTIONS")

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
        }, resp, "organization")
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        handleOptionsRequest(resp, "GET, OPTIONS")
    }
}
