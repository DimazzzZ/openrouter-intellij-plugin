package org.zhavoronkov.openrouter.aiassistant

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/**
 * OpenRouter Chat Model Provider for AI Assistant integration
 * Handles the actual chat/completion requests when AI Assistant uses OpenRouter models
 */
class OpenRouterChatModelProvider {

    private val settingsService = OpenRouterSettingsService.getInstance()
    private val gson = Gson()

    companion object {
        private const val MAX_TOKENS_DEFAULT = 2000
        private const val TEMPERATURE_DEFAULT = 0.7
        private const val SUPPORTS_STREAMING = true

        // Token estimation: ~4 characters per token for most models
        private const val CHARS_PER_TOKEN = 4

        // HTTP client timeouts (in seconds)
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
    }

    /**
     * Send a chat completion request to OpenRouter
     * This method adapts AI Assistant requests to OpenRouter's API format
     */
    fun sendChatRequest(
        modelId: String,
        messages: List<ChatMessage>,
        maxTokens: Int = MAX_TOKENS_DEFAULT,
        temperature: Double = TEMPERATURE_DEFAULT,
        stream: Boolean = false
    ): CompletableFuture<ChatResponse> {
        return CompletableFuture.supplyAsync {
            try {
                if (!settingsService.isConfigured()) {
                    return@supplyAsync ChatResponse.error("OpenRouter not configured")
                }

                val requestBody = createChatRequestBody(modelId, messages, maxTokens, temperature, stream)
                val response = makeOpenRouterRequest(requestBody)

                response ?: ChatResponse.error("No response from OpenRouter")
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Error sending chat request to OpenRouter - service not available", e)
                ChatResponse.error("Chat request failed: ${e.message}")
            } catch (e: ExecutionException) {
                PluginLogger.Service.error("Error executing chat request to OpenRouter", e)
                ChatResponse.error("Chat request failed: ${e.cause?.message ?: e.message}")
            } catch (e: TimeoutException) {
                PluginLogger.Service.error("Chat request to OpenRouter timed out", e)
                ChatResponse.error("Chat request timed out")
            }
        }
    }

    /**
     * Send a completion request (for non-chat use cases)
     */
    @Suppress("Unused")
    fun sendCompletionRequest(
        modelId: String,
        prompt: String,
        maxTokens: Int = MAX_TOKENS_DEFAULT,
        temperature: Double = TEMPERATURE_DEFAULT
    ): CompletableFuture<CompletionResponse> {
        return CompletableFuture.supplyAsync {
            try {
                if (!settingsService.isConfigured()) {
                    return@supplyAsync CompletionResponse.error("OpenRouter not configured")
                }

                // Convert prompt to chat format (OpenRouter primarily uses chat completions)
                val messages = listOf(ChatMessage("user", prompt))
                val chatResponse = sendChatRequest(modelId, messages, maxTokens, temperature).get()

                // Convert chat response to completion response
                if (chatResponse.isSuccess()) {
                    CompletionResponse.success(chatResponse.content ?: "")
                } else {
                    CompletionResponse.error(chatResponse.error ?: "Completion failed")
                }
            } catch (e: IllegalStateException) {
                PluginLogger.Service.error("Error sending completion request to OpenRouter", e)
                CompletionResponse.error("Completion request failed: ${e.message}")
            } catch (e: ExecutionException) {
                PluginLogger.Service.error("Error executing completion request to OpenRouter", e)
                CompletionResponse.error("Completion request failed: ${e.cause?.message ?: e.message}")
            } catch (e: TimeoutException) {
                PluginLogger.Service.error("Completion request to OpenRouter timed out", e)
                CompletionResponse.error("Completion request timed out")
            }
        }
    }

    /**
     * Check if streaming is supported for the given model
     * @param modelId The model ID (reserved for future model-specific streaming support)
     */
    @Suppress("unused", "UnusedParameter")
    fun supportsStreaming(modelId: String): Boolean {
        // All OpenRouter models support streaming, parameter reserved for future model-specific logic
        return SUPPORTS_STREAMING
    }

    /**
     * Get the estimated token count for a message
     * This is a rough approximation since we don't have access to the exact tokenizer
     */
    @Suppress("Unused")
    fun estimateTokenCount(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    private fun createChatRequestBody(
        modelId: String,
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double,
        stream: Boolean
    ): String {
        val requestMap = mapOf(
            "model" to modelId,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "stream" to stream
        )
        return gson.toJson(requestMap)
    }

    private fun makeOpenRouterRequest(requestBody: String): ChatResponse? {
        return try {
            val request = OpenRouterRequestBuilder.buildPostRequest(
                url = "https://openrouter.ai/api/v1/chat/completions",
                jsonBody = requestBody,
                authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                authToken = settingsService.getApiKey()
            )

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    parseOpenRouterResponse(responseBody)
                } else {
                    ChatResponse.error("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: java.io.IOException) {
            PluginLogger.Service.error("Error making OpenRouter request", e)
            ChatResponse.error("Request failed: ${e.message}")
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error making OpenRouter request", e)
            ChatResponse.error("Request failed: ${e.message}")
        }
    }

    private fun parseOpenRouterResponse(responseBody: String?): ChatResponse {
        return try {
            when {
                responseBody.isNullOrBlank() -> ChatResponse.error("Empty response from OpenRouter")
                else -> parseValidResponse(responseBody)
            }
        } catch (e: com.google.gson.JsonParseException) {
            PluginLogger.Service.error("Error parsing OpenRouter response", e)
            ChatResponse.error("Response parsing failed: ${e.message}")
        } catch (e: IllegalStateException) {
            PluginLogger.Service.error("Error parsing OpenRouter response", e)
            ChatResponse.error("Response parsing failed: ${e.message}")
        }
    }

    private fun parseValidResponse(responseBody: String): ChatResponse {
        val responseJson = gson.fromJson(responseBody, JsonObject::class.java)
            ?: return ChatResponse.error("Invalid response format")
        return parseResponseJson(responseJson)
    }

    private fun parseResponseJson(responseJson: JsonObject): ChatResponse {
        val errorObject = ResponseJsonParser.getAsJsonObjectOrNull(responseJson, "error")
        if (errorObject != null) {
            val errorMessage = ResponseJsonParser.getAsStringOrNull(errorObject, "message") ?: "Unknown error"
            return ChatResponse.error(errorMessage)
        }

        val choices = ResponseJsonParser.getAsJsonArrayOrNull(responseJson, "choices")
        val firstChoice = choices?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val content = firstChoice
            ?.let { ResponseJsonParser.getAsJsonObjectOrNull(it, "message") }
            ?.let { ResponseJsonParser.getAsStringOrNull(it, "content") }

        return if (content != null) {
            ChatResponse.success(content)
        } else {
            ChatResponse.error("No choices or content in response")
        }
    }
}

/**
 * Data classes for chat functionality
 */
data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class ChatResponse(
    val content: String?,
    val error: String?,
    val usage: Usage? = null
) {
    fun isSuccess(): Boolean = error == null

    companion object {
        fun success(content: String, usage: Usage? = null) = ChatResponse(content, null, usage)
        fun error(error: String) = ChatResponse(null, error)
    }

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}

data class CompletionResponse(
    val content: String?,
    val error: String?
) {
    fun isSuccess(): Boolean = error == null

    companion object {
        fun success(content: String) = CompletionResponse(content, null)
        fun error(error: String) = CompletionResponse(null, error)
    }
}
