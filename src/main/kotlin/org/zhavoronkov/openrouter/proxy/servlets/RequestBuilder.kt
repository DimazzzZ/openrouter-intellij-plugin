package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import okhttp3.Request
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Builds requests for OpenRouter API
 */
class RequestBuilder {

    companion object {
        private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private val gson = Gson()

    fun buildOpenRouterRequest(jsonBody: String, apiKey: String): Request {
        return OpenRouterRequestBuilder.buildPostRequest(
            url = OPENROUTER_API_URL,
            jsonBody = jsonBody,
            authType = OpenRouterRequestBuilder.AuthType.API_KEY,
            authToken = apiKey
        )
    }

    fun parseRequestBody(requestBody: String, requestId: String): JsonObject? {
        return try {
            gson.fromJson(requestBody, JsonObject::class.java)
        } catch (e: JsonSyntaxException) {
            PluginLogger.Service.error("[$requestId] Failed to parse request body", e)
            null
        }
    }
}
