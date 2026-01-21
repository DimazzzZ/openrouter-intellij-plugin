package org.zhavoronkov.openrouter.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Utility for generating test media files (images, audio, video) on-demand.
 *
 * Files are generated using the media-gen CLI tool and stored in .gradle/test-media/
 * which is git-ignored. Files are only generated if they don't already exist.
 *
 * Usage:
 * ```kotlin
 * // Get base64 data URL for an image
 * val imageDataUrl = TestMediaGenerator.getImageDataUrl("test-image.png")
 *
 * // Get base64 data URL for audio
 * val audioDataUrl = TestMediaGenerator.getAudioDataUrl("test-audio.mp3")
 *
 * // Get base64 data URL for video
 * val videoDataUrl = TestMediaGenerator.getVideoDataUrl("test-video.mp4")
 * ```
 */
object TestMediaGenerator {

    private val projectRoot: Path = Paths.get(System.getProperty("user.dir"))
    private val mediaDir: Path = projectRoot.resolve(".gradle/test-media")
    private val mediaGenBinary: Path = projectRoot.resolve(".gradle/media-gen/media-gen")

    init {
        // Ensure media directory exists
        Files.createDirectories(mediaDir)
    }

    /**
     * Get base64 data URL for a test image.
     * Generates the image if it doesn't exist.
     */
    fun getImageDataUrl(filename: String = "test-image.png"): String {
        val file = ensureImageExists(filename)
        return fileToDataUrl(file, "image/png")
    }

    /**
     * Get base64 data URL for a test audio file.
     * Generates the audio if it doesn't exist.
     */
    fun getAudioDataUrl(filename: String = "test-audio.mp3"): String {
        val file = ensureAudioExists(filename)
        return fileToDataUrl(file, "audio/mpeg")
    }

    /**
     * Get base64 data URL for a test video file.
     * Generates the video if it doesn't exist.
     */
    fun getVideoDataUrl(filename: String = "test-video.mp4"): String {
        val file = ensureVideoExists(filename)
        return fileToDataUrl(file, "video/mp4")
    }

    /**
     * Ensure media-gen binary is downloaded and available.
     */
    private fun ensureMediaGenBinary() {
        if (Files.exists(mediaGenBinary)) {
            return
        }

        // Create directory
        Files.createDirectories(mediaGenBinary.parent)

        // Detect OS and architecture
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val binaryName = when {
            os.contains("mac") && arch.contains("aarch64") -> "media-gen-macos-arm64"
            os.contains("mac") -> "media-gen-macos-x86_64"
            os.contains("linux") -> "media-gen-linux-x86_64"
            os.contains("win") -> "media-gen-windows-x86_64.exe"
            else -> throw IllegalStateException("Unsupported OS: $os $arch")
        }

        val downloadUrl = "https://github.com/DimazzzZ/media-gen/releases/download/v0.2.0/$binaryName"

        println("Downloading media-gen from $downloadUrl...")

        // Download using curl
        val process = ProcessBuilder(
            "curl",
            "-L",
            "-o",
            mediaGenBinary.toString(),
            downloadUrl
        ).redirectErrorStream(true).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Failed to download media-gen: exit code $exitCode")
        }

        // Make executable on Unix systems
        if (!os.contains("win")) {
            ProcessBuilder("chmod", "+x", mediaGenBinary.toString()).start().waitFor()
        }

        println("✅ media-gen downloaded successfully")
    }

    /**
     * Ensure an image file exists, generating it if necessary.
     */
    private fun ensureImageExists(filename: String): File {
        val file = mediaDir.resolve(filename).toFile()
        if (file.exists()) {
            return file
        }

        ensureMediaGenBinary()

        // Generate a simple image using Java AWT (no external dependency needed)
        generateSimpleImage(file)

        return file
    }

    /**
     * Generate a simple test image using Java AWT.
     */
    private fun generateSimpleImage(outputFile: File) {
        val image = java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()

        // Draw gradient background
        val gradient = java.awt.GradientPaint(
            0f,
            0f,
            java.awt.Color(100, 150, 255),
            200f,
            200f,
            java.awt.Color(255, 100, 150)
        )
        g.paint = gradient
        g.fillRect(0, 0, 200, 200)

        // Draw text
        g.color = java.awt.Color.WHITE
        g.font = java.awt.Font("Arial", java.awt.Font.BOLD, 24)
        g.drawString("Test Image", 40, 100)

        g.dispose()

        // Save as PNG
        javax.imageio.ImageIO.write(image, "png", outputFile)
    }

    /**
     * Ensure an audio file exists, generating it if necessary.
     */
    private fun ensureAudioExists(filename: String): File {
        val file = mediaDir.resolve(filename).toFile()
        if (file.exists()) {
            return file
        }

        ensureMediaGenBinary()

        // Generate audio using media-gen
        println("Generating test audio: $filename")
        val process = ProcessBuilder(
            mediaGenBinary.toString(),
            "audio",
            "--duration", "3",
            "--frequency", "440",
            "--sample-rate", "44100",
            "--bitrate", "128k",
            "--format", "mp3",
            "--output", file.absolutePath
        ).redirectErrorStream(true).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to generate audio: $output")
        }

        println("✅ Generated audio: $filename (${file.length() / 1024}KB)")
        return file
    }

    /**
     * Ensure a video file exists, generating it if necessary.
     */
    private fun ensureVideoExists(filename: String): File {
        val file = mediaDir.resolve(filename).toFile()
        if (file.exists()) {
            return file
        }

        ensureMediaGenBinary()

        // Generate video using media-gen
        println("Generating test video: $filename")
        val process = ProcessBuilder(
            mediaGenBinary.toString(),
            "video",
            "--duration", "3",
            "--width", "640",
            "--height", "480",
            "--fps", "30",
            "--bitrate", "500k",
            "--format", "mp4",
            "--output", file.absolutePath
        ).redirectErrorStream(true).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Failed to generate video: $output")
        }

        println("✅ Generated video: $filename (${file.length() / 1024}KB)")
        return file
    }

    /**
     * Convert a file to a base64 data URL.
     */
    private fun fileToDataUrl(file: File, mimeType: String): String {
        val bytes = Files.readAllBytes(file.toPath())
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return "data:$mimeType;base64,$base64"
    }

    /**
     * Get the media directory path.
     */
    fun getMediaDir(): Path = mediaDir

    /**
     * Clean up all generated media files (useful for testing).
     */
    fun cleanupAll() {
        mediaDir.toFile().listFiles()?.forEach { it.delete() }
    }
}
