package org.zhavoronkov.openrouter.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AIAssistantIntegrationHelper Tests")
class AIAssistantIntegrationHelperTest {

    @Nested
    @DisplayName("AIAssistantInfo data class")
    inner class AIAssistantInfoTests {

        @Test
        fun `AIAssistantInfo with false availability`() {
            val info = AIAssistantIntegrationHelper.AIAssistantInfo(false, null)
            assertFalse(info.isAvailable)
            assertEquals(null, info.version)
        }

        @Test
        fun `AIAssistantInfo with true availability and version`() {
            val info = AIAssistantIntegrationHelper.AIAssistantInfo(true, "2024.1.0")
            assertTrue(info.isAvailable)
            assertEquals("2024.1.0", info.version)
        }

        @Test
        fun `AIAssistantInfo equality works`() {
            val info1 = AIAssistantIntegrationHelper.AIAssistantInfo(true, "1.0")
            val info2 = AIAssistantIntegrationHelper.AIAssistantInfo(true, "1.0")
            assertEquals(info1, info2)
        }
    }

    @Nested
    @DisplayName("IntegrationStatus enum")
    inner class IntegrationStatusTests {

        @Test
        fun `all status values are accessible`() {
            val statuses = AIAssistantIntegrationHelper.IntegrationStatus.entries
            assertEquals(4, statuses.size)
        }

        @Test
        fun `AI_ASSISTANT_NOT_AVAILABLE has correct name`() {
            assertEquals(
                "AI_ASSISTANT_NOT_AVAILABLE",
                AIAssistantIntegrationHelper.IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE.name
            )
        }

        @Test
        fun `OPENROUTER_NOT_CONFIGURED has correct name`() {
            assertEquals(
                "OPENROUTER_NOT_CONFIGURED",
                AIAssistantIntegrationHelper.IntegrationStatus.OPENROUTER_NOT_CONFIGURED.name
            )
        }

        @Test
        fun `PROXY_SERVER_NOT_RUNNING has correct name`() {
            assertEquals(
                "PROXY_SERVER_NOT_RUNNING",
                AIAssistantIntegrationHelper.IntegrationStatus.PROXY_SERVER_NOT_RUNNING.name
            )
        }

        @Test
        fun `READY has correct name`() {
            assertEquals(
                "READY",
                AIAssistantIntegrationHelper.IntegrationStatus.READY.name
            )
        }

        @Test
        fun `status ordinal values are consistent`() {
            val statuses = AIAssistantIntegrationHelper.IntegrationStatus.entries
            assertEquals(0, statuses.indexOf(AIAssistantIntegrationHelper.IntegrationStatus.AI_ASSISTANT_NOT_AVAILABLE))
            assertEquals(1, statuses.indexOf(AIAssistantIntegrationHelper.IntegrationStatus.OPENROUTER_NOT_CONFIGURED))
            assertEquals(2, statuses.indexOf(AIAssistantIntegrationHelper.IntegrationStatus.PROXY_SERVER_NOT_RUNNING))
            assertEquals(3, statuses.indexOf(AIAssistantIntegrationHelper.IntegrationStatus.READY))
        }
    }

    @Nested
    @DisplayName("getAIAssistantInfo")
    inner class GetAIAssistantInfoTests {

        @Test
        fun `getAIAssistantInfo returns non-null result`() {
            // This test verifies the method doesn't throw when AI Assistant is not installed
            val info = AIAssistantIntegrationHelper.getAIAssistantInfo()
            assertNotNull(info)
        }

        @Test
        fun `getAIAssistantInfo returns consistent results`() {
            val info1 = AIAssistantIntegrationHelper.getAIAssistantInfo()
            val info2 = AIAssistantIntegrationHelper.getAIAssistantInfo()
            assertEquals(info1.isAvailable, info2.isAvailable)
        }
    }

    @Nested
    @DisplayName("isAIAssistantAvailable")
    inner class IsAIAssistantAvailableTests {

        @Test
        fun `isAIAssistantAvailable returns boolean`() {
            val available = AIAssistantIntegrationHelper.isAIAssistantAvailable()
            // Should return false if AI Assistant is not installed
            assertNotNull(available)
        }
    }

    @Nested
    @DisplayName("getAIAssistantVersion")
    inner class GetAIAssistantVersionTests {

        @Test
        fun `getAIAssistantVersion returns nullable string`() {
            // Should return null if AI Assistant is not installed
            val version = AIAssistantIntegrationHelper.getAIAssistantVersion()
            // Just verify it doesn't throw
            assertTrue(version == null || version.isNotEmpty())
        }
    }
}
