package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConnectionStatus Tests")
class ConnectionStatusTest {

    @Nested
    @DisplayName("Enum values")
    inner class EnumValuesTests {

        @Test
        fun `all status values are accessible`() {
            val statuses = ConnectionStatus.entries
            assertEquals(5, statuses.size)
        }

        @Test
        fun `READY has correct properties`() {
            val status = ConnectionStatus.READY
            assertEquals("Ready", status.displayName)
            assertNotNull(status.icon)
            assertTrue(status.description.isNotEmpty())
        }

        @Test
        fun `CONNECTING has correct properties`() {
            val status = ConnectionStatus.CONNECTING
            assertEquals("Connecting...", status.displayName)
            assertNotNull(status.icon)
            assertTrue(status.description.isNotEmpty())
        }

        @Test
        fun `ERROR has correct properties`() {
            val status = ConnectionStatus.ERROR
            assertEquals("Error", status.displayName)
            assertNotNull(status.icon)
            assertTrue(status.description.isNotEmpty())
        }

        @Test
        fun `NOT_CONFIGURED has correct properties`() {
            val status = ConnectionStatus.NOT_CONFIGURED
            assertEquals("Not Configured", status.displayName)
            assertNotNull(status.icon)
            assertTrue(status.description.isNotEmpty())
        }

        @Test
        fun `OFFLINE has correct properties`() {
            val status = ConnectionStatus.OFFLINE
            assertEquals("Offline", status.displayName)
            assertNotNull(status.icon)
            assertTrue(status.description.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("Display names")
    inner class DisplayNamesTests {

        @Test
        fun `all display names are unique`() {
            val displayNames = ConnectionStatus.entries.map { it.displayName }
            assertEquals(displayNames.size, displayNames.distinct().size)
        }

        @Test
        fun `all display names are non-empty`() {
            ConnectionStatus.entries.forEach { status ->
                assertTrue(status.displayName.isNotEmpty())
            }
        }
    }

    @Nested
    @DisplayName("Descriptions")
    inner class DescriptionsTests {

        @Test
        fun `all descriptions are non-empty`() {
            ConnectionStatus.entries.forEach { status ->
                assertTrue(status.description.isNotEmpty())
            }
        }

        @Test
        fun `READY description mentions connected`() {
            assertTrue(ConnectionStatus.READY.description.lowercase().contains("connected"))
        }

        @Test
        fun `ERROR description mentions failed or error`() {
            val desc = ConnectionStatus.ERROR.description.lowercase()
            assertTrue(desc.contains("failed") || desc.contains("error"))
        }

        @Test
        fun `NOT_CONFIGURED description mentions not configured`() {
            assertTrue(ConnectionStatus.NOT_CONFIGURED.description.lowercase().contains("not configured"))
        }

        @Test
        fun `OFFLINE description mentions connection`() {
            val desc = ConnectionStatus.OFFLINE.description.lowercase()
            assertTrue(desc.contains("connection") || desc.contains("internet"))
        }
    }

    @Nested
    @DisplayName("Icons")
    inner class IconsTests {

        @Test
        fun `all icons are non-null`() {
            ConnectionStatus.entries.forEach { status ->
                assertNotNull(status.icon)
            }
        }
    }
}
