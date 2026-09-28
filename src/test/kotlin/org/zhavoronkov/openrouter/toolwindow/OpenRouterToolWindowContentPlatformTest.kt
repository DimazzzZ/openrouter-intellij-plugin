package org.zhavoronkov.openrouter.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.toolwindow.requests.RequestsNavigator
import org.zhavoronkov.openrouter.toolwindow.status.StatusTabPanel
import org.zhavoronkov.openrouter.toolwindow.status.StatusTabState

/**
 * Platform test for [StatusTabPanel].
 *
 * Extends [BasePlatformTestCase] so a real IntelliJ [com.intellij.openapi.project.Project]
 * is available for the panel's "Configure" button, which opens the settings dialog for
 * that project. The settings service is still mocked to drive the unconfigured state.
 */
class OpenRouterToolWindowContentPlatformTest : BasePlatformTestCase() {

    fun testUnconfiguredStateSetsLabels() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        val statusTab = StatusTabPanel(project, settingsService)
        try {
            assertEquals(StatusTabState.State.NOT_CONFIGURED, statusTab.getStateForTest())
        } finally {
            statusTab.dispose()
        }
    }

    /**
     * Regression test for the disposal leak found in Task 7's review, round 1.
     *
     * [StatusTabPanel] registers its shared-cache subscription with
     * `ApplicationManager.getApplication().messageBus.connect(this)`. That call makes the
     * resulting [com.intellij.util.messages.MessageBusConnection] a Disposer CHILD of the panel
     * ([com.intellij.util.messages.MessageBusConnection] extends [com.intellij.openapi.Disposable],
     * and `connect(parentDisposable)` registers it under that parent). A Disposer child is only
     * torn down when the Disposer actually walks the tree - i.e. when [Disposer.dispose] runs on
     * the panel (or an ancestor of it) - never by an ordinary Kotlin method call. Calling
     * `panel.dispose()` directly runs only that override's own body (cancelling the coroutine
     * scope here) and does not ask the Disposer to do anything, so the connection is left
     * subscribed forever: exactly the bug this test pins.
     *
     * [OpenRouterToolWindowContent] fixes this by calling `Disposer.register(this, statusTab)`
     * in its `init` (replacing a bare `statusTab.dispose()` call in its own `dispose()`), and is
     * itself registered as the tool window content's disposer by
     * `OpenRouterToolWindowFactory.createToolWindowContent`
     * (`content.setDisposer(toolWindowContent)`). Reproducing that exact three-level chain here
     * would additionally require constructing `ChatPanel` - a hard, non-injectable dependency of
     * `OpenRouterToolWindowContent` whose `init` reaches into
     * `settingsService.favoriteModelsManager` / `.presetsManager` deeply enough that a plain
     * unstubbed mock NPEs - which is unrelated scaffolding for a disposal fix. This test instead
     * proves the underlying mechanism directly against the real [StatusTabPanel] class, standing
     * a throwaway [com.intellij.openapi.Disposable] in for [OpenRouterToolWindowContent]: same
     * mechanism, minus the unrelated construction cost. The written trace above covers the
     * remaining hop from the factory down to `OpenRouterToolWindowContent`'s own `init`/`dispose`.
     */
    fun testDisposingOwnerDisconnectsStatsSubscriptionButBareDisposeDoesNot() {
        val settingsService = mock(OpenRouterSettingsService::class.java)
        `when`(settingsService.isConfigured()).thenReturn(false)
        `when`(settingsService.getProvisioningKey()).thenReturn("")

        // The bug, pinned: a bare method-call dispose() - what OpenRouterToolWindowContent.dispose()
        // used to do - does NOT disconnect the subscription.
        val bareDisposePanel = StatusTabPanel(project, settingsService)
        bareDisposePanel.dispose()
        assertFalse(
            "A bare dispose() method call must not disconnect a Disposer-parented connection " +
                "- that gap is exactly the leak this test exists to catch",
            bareDisposePanel.isStatsConnectionDisposedForTest()
        )

        // The fix, proven: registering the panel as a Disposer child of its owner, then disposing
        // that owner, tears the connection down.
        val owner = Disposer.newDisposable("fake-OpenRouterToolWindowContent")
        val registeredPanel = StatusTabPanel(project, settingsService)
        Disposer.register(owner, registeredPanel)
        try {
            assertFalse(
                "Subscription must still be live while the owner is alive",
                registeredPanel.isStatsConnectionDisposedForTest()
            )

            Disposer.dispose(owner)

            assertTrue(
                "Disposer.dispose(owner) must cascade through to the panel's message-bus connection",
                registeredPanel.isStatsConnectionDisposedForTest()
            )
        } finally {
            if (!Disposer.isDisposed(owner)) {
                Disposer.dispose(owner)
            }
        }
    }

    /**
     * A Consumer's warning is counted on the Requests tab until the user looks at it; a normal reply
     * is not, and neither is the chat's own, whose warning is already under the reply.
     */
    fun testAWarningIsCountedOnTheRequestsTabUntilItIsLookedAt() {
        val content = OpenRouterToolWindowContent(project)
        try {
            val tabs = content.getTabbedPaneForTest()
            val bus = ApplicationManager.getApplication().messageBus.syncPublisher(RequestLogListener.TOPIC)
            fun record(finishReason: String) = RequestRecord(
                startedAtMillis = 0,
                durationMillis = 1,
                source = RequestSource.PROXY,
                sender = "Junie",
                requestedModel = "m",
                reply = ReplyFacts(finishReason = finishReason)
            )

            bus.changed(record("length"))
            bus.changed(record("stop"))
            bus.changed(record("content_filter"))
            bus.changed(record("length").copy(source = RequestSource.CHAT, sender = "Chat"))
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals("Requests (2)", tabs.getTitleAt(1))

            tabs.selectedIndex = 1
            assertEquals("Requests", tabs.getTitleAt(1))

            tabs.selectedIndex = 0
            bus.changed(record("length"))
            bus.changed(null)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals("clearing the log clears the count", "Requests", tabs.getTitleAt(1))
        } finally {
            Disposer.dispose(content)
        }
    }

    fun testRevealingARequestOpensTheRequestsTab() {
        val content = OpenRouterToolWindowContent(project)
        try {
            val record = RequestRecord(0, 1, RequestSource.PROXY, "Junie", "m")

            project.messageBus.syncPublisher(RequestsNavigator.TOPIC).reveal(record)

            assertEquals(1, content.getTabbedPaneForTest().selectedIndex)
        } finally {
            Disposer.dispose(content)
        }
    }

    fun testTheTabsAreChatThenRequestsThenStatus() {
        val content = OpenRouterToolWindowContent(project)
        try {
            val tabbedPane = content.getTabbedPaneForTest()
            val titles = (0 until tabbedPane.tabCount).map { tabbedPane.getTitleAt(it) }

            assertEquals(listOf("Chat", "Requests", "Status"), titles)
            assertEquals("the chat stays the tab the tool window opens on", 0, tabbedPane.selectedIndex)
        } finally {
            Disposer.dispose(content)
        }
    }

    /**
     * Fix round 1, finding 3: the `ChangeListener` wiring `onActivated()` to tab selection had
     * zero coverage - deleting it, or inverting its `selectedComponent === statusTab.component`
     * check so it fires for Chat instead, both left every other test passing.
     *
     * Constructed with the REAL default settings/[org.zhavoronkov.openrouter.services.OpenRouterService]
     * (no `mock(...)`): [ChatPanel][org.zhavoronkov.openrouter.toolwindow.ChatPanel]'s own `init`
     * reaches deep enough into `settingsService.favoriteModelsManager`/`.presetsManager` that an
     * unstubbed mock NPEs (see [testDisposingOwnerDisconnectsStatsSubscriptionButBareDisposeDoesNot]'s
     * own KDoc on why this class was never constructed directly before) - and the real services are
     * what production actually wires in via `OpenRouterToolWindowFactory`.
     *
     * The observable effect is [StatusTabPanel.onActivated]'s own call counter: a fresh count of
     * `0` proves nothing has consumed the gate yet; a direct `onActivated()` call afterward would
     * therefore return `true` (a fresh gate) if the listener had NOT already fired for Status, or
     * increments the count on its own regardless of the gate's state, so - unlike the gate's plain
     * boolean, which can only say "consumed at least once" - it can tell "selecting Status called
     * it exactly once" apart from "selecting Chat afterward called it again", which is exactly the
     * inverted-check bug this test exists to catch.
     */
    fun testSelectingTheStatusTabCallsOnActivatedButSelectingChatDoesNot() {
        val content = OpenRouterToolWindowContent(project)
        val statusTab = content.getStatusTabForTest()
        try {
            val tabbedPane = content.getTabbedPaneForTest()

            assertEquals(
                "construction alone (still on the default Chat tab) must not call onActivated()",
                0,
                statusTab.getOnActivatedCallCountForTest()
            )

            tabbedPane.selectedIndex = 2 // Status
            assertEquals(
                "selecting the Status tab must call onActivated() through the ChangeListener",
                1,
                statusTab.getOnActivatedCallCountForTest()
            )

            tabbedPane.selectedIndex = 0 // Chat
            assertEquals(
                "selecting the Chat tab must NOT call onActivated() again - the listener must " +
                    "check WHICH tab is now selected, not fire unconditionally",
                1,
                statusTab.getOnActivatedCallCountForTest()
            )
        } finally {
            Disposer.dispose(content)
        }

        // Close-out round 3, Important E: this must run OUTSIDE the `finally` above - a failure
        // here would otherwise replace whatever the try block itself threw, masking the real
        // cause. Closes the previously-parked ruling that this production wiring
        // (`Disposer.register(this, statusTab)` in OpenRouterToolWindowContent's init) was
        // unprovable without a real ChatPanel: this test builds exactly that, real services and
        // all, so disposing the real `content` above must cascade all the way down to statusTab's
        // own message-bus connection.
        assertTrue(
            "disposing the real OpenRouterToolWindowContent must cascade through to " +
                "statusTab's own Disposer-parented message-bus connection",
            statusTab.isStatsConnectionDisposedForTest()
        )
    }
}
