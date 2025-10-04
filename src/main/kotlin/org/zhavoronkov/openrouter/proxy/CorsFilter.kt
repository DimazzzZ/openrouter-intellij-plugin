package org.zhavoronkov.openrouter.proxy

import org.zhavoronkov.openrouter.utils.PluginLogger
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * CORS filter to allow cross-origin requests from AI Assistant
 */
class CorsFilter : Filter {

    override fun init(filterConfig: FilterConfig?) {
        PluginLogger.Service.debug("CORS filter initialized")
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        // Set CORS headers
        httpResponse.setHeader("Access-Control-Allow-Origin", "*")
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
        httpResponse.setHeader("Access-Control-Max-Age", "3600")

        // Handle preflight requests
        if ("OPTIONS".equals(httpRequest.method, ignoreCase = true)) {
            httpResponse.status = HttpServletResponse.SC_OK
            return
        }

        // Continue with the request
        chain.doFilter(request, response)
    }

    override fun destroy() {
        PluginLogger.Service.debug("CORS filter destroyed")
    }
}
