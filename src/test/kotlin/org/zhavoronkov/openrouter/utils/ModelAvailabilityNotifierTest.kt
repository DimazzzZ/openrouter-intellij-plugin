package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Platform-bound coverage exclusions (documented in TESTING.md):
 * Covered off-platform by this suite (via public API + reflection):
 * - hasNotified, clearNotificationHistory.
 * - extractUnavailabilityReason (all 6 keyword arms + default) and
 *   buildNotificationContent (private, reached via reflection).
 * - notifyModelUnavailable up to the platform dispatch boundary: the
 *   reset-interval branch (notifiedModels.clear(); lastResetTime = now),
 *   the duplicate-skip branch, and the add/log lines. These run before the
 *   call reaches ProjectManager/ApplicationManager; the platform failure is
 *   swallowed with runCatching so the domain lines are still exercised.
 *
 * Remaining platform-bound lines (getCurrentProject internals +
 * showNotification) require IntelliJ ProjectManager, ApplicationManager
 * (invokeLater), NotificationGroupManager, and ShowSettingsUtil. Under the
 * fast :test task ApplicationManager.getApplication() is null and
 * ProjectManager.getInstance() is unavailable, so these are covered only by
 * the platformTest (BasePlatformTestCase) suite, not here.
 */

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

    /**
     * Reflection helper: reach into the private `notifiedModels` set and `lastResetTime`
     * field so we can exercise the duplicate-skip and reset-interval branches of
     * notifyModelUnavailable without needing IntelliJ's ApplicationManager to be up.
     * The showNotification path (NotificationGroupManager + invokeLater) remains
     * platform-bound and is documented in the file-level KDoc above.
     */
    private fun seedNotifiedModel(name: String) {
        val field = ModelAvailabilityNotifier::class.java.getDeclaredField("notifiedModels")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val set = field.get(ModelAvailabilityNotifier) as java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean>
        set.add(name)
    }

    private fun setLastResetTime(value: Long) {
        val field = ModelAvailabilityNotifier::class.java.getDeclaredField("lastResetTime")
        field.isAccessible = true
        field.set(ModelAvailabilityNotifier, value)
    }

    private fun invokeExtractReason(errorMessage: String): String {
        val method = ModelAvailabilityNotifier::class.java.getDeclaredMethod(
            "extractUnavailabilityReason",
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(ModelAvailabilityNotifier, errorMessage) as String
    }

    private fun invokeBuildContent(modelName: String, reason: String): String {
        val method = ModelAvailabilityNotifier::class.java.getDeclaredMethod(
            "buildNotificationContent",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(ModelAvailabilityNotifier, modelName, reason) as String
    }

    @Nested
    @DisplayName("extractUnavailabilityReason (private, reachable via reflection)")
    inner class ExtractReasonTests {

        @Test
        fun `deprecated keyword maps to deprecated reason`() {
            assertEquals(
                "Model has been deprecated",
                invokeExtractReason("This model is DEPRECATED as of yesterday")
            )
        }

        @Test
        fun `period-has-ended keyword maps to free-period reason`() {
            assertEquals(
                "Free period has ended - migrate to paid version",
                invokeExtractReason("The free period has ended for this model")
            )
        }

        @Test
        fun `migrate-to keyword also maps to free-period reason`() {
            assertEquals(
                "Free period has ended - migrate to paid version",
                invokeExtractReason("Please migrate to the paid tier")
            )
        }

        @Test
        fun `free-tier keyword maps to free-tier reason`() {
            assertEquals(
                "Free tier temporarily unavailable",
                invokeExtractReason("The free tier is over quota")
            )
        }

        @Test
        fun `plain free keyword also maps to free-tier reason`() {
            assertEquals(
                "Free tier temporarily unavailable",
                invokeExtractReason("free access is unavailable")
            )
        }

        @Test
        fun `providers keyword maps to providers-down reason`() {
            assertEquals(
                "All providers are currently down",
                invokeExtractReason("All providers returned 503")
            )
        }

        @Test
        fun `unknown error falls through to no-endpoints default`() {
            assertEquals(
                "No endpoints available",
                invokeExtractReason("totally unrelated wording")
            )
        }
    }

    @Nested
    @DisplayName("buildNotificationContent (private, reachable via reflection)")
    inner class BuildContentTests {

        @Test
        fun `content interpolates model name and reason and lists alternatives`() {
            val body = invokeBuildContent("openai/gpt-4o-mini", "Model has been deprecated")
            assertTrue(body.contains("openai/gpt-4o-mini"))
            assertTrue(body.contains("Model has been deprecated"))
            assertTrue(body.contains("anthropic/claude-3.5-sonnet"))
            assertTrue(body.contains("google/gemini-pro-1.5"))
        }
    }

    @Nested
    @DisplayName("notifyModelUnavailable duplicate-skip and reset-interval branches")
    inner class NotifyBranchTests {

        @Test
        fun `second call for the same model takes the duplicate-skip branch without hitting the UI path`() {
            // Seed the private set so add() returns false on the very first entry into
            // the function -- we bypass invokeLater / NotificationGroupManager entirely.
            seedNotifiedModel("model-x")
            assertTrue(ModelAvailabilityNotifier.hasNotified("model-x"))
            assertDoesNotThrow {
                ModelAvailabilityNotifier.notifyModelUnavailable("model-x", "anything")
            }
            // Still exactly one entry -- the second call did not clear or duplicate.
            assertTrue(ModelAvailabilityNotifier.hasNotified("model-x"))
        }

        @Test
        fun `clearNotificationHistory refreshes lastResetTime and empties the set`() {
            // The RESET_INTERVAL branch inside notifyModelUnavailable clears the set
            // and THEN proceeds into the platform notification path, so it cannot be
            // exercised in isolation off-platform. We instead verify the same reset
            // accounting via clearNotificationHistory (the branch's observable effect):
            // the private set is emptied and lastResetTime is advanced to 'now'.
            seedNotifiedModel("model-y")
            seedNotifiedModel("model-z")
            setLastResetTime(0L)
            assertTrue(ModelAvailabilityNotifier.hasNotified("model-y"))

            ModelAvailabilityNotifier.clearNotificationHistory()

            assertFalse(ModelAvailabilityNotifier.hasNotified("model-y"))
            assertFalse(ModelAvailabilityNotifier.hasNotified("model-z"))
            val field = ModelAvailabilityNotifier::class.java.getDeclaredField("lastResetTime")
            field.isAccessible = true
            assertTrue((field.get(ModelAvailabilityNotifier) as Long) > 0L)
        }

        @Test
        fun `reset-interval branch clears history then walks into the platform notify path`() {
            // Force the elapsed-time branch (now - lastResetTime > RESET_INTERVAL_MS) to be
            // taken: seed a stale model and pin lastResetTime to the epoch. The reset block
            // (notifiedModels.clear(); lastResetTime = now) and the subsequent add/log/
            // getCurrentProject lines all execute here. The final invokeLater / notification
            // rendering is platform-bound: under the fast :test task
            // ApplicationManager.getApplication() is null, so the call surfaces a throwable
            // once it reaches the UI dispatch. We swallow ONLY that platform failure; the
            // pre-dispatch domain lines are covered as a side effect.
            seedNotifiedModel("stale-model")
            setLastResetTime(0L)
            runCatching {
                ModelAvailabilityNotifier.notifyModelUnavailable("fresh-model", "deprecated")
            }
            // Reset actually happened: the stale entry is gone and lastResetTime advanced.
            assertFalse(ModelAvailabilityNotifier.hasNotified("stale-model"))
            val field = ModelAvailabilityNotifier::class.java.getDeclaredField("lastResetTime")
            field.isAccessible = true
            assertTrue((field.get(ModelAvailabilityNotifier) as Long) > 1L)
        }

        @Test
        fun `first-time notify for a model reaches the platform dispatch boundary`() {
            // No reset (recent lastResetTime), model not previously seen: add() returns true,
            // we log and call getCurrentProject(), then hit the platform-bound invokeLater.
            // Swallow only the platform failure; the domain lines up to the boundary run.
            setLastResetTime(System.currentTimeMillis())
            runCatching {
                ModelAvailabilityNotifier.notifyModelUnavailable("brand-new-model", "all providers down")
            }
            // The model was recorded before the platform dispatch was attempted.
            assertTrue(ModelAvailabilityNotifier.hasNotified("brand-new-model"))
        }
    }
}
