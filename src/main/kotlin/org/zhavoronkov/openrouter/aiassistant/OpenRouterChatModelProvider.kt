package org.zhavoronkov.openrouter.aiassistant

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.zhavoronkov.openrouter.services.OpenRouterService
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.PluginLogger
import java.util.concurrent.CompletableFuture

/**
 * OpenRouter Chat Model Provider for AI Assistant integration
 * Handles the actual chat/completion requests when AI Assistant uses OpenRouter models
 */
class OpenRouterChatModelProvider {
    
    private val openRouterService = OpenRouterService.getInstance()
    private val settingsService = OpenRouterSettingsService.getInstance()
    private val gson = Gson()
    
    companion object {
        private val logger = Logger.getInstance(OpenRouterChatModelProvider::class.java)
        private const val MAX_TOKENS_DEFAULT = 2000
        private const val TEMPERATURE_DEFAULT = 0.7
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
                
            } catch (e: Exception) {
                PluginLogger.Service.error("Error sending chat request to OpenRouter", e)
                ChatResponse.error("Chat request failed: ${e.message}")
            }
        }
    }
    
    /**
     * Send a completion request (for non-chat use cases)
     */
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
                
            } catch (e: Exception) {
                PluginLogger.Service.error("Error sending completion request to OpenRouter", e)
                CompletionResponse.error("Completion request failed: ${e.message}")
            }
        }
    }
    
    /**
     * Check if streaming is supported for the given model
     */
    fun supportsStreaming(modelId: String): Boolean {
        // Most OpenRouter models support streaming
        return true
    }
    
    /**
     * Get the estimated token count for a message
     * This is a rough approximation since we don't have access to the exact tokenizer
     */
    fun estimateTokenCount(text: String): Int {
        // Rough estimation: ~4 characters per token for most models
        return (text.length / 4).coerceAtLeast(1)
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
            val request = okhttp3.Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${settingsService.getApiKey()}")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    parseOpenRouterResponse(responseBody)
                } else {
                    ChatResponse.error("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            PluginLogger.Service.error("Error making OpenRouter request", e)
            ChatResponse.error("Request failed: ${e.message}")
        }
    }
    
    private fun parseOpenRouterResponse(responseBody: String?): ChatResponse {
        return try {
            if (responseBody.isNullOrBlank()) {
                return ChatResponse.error("Empty response from OpenRouter")
            }
            
            val responseMap = gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>
                ?: return ChatResponse.error("Invalid response format")
            
            // Check for error in response
            val error = responseMap["error"] as? Map<String, Any>
            if (error != null) {
                val errorMessage = error["message"] as? String ?: "Unknown error"
                return ChatResponse.error(errorMessage)
            }
            
            // Parse successful response
            val choices = responseMap["choices"] as? List<Map<String, Any>>
            if (choices.isNullOrEmpty()) {
                return ChatResponse.error("No choices in response")
            }
            
            val firstChoice = choices[0]
            val message = firstChoice["message"] as? Map<String, Any>
            val content = message?.get("content") as? String
            
            if (content != null) {
                ChatResponse.success(content)
            } else {
                ChatResponse.error("No content in response")
            }
            
        } catch (e: Exception) {
            PluginLogger.Service.error("Error parsing OpenRouter response", e)
            ChatResponse.error("Response parsing failed: ${e.message}")
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
