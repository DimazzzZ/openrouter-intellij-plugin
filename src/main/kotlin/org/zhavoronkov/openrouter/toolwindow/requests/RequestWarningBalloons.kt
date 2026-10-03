package org.zhavoronkov.openrouter.toolwindow.requests

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.zhavoronkov.openrouter.models.FixPage
import org.zhavoronkov.openrouter.requests.RequestLogListener
import org.zhavoronkov.openrouter.requests.RequestRecord
import org.zhavoronkov.openrouter.requests.WarningAnnouncement
import org.zhavoronkov.openrouter.requests.WarningBurst
import org.zhavoronkov.openrouter.services.OpenRouterSettingsService
import org.zhavoronkov.openrouter.ui.Edt

/** Asks a project's tool window to open the Requests tab at one request. */
fun interface RequestsNavigator {
    /** Shows [record] on the Requests tab, selected. */
    fun reveal(record: RequestRecord)

    companion object {
        /** Where a project's tool window hears which request to show. */
        val TOPIC: Topic<RequestsNavigator> = Topic.create("OpenRouter reveal a request", RequestsNavigator::class.java)

        /** Opens the OpenRouter tool window in [project] and shows [record] on its Requests tab. */
        fun reveal(project: Project, record: RequestRecord) {
            val window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
            window.activate { project.messageBus.syncPublisher(TOPIC).reveal(record) }
        }

        private const val TOOL_WINDOW_ID = "OpenRouter"
    }
}

/**
 * Tells the user when a Consumer's request failed or its reply stopped early, once per burst.
 *
 * The first warning of a [WarningBurst] raises a balloon. A later one in the same burst never
 * raises another: the platform cannot change a notification's text once shown, so the burst's
 * notification is replaced by one that counts them - as a balloon while the old balloon is still
 * [onScreen], where it reads as the same balloon updating, and otherwise only as an entry in the
 * Notifications tool window, through a group that shows no balloon. "Show" opens the Requests tab
 * at the latest of them. [enabled] off silences every balloon; the Requests tab counts the warnings
 * regardless.
 */
@Service(Service.Level.APP)
class RequestWarningBalloons(
    private val burst: WarningBurst = WarningBurst(),
    private val enabled: () -> Boolean = {
        OpenRouterSettingsService.getInstance().uiPreferencesManager.requestWarningBalloons
    },
    private val notify: (Notification) -> Unit = { Notifications.Bus.notify(it) },
    private val onScreen: (Notification) -> Boolean = { it.balloon?.isDisposed == false },
    private val onEdt: (Runnable) -> Unit = Edt::later,
    /** Opens the settings page that fixes a refusal. */
    private val openFixPage: (Project?, FixPage) -> Unit = FixPageSettings::open
) : Disposable {
    private var current: Notification? = null

    /** The page that fixes the latest refusal in the current burst, kept when a later warning folds in. */
    private var burstFix: FixPage? = null

    /** Raises or updates the balloon for [record] if it went wrong, unless balloons are turned off. */
    fun onRecord(record: RequestRecord) {
        if (!enabled()) return
        val announcement = burst.onRecord(record) ?: return
        onEdt { announce(announcement) }
    }

    private fun announce(announcement: WarningAnnouncement) {
        val latestFix = announcement.latest.fixAt
        burstFix = if (announcement is WarningAnnouncement.Raise) latestFix else latestFix ?: burstFix
        val previous = current
        val group = when {
            announcement is WarningAnnouncement.Raise -> GROUP_ID
            previous != null && onScreen(previous) -> GROUP_ID
            else -> LOG_ONLY_GROUP_ID
        }
        previous?.expire()
        current = notification(group, announcement).also(notify)
    }

    /**
     * The balloon for [announcement]: "Show" opens the Requests tab at the latest request, and a
     * burst holding a request the plugin refused itself also offers the settings page that fixes
     * the latest such refusal.
     */
    private fun notification(group: String, announcement: WarningAnnouncement): Notification {
        val record = announcement.latest
        val balloon = Notification(group, title(announcement), content(announcement), NotificationType.WARNING)
        burstFix?.let { page ->
            balloon.addAction(
                NotificationAction.create(FixPageSettings.actionText(page)) { event, notification ->
                    openFixPage(event.project, page)
                    notification.expire()
                }
            )
        }
        return balloon.addAction(
            NotificationAction.create("Show") { event, notification ->
                event.project?.let { RequestsNavigator.reveal(it, record) }
                notification.expire()
            }
        )
    }

    /** Who and what, escaped: the sender is whatever a User-Agent said, and the text is HTML. */
    private fun title(announcement: WarningAnnouncement): String =
        StringUtil.escapeXmlEntities("${announcement.latest.sender} · ${announcement.latest.requestedModel}")

    /** Why, escaped: an error is an upstream's own message. */
    private fun content(announcement: WarningAnnouncement): String {
        val reason = StringUtil.escapeXmlEntities(announcement.reason)
        return when (announcement) {
            is WarningAnnouncement.Raise -> reason
            is WarningAnnouncement.Fold -> {
                val others = if (announcement.more == 1) "1 more request" else "${announcement.more} more requests"
                "$reason - and $others went wrong"
            }
        }
    }

    /** A notification must not outlive the plugin whose action it holds. */
    override fun dispose() {
        current?.expire()
        current = null
    }

    companion object {
        const val GROUP_ID = "OpenRouter Requests"

        /** The same announcements without a balloon, for a count that only needs to be kept. */
        const val LOG_ONLY_GROUP_ID = "OpenRouter Requests (folded)"

        fun getInstance(): RequestWarningBalloons =
            ApplicationManager.getApplication().getService(RequestWarningBalloons::class.java)
    }
}

/** Hands every recorded request to [RequestWarningBalloons]; registered in plugin.xml. */
class RequestWarningListener : RequestLogListener {
    override fun changed(added: RequestRecord?) {
        added?.let { RequestWarningBalloons.getInstance().onRecord(it) }
    }
}
