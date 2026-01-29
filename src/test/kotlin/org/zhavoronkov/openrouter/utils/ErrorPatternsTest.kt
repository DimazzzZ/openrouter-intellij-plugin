package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for ErrorPatterns utility object
 */
@DisplayName("ErrorPatterns Tests")
class ErrorPatternsTest {

    // ========== Image Support Tests ==========

    @ParameterizedTest
    @DisplayName("Should detect image not supported pattern")
    @ValueSource(strings = [
        "This model does not support image input",
        "Model cannot support image input",
        "The endpoint does not support image input",
        "SUPPORT IMAGE INPUT"
    ])
    fun testIsImageNotSupportedReturnsTrue(errorBody: String) {
        assertTrue(ErrorPatterns.isImageNotSupported(errorBody), "Should detect image not supported in: $errorBody")
    }

    @ParameterizedTest
    @DisplayName("Should not detect image not supported when pattern is absent")
    @ValueSource(strings = [
        "This model supports image input",
        "Image processing error",
        "",
        "Something went wrong"
    ])
    fun testIsImageNotSupportedReturnsFalse(errorBody: String) {
        assertFalse(ErrorPatterns.isImageNotSupported(errorBody), "Should not detect image not supported in: $errorBody")
    }

    // ========== Audio Support Tests ==========

    @ParameterizedTest
    @DisplayName("Should detect audio not supported pattern")
    @ValueSource(strings = [
        "This model does not support audio input",
        "Model cannot support audio input",
        "Audio not supported by this model",
        "AUDIO NOT SUPPORTED"
    ])
    fun testIsAudioNotSupportedReturnsTrue(errorBody: String) {
        assertTrue(ErrorPatterns.isAudioNotSupported(errorBody), "Should detect audio not supported in: $errorBody")
    }

    @ParameterizedTest
    @DisplayName("Should not detect audio not supported when pattern is absent")
    @ValueSource(strings = [
        "This model supports audio input",
        "Audio processing error",
        "",
        "Something went wrong"
    ])
    fun testIsAudioNotSupportedReturnsFalse(errorBody: String) {
        assertFalse(ErrorPatterns.isAudioNotSupported(errorBody), "Should not detect audio not supported in: $errorBody")
    }

    // ========== Video Support Tests ==========

    @ParameterizedTest
    @DisplayName("Should detect video not supported pattern")
    @ValueSource(strings = [
        "This model does not support video input",
        "Model cannot support video input",
        "Video not supported by this model",
        "VIDEO NOT SUPPORTED"
    ])
    fun testIsVideoNotSupportedReturnsTrue(errorBody: String) {
        assertTrue(ErrorPatterns.isVideoNotSupported(errorBody), "Should detect video not supported in: $errorBody")
    }

    @ParameterizedTest
    @DisplayName("Should not detect video not supported when pattern is absent")
    @ValueSource(strings = [
        "This model supports video input",
        "Video processing error",
        "",
        "Something went wrong"
    ])
    fun testIsVideoNotSupportedReturnsFalse(errorBody: String) {
        assertFalse(ErrorPatterns.isVideoNotSupported(errorBody), "Should not detect video not supported in: $errorBody")
    }

    // ========== File/PDF Support Tests ==========

    @ParameterizedTest
    @DisplayName("Should detect file/PDF not supported pattern")
    @ValueSource(strings = [
        "This model does not support pdf files",
        "Model cannot support file upload",
        "PDF not supported by this model",
        "File not supported",
        "Does not support PDF documents",
        "SUPPORT FILE upload"
    ])
    fun testIsFileNotSupportedReturnsTrue(errorBody: String) {
        assertTrue(ErrorPatterns.isFileNotSupported(errorBody), "Should detect file not supported in: $errorBody")
    }

    @ParameterizedTest
    @DisplayName("Should not detect file not supported when pattern is absent")
    @ValueSource(strings = [
        "This model supports PDF files",
        "File uploaded successfully",
        "",
        "Something went wrong"
    ])
    fun testIsFileNotSupportedReturnsFalse(errorBody: String) {
        assertFalse(ErrorPatterns.isFileNotSupported(errorBody), "Should not detect file not supported in: $errorBody")
    }

    // ========== Free Tier Ended Tests ==========

    @Test
    @DisplayName("Should detect free tier ended with 'period has ended'")
    fun testIsFreeTierEndedWithPeriodEnded() {
        val errorBody = "Your free trial period has ended. Please upgrade."
        assertTrue(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    @Test
    @DisplayName("Should detect free tier ended with 'migrate to'")
    fun testIsFreeTierEndedWithMigrateTo() {
        val errorBody = "Your free tier has ended. Please migrate to a paid plan to continue."
        assertTrue(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    @Test
    @DisplayName("Should detect free tier ended with 'paid slug'")
    fun testIsFreeTierEndedWithPaidSlug() {
        val errorBody = "Your free account requires a paid slug to access this feature."
        assertTrue(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    @Test
    @DisplayName("Should detect free tier ended case-insensitively")
    fun testIsFreeTierEndedCaseInsensitive() {
        val errorBody = "FREE PERIOD HAS ENDED"
        assertTrue(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    @Test
    @DisplayName("Should not detect free tier ended without 'free' keyword")
    fun testIsFreeTierEndedWithoutFreeKeyword() {
        val errorBody = "Your trial period has ended. Please upgrade."
        assertFalse(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    @Test
    @DisplayName("Should not detect free tier ended without migration keywords")
    fun testIsFreeTierEndedWithoutMigrationKeywords() {
        val errorBody = "This is a free service"
        assertFalse(ErrorPatterns.isFreeTierEnded(errorBody))
    }

    // ========== Pattern Constants Tests ==========

    @Test
    @DisplayName("Should have correct constant values")
    fun testConstants() {
        assertEquals("support image input", ErrorPatterns.IMAGE_NOT_SUPPORTED)
        assertEquals("support audio input", ErrorPatterns.AUDIO_NOT_SUPPORTED_1)
        assertEquals("audio not supported", ErrorPatterns.AUDIO_NOT_SUPPORTED_2)
        assertEquals("support video input", ErrorPatterns.VIDEO_NOT_SUPPORTED_1)
        assertEquals("video not supported", ErrorPatterns.VIDEO_NOT_SUPPORTED_2)
        assertEquals("support pdf", ErrorPatterns.FILE_NOT_SUPPORTED_1)
        assertEquals("support file", ErrorPatterns.FILE_NOT_SUPPORTED_2)
        assertEquals("pdf not supported", ErrorPatterns.FILE_NOT_SUPPORTED_3)
        assertEquals("file not supported", ErrorPatterns.FILE_NOT_SUPPORTED_4)
        assertEquals("No endpoints found", ErrorPatterns.NO_ENDPOINTS_FOUND)
        assertEquals("period has ended", ErrorPatterns.FREE_PERIOD_ENDED)
        assertEquals("migrate to", ErrorPatterns.MIGRATE_TO_PAID)
        assertEquals("paid slug", ErrorPatterns.PAID_SLUG)
        assertEquals("free", ErrorPatterns.FREE)
        assertEquals("rate limit", ErrorPatterns.RATE_LIMIT)
        assertEquals("too many requests", ErrorPatterns.TOO_MANY_REQUESTS)
        assertEquals("unauthorized", ErrorPatterns.UNAUTHORIZED)
        assertEquals("not found", ErrorPatterns.NOT_FOUND)
        assertEquals("unavailable", ErrorPatterns.UNAVAILABLE)
        assertEquals("timeout", ErrorPatterns.TIMEOUT)
        assertEquals("provider", ErrorPatterns.PROVIDER_ERROR)
        assertEquals("error", ErrorPatterns.ERROR)
    }
}
