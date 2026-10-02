package org.zhavoronkov.openrouter.proxy.validation

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.services.FavoriteModelsService

/**
 * Which favourites a refusal for unsupported input suggests, and which message contents count as
 * multimodal at all.
 */
@DisplayName("MultimodalContentValidator suggestions and content shapes")
class MultimodalContentValidatorFavoritesTest {

    private val favorites = mock(FavoriteModelsService::class.java)
    private val validator = MultimodalContentValidator(favorites)

    private fun model(id: String, vararg modalities: String) = OpenRouterModelInfo(
        id = id,
        name = id,
        created = 0,
        architecture = ModelArchitecture(inputModalities = modalities.toList())
    )

    private val textOnly = model("text/only", "text")

    private fun request(content: JsonElement) = OpenAIChatCompletionRequest(
        model = textOnly.id,
        messages = listOf(OpenAIChatMessage(role = "user", content = content))
    )

    private val image = JsonParser.parseString("""[{"type":"image_url","image_url":{"url":"https://x/y.png"}}]""")

    private fun refusalMessage(): String {
        val result = validator.validate(request(image), "t")
        assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid, "got $result")
        return (result as MultimodalContentValidator.ValidationResult.Invalid).errorMessage
    }

    @Test
    @DisplayName("only favourites the catalogue lists as capable are suggested, a pair by its model")
    fun suggestsCapableFavoritesOnly() {
        `when`(favorites.getModelById(textOnly.id)).thenReturn(textOnly)
        `when`(favorites.getCachedModels()).thenReturn(listOf(textOnly, model("vision/model", "text", "image")))
        `when`(favorites.getFavoriteModels()).thenReturn(
            listOf(
                model("vision/model@preset/research"),
                model("text/only"),
                model("gone/model")
            )
        )

        val message = refusalMessage()

        assertTrue(message.contains("- vision/model@preset/research"), message)
        assertFalse(message.contains("- text/only"), message)
        assertFalse(message.contains("- gone/model"), message)
    }

    @Test
    @DisplayName("with no catalogue loaded the refusal falls back to the general suggestions")
    fun noCatalogue() {
        `when`(favorites.getModelById(textOnly.id)).thenReturn(textOnly)
        `when`(favorites.getCachedModels()).thenReturn(null)

        val message = refusalMessage()

        assertTrue(message.contains("Try a vision-capable model like:"), message)
        verify(favorites, never()).getFavoriteModels()
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = ["""{"type":"image_url"}""", "null", """[{"image_url":{"url":"u"}}]""", """["text"]"""])
    @DisplayName("content that is an object, null, or parts without a type is not multimodal")
    fun notMultimodal(content: String) {
        val result = validator.validate(request(JsonParser.parseString(content)), "t")

        assertEquals(MultimodalContentValidator.ValidationResult.Valid, result)
        verify(favorites, never()).getModelById(textOnly.id)
    }
}
