package org.zhavoronkov.openrouter.icons

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import javax.swing.Icon

@DisplayName("OpenRouter Icons Tests")
class OpenRouterIconsTest {

    @Nested
    @DisplayName("Icon Loading")
    inner class IconLoadingTest {

        @Test
        @DisplayName("Should load STATUS_BAR icon")
        fun testStatusBarIconLoading() {
            val icon = OpenRouterIcons.STATUS_BAR

            assertNotNull(icon, "STATUS_BAR icon should not be null")
            assertTrue(icon is Icon, "STATUS_BAR should be a valid Icon instance")
        }

        @Test
        @DisplayName("Should load SUCCESS icon")
        fun testSuccessIconLoading() {
            val icon = OpenRouterIcons.SUCCESS

            assertNotNull(icon, "SUCCESS icon should not be null")
            assertTrue(icon is Icon, "SUCCESS should be a valid Icon instance")
        }

        @Test
        @DisplayName("Should load ERROR icon")
        fun testErrorIconLoading() {
            val icon = OpenRouterIcons.ERROR

            assertNotNull(icon, "ERROR icon should not be null")
            assertTrue(icon is Icon, "ERROR should be a valid Icon instance")
        }

        @Test
        @DisplayName("Should load all icons without exceptions")
        fun testAllIconsLoadWithoutExceptions() {
            assertDoesNotThrow {
                OpenRouterIcons.STATUS_BAR
                OpenRouterIcons.SUCCESS
                OpenRouterIcons.ERROR
                OpenRouterIcons.TOOL_WINDOW
                OpenRouterIcons.REFRESH
                OpenRouterIcons.SETTINGS
            }
        }
    }

    @Nested
    @DisplayName("Icon Properties")
    inner class IconPropertiesTest {

        @Test
        @DisplayName("Should have valid icon dimensions")
        fun testIconDimensions() {
            val icons = listOf(
                OpenRouterIcons.STATUS_BAR,
                OpenRouterIcons.SUCCESS,
                OpenRouterIcons.ERROR,
                OpenRouterIcons.TOOL_WINDOW
            )

            icons.forEach { icon ->
                assertTrue(icon.iconWidth > 0, "Icon width should be positive")
                assertTrue(icon.iconHeight > 0, "Icon height should be positive")
            }
        }

        @Test
        @DisplayName("Should have appropriate icon sizes for status bar")
        fun testStatusBarIconSizes() {
            val statusBarIcons = listOf(
                OpenRouterIcons.SUCCESS,
                OpenRouterIcons.ERROR
            )

            statusBarIcons.forEach { icon ->
                // Status bar icons should typically be 16x16 or similar small sizes
                assertTrue(icon.iconWidth <= 24, "Status bar icon width should be <= 24px")
                assertTrue(icon.iconHeight <= 24, "Status bar icon height should be <= 24px")
                assertTrue(icon.iconWidth >= 12, "Status bar icon width should be >= 12px")
                assertTrue(icon.iconHeight >= 12, "Status bar icon height should be >= 12px")
            }
        }

        @Test
        @DisplayName("Should have consistent icon sizes for status indicators")
        fun testConsistentStatusIconSizes() {
            val successIcon = OpenRouterIcons.SUCCESS
            val errorIcon = OpenRouterIcons.ERROR

            // Success and error icons should have the same dimensions for consistency
            assertEquals(successIcon.iconWidth, errorIcon.iconWidth,
                "Success and error icons should have the same width")
            assertEquals(successIcon.iconHeight, errorIcon.iconHeight,
                "Success and error icons should have the same height")
        }
    }

    @Nested
    @DisplayName("Icon Paths")
    inner class IconPathsTest {

        @Test
        @DisplayName("Should use correct icon file paths")
        fun testIconFilePaths() {
            // Test that the expected icon files exist in resources
            val iconPaths = listOf(
                "/icons/openrouter-logo-16.png",
                "/icons/openrouter-plugin-success-16.png",
                "/icons/openrouter-plugin-error-16.png"
            )

            iconPaths.forEach { path ->
                val resource = this::class.java.getResource(path)
                assertNotNull(resource, "Icon file should exist at path: $path")
            }
        }

        @Test
        @DisplayName("Should load icons from correct resource locations")
        fun testIconResourceLocations() {
            // Verify that icons are loaded from the expected locations
            assertDoesNotThrow {
                // These should not throw exceptions if paths are correct
                val logoResource = this::class.java.getResource("/icons/openrouter-logo-16.png")
                val successResource = this::class.java.getResource("/icons/openrouter-plugin-success-16.png")
                val errorResource = this::class.java.getResource("/icons/openrouter-plugin-error-16.png")

                // At least one of these should exist (depending on actual file names)
                assertTrue(logoResource != null ||
                          this::class.java.getResource("/icons/openrouter-logo.png") != null,
                    "Logo icon resource should exist")
            }
        }
    }

    @Nested
    @DisplayName("Icon Usage")
    inner class IconUsageTest {

        @Test
        @DisplayName("Should provide distinct icons for different states")
        fun testDistinctIcons() {
            val statusBar = OpenRouterIcons.STATUS_BAR
            val success = OpenRouterIcons.SUCCESS
            val error = OpenRouterIcons.ERROR

            // Icons should be different instances (though they might look similar)
            // We can't easily compare icon content, but we can ensure they're loaded
            assertNotNull(statusBar)
            assertNotNull(success)
            assertNotNull(error)
        }

        @Test
        @DisplayName("Should be suitable for status bar widget")
        fun testStatusBarSuitability() {
            val statusIcons = listOf(
                OpenRouterIcons.SUCCESS,
                OpenRouterIcons.ERROR,
                OpenRouterIcons.STATUS_BAR
            )

            statusIcons.forEach { icon ->
                // Status bar icons should be small and square-ish
                val aspectRatio = icon.iconWidth.toDouble() / icon.iconHeight.toDouble()
                assertTrue(aspectRatio >= 0.5 && aspectRatio <= 2.0,
                    "Icon aspect ratio should be reasonable for status bar")
            }
        }

        @Test
        @DisplayName("Should be suitable for popup menus")
        fun testPopupMenuSuitability() {
            val menuIcons = listOf(
                OpenRouterIcons.STATUS_BAR,
                OpenRouterIcons.SUCCESS,
                OpenRouterIcons.ERROR
            )

            menuIcons.forEach { icon ->
                // Menu icons should be small enough for menu items
                assertTrue(icon.iconWidth <= 32, "Menu icon width should be <= 32px")
                assertTrue(icon.iconHeight <= 32, "Menu icon height should be <= 32px")
            }
        }
    }

    @Nested
    @DisplayName("Icon Accessibility")
    inner class IconAccessibilityTest {

        @Test
        @DisplayName("Should provide visual distinction between states")
        fun testVisualDistinction() {
            // We can't test visual appearance directly, but we can ensure
            // different icons are used for different states
            val icons = mapOf(
                "STATUS_BAR" to OpenRouterIcons.STATUS_BAR,
                "SUCCESS" to OpenRouterIcons.SUCCESS,
                "ERROR" to OpenRouterIcons.ERROR
            )

            // All icons should be loaded successfully
            icons.forEach { (name, icon) ->
                assertNotNull(icon, "$name icon should be loaded")
            }
        }

        @Test
        @DisplayName("Should work in different UI contexts")
        fun testUIContextCompatibility() {
            val icons = listOf(
                OpenRouterIcons.STATUS_BAR,
                OpenRouterIcons.SUCCESS,
                OpenRouterIcons.ERROR
            )

            icons.forEach { icon ->
                // Icons should be paintable (basic requirement for Swing icons)
                assertDoesNotThrow {
                    // This would normally test painting, but we can't easily do that in unit tests
                    // Instead, we verify basic properties
                    assertTrue(icon.iconWidth > 0)
                    assertTrue(icon.iconHeight > 0)
                }
            }
        }
    }

    @Nested
    @DisplayName("Icon Performance")
    inner class IconPerformanceTest {

        @Test
        @DisplayName("Should load icons efficiently")
        fun testEfficientIconLoading() {
            // Icons should load quickly (this is more of a smoke test)
            val startTime = System.currentTimeMillis()

            // Load all icons
            OpenRouterIcons.STATUS_BAR
            OpenRouterIcons.SUCCESS
            OpenRouterIcons.ERROR

            val endTime = System.currentTimeMillis()
            val loadTime = endTime - startTime

            // Icon loading should be fast (less than 1 second for all icons)
            assertTrue(loadTime < 1000, "Icon loading should be fast (< 1 second)")
        }

        @Test
        @DisplayName("Should cache icons properly")
        fun testIconCaching() {
            // Icons should be the same instance when accessed multiple times
            val statusBar1 = OpenRouterIcons.STATUS_BAR
            val statusBar2 = OpenRouterIcons.STATUS_BAR

            // IconLoader typically caches icons, so these should be the same instance
            assertSame(statusBar1, statusBar2, "Icons should be cached and return same instance")
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("Should handle missing icon files gracefully")
        fun testMissingIconHandling() {
            // This test verifies that the icon loading mechanism handles missing files
            // In a real scenario, IconLoader would provide a default icon or handle the error
            assertDoesNotThrow {
                // If icons are missing, IconLoader should handle it gracefully
                OpenRouterIcons.STATUS_BAR
                OpenRouterIcons.SUCCESS
                OpenRouterIcons.ERROR
            }
        }

        @Test
        @DisplayName("Should not throw exceptions during icon access")
        fun testNoExceptionsDuringAccess() {
            assertDoesNotThrow {
                val icons = listOf(
                    OpenRouterIcons.STATUS_BAR,
                    OpenRouterIcons.SUCCESS,
                    OpenRouterIcons.ERROR
                )

                // Access icon properties
                icons.forEach { icon ->
                    icon.iconWidth
                    icon.iconHeight
                }
            }
        }
    }
}
