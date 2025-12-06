package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Health check endpoint for the proxy server
 */
class HealthCheckServlet : HttpServlet() {

    private val gson = Gson()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            PluginLogger.Service.debug("Health check request received")

            val healthResponse = ResponseTranslator.createHealthCheckResponse()

            resp.contentType = "application/json"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(gson.toJson(healthResponse))

            PluginLogger.Service.debug("Health check response sent successfully")
        } catch (e: Exception) {
            PluginLogger.Service.error("Health check failed", e)

            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"

            val errorResponse = mapOf(
                "status" to "error",
                "message" to "Health check failed",
                "error" to e.message
            )
            resp.writer.write(gson.toJson(errorResponse))
        }
    }

    override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
        // Handle CORS preflight requests
        resp.setHeader("Access-Control-Allow-Origin", "*")
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        resp.status = HttpServletResponse.SC_OK
    }
}
