package org.zhavoronkov.openrouter.proxy.validation

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.models.ModelArchitecture
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.services.FavoriteModelsService

@DisplayName("Multimodal Content Validator Tests")
class MultimodalContentValidatorTest {

    private lateinit var mockFavoriteModelsService: FavoriteModelsService
    private lateinit var validator: MultimodalContentValidator
    private val gson = Gson()

    @BeforeEach
    fun setup() {
        mockFavoriteModelsService = mock(FavoriteModelsService::class.java)
        validator = MultimodalContentValidator(mockFavoriteModelsService)
    }

    @Test
    fun `should validate text-only request as valid`() {
        val request = createTextOnlyRequest()
        
        val result = validator.validate(request, "test-001")
        
        assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
    }

    @Test
    fun `should detect image content and validate against model capabilities`() {
        val request = createImageRequest()
        val visionModel = createModelWithCapabilities(listOf("text", "image"))
        
        `when`(mockFavoriteModelsService.getModelById("openai/gpt-4o"))
            .thenReturn(visionModel)
        
        val result = validator.validate(request, "test-002")
        
        assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
    }

    @Test
    fun `should reject image content for non-vision model`() {
        val request = createImageRequest()
        val textOnlyModel = createModelWithCapabilities(listOf("text"))
        
        `when`(mockFavoriteModelsService.getModelById("openai/gpt-4o"))
            .thenReturn(textOnlyModel)
        
        val result = validator.validate(request, "test-003")
        
        assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        if (result is MultimodalContentValidator.ValidationResult.Invalid) {
            assertEquals(MultimodalContentValidator.ContentType.IMAGE, result.contentType)
            assertTrue(result.errorMessage.contains("doesn't support image input"))
        }
    }

    @Test
    fun `should skip validation when model not in cache`() {
        val request = createImageRequest()
        
        `when`(mockFavoriteModelsService.getModelById("openai/gpt-4o"))
            .thenReturn(null)
        
        val result = validator.validate(request, "test-004")
        
        assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
    }

    @Test
    fun `should detect audio content`() {
        val request = createAudioRequest()
        val audioModel = createModelWithCapabilities(listOf("text", "audio"))
        
        `when`(mockFavoriteModelsService.getModelById("openai/gpt-4o-audio-preview"))
            .thenReturn(audioModel)
        
        val result = validator.validate(request, "test-005")
        
        assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
    }

    private fun createTextOnlyRequest(): OpenAIChatCompletionRequest {
        val message = OpenAIChatMessage(
            role = "user",
            content = JsonParser.parseString("\"Hello, world!\"")
        )
        return OpenAIChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = listOf(message)
        )
    }

    private fun createImageRequest(): OpenAIChatCompletionRequest {
        val contentJson = """
            [
                {"type": "text", "text": "What's in this image?"},
                {"type": "image_url", "image_url": {"url": "https://example.com/image.jpg"}}
            ]
        """.trimIndent()
        
        val message = OpenAIChatMessage(
            role = "user",
            content = JsonParser.parseString(contentJson)
        )
        return OpenAIChatCompletionRequest(
            model = "openai/gpt-4o",
            messages = listOf(message)
        )
    }

    private fun createAudioRequest(): OpenAIChatCompletionRequest {
        val contentJson = """
            [
                {"type": "text", "text": "Transcribe this audio"},
                {"type": "input_audio", "input_audio": {"data": "base64data"}}
            ]
        """.trimIndent()
        
        val message = OpenAIChatMessage(
            role = "user",
            content = JsonParser.parseString(contentJson)
        )
        return OpenAIChatCompletionRequest(
            model = "openai/gpt-4o-audio-preview",
            messages = listOf(message)
        )
    }

    private fun createModelWithCapabilities(inputModalities: List<String>): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = "test-model",
            name = "Test Model",
            created = System.currentTimeMillis() / 1000,
            architecture = ModelArchitecture(
                inputModalities = inputModalities,
                outputModalities = listOf("text")
            )
        )
    }
}

