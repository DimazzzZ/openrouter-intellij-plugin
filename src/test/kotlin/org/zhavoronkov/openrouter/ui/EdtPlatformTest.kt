package org.zhavoronkov.openrouter.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The plugin's own UI - the Requests tab, its warning balloons, the chat - is updated through
 * [Edt.later]. A modal dialog open in front of it, Settings say, must not hold those updates back
 * until it closes.
 */
class EdtPlatformTest : BasePlatformTestCase() {

    private companion object {
        const val POST_TIMEOUT_SECONDS = 10L
    }

    fun testAnUpdatePostedFromAnotherThreadRunsOnTheEdt() {
        val onEdt = AtomicBoolean()

        postFromPooledThread { onEdt.set(ApplicationManager.getApplication().isDispatchThread) }

        PlatformTestUtil.waitWithEventsDispatching(
            "the update never ran",
            { onEdt.get() },
            POST_TIMEOUT_SECONDS.toInt()
        )
    }

    fun testAnUpdateRunsWhileAModalDialogIsOpen() {
        val modal = Any()
        LaterInvocator.enterModal(modal)
        try {
            val ours = AtomicBoolean()
            val plain = AtomicBoolean()

            postFromPooledThread { ours.set(true) }
            ApplicationManager.getApplication().executeOnPooledThread {
                ApplicationManager.getApplication().invokeLater { plain.set(true) }
            }.get(POST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            assertTrue("Edt.later was held back by the modal dialog", ours.get())
            assertFalse("a plain invokeLater should wait for the dialog, or this test proves nothing", plain.get())
        } finally {
            LaterInvocator.leaveModal(modal)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun postFromPooledThread(action: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread { Edt.later(action) }
            .get(POST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
}
