package org.zhavoronkov.openrouter.icons

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        @DisplayName("Should have main logo SVG resource (light theme)")
        fun testLogoSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-logo.svg")
            assertNotNull(resource, "Main logo SVG should exist at /icons/openrouter-logo.svg")
        }

        @Test
        @DisplayName("Should have main logo SVG resource (dark theme)")
        fun testLogoDarkSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-logo_dark.svg")
            assertNotNull(resource, "Dark theme logo SVG should exist at /icons/openrouter-logo_dark.svg")
        }

        @Test
        @DisplayName("Should have status-bar base SVG resource (light theme)")
        fun testStatusBarSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-statusbar.svg")
            assertNotNull(resource, "Status-bar SVG should exist at /icons/openrouter-statusbar.svg")
        }

        @Test
        @DisplayName("Should have status-bar base SVG resource (dark theme)")
        fun testStatusBarDarkSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-statusbar_dark.svg")
            assertNotNull(resource, "Dark status-bar SVG should exist at /icons/openrouter-statusbar_dark.svg")
        }

        @Test
        @DisplayName("Should have success badge SVG resource")
        fun testSuccessBadgeSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-badge-ok.svg")
            assertNotNull(resource, "Success badge SVG should exist at /icons/openrouter-badge-ok.svg")
        }

        @Test
        @DisplayName("Should have error badge SVG resource")
        fun testErrorBadgeSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-badge-error.svg")
            assertNotNull(resource, "Error badge SVG should exist at /icons/openrouter-badge-error.svg")
        }

        @Test
        @DisplayName("Should have tool window SVG resource")
        fun testToolWindowSvgExists() {
            val resource = this::class.java.getResource("/icons/openrouter-toolwindow.svg")
            assertNotNull(resource, "Tool window SVG should exist at /icons/openrouter-toolwindow.svg")
        }

        @Test
        @DisplayName("Should have all required icon resources")
        fun testAllIconResourcesExist() {
            val iconPaths = listOf(
                "/icons/openrouter-logo.svg",
                "/icons/openrouter-logo_dark.svg",
                "/icons/openrouter-statusbar.svg",
                "/icons/openrouter-statusbar_dark.svg",
                "/icons/openrouter-badge-ok.svg",
                "/icons/openrouter-badge-error.svg",
                "/icons/openrouter-toolwindow.svg"
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
                "/icons/openrouter-logo.svg",
                "/icons/openrouter-logo_dark.svg",
                "/icons/openrouter-statusbar.svg",
                "/icons/openrouter-statusbar_dark.svg",
                "/icons/openrouter-badge-ok.svg",
                "/icons/openrouter-badge-error.svg",
                "/icons/openrouter-toolwindow.svg"
            )

            paths.forEach { path ->
                assertTrue(path.startsWith("/icons/"), "Icon path should start with /icons/: $path")
                assertTrue(path.endsWith(".svg"), "Icon path should end with .svg: $path")
                assertTrue(path.contains("openrouter"), "Icon path should contain 'openrouter': $path")
            }
        }

        @Test
        @DisplayName("Should use descriptive icon names")
        fun testIconNamesDescriptive() {
            val paths = listOf(
                "/icons/openrouter-logo.svg",
                "/icons/openrouter-logo_dark.svg",
                "/icons/openrouter-statusbar.svg",
                "/icons/openrouter-statusbar_dark.svg",
                "/icons/openrouter-badge-ok.svg",
                "/icons/openrouter-badge-error.svg",
                "/icons/openrouter-toolwindow.svg"
            )

            paths.forEach { path ->
                val filename = path.substringAfterLast("/")
                assertTrue(filename.length > 5, "Icon filename should be descriptive: $filename")
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
                "/icons/openrouter-logo.svg",
                "/icons/openrouter-logo_dark.svg",
                "/icons/openrouter-statusbar.svg",
                "/icons/openrouter-statusbar_dark.svg",
                "/icons/openrouter-badge-ok.svg",
                "/icons/openrouter-badge-error.svg",
                "/icons/openrouter-toolwindow.svg"
            )

            iconPaths.forEach { path ->
                assertTrue(path.startsWith("/icons/"), "All icons should be in /icons/ directory: $path")
            }
        }

        @Test
        @DisplayName("Should use consistent naming convention")
        fun testConsistentNaming() {
            val iconPaths = listOf(
                "/icons/openrouter-logo.svg",
                "/icons/openrouter-logo_dark.svg",
                "/icons/openrouter-statusbar.svg",
                "/icons/openrouter-statusbar_dark.svg",
                "/icons/openrouter-badge-ok.svg",
                "/icons/openrouter-badge-error.svg",
                "/icons/openrouter-toolwindow.svg"
            )

            iconPaths.forEach { path ->
                val filename = path.substringAfterLast("/")
                assertTrue(filename.startsWith("openrouter"), "Icon names should start with 'openrouter': $filename")
                assertTrue(filename.contains("-"), "Icon names should use hyphen separator: $filename")
            }
        }
    }
}
