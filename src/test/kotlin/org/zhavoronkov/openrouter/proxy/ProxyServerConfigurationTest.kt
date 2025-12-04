package org.zhavoronkov.openrouter.proxy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

@DisplayName("Proxy Server Configuration Tests")
class ProxyServerConfigurationTest {

    @BeforeEach
    fun setUp() {
        // This test focuses on configuration logic, not actual server instances
        // No setup needed for configuration tests
    }

    @Nested
    @DisplayName("Port Selection Logic Tests")
    inner class PortSelectionLogicTests {

        @Test
        @DisplayName("Should determine port selection strategy correctly")
        fun testPortSelectionStrategy() {
            // Test specific port configuration
            val specificPortSettings = OpenRouterSettings(proxyPort = 8888)
            assertTrue(specificPortSettings.proxyPort > 0, "Should use specific port when configured")
            
            // Test auto-select configuration
            val autoSelectSettings = OpenRouterSettings(proxyPort = 0)
            assertEquals(0, autoSelectSettings.proxyPort, "Should use auto-select when port is 0")
            
            // Verify range is available for auto-select
            assertTrue(autoSelectSettings.proxyPortRangeStart < autoSelectSettings.proxyPortRangeEnd,
                "Range start should be less than range end")
            assertTrue(autoSelectSettings.proxyPortRangeStart >= 1024, 
                "Range start should be >= 1024")
            assertTrue(autoSelectSettings.proxyPortRangeEnd <= 65535,
                "Range end should be <= 65535")
        }

        @Test 
        @DisplayName("Should validate port range availability")
        fun testPortRangeValidation() {
            val settings = OpenRouterSettings(
                proxyPortRangeStart = 8880,
                proxyPortRangeEnd = 8899
            )
            
            // Range should be valid
            assertTrue(settings.proxyPortRangeStart <= settings.proxyPortRangeEnd,
                "Start port should be <= end port")
            
            // Range should have at least one port
            assertTrue(settings.proxyPortRangeEnd - settings.proxyPortRangeStart >= 0,
                "Range should contain at least one port")
        }

        @Test
        @DisplayName("Should handle edge cases in port configuration")
        fun testPortConfigurationEdgeCases() {
            // Single port range
            val singlePortSettings = OpenRouterSettings(
                proxyPortRangeStart = 8880,
                proxyPortRangeEnd = 8880
            )
            assertEquals(1, singlePortSettings.proxyPortRangeEnd - singlePortSettings.proxyPortRangeStart + 1,
                "Single port range should have exactly one port")
                
            // Maximum valid port 
            val maxPortSettings = OpenRouterSettings(proxyPort = 65535)
            assertEquals(65535, maxPortSettings.proxyPort, "Should handle maximum valid port")
            
            // Minimum valid port
            val minPortSettings = OpenRouterSettings(proxyPort = 1024) 
            assertEquals(1024, minPortSettings.proxyPort, "Should handle minimum valid port")
        }
    }

    @Nested
    @DisplayName("Configuration Integration Tests")
    inner class ConfigurationIntegrationTests {

        @Test
        @DisplayName("Should respect new default port range (8880-8899)")
        fun testNewDefaultPortRange() {
            val settings = OpenRouterSettings()

            // Verify new defaults are properly set
            assertEquals(8880, settings.proxyPortRangeStart,
                "Default start port should be 8880 (avoiding common port 8080)")
            assertEquals(8899, settings.proxyPortRangeEnd,
                "Default end port should be 8899")
            assertEquals(0, settings.proxyPort,
                "Default specific port should be 0 (auto-select)")
            assertFalse(settings.proxyAutoStart,
                "Auto-start should be disabled by default")
        }

        @Test
        @DisplayName("Should handle complete configuration scenarios")
        fun testCompleteConfigurationScenarios() {
            // Scenario 1: Auto-start with specific port
            val specificPortConfig = OpenRouterSettings(
                proxyAutoStart = true,
                proxyPort = 8888,
                proxyPortRangeStart = 8880,
                proxyPortRangeEnd = 8899
            )
            assertTrue(specificPortConfig.proxyAutoStart, "Auto-start should be enabled")
            assertEquals(8888, specificPortConfig.proxyPort, "Should use specific port")

            // Scenario 2: Auto-start with range selection
            val rangeConfig = OpenRouterSettings(
                proxyAutoStart = true,
                proxyPort = 0, // Auto-select from range
                proxyPortRangeStart = 9000,
                proxyPortRangeEnd = 9020
            )
            assertTrue(rangeConfig.proxyAutoStart, "Auto-start should be enabled")
            assertEquals(0, rangeConfig.proxyPort, "Should use auto-select")
            assertEquals(9000, rangeConfig.proxyPortRangeStart, "Should use custom range start")
            assertEquals(9020, rangeConfig.proxyPortRangeEnd, "Should use custom range end")

            // Scenario 3: Manual start only
            val manualConfig = OpenRouterSettings(
                proxyAutoStart = false,
                proxyPort = 8889
            )
            assertFalse(manualConfig.proxyAutoStart, "Auto-start should be disabled")
            assertEquals(8889, manualConfig.proxyPort, "Should use configured port for manual start")
        }

        @Test
        @DisplayName("Should validate configuration consistency")
        fun testConfigurationValidation() {
            // Valid configurations
            val validConfigs = listOf(
                OpenRouterSettings(proxyPort = 0, proxyPortRangeStart = 8880, proxyPortRangeEnd = 8899),
                OpenRouterSettings(proxyPort = 8888, proxyPortRangeStart = 8880, proxyPortRangeEnd = 8899),
                OpenRouterSettings(proxyPort = 0, proxyPortRangeStart = 1024, proxyPortRangeEnd = 65535)
            )

            validConfigs.forEach { config ->
                assertTrue(config.proxyPortRangeStart >= 1024,
                    "Range start should be >= 1024 for config: $config")
                assertTrue(config.proxyPortRangeEnd <= 65535,
                    "Range end should be <= 65535 for config: $config")
                assertTrue(config.proxyPortRangeStart <= config.proxyPortRangeEnd,
                    "Range start should be <= end for config: $config")
                assertTrue(config.proxyPort == 0 || (config.proxyPort >= 1024 && config.proxyPort <= 65535),
                    "Specific port should be 0 or in valid range for config: $config")
            }
        }
    }

    @Nested
    @DisplayName("Auto-start Configuration Tests")
    inner class AutoStartConfigurationTests {

        @Test
        @DisplayName("Should respect auto-start disabled setting")
        fun testAutoStartDisabled() {
            val settings = OpenRouterSettings()

            assertFalse(settings.proxyAutoStart,
                "Auto-start should be disabled by default for better user control")
        }

        @Test
        @DisplayName("Should enable auto-start when configured")
        fun testAutoStartEnabled() {
            val settings = OpenRouterSettings(proxyAutoStart = true)

            assertTrue(settings.proxyAutoStart,
                "Auto-start should be enabled when configured")
        }

        @Test
        @DisplayName("Should support different auto-start scenarios")
        fun testAutoStartScenarios() {
            // Scenario 1: Auto-start disabled, specific port
            val disabledSpecific = OpenRouterSettings(proxyAutoStart = false, proxyPort = 8888)
            assertFalse(disabledSpecific.proxyAutoStart, "Auto-start should be disabled")
            assertEquals(8888, disabledSpecific.proxyPort, "Should still have port configured")

            // Scenario 2: Auto-start enabled, range selection
            val enabledRange = OpenRouterSettings(proxyAutoStart = true, proxyPort = 0)
            assertTrue(enabledRange.proxyAutoStart, "Auto-start should be enabled")
            assertEquals(0, enabledRange.proxyPort, "Should use range selection")

            // Scenario 3: Configuration consistency
            val allEnabled = OpenRouterSettings(
                proxyAutoStart = true,
                proxyPort = 8889,
                proxyPortRangeStart = 8880,
                proxyPortRangeEnd = 8899
            )
            assertTrue(allEnabled.proxyAutoStart, "Auto-start should be enabled")
            assertEquals(8889, allEnabled.proxyPort, "Should use specific port")
            assertTrue(allEnabled.proxyPort in allEnabled.proxyPortRangeStart..allEnabled.proxyPortRangeEnd,
                "Specific port should be within configured range")
        }
    }

    @Nested
    @DisplayName("Port Configuration Logic Tests")
    inner class PortConfigurationLogicTests {

        @Test
        @DisplayName("Should validate port ranges are reasonable")
        fun testPortRangeReasonableness() {
            val settings = OpenRouterSettings()

            // Default range should be reasonable size
            val rangeSize = settings.proxyPortRangeEnd - settings.proxyPortRangeStart + 1
            assertTrue(rangeSize >= 10, "Default range should have at least 10 ports")
            assertTrue(rangeSize <= 100, "Default range should not be excessively large")

            // Range should avoid common well-known ports
            assertTrue(settings.proxyPortRangeStart > 8000,
                "Range should start above common development ports")
            assertTrue(settings.proxyPortRangeEnd < 9000,
                "Range should end before higher service ports")
        }

        @Test
        @DisplayName("Should handle different port selection strategies")
        fun testPortSelectionStrategies() {
            // Strategy 1: Specific port (user knows what they want)
            val specificConfig = OpenRouterSettings(proxyPort = 8888)
            assertTrue(specificConfig.proxyPort > 0, "Should use specific port strategy")

            // Strategy 2: Range selection (let system choose)
            val rangeConfig = OpenRouterSettings(proxyPort = 0)
            assertEquals(0, rangeConfig.proxyPort, "Should use range selection strategy")
            assertTrue(rangeConfig.proxyPortRangeStart < rangeConfig.proxyPortRangeEnd,
                "Range should be valid for selection")

            // Strategy 3: Mixed approach (fallback from specific to range)
            val mixedConfig = OpenRouterSettings(
                proxyPort = 8889,  // Try specific first
                proxyPortRangeStart = 8880,  // Fallback range
                proxyPortRangeEnd = 8899
            )
            assertTrue(mixedConfig.proxyPort > 0, "Should prefer specific port")
            assertTrue(mixedConfig.proxyPort in mixedConfig.proxyPortRangeStart..mixedConfig.proxyPortRangeEnd,
                "Specific port should be within fallback range for consistency")
        }
    }
}
