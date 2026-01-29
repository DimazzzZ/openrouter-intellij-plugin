package org.zhavoronkov.openrouter.proxy.validation

import com.google.gson.JsonElement
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.services.FavoriteModelsService
import org.zhavoronkov.openrouter.utils.ModelProviderUtils
import org.zhavoronkov.openrouter.utils.ModelSuggestions
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Validates multimodal content in chat requests against model capabilities.
 * Pre-validates requests before sending to OpenRouter to provide immediate,
 * user-friendly error messages.
 */
class MultimodalContentValidator(
    private val favoriteModelsService: FavoriteModelsService = FavoriteModelsService.getInstance()
) {

    /**
     * Content types that can be detected in multimodal messages
     */
    enum class ContentType(val displayName: String) {
        IMAGE("image"),
        AUDIO("audio"),
        VIDEO("video"),
        FILE("file")
    }

    /**
     * Result of validation
     */
    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(
            val contentType: ContentType,
            val modelId: String,
            val errorMessage: String
        ) : ValidationResult()
    }

    /**
     * Validate a chat completion request against model capabilities.
     * Returns Valid if the model supports all content types in the request,
     * or Invalid with details if the model doesn't support a content type.
     */
    fun validate(request: OpenAIChatCompletionRequest, requestId: String): ValidationResult {
        val modelId = request.model
        val detectedContentTypes = detectContentTypes(request)

        if (detectedContentTypes.isEmpty()) {
            PluginLogger.Service.debug("[Chat-$requestId] No multimodal content detected")
            return ValidationResult.Valid
        }

        PluginLogger.Service.info(
            "[Chat-$requestId] Detected multimodal content: ${detectedContentTypes.joinToString { it.displayName }}"
        )

        // Look up model capabilities
        val model = favoriteModelsService.getModelById(modelId)
        if (model == null) {
            // Model not in cache - let OpenRouter handle validation
            PluginLogger.Service.debug(
                "[Chat-$requestId] Model '$modelId' not in cache, skipping pre-validation"
            )
            return ValidationResult.Valid
        }

        // Check each detected content type against model capabilities
        for (contentType in detectedContentTypes) {
            if (!modelSupportsContentType(model, contentType)) {
                val errorMessage = createErrorMessage(contentType, modelId)
                PluginLogger.Service.warn(
                    "[Chat-$requestId] Model '$modelId' doesn't support ${contentType.displayName} input"
                )
                return ValidationResult.Invalid(contentType, modelId, errorMessage)
            }
        }

        PluginLogger.Service.debug("[Chat-$requestId] Model '$modelId' supports all detected content types")
        return ValidationResult.Valid
    }

    /**
     * Detect all content types present in the request messages
     */
    private fun detectContentTypes(request: OpenAIChatCompletionRequest): Set<ContentType> {
        val contentTypes = mutableSetOf<ContentType>()

        for (message in request.messages) {
            detectContentTypesInMessage(message.content, contentTypes)
        }

        return contentTypes
    }

    /**
     * Detect content types in a message's content field
     */
    private fun detectContentTypesInMessage(content: JsonElement, contentTypes: MutableSet<ContentType>) {
        when {
            content.isJsonArray -> {
                val contentArray = content.asJsonArray
                for (part in contentArray) {
                    detectContentTypeInPart(part, contentTypes)
                }
            }
            content.isJsonPrimitive -> {
                // Plain text - no multimodal content
            }
        }
    }

    /**
     * Detect content type in a single content part
     */
    private fun detectContentTypeInPart(part: JsonElement, contentTypes: MutableSet<ContentType>) {
        if (!part.isJsonObject) return

        val partObj = part.asJsonObject
        val type = partObj.get("type")?.asString ?: return

        when (type) {
            "image_url" -> contentTypes.add(ContentType.IMAGE)
            "input_audio", "audio" -> contentTypes.add(ContentType.AUDIO)
            "video_url", "video" -> contentTypes.add(ContentType.VIDEO)
            "file", "document" -> contentTypes.add(ContentType.FILE)
        }
    }

    /**
     * Check if a model supports a specific content type
     */
    private fun modelSupportsContentType(model: OpenRouterModelInfo, contentType: ContentType): Boolean {
        val inputModalities = model.architecture?.inputModalities ?: emptyList()

        return when (contentType) {
            ContentType.IMAGE -> inputModalities.any { it.equals("image", ignoreCase = true) }
            ContentType.AUDIO -> inputModalities.any { it.equals("audio", ignoreCase = true) }
            ContentType.VIDEO -> inputModalities.any { it.equals("video", ignoreCase = true) }
            ContentType.FILE -> inputModalities.any {
                it.equals("file", ignoreCase = true) || it.equals("document", ignoreCase = true)
            }
        }
    }

    /**
     * Create a user-friendly error message for unsupported content type.
     * Dynamically suggests models from user's favorites that support the capability.
     */
    private fun createErrorMessage(contentType: ContentType, modelId: String): String {
        val capability = contentTypeToCapability(contentType)
        val suggestedModels = findCapableModelsInFavorites(capability)

        val header = when (contentType) {
            ContentType.IMAGE -> "This model doesn't support image input."
            ContentType.AUDIO -> "This model doesn't support audio input."
            ContentType.VIDEO -> "This model doesn't support video input."
            ContentType.FILE -> "This model doesn't support file/document input."
        }

        val suggestions = if (suggestedModels.isNotEmpty()) {
            val modelList = suggestedModels.take(MAX_SUGGESTIONS).joinToString("\n") { "- $it" }
            "Try one of your favorite models that supports this:\n$modelList"
        } else {
            getDefaultSuggestions(contentType)
        }

        return "$header\n$suggestions\n\nCheck model capabilities: https://openrouter.ai/models"
    }

    /**
     * Find models in user's favorites that support the given capability
     */
    private fun findCapableModelsInFavorites(capability: ModelProviderUtils.Capability): List<String> {
        val cachedModels = favoriteModelsService.getCachedModels() ?: return emptyList()
        val favoriteModels = favoriteModelsService.getFavoriteModels()

        return favoriteModels
            .mapNotNull { favorite -> cachedModels.find { it.id == favorite.id } }
            .filter { ModelProviderUtils.hasCapability(it, capability) }
            .map { it.id }
    }

    /**
     * Convert content type to ModelProviderUtils capability
     */
    private fun contentTypeToCapability(contentType: ContentType): ModelProviderUtils.Capability {
        return when (contentType) {
            ContentType.IMAGE -> ModelProviderUtils.Capability.VISION
            ContentType.AUDIO -> ModelProviderUtils.Capability.AUDIO
            ContentType.VIDEO -> ModelProviderUtils.Capability.VISION // Video often uses vision capability
            ContentType.FILE -> ModelProviderUtils.Capability.VISION // Files often use vision capability
        }
    }

    /**
     * Get default suggestions when no favorites support the capability
     */
    private fun getDefaultSuggestions(contentType: ContentType): String {
        return when (contentType) {
            ContentType.IMAGE -> ModelSuggestions.createSuggestionSection(
                "Try a vision-capable model like:",
                ModelSuggestions.VISION_MODELS
            )
            ContentType.AUDIO -> ModelSuggestions.createSuggestionSection(
                "Try an audio-capable model like:",
                ModelSuggestions.AUDIO_MODELS
            )
            ContentType.VIDEO -> ModelSuggestions.createSuggestionSection(
                "Try a video-capable model like:",
                ModelSuggestions.VIDEO_MODELS
            )
            ContentType.FILE -> ModelSuggestions.createSuggestionSection(
                "Try a model with file support like:",
                ModelSuggestions.FILE_MODELS
            )
        }
    }

    companion object {
        private const val MAX_SUGGESTIONS = 5

        /**
         * Get suggested vision-capable models
         */
        fun getVisionCapableModels(): List<String> = ModelSuggestions.VISION_MODELS

        /**
         * Get suggested audio-capable models
         */
        fun getAudioCapableModels(): List<String> = ModelSuggestions.AUDIO_MODELS
    }
}

