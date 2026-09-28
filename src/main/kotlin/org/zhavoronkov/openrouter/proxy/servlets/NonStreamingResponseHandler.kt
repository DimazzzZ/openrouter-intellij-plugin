package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.requests.RequestTrace
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Handles non-streaming chat completion requests
 */
class NonStreamingResponseHandler(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    // The endpoint this handler posts to. Defaulted to the live API so existing callers are
    // unaffected; ChatCompletionServlet threads its own value through so the servlet and this
    // handler can be pointed at one test double together, rather than the servlet alone.
    private val openRouterApiUrl: () -> String = ::defaultChatCompletionsUrl
) {

    companion object {
        private const val NANOSECONDS_TO_MILLISECONDS = 1_000_000L
    }

    @Suppress("LongParameterList")
    fun handleNonStreamingRequest(
        resp: HttpServletResponse,
        requestBody: String,
        apiKey: String,
        originalModel: String,
        requestId: String,
        startNs: Long,
        trace: RequestTrace? = null
    ) {
        val openRouterResponse = executeOpenRouterRequest(requestBody, apiKey, resp, requestId, trace) ?: return
        val openAIResponse = translateResponse(openRouterResponse, originalModel, resp, requestId, trace) ?: return
        sendSuccessResponse(resp, openAIResponse, startNs, requestId)
    }

    private fun executeOpenRouterRequest(
        requestBody: String,
        apiKey: String,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace?
    ): ChatCompletionResponse? {
        PluginLogger.Service.info("[Chat-$requestId] Dispatching request to OpenRouter API…")

        val request = OpenRouterRequestBuilder.buildPostRequest(
            url = openRouterApiUrl(),
            jsonBody = requestBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )

        return try {
            httpClient.newCall(request).execute().use { response ->
                handleOpenRouterResponse(response, resp, requestId, trace)
            }
        } catch (e: IOException) {
            trace?.fail("Network error calling OpenRouter: ${e.message}")
            PluginLogger.Service.error("[Chat-$requestId] Network error calling OpenRouter: ${e.message}", e)
            sendErrorResponse(
                resp,
                "Network error calling OpenRouter: ${e.message}",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE
            )
            null
        } catch (e: JsonSyntaxException) {
            trace?.fail("Failed to parse OpenRouter response: ${e.message}")
            PluginLogger.Service.error("[Chat-$requestId] JSON parsing error: ${e.message}", e)
            sendErrorResponse(
                resp,
                "Failed to parse OpenRouter response: ${e.message}",
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            )
            null
        }
    }

    private fun handleOpenRouterResponse(
        response: okhttp3.Response,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace?
    ): ChatCompletionResponse? {
        // Log OpenRouter-specific metadata headers at debug level
        logOpenRouterMetadata(response, requestId)

        return when {
            !response.isSuccessful -> {
                val errorBody = response.body?.string() ?: "Unknown error"
                PluginLogger.Service.error(
                    "[Chat-$requestId] OpenRouter returned error: ${response.code} - $errorBody"
                )
                trace?.fail("OpenRouter API error ${response.code}: $errorBody")
                sendErrorResponse(resp, "OpenRouter API error: $errorBody", response.code)
                null
            }
            else -> parseOpenRouterResponseBody(response, resp, requestId, trace)
        }
    }

    private fun parseOpenRouterResponseBody(
        response: okhttp3.Response,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace?
    ): ChatCompletionResponse? {
        val responseBody = response.body?.string()
        return if (responseBody == null) {
            trace?.fail("No response body from OpenRouter")
            PluginLogger.Service.error("[Chat-$requestId] OpenRouter returned null response body")
            sendErrorResponse(
                resp,
                "Failed to get response from OpenRouter",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE
            )
            null
        } else {
            // Read from the body OpenRouter sent, not the translated one: translation keeps only
            // what an OpenAI client expects, and the provider, cost and search count are not that.
            val tree = gson.fromJson(responseBody, JsonObject::class.java)
            tree?.let { trace?.observe(it) }
            val openRouterResponse = gson.fromJson(tree, ChatCompletionResponse::class.java)
            PluginLogger.Service.info("[Chat-$requestId] Received response from OpenRouter")
            openRouterResponse
        }
    }

    private fun translateResponse(
        openRouterResponse: ChatCompletionResponse,
        originalModel: String,
        resp: HttpServletResponse,
        requestId: String,
        trace: RequestTrace?
    ): OpenAIChatCompletionResponse? {
        val openAIResponse = ResponseTranslator.translateChatCompletionResponse(
            openRouterResponse,
            originalModel
        )
        val openAIResponseJson = gson.toJson(openAIResponse)
        PluginLogger.Service.debug("[Chat-$requestId] Translated OpenAI response: $openAIResponseJson")

        if (!ResponseTranslator.validateTranslatedResponse(openAIResponse)) {
            PluginLogger.Service.error("[Chat-$requestId] Response validation failed")
            trace?.fail("Invalid response format")
            sendErrorResponse(resp, "Invalid response format", HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            return null
        }

        return openAIResponse
    }

    private fun sendSuccessResponse(
        resp: HttpServletResponse,
        openAIResponse: OpenAIChatCompletionResponse,
        startNs: Long,
        requestId: String
    ) {
        val durationMs = (System.nanoTime() - startNs) / NANOSECONDS_TO_MILLISECONDS
        PluginLogger.Service.info(
            "[Chat-$requestId] ✅ Chat completion successful in ${durationMs}ms, returning response"
        )

        resp.contentType = "application/json"
        resp.status = HttpServletResponse.SC_OK
        resp.writer.write(gson.toJson(openAIResponse))
    }

    private fun sendErrorResponse(resp: HttpServletResponse, message: String, statusCode: Int) {
        resp.contentType = "application/json"
        resp.status = statusCode
        resp.writer.write("""{"error": {"message": "$message", "type": "api_error", "code": "$statusCode"}}""")
    }

    /**
     * Log OpenRouter-specific response metadata headers at debug level.
     * These include routing decisions, model used, generation ID, etc.
     */
    private fun logOpenRouterMetadata(response: okhttp3.Response, requestId: String) {
        val metadataHeaders = response.headers.names()
            .filter(::isOpenRouterMetadataHeader)

        if (metadataHeaders.isNotEmpty()) {
            val headerSummary = metadataHeaders.joinToString(", ") { name ->
                "$name=${response.header(name)}"
            }
            PluginLogger.Service.debug("[Chat-$requestId] OpenRouter metadata: $headerSummary")
        }
    }
}
