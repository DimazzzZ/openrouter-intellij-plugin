package org.zhavoronkov.openrouter.utils

/**
 * Centralized error pattern constants for error detection and handling.
 * This ensures consistency across error detection logic and makes patterns easy to update.
 */
object ErrorPatterns {

    // Multimodal capability errors
    const val IMAGE_NOT_SUPPORTED = "support image input"
    const val AUDIO_NOT_SUPPORTED_1 = "support audio input"
    const val AUDIO_NOT_SUPPORTED_2 = "audio not supported"
    const val VIDEO_NOT_SUPPORTED_1 = "support video input"
    const val VIDEO_NOT_SUPPORTED_2 = "video not supported"
    const val FILE_NOT_SUPPORTED_1 = "support pdf"
    const val FILE_NOT_SUPPORTED_2 = "support file"
    const val FILE_NOT_SUPPORTED_3 = "pdf not supported"
    const val FILE_NOT_SUPPORTED_4 = "file not supported"

    // Model availability errors
    const val NO_ENDPOINTS_FOUND = "No endpoints found"
    const val FREE_PERIOD_ENDED = "period has ended"
    const val MIGRATE_TO_PAID = "migrate to"
    const val PAID_SLUG = "paid slug"
    const val FREE = "free"

    // Rate limiting
    const val RATE_LIMIT = "rate limit"
    const val TOO_MANY_REQUESTS = "too many requests"

    // Authentication
    const val UNAUTHORIZED = "unauthorized"

    // Availability
    const val NOT_FOUND = "not found"
    const val UNAVAILABLE = "unavailable"
    const val TIMEOUT = "timeout"

    // Provider errors
    const val PROVIDER_ERROR = "provider"
    const val ERROR = "error"

    /**
     * Check if error body contains image input not supported pattern
     */
    fun isImageNotSupported(errorBody: String): Boolean {
        return errorBody.contains(IMAGE_NOT_SUPPORTED, ignoreCase = true)
    }

    /**
     * Check if error body contains audio input not supported pattern
     */
    fun isAudioNotSupported(errorBody: String): Boolean {
        return errorBody.contains(AUDIO_NOT_SUPPORTED_1, ignoreCase = true) ||
            errorBody.contains(AUDIO_NOT_SUPPORTED_2, ignoreCase = true)
    }

    /**
     * Check if error body contains video input not supported pattern
     */
    fun isVideoNotSupported(errorBody: String): Boolean {
        return errorBody.contains(VIDEO_NOT_SUPPORTED_1, ignoreCase = true) ||
            errorBody.contains(VIDEO_NOT_SUPPORTED_2, ignoreCase = true)
    }

    /**
     * Check if error body contains file/PDF input not supported pattern
     */
    fun isFileNotSupported(errorBody: String): Boolean {
        return errorBody.contains(FILE_NOT_SUPPORTED_1, ignoreCase = true) ||
            errorBody.contains(FILE_NOT_SUPPORTED_2, ignoreCase = true) ||
            errorBody.contains(FILE_NOT_SUPPORTED_3, ignoreCase = true) ||
            errorBody.contains(FILE_NOT_SUPPORTED_4, ignoreCase = true)
    }

    /**
     * Check if error body contains free tier ended pattern
     */
    fun isFreeTierEnded(errorBody: String): Boolean {
        return errorBody.contains(FREE, ignoreCase = true) &&
            (errorBody.contains(FREE_PERIOD_ENDED, ignoreCase = true) ||
                errorBody.contains(MIGRATE_TO_PAID, ignoreCase = true) ||
                errorBody.contains(PAID_SLUG, ignoreCase = true))
    }
}

