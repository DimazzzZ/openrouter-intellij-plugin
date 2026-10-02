package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.requests.ReplyFacts
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.RequestSource
import org.zhavoronkov.openrouter.requests.WarningBurst

/**
 * The balloon a Consumer's failed request raises: its words, that later ones in a burst fold into
 * it instead of raising their own, and that the setting silences it.
 */
class RequestWarningBalloonsPlatformTest : BasePlatformTestCase() {

    private var now = 0L
    private var enabled = true
    private var balloonStillShown = false
    private val raised = mutableListOf<Notification>()

    private val balloons = RequestWarningBalloons(
        burst = WarningBurst(clock = { now }, windowMillis = 60_000),
        enabled = { enabled },
        notify = { raised += it },
        onScreen = { balloonStillShown },
        onEdt = Runnable::run
    )

    private fun record(
        finishReason: String? = "length",
        error: String? = null,
        sender: String = "Junie",
        source: RequestSource = RequestSource.PROXY
    ) = RequestRecord(now, 5, source, sender, "openai/gpt-4o", ReplyFacts(finishReason = finishReason), error)

    fun testAReplyCutOffRaisesAWarningNamingTheConsumerAndTheReason() {
        balloons.onRecord(record())

        val balloon = raised.single()
        assertEquals("Junie · openai/gpt-4o", balloon.title)
        assertEquals("Cut off at the token limit", balloon.content)
        assertEquals(NotificationType.WARNING, balloon.type)
        assertEquals(listOf("Show"), balloon.actions.map { it.templateText })
    }

    /** A balloon still on screen is replaced by one that counts, and the old one expires. */
    fun testAFoldReplacesABalloonStillOnScreen() {
        balloons.onRecord(record())
        balloonStillShown = true
        now = 10_000
        balloons.onRecord(record(error = "Provider overloaded", sender = "Cline"))

        val (first, second) = raised
        assertTrue("the first balloon goes", first.isExpired)
        assertEquals(RequestWarningBalloons.GROUP_ID, second.groupId)
        assertEquals("the balloon names the latest", "Cline · openai/gpt-4o", second.title)
        assertEquals("Provider overloaded - and 1 more request went wrong", second.content)
    }

    /** A balloon already gone raises no new one: only the Notifications entry is brought up to date. */
    fun testAFoldAfterTheBalloonWentRaisesNoNewBalloon() {
        balloons.onRecord(record())
        now = 10_000
        balloons.onRecord(record())
        now = 20_000
        balloons.onRecord(record())

        assertEquals(
            listOf(
                RequestWarningBalloons.GROUP_ID,
                RequestWarningBalloons.LOG_ONLY_GROUP_ID,
                RequestWarningBalloons.LOG_ONLY_GROUP_ID
            ),
            raised.map { it.groupId }
        )
        assertEquals(listOf(true, true, false), raised.map { it.isExpired })
        assertEquals("Cut off at the token limit - and 2 more requests went wrong", raised.last().content)
    }

    /** The sender is whatever a User-Agent said and the reason an upstream's message; neither is markup. */
    fun testTheTextIsEscaped() {
        balloons.onRecord(record(error = "<b>bad</b> & worse", sender = "<script>"))

        val balloon = raised.single()
        assertEquals("&lt;script&gt; · openai/gpt-4o", balloon.title)
        assertEquals("&lt;b&gt;bad&lt;/b&gt; &amp; worse", balloon.content)
    }

    fun testAWarningAfterTheBurstRaisesANewBalloon() {
        balloons.onRecord(record())
        now = 60_000
        balloons.onRecord(record())

        assertEquals(2, raised.size)
    }

    fun testANormalStopAndTheChatRaiseNothing() {
        balloons.onRecord(record(finishReason = "stop"))
        balloons.onRecord(record(sender = "Chat", source = RequestSource.CHAT))

        assertTrue(raised.isEmpty())
    }

    fun testTurningBalloonsOffSilencesThem() {
        enabled = false

        balloons.onRecord(record())

        assertTrue(raised.isEmpty())
    }

    /** A request the plugin refused offers the page that fixes it, beside Show. */
    fun testARefusalOffersThePageThatFixesIt() {
        val refused = record(error = "OpenRouter plugin: 'x/y' is not in OpenRouter's model catalogue")
        balloons.onRecord(refused.copy(fixAt = FixPage.PRESETS))

        assertEquals(listOf("Open Presets", "Show"), raised.single().actions.map { it.templateText })
    }

    fun testAWarningThePluginDidNotRaiseOffersOnlyShow() {
        balloons.onRecord(record(error = "Provider overloaded"))

        assertEquals(listOf("Show"), raised.single().actions.map { it.templateText })
    }

    /** Refusals in a burst fold like any other warning, and the balloon offers the latest one's page. */
    fun testRefusalsAreAnnouncedOncePerBurst() {
        balloons.onRecord(record(error = "refused").copy(fixAt = FixPage.PRESETS))
        now = 5_000
        balloons.onRecord(record(error = "refused again").copy(fixAt = FixPage.OUTPUT_SCHEMAS))

        assertEquals("the second folds into the first, replacing it only in the log", 2, raised.size)
        assertEquals(RequestWarningBalloons.LOG_ONLY_GROUP_ID, raised.last().groupId)
        assertEquals(listOf("Open Output Schemas", "Show"), raised.last().actions.map { it.templateText })
    }

    /** A later warning of another kind folds in, and the burst still offers the refusal's fix. */
    fun testAWarningFoldingIntoARefusalKeepsTheFix() {
        balloons.onRecord(record(error = "refused").copy(fixAt = FixPage.OUTPUT_SCHEMAS))
        now = 5_000
        balloons.onRecord(record(finishReason = "length"))

        assertEquals(listOf("Open Output Schemas", "Show"), raised.last().actions.map { it.templateText })
    }

    /** The light test project has no OpenRouter tool window to open, so Show only closes its balloon. */
    fun testShowClosesTheBalloon() {
        balloons.onRecord(record())
        val balloon = raised.single()
        val show = balloon.actions.single { it.templateText == "Show" }

        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(Notification.KEY, balloon)
            .build()

        Notification.fire(balloon, show, context)

        assertTrue(balloon.isExpired)
    }

    private fun context(withProject: Boolean, balloon: Notification) = SimpleDataContext.builder()
        .apply { if (withProject) add(CommonDataKeys.PROJECT, project) }
        .add(Notification.KEY, balloon)
        .build()

    /** Show opens the OpenRouter tool window and asks it for the request the balloon is about. */
    fun testShowOpensTheRequestsTabAtTheRequest() {
        val revealed = mutableListOf<RequestRecord>()
        project.messageBus.connect(testRootDisposable)
            .subscribe(RequestsNavigator.TOPIC, RequestsNavigator { revealed += it })
        val windows = ToolWindowManager.getInstance(project)
        windows.registerToolWindow(RegisterToolWindowTask(id = "OpenRouter"))
        try {
            val cutOff = record()
            balloons.onRecord(cutOff)
            val balloon = raised.single()

            Notification.fire(balloon, balloon.actions.single { it.templateText == "Show" }, context(true, balloon))

            assertEquals(listOf(cutOff), revealed)
            assertTrue(balloon.isExpired)
        } finally {
            windows.unregisterToolWindow("OpenRouter")
        }
    }

    /** From the welcome screen there is no project to open the tab in; Show only closes the balloon. */
    fun testShowWithoutAProjectOnlyClosesTheBalloon() {
        balloons.onRecord(record())
        val balloon = raised.single()

        Notification.fire(balloon, balloon.actions.single { it.templateText == "Show" }, context(false, balloon))

        assertTrue(balloon.isExpired)
    }

    /** The platform's own check: a notification whose balloon never showed is gone, so a fold only logs. */
    fun testANotificationWhoseBalloonNeverShowedCountsAsGone() {
        val platformChecked = RequestWarningBalloons(
            burst = WarningBurst(clock = { now }, windowMillis = 60_000),
            enabled = { true },
            notify = { raised += it },
            onEdt = Runnable::run
        )
        platformChecked.onRecord(record())
        now = 10_000
        platformChecked.onRecord(record())

        assertEquals(
            listOf(RequestWarningBalloons.GROUP_ID, RequestWarningBalloons.LOG_ONLY_GROUP_ID),
            raised.map { it.groupId }
        )
    }

    /** Once the plugin let go of its balloon, a warning folding into the burst has nothing to replace. */
    fun testAFoldAfterThePluginLetGoOfItsBalloonOnlyLogs() {
        balloons.onRecord(record())
        balloons.dispose()
        balloonStillShown = true
        now = 10_000

        balloons.onRecord(record())

        assertEquals(RequestWarningBalloons.LOG_ONLY_GROUP_ID, raised.last().groupId)
    }

    fun testLettingGoWithNoBalloonRaisedIsHarmless() {
        balloons.dispose()

        assertTrue(raised.isEmpty())
    }

    fun testABalloonDoesNotOutliveThePlugin() {
        balloons.onRecord(record())

        balloons.dispose()

        assertTrue(raised.single().isExpired)
    }
}
