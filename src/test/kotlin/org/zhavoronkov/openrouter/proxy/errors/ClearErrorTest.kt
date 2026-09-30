package org.zhavoronkov.openrouter.proxy.errors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.FixPage

class ClearErrorTest {

    @Test
    @DisplayName("the message says what is wrong and where to fix it, on one line")
    fun message() {
        val error = ClearError("model_not_found", "'x/y' is not in OpenRouter's model catalogue", FixPage.FAVORITE_MODELS)

        assertEquals(
            "'x/y' is not in OpenRouter's model catalogue; fix it in Settings → Tools → OpenRouter → Favorite Models",
            error.message
        )
    }

    @Test
    @DisplayName("the advice before the page can be worded for the reason")
    fun advice() {
        val error = ClearError("output_schema_not_found", "No such schema", FixPage.OUTPUT_SCHEMAS, advice = "save it in")

        assertEquals("No such schema; save it in Settings → Tools → OpenRouter → Output Schemas", error.message)
    }

    @Test
    @DisplayName("the body is OpenAI's error shape, the message prefixed so it reads as the plugin's")
    fun body() {
        val error = ClearError("model_not_found", "'x/y' is not in OpenRouter's model catalogue", FixPage.FAVORITE_MODELS)

        assertEquals(
            mapOf(
                "error" to mapOf(
                    "message" to "OpenRouter plugin: 'x/y' is not in OpenRouter's model catalogue; " +
                        "fix it in Settings → Tools → OpenRouter → Favorite Models",
                    "type" to "invalid_request_error",
                    "code" to "model_not_found"
                )
            ),
            error.body()
        )
    }
}
