package org.zhavoronkov.openrouter.proxy.translation

import com.google.gson.JsonPrimitive
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService

/** The default token limit the translator takes from the application's settings. */
class RequestTranslatorPlatformTest : BasePlatformTestCase() {

    private val preferences get() = OpenRouterSettingsService.getInstance().uiPreferencesManager

    private val request = OpenAIChatCompletionRequest(
        model = "openai/gpt-4o",
        messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello")))
    )

    private fun maxTokensWithDefault(default: Int): Int? {
        val before = preferences.defaultMaxTokens
        try {
            preferences.defaultMaxTokens = default
            return RequestTranslator.translateChatCompletionRequest(request).maxTokens
        } finally {
            preferences.defaultMaxTokens = before
        }
    }

    fun testARequestWithoutALimitGetsTheDefaultSetInTheSettings() {
        assertEquals(512, maxTokensWithDefault(512))
    }

    fun testADefaultOfZeroAddsNoLimit() {
        assertNull(maxTokensWithDefault(0))
    }
}
