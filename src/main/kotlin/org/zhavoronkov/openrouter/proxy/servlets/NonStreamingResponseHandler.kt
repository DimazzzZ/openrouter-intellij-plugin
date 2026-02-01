package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import jakarta.servlet.http.HttpServletResponse
import okhttp3.OkHttpClient
import org.zhavoronkov.openrouter.models.ChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionResponse
import org.zhavoronkov.openrouter.proxy.translation.ResponseTranslator
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.io.IOException

/**
 * Handles non-streaming chat completion requests
 */
class NonStreamingResponseHandler(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {

    companion object {
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val NANOSECONDS_TO_MILLISECONDS = 1_000_000L
    }

    @Suppress("LongParameterList")
    fun handleNonStreamingRequest(
        resp: HttpServletResponse,
        requestBody: String,
        apiKey: String,
        originalModel: String,
        requestId: String,
        startNs: Long
    ) {
        val openRouterResponse = executeOpenRouterRequest(requestBody, apiKey, resp, requestId) ?: return
        val openAIResponse = translateResponse(openRouterResponse, originalModel, resp, requestId) ?: return
        sendSuccessResponse(resp, openAIResponse, startNs, requestId)
    }

    private fun executeOpenRouterRequest(
        requestBody: String,
        apiKey: String,
        resp: HttpServletResponse,
        requestId: String
    ): ChatCompletionResponse? {
        PluginLogger.Service.info("[Chat-$requestId] Dispatching request to OpenRouter API…")

        val request = OpenRouterRequestBuilder.buildPostRequest(
            url = OPENROUTER_API_URL,
            jsonBody = requestBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )

        return try {
            httpClient.newCall(request).execute().use { response ->
                handleOpenRouterResponse(response, resp, requestId)
            }
        } catch (e: IOException) {
            PluginLogger.Service.error("[Chat-$requestId] Network error calling OpenRouter: ${e.message}", e)
            sendErrorResponse(
                resp,
                "Network error calling OpenRouter: ${e.message}",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE
            )
            null
        } catch (e: JsonSyntaxException) {
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
        requestId: String
    ): ChatCompletionResponse? {
        return when {
            !response.isSuccessful -> {
                val errorBody = response.body?.string() ?: "Unknown error"
                PluginLogger.Service.error(
                    "[Chat-$requestId] OpenRouter returned error: ${response.code} - $errorBody"
                )
                sendErrorResponse(resp, "OpenRouter API error: $errorBody", response.code)
                null
            }
            else -> parseOpenRouterResponseBody(response, resp, requestId)
        }
    }

    private fun parseOpenRouterResponseBody(
        response: okhttp3.Response,
        resp: HttpServletResponse,
        requestId: String
    ): ChatCompletionResponse? {
        val responseBody = response.body?.string()
        return if (responseBody == null) {
            PluginLogger.Service.error("[Chat-$requestId] OpenRouter returned null response body")
            sendErrorResponse(
                resp,
                "Failed to get response from OpenRouter",
                HttpServletResponse.SC_SERVICE_UNAVAILABLE
            )
            null
        } else {
            val openRouterResponse = gson.fromJson(responseBody, ChatCompletionResponse::class.java)
            PluginLogger.Service.info("[Chat-$requestId] Received response from OpenRouter")
            openRouterResponse
        }
    }

    private fun translateResponse(
        openRouterResponse: ChatCompletionResponse,
        originalModel: String,
        resp: HttpServletResponse,
        requestId: String
    ): OpenAIChatCompletionResponse? {
        val openAIResponse = ResponseTranslator.translateChatCompletionResponse(
            openRouterResponse,
            originalModel
        )
        val openAIResponseJson = gson.toJson(openAIResponse)
        PluginLogger.Service.debug("[Chat-$requestId] Translated OpenAI response: $openAIResponseJson")

        if (!ResponseTranslator.validateTranslatedResponse(openAIResponse)) {
            PluginLogger.Service.error("[Chat-$requestId] Response validation failed")
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
}
