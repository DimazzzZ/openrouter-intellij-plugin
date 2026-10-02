package org.zhavoronkov.openrouter.requests

/**
 * What to tell the user about a request that went wrong: a new balloon, or an update to the one
 * already on screen. [latest] is the request "Show" opens; [more] is how many others the balloon
 * stands for.
 */
sealed interface WarningAnnouncement {
    val latest: RequestRecord
    val reason: String

    /** The first warning of a burst: a new balloon. */
    data class Raise(override val latest: RequestRecord, override val reason: String) : WarningAnnouncement

    /** A later warning in the same burst: the balloon on screen now stands for [more] others too. */
    data class Fold(
        override val latest: RequestRecord,
        override val reason: String,
        val more: Int
    ) : WarningAnnouncement
}

/**
 * Groups the requests that went wrong into bursts, so a Consumer failing in a loop is announced once
 * rather than on every request.
 *
 * A burst starts with the first [unseenWarning] and lasts [windowMillis], counted from its start:
 * the warnings inside it fold into the first, counted, and the first one after it starts a new
 * burst. The window does not slide, so a Consumer failing steadily is announced again every window
 * rather than never again.
 */
class WarningBurst(
    private val clock: () -> Long = System::currentTimeMillis,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS
) {
    private var burstStartedAt: Long? = null
    private var folded = 0

    /** The announcement for [record], or null when it needs none. */
    @Synchronized
    fun onRecord(record: RequestRecord): WarningAnnouncement? {
        val reason = record.unseenWarning ?: return null
        val now = clock()
        val startedAt = burstStartedAt
        if (startedAt != null && now - startedAt < windowMillis) {
            folded++
            return WarningAnnouncement.Fold(record, reason, folded)
        }
        burstStartedAt = now
        folded = 0
        return WarningAnnouncement.Raise(record, reason)
    }

    companion object {
        /** "A few minutes": long enough to cover a Consumer retrying, short enough to hear of a new failure. */
        const val DEFAULT_WINDOW_MILLIS = 3 * 60 * 1000L
    }
}
