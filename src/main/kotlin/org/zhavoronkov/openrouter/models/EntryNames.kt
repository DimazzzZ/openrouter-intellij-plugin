package org.zhavoronkov.openrouter.models

/**
 * The naming rule for the entries the user names - an Output Schema today - kept apart from any
 * one of them, so that every place that reads or compares a name agrees with the page that saved it.
 *
 * The set is letters, digits, underscores and hyphens, up to 64 of them: an Output Schema's name
 * is sent to whichever provider serves the request, and OpenAI's API accepts only that set.
 * Names are compared trimmed and without case, since two entries in a drop-down that differ only
 * in case read as the same one.
 */
object EntryNames {

    private val PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    const val RULE = "Use only letters, digits, _ and - (no spaces), up to 64 characters"

    /**
     * Why [name] cannot name the kind of entry [anEntry] calls one ("A schema"), or
     * null when it can. [existing] are the names already taken, not counting the entry being
     * edited; [reserved] maps each name that is never allowed to the reason it is not.
     */
    fun problem(name: String, existing: List<String>, anEntry: String, reserved: Map<String, String>): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "$anEntry needs a name"
        if (!PATTERN.matches(trimmed)) return RULE
        reserved.entries.firstOrNull { same(it.key, trimmed) }?.let { return it.value }
        val clash = existing.firstOrNull { same(it, trimmed) }
        return clash?.let { "$anEntry named '$it' already exists" }
    }

    /**
     * Whether two names name the same entry. Every place that matches names - uniqueness, a
     * selection finding its entry, two selections being equal - goes through this.
     */
    fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    /** The key [same] compares by, for anything that hashes names. */
    fun key(name: String): String = name.trim().lowercase()
}
