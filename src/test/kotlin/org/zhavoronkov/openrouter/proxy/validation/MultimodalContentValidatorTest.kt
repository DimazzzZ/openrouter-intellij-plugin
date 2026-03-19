package org.zhavoronkov.openrouter.proxy.validation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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

    private fun createMockFavoriteService(): FavoriteModelsService = mock(FavoriteModelsService::class.java)

    private fun createImageContent(): JsonArray {
        val contentPart = JsonObject().apply {
            addProperty("type", "image_url")
            add(
                "image_url",
                JsonObject().apply {
                    addProperty("url", "https://example.com/image.png")
                }
            )
        }
        return JsonArray().apply { add(contentPart) }
    }

    private fun createAudioContent(): JsonArray {
        val contentPart = JsonObject().apply {
            addProperty("type", "input_audio")
            add(
                "input_audio",
                JsonObject().apply {
                    addProperty("data", "base64audio")
                    addProperty("format", "mp3")
                }
            )
        }
        return JsonArray().apply { add(contentPart) }
    }

    private fun createVideoContent(): JsonArray {
        val contentPart = JsonObject().apply {
            addProperty("type", "video_url")
            add(
                "video_url",
                JsonObject().apply {
                    addProperty("url", "https://youtube.com/watch?v=123")
                }
            )
        }
        return JsonArray().apply { add(contentPart) }
    }

    private fun createFileContent(): JsonArray {
        val contentPart = JsonObject().apply {
            addProperty("type", "file")
            add(
                "file",
                JsonObject().apply {
                    addProperty("filename", "document.pdf")
                    addProperty("file_data", "data:application/pdf;base64,...")
                }
            )
        }
        return JsonArray().apply { add(contentPart) }
    }

    private fun createModelWithModalities(id: String, modalities: List<String>): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = id,
            name = id,
            created = 0,
            architecture = ModelArchitecture(inputModalities = modalities)
        )
    }

    @Nested
    @DisplayName("ContentType Enum Tests")
    inner class ContentTypeTests {

        @Test
        @DisplayName("should have correct display names")
        fun testDisplayNames() {
            assertEquals("image", MultimodalContentValidator.ContentType.IMAGE.displayName)
            assertEquals("audio", MultimodalContentValidator.ContentType.AUDIO.displayName)
            assertEquals("video", MultimodalContentValidator.ContentType.VIDEO.displayName)
            assertEquals("file", MultimodalContentValidator.ContentType.FILE.displayName)
        }

        @Test
        @DisplayName("should have all expected content types")
        fun testAllContentTypes() {
            val types = MultimodalContentValidator.ContentType.entries
            assertEquals(4, types.size)
            assertTrue(types.contains(MultimodalContentValidator.ContentType.IMAGE))
            assertTrue(types.contains(MultimodalContentValidator.ContentType.AUDIO))
            assertTrue(types.contains(MultimodalContentValidator.ContentType.VIDEO))
            assertTrue(types.contains(MultimodalContentValidator.ContentType.FILE))
        }
    }

    @Nested
    @DisplayName("ValidationResult Tests")
    inner class ValidationResultTests {

        @Test
        @DisplayName("Valid result should be singleton object")
        fun testValidResult() {
            val result1 = MultimodalContentValidator.ValidationResult.Valid
            val result2 = MultimodalContentValidator.ValidationResult.Valid
            assertEquals(result1, result2)
        }

        @Test
        @DisplayName("Invalid result should contain error details")
        fun testInvalidResult() {
            val result = MultimodalContentValidator.ValidationResult.Invalid(
                contentType = MultimodalContentValidator.ContentType.IMAGE,
                modelId = "openai/gpt-4",
                errorMessage = "Model doesn't support images"
            )

            assertEquals(MultimodalContentValidator.ContentType.IMAGE, result.contentType)
            assertEquals("openai/gpt-4", result.modelId)
            assertEquals("Model doesn't support images", result.errorMessage)
        }
    }

    @Nested
    @DisplayName("Plain Text Validation Tests")
    inner class PlainTextTests {

        @Test
        @DisplayName("should return Valid for plain text messages")
        fun testPlainTextMessage() {
            val favoriteService = createMockFavoriteService()
            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello, how are you?")))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should return Valid for multiple plain text messages")
        fun testMultiplePlainTextMessages() {
            val favoriteService = createMockFavoriteService()
            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(
                    OpenAIChatMessage(role = "system", content = JsonPrimitive("You are helpful")),
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("Hello")),
                    OpenAIChatMessage(role = "assistant", content = JsonPrimitive("Hi!")),
                    OpenAIChatMessage(role = "user", content = JsonPrimitive("How are you?"))
                )
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }
    }

    @Nested
    @DisplayName("Image Content Validation Tests")
    inner class ImageValidationTests {

        @Test
        @DisplayName("should return Invalid when model lacks vision capability")
        fun testImageWithoutVision() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(listOf(modelInfo))
            `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(modelInfo))

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            assertEquals(MultimodalContentValidator.ContentType.IMAGE, invalidResult.contentType)
            assertEquals("openai/gpt-4", invalidResult.modelId)
            assertTrue(invalidResult.errorMessage.contains("image"))
        }

        @Test
        @DisplayName("should return Valid when model supports vision")
        fun testImageWithVision() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4-vision-preview", listOf("text", "image"))
            `when`(favoriteService.getModelById("openai/gpt-4-vision-preview")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4-vision-preview",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }
    }

    @Nested
    @DisplayName("Audio Content Validation Tests")
    inner class AudioValidationTests {

        @Test
        @DisplayName("should return Invalid when model lacks audio capability")
        fun testAudioWithoutSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(listOf(modelInfo))
            `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(modelInfo))

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createAudioContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            assertEquals(MultimodalContentValidator.ContentType.AUDIO, invalidResult.contentType)
            assertTrue(invalidResult.errorMessage.contains("audio"))
        }

        @Test
        @DisplayName("should return Valid when model supports audio")
        fun testAudioWithSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4o-audio-preview", listOf("text", "audio"))
            `when`(favoriteService.getModelById("openai/gpt-4o-audio-preview")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4o-audio-preview",
                messages = listOf(OpenAIChatMessage(role = "user", content = createAudioContent()))
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should detect audio type with alternate syntax")
        fun testAlternateAudioSyntax() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "audio")
                    }
                )
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-3")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("Video Content Validation Tests")
    inner class VideoValidationTests {

        @Test
        @DisplayName("should return Invalid when model lacks video capability")
        fun testVideoWithoutSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(listOf(modelInfo))
            `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(modelInfo))

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createVideoContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            assertEquals(MultimodalContentValidator.ContentType.VIDEO, invalidResult.contentType)
            assertTrue(invalidResult.errorMessage.contains("video"))
        }

        @Test
        @DisplayName("should return Valid when model supports video")
        fun testVideoWithSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("google/gemini-pro-vision", listOf("text", "video"))
            `when`(favoriteService.getModelById("google/gemini-pro-vision")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "google/gemini-pro-vision",
                messages = listOf(OpenAIChatMessage(role = "user", content = createVideoContent()))
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should detect video type with alternate syntax")
        fun testAlternateVideoSyntax() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "video")
                    }
                )
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-3")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("File Content Validation Tests")
    inner class FileValidationTests {

        @Test
        @DisplayName("should return Invalid when model lacks file capability")
        fun testFileWithoutSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(listOf(modelInfo))
            `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(modelInfo))

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createFileContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            assertEquals(MultimodalContentValidator.ContentType.FILE, invalidResult.contentType)
            assertTrue(invalidResult.errorMessage.contains("file") || invalidResult.errorMessage.contains("document"))
        }

        @Test
        @DisplayName("should return Valid when model supports file")
        fun testFileWithSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("anthropic/claude-3-opus", listOf("text", "file"))
            `when`(favoriteService.getModelById("anthropic/claude-3-opus")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "anthropic/claude-3-opus",
                messages = listOf(OpenAIChatMessage(role = "user", content = createFileContent()))
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should return Valid when model supports document modality")
        fun testFileWithDocumentModality() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("anthropic/claude-3-opus", listOf("text", "document"))
            `when`(favoriteService.getModelById("anthropic/claude-3-opus")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "anthropic/claude-3-opus",
                messages = listOf(OpenAIChatMessage(role = "user", content = createFileContent()))
            )

            val result = validator.validate(request, "req-3")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should detect document type")
        fun testDocumentType() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "document")
                    }
                )
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-4")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }
    }

    @Nested
    @DisplayName("Unknown Model Tests")
    inner class UnknownModelTests {

        @Test
        @DisplayName("should return Valid when model not in cache")
        fun testUnknownModel() {
            val favoriteService = createMockFavoriteService()
            `when`(favoriteService.getModelById("unknown/model")).thenReturn(null)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "unknown/model",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            // Should skip pre-validation and let OpenRouter handle it
            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }
    }

    @Nested
    @DisplayName("Mixed Content Tests")
    inner class MixedContentTests {

        @Test
        @DisplayName("should detect all content types in mixed message")
        fun testMixedContent() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Check this:")
                    }
                )
                add(JsonObject().apply { addProperty("type", "image_url") })
                add(JsonObject().apply { addProperty("type", "input_audio") })
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-1")

            // Should detect at least one unsupported type
            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }

        @Test
        @DisplayName("should return Valid when model supports all content types in request")
        fun testMixedContentWithSupport() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4o", listOf("text", "image", "audio"))
            `when`(favoriteService.getModelById("openai/gpt-4o")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", "Check this:")
                    }
                )
                add(JsonObject().apply { addProperty("type", "image_url") })
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4o",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-2")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    inner class ErrorMessageTests {

        @Test
        @DisplayName("error message should include suggestions")
        fun testErrorMessageSuggestions() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4", listOf("text"))
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            assertTrue(invalidResult.errorMessage.contains("openrouter.ai/models"))
        }

        @Test
        @DisplayName("error message should include favorite models that support capability")
        fun testErrorMessageWithFavorites() {
            val favoriteService = createMockFavoriteService()
            val textOnlyModel = createModelWithModalities("openai/gpt-4", listOf("text"))
            val visionModel = createModelWithModalities("openai/gpt-4-vision-preview", listOf("text", "image"))

            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(textOnlyModel)
            `when`(favoriteService.getCachedModels()).thenReturn(listOf(textOnlyModel, visionModel))
            `when`(favoriteService.getFavoriteModels()).thenReturn(listOf(textOnlyModel, visionModel))

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
            val invalidResult = result as MultimodalContentValidator.ValidationResult.Invalid
            // Should suggest the vision-capable model from favorites
            assertTrue(
                invalidResult.errorMessage.contains("gpt-4-vision") ||
                    invalidResult.errorMessage.contains("Try")
            )
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty content array")
        fun testEmptyContentArray() {
            val favoriteService = createMockFavoriteService()
            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = JsonArray()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should handle content part without type")
        fun testContentPartWithoutType() {
            val favoriteService = createMockFavoriteService()
            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("text", "Hello")
                        // No "type" field
                    }
                )
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should handle non-object content parts in array")
        fun testNonObjectContentPart() {
            val favoriteService = createMockFavoriteService()
            val validator = MultimodalContentValidator(favoriteService)
            val content = JsonArray().apply {
                add(JsonPrimitive("text content"))
            }
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = content))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }

        @Test
        @DisplayName("should handle model with null architecture")
        fun testModelWithNullArchitecture() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = OpenRouterModelInfo(
                id = "openai/gpt-4",
                name = "GPT-4",
                created = 0,
                architecture = null
            )
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            // Should be invalid since null architecture means no modalities
            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }

        @Test
        @DisplayName("should handle model with null input modalities")
        fun testModelWithNullModalities() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = OpenRouterModelInfo(
                id = "openai/gpt-4",
                name = "GPT-4",
                created = 0,
                architecture = ModelArchitecture(inputModalities = null)
            )
            `when`(favoriteService.getModelById("openai/gpt-4")).thenReturn(modelInfo)
            `when`(favoriteService.getCachedModels()).thenReturn(emptyList())
            `when`(favoriteService.getFavoriteModels()).thenReturn(emptyList())

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            // Should be invalid since null modalities means no support
            assertTrue(result is MultimodalContentValidator.ValidationResult.Invalid)
        }

        @Test
        @DisplayName("should handle case-insensitive modality matching")
        fun testCaseInsensitiveModality() {
            val favoriteService = createMockFavoriteService()
            val modelInfo = createModelWithModalities("openai/gpt-4-vision", listOf("Text", "IMAGE"))
            `when`(favoriteService.getModelById("openai/gpt-4-vision")).thenReturn(modelInfo)

            val validator = MultimodalContentValidator(favoriteService)
            val request = OpenAIChatCompletionRequest(
                model = "openai/gpt-4-vision",
                messages = listOf(OpenAIChatMessage(role = "user", content = createImageContent()))
            )

            val result = validator.validate(request, "req-1")

            assertTrue(result is MultimodalContentValidator.ValidationResult.Valid)
        }
    }

    @Nested
    @DisplayName("Companion Object Tests")
    inner class CompanionObjectTests {

        @Test
        @DisplayName("should return vision-capable models list")
        fun testVisionCapableModels() {
            val models = MultimodalContentValidator.getVisionCapableModels()

            assertNotNull(models)
            assertFalse(models.isEmpty())
        }

        @Test
        @DisplayName("should return audio-capable models list")
        fun testAudioCapableModels() {
            val models = MultimodalContentValidator.getAudioCapableModels()

            assertNotNull(models)
            assertFalse(models.isEmpty())
        }
    }
}
