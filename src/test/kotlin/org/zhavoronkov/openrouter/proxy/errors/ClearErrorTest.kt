package org.zhavoronkov.openrouter.proxy.errors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.proxy.pairs.PairProblem

class ClearErrorTest {

    private val problems = listOf(
        PairProblem.MissingPreset("research"),
        PairProblem.OutputNotServable("research", "m does not support JSON output"),
        PairProblem.WebSearchDropsJson("research")
    )

    @Test
    @DisplayName("every reason has a code of its own and exactly one page that fixes it")
    fun codesAndPages() {
        assertEquals(
            listOf(
                "preset_not_found" to FixPage.PRESETS,
                "output_not_supported" to FixPage.PRESETS,
                "output_dropped_by_web_search" to FixPage.PRESETS
            ),
            problems.map(ClearError::of).map { it.code to it.fixAt }
        )
    }

    @Test
    @DisplayName("every message is one line, says what is wrong and names the page that fixes it")
    fun messages() {
        problems.map(ClearError::of).forEach { error ->
            assertEquals(1, error.message.lines().size, error.message)
            assertTrue(error.message.endsWith("fix it in ${error.fixAt.path}"), error.message)
        }
    }

    @Test
    @DisplayName("the body is OpenAI's error shape, the message prefixed so it reads as the plugin's")
    fun body() {
        val error = ClearError("preset_not_found", "No preset named 'research' is saved on OpenRouter", FixPage.PRESETS)

        assertEquals(
            mapOf(
                "error" to mapOf(
                    "message" to "OpenRouter plugin: No preset named 'research' is saved on OpenRouter; " +
                        "fix it in Settings → Tools → OpenRouter → Presets",
                    "type" to "invalid_request_error",
                    "code" to "preset_not_found"
                )
            ),
            error.body()
        )
    }
}
