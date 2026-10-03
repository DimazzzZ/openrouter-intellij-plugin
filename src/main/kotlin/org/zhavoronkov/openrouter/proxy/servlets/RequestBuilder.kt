package org.zhavoronkov.openrouter.proxy.servlets

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import okhttp3.Request
import org.zhavoronkov.openrouter.models.DataRegion
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.utils.OpenRouterRequestBuilder
import org.zhavoronkov.openrouter.utils.PluginLogger
import org.zhavoronkov.openrouter.utils.applicationServiceOrNull

/**
 * Builds requests for OpenRouter API
 */
class RequestBuilder(
    private val chatCompletionsUrl: () -> String = ::defaultChatCompletionsUrl
) {

    private val gson = Gson()

    fun buildOpenRouterRequest(jsonBody: String, apiKey: String): Request {
        return OpenRouterRequestBuilder.buildPostRequest(
            url = chatCompletionsUrl(),
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

/**
 * The outbound chat-completions URL, resolved per call rather than captured once.
 *
 * Per call matters because the data region is a setting and the proxy is not restarted when a
 * setting changes: a URL captured at construction would keep sending requests to the region the
 * user has just left.
 *
 * Outside a running IDE there is no settings service to ask, so this answers the unpinned
 * endpoint. Nothing is lost by that - a region is a user setting, and there is no user.
 */
internal fun defaultChatCompletionsUrl(): String {
    // Unreachable branch: getApiBaseUrl returns a non-null String, so the elvis sees null only with no settings
    val baseUrl = applicationServiceOrNull(OpenRouterSettingsService::class.java)?.getApiBaseUrl()
        ?: DataRegion.GLOBAL.baseUrl
    return "$baseUrl/chat/completions"
}
