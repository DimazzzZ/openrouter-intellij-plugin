package org.zhavoronkov.openrouter.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/**
 * Utility for generating test media files (images, audio, video) on-demand.
 *
 * - Images: Generated using Java AWT (no external dependencies)
 * - Audio: Generated using the media-gen CLI tool
 * - Video: Generated as minimal valid MP4 files (no external dependencies)
 *
 * Files are stored in .gradle/test-media/ which is git-ignored.
 * Files are only generated if they don't already exist.
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
     * Generates a minimal valid MP4 file if it doesn't exist.
     *
     * Note: This generates a simple MP4 structure for testing purposes.
     * For audio generation, media-gen is used.
     */
    fun getVideoDataUrl(filename: String = "test-video.mp4"): String {
        val file = ensureVideoExists(filename)
        return fileToDataUrl(file, "video/mp4")
    }

    /**
     * Ensure media-gen binary is downloaded and available.
     */
    @Suppress("ThrowsCount")
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
            else -> error("Unsupported OS: $os $arch")
        }

        val downloadUrl = "https://github.com/DimazzzZ/media-gen/releases/download/v0.2.0/$binaryName"

        println("Downloading media-gen from $downloadUrl...")

        // Download using curl with explicit options
        val process = ProcessBuilder(
            "curl",
            "-L", // Follow redirects
            "-f", // Fail on HTTP errors
            "-S", // Show errors
            "-s", // Silent mode (no progress bar)
            "-o",
            mediaGenBinary.toString(),
            downloadUrl
        ).start() // Don't redirect error stream to avoid potential issues

        // Read output in a separate thread to avoid deadlock
        val outputReader = Thread {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val errorReader = Thread {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        outputReader.start()
        errorReader.start()

        // Wait for process to complete
        val exitCode = process.waitFor()
        outputReader.join()
        errorReader.join()

        check(exitCode == 0) {
            "Failed to download media-gen from $downloadUrl\n" +
                "Exit code: $exitCode\n" +
                "Please verify the URL is correct and the release exists at:\n" +
                "https://github.com/DimazzzZ/media-gen/releases/tag/v0.2.0"
        }

        // Verify file was downloaded and has content
        check(Files.exists(mediaGenBinary) && Files.size(mediaGenBinary) > 0L) {
            "media-gen binary was not downloaded correctly.\n" +
                "File exists: ${Files.exists(mediaGenBinary)}\n" +
                "Size: ${if (Files.exists(mediaGenBinary)) Files.size(mediaGenBinary) else 0}\n" +
                "URL: $downloadUrl"
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
        ).start()

        // Consume streams to prevent deadlock
        val output = StringBuilder()
        val outputThread = Thread {
            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    output.append(line).append("\n")
                }
            }
        }
        val errorThread = Thread {
            process.errorStream.bufferedReader().use { reader ->
                reader.lines().forEach { line ->
                    output.append(line).append("\n")
                }
            }
        }
        outputThread.start()
        errorThread.start()

        // Wait for process
        val exitCode = process.waitFor()
        outputThread.join(5000) // Wait max 5 seconds
        errorThread.join(5000)

        // Check if file was actually created
        check(file.exists() && file.length() > 0L) {
            "Failed to generate audio file. Exit code: $exitCode, Output: '${output.toString().trim()}', " +
                "File exists: ${file.exists()}, File size: ${if (file.exists()) file.length() else 0}"
        }

        check(exitCode == 0) {
            "Failed to generate audio: exit code $exitCode, output: '${output.toString().trim()}'"
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

        // Generate a minimal valid MP4 file for testing
        // This creates a basic MP4 structure that parsers will recognize as valid video
        println("Generating test video: $filename")
        generateMinimalMp4(file)

        println("✅ Generated video: $filename (${file.length() / 1024}KB)")
        return file
    }

    /**
     * Generate a minimal valid MP4 file for testing purposes.
     * This creates a very basic MP4 structure that parsers will recognize as valid video.
     */
    private fun generateMinimalMp4(outputFile: File) {
        // Create a minimal valid MP4 file with basic atoms/boxes
        // This is sufficient for testing data URL generation
        // We make it larger to ensure base64 encoded data URL is > 100 chars
        val mp4Data = byteArrayOf(
            // ftyp box (file type)
            0x00, 0x00, 0x00, 0x20, // size
            0x66, 0x74, 0x79, 0x70, // 'ftyp'
            0x69, 0x73, 0x6F, 0x6D, // 'isom' major brand
            0x00, 0x00, 0x02, 0x00, // minor version
            0x69, 0x73, 0x6F, 0x6D, // compatible brand 'isom'
            0x69, 0x73, 0x6F, 0x32, // compatible brand 'iso2'
            0x6D, 0x70, 0x34, 0x31, // compatible brand 'mp41'
            0x00, 0x00, 0x00, 0x00, // padding

            // mdat box (media data) - padded for test purposes
            0x00, 0x00, 0x00, 0x48, // size (72 bytes)
            0x6D, 0x64, 0x61, 0x74, // 'mdat'
            // Padding data (64 bytes of zeros to make file larger)
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        Files.write(outputFile.toPath(), mp4Data)
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
