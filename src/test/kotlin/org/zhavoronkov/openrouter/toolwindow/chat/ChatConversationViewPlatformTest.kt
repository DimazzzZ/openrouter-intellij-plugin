package org.zhavoronkov.openrouter.toolwindow.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane

private const val LOADING_LABEL_NAME = "loadingLabel"

/**
 * Regression test for the loading-indicator removal bug.
 *
 * `hideLoading()` used to be an intentional no-op — only `showError()`
 * actually removed the "loadingLabel" component — so a stray grey "..."
 * row survived above every successful reply until the next `openChat()`
 * cleared the whole panel (see the Task 3 report). This proves
 * `hideLoading()` now actually removes it, specifically on the success
 * path: showLoading() -> hideLoading() -> addMessage(...).
 */
class ChatConversationViewPlatformTest : BasePlatformTestCase() {

    private lateinit var view: ChatConversationView

    override fun setUp() {
        super.setUp()
        view = ChatConversationView()
    }

    private fun messagesPanel(): MessagesPanel {
        val scrollPane = view.component as JBScrollPane
        return scrollPane.viewport.view as MessagesPanel
    }

    private fun hasLoadingLabel(): Boolean =
        messagesPanel().components.any { it.name == LOADING_LABEL_NAME }

    fun testShowLoadingAddsLoadingLabel() {
        view.showLoading()

        assertTrue("loadingLabel should be present after showLoading()", hasLoadingLabel())
    }

    fun testHideLoadingRemovesLoadingLabel() {
        view.showLoading()
        view.hideLoading()

        assertFalse("loadingLabel should be gone after hideLoading()", hasLoadingLabel())
    }

    fun testHideLoadingCalledTwiceIsHarmless() {
        view.showLoading()
        view.hideLoading()
        view.hideLoading()

        assertFalse(hasLoadingLabel())
    }

    fun testHideLoadingWithNoLoadingInProgressIsHarmless() {
        view.hideLoading()

        assertFalse(hasLoadingLabel())
    }

    /**
     * The case that actually broke in production: on the real success path
     * (setLoading(true) -> ... -> setLoading(false) -> addAssistantMessage),
     * no loading row must be left sitting above the rendered reply.
     */
    fun testSuccessPathLeavesNoLoadingLabelAboveMessage() {
        view.showLoading()
        view.hideLoading()
        view.addMessage("Hello", isUser = false)

        assertFalse(
            "no loadingLabel should remain once the reply is added",
            hasLoadingLabel()
        )
        assertEquals(
            "exactly one message component should be present",
            1,
            messagesPanel().componentCount
        )
    }
}
