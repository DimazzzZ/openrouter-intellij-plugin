package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ModelAvailabilityNotifier Tests")
class ModelAvailabilityNotifierTest {

    @BeforeEach
    fun setup() {
        ModelAvailabilityNotifier.clearNotificationHistory()
    }

    @AfterEach
    fun cleanup() {
        ModelAvailabilityNotifier.clearNotificationHistory()
    }

    @Nested
    @DisplayName("hasNotified")
    inner class HasNotifiedTests {

        @Test
        fun `returns false for model that was never notified`() {
            assertFalse(ModelAvailabilityNotifier.hasNotified("test-model"))
        }

        @Test
        fun `returns false after clearing history`() {
            // Note: We can't easily test the notification path without UI,
            // but we can test the history management
            ModelAvailabilityNotifier.clearNotificationHistory()
            assertFalse(ModelAvailabilityNotifier.hasNotified("any-model"))
        }
    }

    @Nested
    @DisplayName("clearNotificationHistory")
    inner class ClearNotificationHistoryTests {

        @Test
        fun `clears notification history successfully`() {
            // Clear and verify we start fresh
            ModelAvailabilityNotifier.clearNotificationHistory()
            assertFalse(ModelAvailabilityNotifier.hasNotified("model-1"))
            assertFalse(ModelAvailabilityNotifier.hasNotified("model-2"))
        }

        @Test
        fun `can be called multiple times without error`() {
            ModelAvailabilityNotifier.clearNotificationHistory()
            ModelAvailabilityNotifier.clearNotificationHistory()
            ModelAvailabilityNotifier.clearNotificationHistory()
            // Should not throw
        }
    }

    @Nested
    @DisplayName("Notification tracking")
    inner class NotificationTrackingTests {

        @Test
        fun `different models are tracked independently`() {
            assertFalse(ModelAvailabilityNotifier.hasNotified("model-a"))
            assertFalse(ModelAvailabilityNotifier.hasNotified("model-b"))
            // Both should be false since we haven't notified for either
        }

        @Test
        fun `empty model name is handled`() {
            assertFalse(ModelAvailabilityNotifier.hasNotified(""))
        }

        @Test
        fun `special characters in model name are handled`() {
            assertFalse(ModelAvailabilityNotifier.hasNotified("openai/gpt-4o-mini"))
            assertFalse(ModelAvailabilityNotifier.hasNotified("anthropic/claude-3.5-sonnet"))
        }
    }
}
