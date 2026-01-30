@file:Suppress("TooGenericExceptionCaught")

package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.zhavoronkov.openrouter.models.ApiResult
import org.zhavoronkov.openrouter.proxy.models.OpenAIModel
import org.zhavoronkov.openrouter.proxy.models.OpenAIModelsResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIPermission
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Servlet that provides OpenAI-compatible /v1/models endpoint
 */
class ModelsServlet(
    private val openRouterService: OpenRouterService
) : HttpServlet() {

    private val settingsService = OpenRouterSettingsService.getInstance()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds
        private const val CACHE_TTL_MINUTES = 15L // Cache models for 15 minutes
        private const val CACHE_TTL_MS = CACHE_TTL_MINUTES * 60 * 1000

        // Auth header preview length for logging
        private const val AUTH_HEADER_PREVIEW_LENGTH = 20

        // Cache for full models list
        private val modelsCache = ConcurrentHashMap<String, OpenAIModelsResponse>()
        private val cacheTimestamp = AtomicLong(0)

        // Request tracking - thread-safe counter using AtomicInteger
        private val requestCounter = AtomicInteger(0)
    }

    private val gson = Gson()

    public override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestId = requestCounter.incrementAndGet()
        val timestamp = System.currentTimeMillis()

        try {
            val userAgent = req.getHeader("User-Agent") ?: "unknown"
            val requestURI = req.requestURI
            val queryString = req.queryString

            PluginLogger.Service.info("═══════════════════════════════════════════════════════")
            PluginLogger.Service.info("[Models-$requestId] NEW /models REQUEST RECEIVED")
            PluginLogger.Service.info(
                "[Models-$requestId] URI: $requestURI${if (queryString != null) "?$queryString" else ""}"
            )
            PluginLogger.Service.info("[Models-$requestId] User-Agent: $userAgent")
            PluginLogger.Service.info("[Models-$requestId] Remote Address: ${req.remoteAddr}")
            PluginLogger.Service.info("═══════════════════════════════════════════════════════")

            // Log all headers for debugging AI Assistant integration (DEBUG level only)
            val headers = req.headerNames.asSequence().associateWith { req.getHeader(it) }
            PluginLogger.Service.debug("Request headers: $headers")

            // Allow unauthenticated requests for model discovery (common practice for AI clients)
            val authHeader = req.getHeader("Authorization")
            if (authHeader != null) {
                // Only log auth header in DEBUG mode
                val authPreview = authHeader.take(AUTH_HEADER_PREVIEW_LENGTH)
                PluginLogger.Service.debug("Authorization header provided: $authPreview...")
            }

            // Parse query parameters for filtering
            val mode = req.getParameter("mode") ?: "curated" // curated, all, search
            val search = req.getParameter("search")
            val provider = req.getParameter("provider")
            val limit = req.getParameter("limit")?.toIntOrNull()

            PluginLogger.Service.info("Models request: mode=$mode, search=$search, provider=$provider, limit=$limit")

            val modelsResponse = when (mode) {
                "all" -> getAllModelsResponse(search, provider, limit)
                "search" -> if (search.isNullOrBlank()) {
                    createCoreModelsResponse()
                } else {
                    searchModels(
                        search,
                        provider,
                        limit
                    )
                }
                else -> createCoreModelsResponse() // Default to curated for fast loading
            }

            PluginLogger.Service.info("Returning ${modelsResponse.data.size} models (mode: $mode)")

            resp.contentType = "application/json"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(gson.toJson(modelsResponse))
        } catch (e: java.util.concurrent.TimeoutException) {
            PluginLogger.Service.error("[Models-$requestId] ❌ Models request timed out", e)
            sendErrorResponse(resp, "Request timed out", HttpServletResponse.SC_REQUEST_TIMEOUT)
        } catch (e: Exception) {
            PluginLogger.Service.error("[Models-$requestId] ❌ Models request failed", e)
            sendErrorResponse(resp, "Internal server error: ${e.message}", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } finally {
            val duration = System.currentTimeMillis() - timestamp
            PluginLogger.Service.info("[Models-$requestId] ✅ Models request completed in ${duration}ms")
            PluginLogger.Service.info("═══════════════════════════════════════════════════════")
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
        // Return user's favorite models with FULL OpenRouter format (provider/model)
        // This is critical - OpenRouter API requires full model names with provider prefix
        var favoriteModelIds = settingsService.favoriteModelsManager.getFavoriteModels()

        // If no favorites are set, ensure we have defaults
        if (favoriteModelIds.isEmpty()) {
            favoriteModelIds = listOf(
                "openai/gpt-4o",
                "openai/gpt-4o-mini",
                "openai/gpt-4-turbo",
                "openai/gpt-4",
                "openai/gpt-3.5-turbo",
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3-opus",
                "anthropic/claude-3-haiku"
            )
        }

        val coreModels = favoriteModelIds.map { modelId ->
            OpenAIModel(
                id = modelId,
                created = System.currentTimeMillis() / 1000, // Use current timestamp
                ownedBy = "openrouter", // Use generic "openrouter" to avoid display issues
                permission = listOf(createDefaultPermission()),
                root = modelId,
                parent = null
            )
        }

        return OpenAIModelsResponse(
            `object` = "list",
            data = coreModels
        )
    }

    /**
     * Get all models from OpenRouter with optional filtering
     */
    private fun getAllModelsResponse(search: String?, provider: String?, limit: Int?): OpenAIModelsResponse {
        // Check cache first
        val now = System.currentTimeMillis()
        val cachedResponse = modelsCache["all"]
        if (cachedResponse != null && (now - cacheTimestamp.get()) < CACHE_TTL_MS) {
            PluginLogger.Service.debug("Returning cached models response (${cachedResponse.data.size} models)")
            return filterModels(cachedResponse, search, provider, limit)
        }

        // Fetch from OpenRouter API
        return try {
            val result = runBlocking {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    openRouterService.getModels()
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val openRouterModels = result.data
                    val openAIModels = convertOpenRouterModelsToOpenAI(openRouterModels)

                    // Cache the result
                    modelsCache["all"] = openAIModels
                    cacheTimestamp.set(now)

                    PluginLogger.Service.info("Fetched and cached ${openAIModels.data.size} models from OpenRouter")
                    filterModels(openAIModels, search, provider, limit)
                }
                is ApiResult.Error -> {
                    PluginLogger.Service.error("Failed to fetch models: ${result.message}")
                    createCoreModelsResponse()
                }
            }
        } catch (e: Exception) {
            PluginLogger.Service.warn("Failed to fetch models from OpenRouter, falling back to curated list", e)
            createCoreModelsResponse()
        }
    }

    /**
     * Search models with specific criteria
     */
    private fun searchModels(search: String, provider: String?, limit: Int?): OpenAIModelsResponse {
        val allModels = getAllModelsResponse(null, null, null)
        return filterModels(allModels, search, provider, limit)
    }

    /**
     * Filter models based on search criteria
     */
    private fun filterModels(
        models: OpenAIModelsResponse,
        search: String?,
        provider: String?,
        limit: Int?
    ): OpenAIModelsResponse {
        var filteredModels = models.data

        // Filter by search term
        if (!search.isNullOrBlank()) {
            val searchLower = search.lowercase()
            filteredModels = filteredModels.filter { model ->
                model.id.lowercase().contains(searchLower) ||
                    model.ownedBy.lowercase().contains(searchLower)
            }
        }

        // Filter by provider
        if (!provider.isNullOrBlank()) {
            filteredModels = filteredModels.filter { model ->
                model.ownedBy.equals(provider, ignoreCase = true) ||
                    model.id.startsWith("$provider/", ignoreCase = true)
            }
        }

        // Apply limit
        if (limit != null && limit > 0) {
            filteredModels = filteredModels.take(limit)
        }

        return OpenAIModelsResponse(
            `object` = "list",
            data = filteredModels
        )
    }

    /**
     * Convert OpenRouter models to OpenAI format
     */
    private fun convertOpenRouterModelsToOpenAI(
        openRouterModels: org.zhavoronkov.openrouter.models.OpenRouterModelsResponse
    ): OpenAIModelsResponse {
        val openAIModels = openRouterModels.data.map { orModel ->
            OpenAIModel(
                id = orModel.id,
                created = orModel.created,
                ownedBy = extractProvider(orModel.id),
                permission = listOf(createDefaultPermission()),
                root = orModel.id,
                parent = null
            )
        }

        return OpenAIModelsResponse(
            `object` = "list",
            data = openAIModels
        )
    }

    /**
     * Extract provider name from model ID (e.g., "openai/gpt-4" -> "openai")
     */
    private fun extractProvider(modelId: String): String {
        return if (modelId.contains("/")) {
            modelId.substringBefore("/")
        } else {
            "openai" // Default to openai for compatibility
        }
    }

    private fun createDefaultPermission(): OpenAIPermission {
        return OpenAIPermission(
            id = "perm-chatcmpl-${System.currentTimeMillis()}",
            created = System.currentTimeMillis() / 1000,
            allowCreateEngine = false,
            allowSampling = true,
            allowLogprobs = true,
            allowSearchIndices = false,
            allowView = true,
            allowFineTuning = false,
            organization = "*",
            isBlocking = false
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
}
