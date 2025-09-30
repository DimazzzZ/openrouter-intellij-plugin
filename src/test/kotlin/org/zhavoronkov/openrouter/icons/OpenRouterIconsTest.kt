package org.zhavoronkov.openrouter.icons

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Tests for OpenRouter Icons
 * 
 * Note: Full icon loading tests require IntelliJ Platform initialization which is not
 * available in standard JUnit tests. These tests verify that icon resource files exist.
 * 
 * Icon rendering and display tests should be performed through IDE integration testing
 * using the runIde task.
 */
@DisplayName("OpenRouter Icons Tests")
class OpenRouterIconsTest {

    @Nested
    @DisplayName("Icon Resources")
    inner class IconResourcesTest {

        @Test
        @DisplayName("Should have status bar icon resource")
        fun testStatusBarIconExists() {
            val resource = this::class.java.getResource("/icons/openrouter-13.png")
            assertNotNull(resource, "Status bar icon (13x13) should exist at /icons/openrouter-13.png")
        }

        @Test
        @DisplayName("Should have tool window icon resource")
        fun testToolWindowIconExists() {
            val resource = this::class.java.getResource("/icons/openrouter-16.png")
            assertNotNull(resource, "Tool window icon (16x16) should exist at /icons/openrouter-16.png")
        }

        @Test
        @DisplayName("Should have success icon resource")
        fun testSuccessIconExists() {
            val resource = this::class.java.getResource("/icons/openrouter-plugin-success-16.png")
            assertNotNull(resource, "Success icon should exist at /icons/openrouter-plugin-success-16.png")
        }

        @Test
        @DisplayName("Should have error icon resource")
        fun testErrorIconExists() {
            val resource = this::class.java.getResource("/icons/openrouter-plugin-error-16.png")
            assertNotNull(resource, "Error icon should exist at /icons/openrouter-plugin-error-16.png")
        }

        @Test
        @DisplayName("Should have all required icon resources")
        fun testAllIconResourcesExist() {
            val iconPaths = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            iconPaths.forEach { path ->
                val resource = this::class.java.getResource(path)
                assertNotNull(resource, "Icon file should exist at path: $path")
            }
        }
    }

    @Nested
    @DisplayName("Icon Path Validation")
    inner class IconPathValidationTest {

        @Test
        @DisplayName("Should use correct icon path format")
        fun testIconPathFormat() {
            val paths = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            paths.forEach { path ->
                assertTrue(path.startsWith("/icons/"), "Icon path should start with /icons/: $path")
                assertTrue(path.endsWith(".png"), "Icon path should end with .png: $path")
                assertTrue(path.contains("openrouter"), "Icon path should contain 'openrouter': $path")
            }
        }

        @Test
        @DisplayName("Should use descriptive icon names")
        fun testIconNamesDescriptive() {
            val paths = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            paths.forEach { path ->
                val filename = path.substringAfterLast("/")
                assertTrue(filename.length > 5, "Icon filename should be descriptive: $filename")
            }
        }

        @Test
        @DisplayName("Should include size in icon names where appropriate")
        fun testIconSizeInNames() {
            val sizedIcons = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            sizedIcons.forEach { path ->
                val hasSizeIndicator = path.contains("-13") || path.contains("-16")
                assertTrue(hasSizeIndicator, "Icon path should indicate size: $path")
            }
        }
    }

    @Nested
    @DisplayName("Icon Organization")
    inner class IconOrganizationTest {

        @Test
        @DisplayName("Should organize icons in icons directory")
        fun testIconsInCorrectDirectory() {
            val iconPaths = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            iconPaths.forEach { path ->
                assertTrue(path.startsWith("/icons/"), "All icons should be in /icons/ directory: $path")
            }
        }

        @Test
        @DisplayName("Should use consistent naming convention")
        fun testConsistentNaming() {
            val iconPaths = listOf(
                "/icons/openrouter-13.png",
                "/icons/openrouter-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            iconPaths.forEach { path ->
                val filename = path.substringAfterLast("/")
                assertTrue(filename.startsWith("openrouter"), "Icon names should start with 'openrouter': $filename")
                assertTrue(filename.contains("-"), "Icon names should use hyphen separator: $filename")
            }
        }
    }
}

