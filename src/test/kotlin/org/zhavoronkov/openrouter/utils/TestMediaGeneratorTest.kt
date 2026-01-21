package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Tests for TestMediaGenerator utility.
 * Verifies that test media files can be generated on-demand and cached.
 */
@DisplayName("Test Media Generator Tests")
class TestMediaGeneratorTest {

    @Test
    @DisplayName("Should generate image data URL")
    fun testGenerateImageDataUrl() {
        // When: Get image data URL
        val dataUrl = TestMediaGenerator.getImageDataUrl("test-gen-image.png")

        // Then: Should have correct format
        assertTrue(dataUrl.startsWith("data:image/png;base64,"))
        assertTrue(dataUrl.length > 100, "Data URL should have actual content")

        // And: File should exist in media directory
        val mediaDir = TestMediaGenerator.getMediaDir()
        val imageFile = mediaDir.resolve("test-gen-image.png").toFile()
        assertTrue(imageFile.exists(), "Image file should exist")
        assertTrue(imageFile.length() > 0, "Image file should not be empty")

        println("✅ Generated image: ${imageFile.length() / 1024}KB")
    }

    @Test
    @DisplayName("Should reuse cached image")
    fun testImageCaching() {
        // Given: Generate image first time
        val dataUrl1 = TestMediaGenerator.getImageDataUrl("test-cached-image.png")
        val file = TestMediaGenerator.getMediaDir().resolve("test-cached-image.png").toFile()
        val firstModified = file.lastModified()

        // Wait a bit to ensure timestamp would change if regenerated
        Thread.sleep(100)

        // When: Get same image again
        val dataUrl2 = TestMediaGenerator.getImageDataUrl("test-cached-image.png")
        val secondModified = file.lastModified()

        // Then: Should return same data URL and not regenerate file
        assertEquals(dataUrl1, dataUrl2, "Data URLs should be identical")
        assertEquals(firstModified, secondModified, "File should not be regenerated")

        println("✅ Image caching works correctly")
    }

    @Test
    @DisplayName("Should generate audio data URL")
    fun testGenerateAudioDataUrl() {
        // When: Get audio data URL
        val dataUrl = TestMediaGenerator.getAudioDataUrl("test-gen-audio.mp3")

        // Then: Should have correct format
        assertTrue(dataUrl.startsWith("data:audio/mpeg;base64,"))
        assertTrue(dataUrl.length > 100, "Data URL should have actual content")

        // And: File should exist in media directory
        val mediaDir = TestMediaGenerator.getMediaDir()
        val audioFile = mediaDir.resolve("test-gen-audio.mp3").toFile()
        assertTrue(audioFile.exists(), "Audio file should exist")
        assertTrue(audioFile.length() > 0, "Audio file should not be empty")

        println("✅ Generated audio: ${audioFile.length() / 1024}KB")
    }

    @Test
    @DisplayName("Should generate video data URL")
    fun testGenerateVideoDataUrl() {
        // When: Get video data URL
        val dataUrl = TestMediaGenerator.getVideoDataUrl("test-gen-video.mp4")

        // Then: Should have correct format
        assertTrue(dataUrl.startsWith("data:video/mp4;base64,"))
        assertTrue(dataUrl.length > 100, "Data URL should have actual content")

        // And: File should exist in media directory
        val mediaDir = TestMediaGenerator.getMediaDir()
        val videoFile = mediaDir.resolve("test-gen-video.mp4").toFile()
        assertTrue(videoFile.exists(), "Video file should exist")
        assertTrue(videoFile.length() > 0, "Video file should not be empty")

        println("✅ Generated video: ${videoFile.length() / 1024}KB")
    }

    @Test
    @DisplayName("Should store files in .gradle/test-media directory")
    fun testMediaDirectory() {
        // When: Get media directory
        val mediaDir = TestMediaGenerator.getMediaDir()

        // Then: Should be in .gradle/test-media
        assertTrue(
            mediaDir.toString().contains(".gradle/test-media"),
            "Media directory should be in .gradle/test-media, got: $mediaDir"
        )
        assertTrue(Files.exists(mediaDir), "Media directory should exist")

        println("✅ Media directory: $mediaDir")
    }

    @Test
    @DisplayName("Should cleanup all generated files")
    fun testCleanup() {
        // Given: Generate some test files
        TestMediaGenerator.getImageDataUrl("test-cleanup-1.png")
        TestMediaGenerator.getImageDataUrl("test-cleanup-2.png")

        val mediaDir = TestMediaGenerator.getMediaDir()
        val filesBefore = mediaDir.toFile().listFiles()?.size ?: 0
        assertTrue(filesBefore > 0, "Should have some files before cleanup")

        // When: Cleanup all files
        TestMediaGenerator.cleanupAll()

        // Then: All files should be removed
        val filesAfter = mediaDir.toFile().listFiles()?.size ?: 0
        assertEquals(0, filesAfter, "All files should be removed after cleanup")

        println("✅ Cleaned up $filesBefore files")
    }

    @Test
    @DisplayName("Should handle multiple different images")
    fun testMultipleImages() {
        // When: Generate multiple different images
        val image1 = TestMediaGenerator.getImageDataUrl("multi-test-1.png")
        val image2 = TestMediaGenerator.getImageDataUrl("multi-test-2.png")
        val image3 = TestMediaGenerator.getImageDataUrl("multi-test-3.png")

        // Then: All should be valid and different files
        assertTrue(image1.startsWith("data:image/png;base64,"))
        assertTrue(image2.startsWith("data:image/png;base64,"))
        assertTrue(image3.startsWith("data:image/png;base64,"))

        // Files should exist
        val mediaDir = TestMediaGenerator.getMediaDir()
        assertTrue(mediaDir.resolve("multi-test-1.png").toFile().exists())
        assertTrue(mediaDir.resolve("multi-test-2.png").toFile().exists())
        assertTrue(mediaDir.resolve("multi-test-3.png").toFile().exists())

        println("✅ Generated 3 different images successfully")
    }
}
