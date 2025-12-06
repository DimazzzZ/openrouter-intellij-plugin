package org.zhavoronkov.openrouter.proxy.servlets

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Servlet that handles OpenAI organization endpoint (/v1/organizations)
 * Provides organization information for OpenAI API compatibility
 */
class OrganizationServlet : OpenAIBaseServlet() {

    companion object {
        // Unix timestamp for organization creation date
        private const val ORGANIZATION_CREATED_TIMESTAMP = 1677610602L
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        handleRequest({
            PluginLogger.Service.debug("Organization endpoint called")

            // Validate authorization
            validateAndExtractApiKey(resp, req) ?: return@handleRequest

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
                "created" to ORGANIZATION_CREATED_TIMESTAMP
            )

            resp.writer.write(gson.toJson(organizationResponse))
            PluginLogger.Service.debug("Organization response sent successfully")
        }, resp, "organization")
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        handleOptionsRequest(resp, "GET, OPTIONS")
    }
}
