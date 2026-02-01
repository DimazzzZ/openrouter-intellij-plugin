package org.zhavoronkov.openrouter.proxy.validation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.services.FavoriteModelsService

@DisplayName("MultimodalContentValidator Tests")
class MultimodalContentValidatorTest {

    @Test
    fun `validate should return invalid when model lacks vision`() {
        val favoriteService = mock(FavoriteModelsService::class.java)
        val modelInfo = OpenRouterModelInfo(
            id = "openai/gpt-4",
            name = "GPT-4",
            created = 0,
            architecture = ModelArchitecture(inputModalities = listOf("text"))
        )
        `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
        `when`(favoriteService.getCachedModels()).thenReturn(listOf(modelInfo))
        `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(modelInfo))

        val validator = MultimodalContentValidator(favoriteService)
        val contentPart = JsonObject().apply {
            addProperty("type", "image_url")
            add("image_url", JsonObject().apply { addProperty("url", "https://example.com") })
        }
        val content = JsonArray().apply { add(contentPart) }
        val request = OpenAIChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(OpenAIChatMessage(role = "user", content = content))
        )

        val result = validator.validate(request, "req-1")

        assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
    }

    @Test
    fun `validate should pass when no multimodal content`() {
        val favoriteService = mock(FavoriteModelsService::class.java)
        val validator = MultimodalContentValidator(favoriteService)
        val request = OpenAIChatCompletionRequest(
            model = "openai/gpt-4",
            messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("hi")))
        )

        val result = validator.validate(request, "req-2")

        assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
    }
}
