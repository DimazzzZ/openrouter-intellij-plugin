package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.zhavoronkov.openrouter.icons.OpenRouterIcons

@DisplayName("Connection Status Tests")
class ConnectionStatusTest {

    @Nested
    @DisplayName("Status Properties")
    inner class StatusPropertiesTest {

        @Test
        @DisplayName("Should have correct CONNECTING status properties")
        fun testConnectingStatus() {
            val status = ConnectionStatus.CONNECTING

            assertEquals("Connecting...", status.displayName)
            assertEquals("Establishing connection", status.description)
            assertNotNull(status.icon)
        }

        @Test
        @DisplayName("Should have correct READY status properties")
        fun testReadyStatus() {
            val status = ConnectionStatus.READY

            assertEquals("Ready", status.displayName)
            assertEquals("Connected and ready to use", status.description)
            assertEquals(OpenRouterIcons.SUCCESS, status.icon)
        }

        @Test
        @DisplayName("Should have correct ERROR status properties")
        fun testErrorStatus() {
            val status = ConnectionStatus.ERROR

            assertEquals("Error", status.displayName)
            assertEquals("Connection failed or API error", status.description)
            assertEquals(OpenRouterIcons.ERROR, status.icon)
        }

        @Test
        @DisplayName("Should have correct NOT_CONFIGURED status properties")
        fun testNotConfiguredStatus() {
            val status = ConnectionStatus.NOT_CONFIGURED

            assertEquals("Not Configured", status.displayName)
            assertEquals("API key not configured", status.description)
            assertNotNull(status.icon)
        }
    }

    @Nested
    @DisplayName("Status Behavior")
    inner class StatusBehaviorTest {

        @Test
        @DisplayName("Should have unique display names")
        fun testUniqueDisplayNames() {
            val displayNames = ConnectionStatus.values().map { it.displayName }
            val uniqueDisplayNames = displayNames.toSet()

            assertEquals(displayNames.size, uniqueDisplayNames.size,
                "All connection statuses should have unique display names")
        }

        @Test
        @DisplayName("Should have non-empty descriptions")
        fun testNonEmptyDescriptions() {
            ConnectionStatus.values().forEach { status ->
                assertFalse(status.description.isBlank(),
                    "Status ${status.name} should have a non-empty description")
            }
        }

        @Test
        @DisplayName("Should have valid icons")
        fun testValidIcons() {
            ConnectionStatus.values().forEach { status ->
                assertNotNull(status.icon,
                    "Status ${status.name} should have a valid icon")
            }
        }

        @Test
        @DisplayName("Should use appropriate icons for each status")
        fun testAppropriateIcons() {
            // READY should use success icon
            assertEquals(OpenRouterIcons.SUCCESS, ConnectionStatus.READY.icon)

            // ERROR should use error icon
            assertEquals(OpenRouterIcons.ERROR, ConnectionStatus.ERROR.icon)

            // NOT_CONFIGURED should use error icon
            assertEquals(OpenRouterIcons.ERROR, ConnectionStatus.NOT_CONFIGURED.icon)

            // OFFLINE should use error icon
            assertEquals(OpenRouterIcons.ERROR, ConnectionStatus.OFFLINE.icon)

            // CONNECTING should have valid icon
            assertNotNull(ConnectionStatus.CONNECTING.icon)
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    inner class StatusTransitionsTest {

        @Test
        @DisplayName("Should support all expected status values")
        fun testAllExpectedStatuses() {
            val expectedStatuses = setOf("CONNECTING", "READY", "ERROR", "NOT_CONFIGURED", "OFFLINE")
            val actualStatuses = ConnectionStatus.values().map { it.name }.toSet()

            assertEquals(expectedStatuses, actualStatuses,
                "Should have exactly the expected connection statuses")
        }

        @Test
        @DisplayName("Should be able to convert from string")
        fun testFromString() {
            assertEquals(ConnectionStatus.READY, ConnectionStatus.valueOf("READY"))
            assertEquals(ConnectionStatus.ERROR, ConnectionStatus.valueOf("ERROR"))
            assertEquals(ConnectionStatus.CONNECTING, ConnectionStatus.valueOf("CONNECTING"))
            assertEquals(ConnectionStatus.NOT_CONFIGURED, ConnectionStatus.valueOf("NOT_CONFIGURED"))
            assertEquals(ConnectionStatus.OFFLINE, ConnectionStatus.valueOf("OFFLINE"))
        }

        @Test
        @DisplayName("Should handle invalid status string")
        fun testInvalidStatusString() {
            assertThrows(IllegalArgumentException::class.java) {
                ConnectionStatus.valueOf("INVALID_STATUS")
            }
        }
    }

    @Nested
    @DisplayName("Status Usage Scenarios")
    inner class StatusUsageScenariosTest {

        @Test
        @DisplayName("Should represent initial connection state")
        fun testInitialConnectionState() {
            val status = ConnectionStatus.CONNECTING

            assertTrue(status.displayName.contains("Connecting"))
            assertTrue(status.description.contains("Connecting"))
        }

        @Test
        @DisplayName("Should represent successful connection state")
        fun testSuccessfulConnectionState() {
            val status = ConnectionStatus.READY

            assertTrue(status.displayName.contains("Ready"))
            assertTrue(status.description.contains("ready"))
        }

        @Test
        @DisplayName("Should represent error state")
        fun testErrorState() {
            val status = ConnectionStatus.ERROR

            assertTrue(status.displayName.contains("Error"))
            assertTrue(status.description.contains("error") || status.description.contains("failed"))
        }

        @Test
        @DisplayName("Should represent not configured state")
        fun testNotConfiguredState() {
            val status = ConnectionStatus.NOT_CONFIGURED

            assertTrue(status.displayName.contains("Not Configured"))
            assertTrue(status.description.contains("not configured"))
        }
    }

    @Nested
    @DisplayName("Status Display")
    inner class StatusDisplayTest {

        @Test
        @DisplayName("Should have user-friendly display names")
        fun testUserFriendlyDisplayNames() {
            ConnectionStatus.values().forEach { status ->
                // Display names should be capitalized and readable
                assertTrue(status.displayName.first().isUpperCase(),
                    "Display name for ${status.name} should start with uppercase")

                // Should not contain underscores (user-friendly)
                assertFalse(status.displayName.contains("_"),
                    "Display name for ${status.name} should not contain underscores")
            }
        }

        @Test
        @DisplayName("Should have descriptive tooltips")
        fun testDescriptiveTooltips() {
            ConnectionStatus.values().forEach { status ->
                assertTrue(status.description.length > 10,
                    "Description for ${status.name} should be descriptive (>10 chars)")

                // Should end with proper punctuation or be a complete phrase
                assertTrue(status.description.endsWith(".") ||
                          status.description.endsWith("...") ||
                          !status.description.contains(" "),
                    "Description for ${status.name} should have proper formatting")
            }
        }

        @Test
        @DisplayName("Should provide clear status indication")
        fun testClearStatusIndication() {
            // READY should indicate positive state
            val readyStatus = ConnectionStatus.READY
            assertTrue(readyStatus.displayName.lowercase().contains("ready") ||
                      readyStatus.description.lowercase().contains("ready") ||
                      readyStatus.description.lowercase().contains("connected"))

            // ERROR should indicate problem state
            val errorStatus = ConnectionStatus.ERROR
            assertTrue(errorStatus.displayName.lowercase().contains("error") ||
                      errorStatus.description.lowercase().contains("error") ||
                      errorStatus.description.lowercase().contains("failed"))
        }
    }

    @Nested
    @DisplayName("Enum Consistency")
    inner class EnumConsistencyTest {

        @Test
        @DisplayName("Should maintain consistent enum ordering")
        fun testConsistentOrdering() {
            val statuses = ConnectionStatus.values()

            // Verify expected order (logical flow)
            assertEquals(ConnectionStatus.READY, statuses[0])
            assertEquals(ConnectionStatus.CONNECTING, statuses[1])
            assertEquals(ConnectionStatus.ERROR, statuses[2])
            assertEquals(ConnectionStatus.NOT_CONFIGURED, statuses[3])
            assertEquals(ConnectionStatus.OFFLINE, statuses[4])
        }

        @Test
        @DisplayName("Should have exactly 5 status values")
        fun testStatusCount() {
            assertEquals(5, ConnectionStatus.values().size,
                "Should have exactly 5 connection status values")
        }

        @Test
        @DisplayName("Should support ordinal access")
        fun testOrdinalAccess() {
            assertEquals(0, ConnectionStatus.READY.ordinal)
            assertEquals(1, ConnectionStatus.CONNECTING.ordinal)
            assertEquals(2, ConnectionStatus.ERROR.ordinal)
            assertEquals(3, ConnectionStatus.NOT_CONFIGURED.ordinal)
            assertEquals(4, ConnectionStatus.OFFLINE.ordinal)
        }
    }
}
