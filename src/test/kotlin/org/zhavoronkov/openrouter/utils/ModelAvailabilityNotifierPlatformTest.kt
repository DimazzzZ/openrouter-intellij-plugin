package org.zhavoronkov.openrouter.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The balloon a model that stopped answering raises: once per model, with the reason read from
 * OpenRouter's error, and the two ways out. The actions themselves open a browser and the
 * Settings dialog, so only their presence is checked.
 */
class ModelAvailabilityNotifierPlatformTest : BasePlatformTestCase() {

    private val shown = mutableListOf<Notification>()

    override fun setUp() {
        super.setUp()
        ModelAvailabilityNotifier.clearNotificationHistory()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    shown += notification
                }
            }
        )
    }

    override fun tearDown() {
        try {
            shown.forEach(Notification::expire)
            ModelAvailabilityNotifier.clearNotificationHistory()
        } finally {
            super.tearDown()
        }
    }

    private fun notify(model: String, error: String): Notification? {
        ModelAvailabilityNotifier.notifyModelUnavailable(model, error)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return shown.lastOrNull()
    }

    fun testAnUnavailableModelRaisesAWarningWithTheReasonAndTheWaysOut() {
        val notification = notify("x/model", "No endpoints found for x/model.")!!

        assertEquals("Model Unavailable: x/model", notification.title)
        assertEquals(NotificationType.WARNING, notification.type)
        assertTrue(notification.content, notification.content.contains("No endpoints available"))
        assertEquals(listOf("View Available Models", "Open Settings"), notification.actions.map { it.templateText })
    }

    fun testTheSameModelIsNotAnnouncedTwice() {
        notify("x/model", "No endpoints found")
        notify("x/model", "No endpoints found")

        assertEquals(1, shown.size)
    }

    fun testTheReasonIsReadFromTheError() {
        val reasons = listOf(
            "This model is deprecated" to "Model has been deprecated",
            "The free period has ended" to "Free period has ended",
            "Please migrate to the paid slug" to "Free period has ended",
            "free tier is busy" to "Free tier temporarily unavailable",
            "All providers failed" to "All providers are currently down"
        )

        reasons.forEachIndexed { index, (error, reason) ->
            val content = notify("model/$index", error)!!.content
            assertTrue("'$error' should read as '$reason': $content", content.contains(reason))
        }
    }
}
