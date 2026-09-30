package org.zhavoronkov.openrouter.models

/**
 * The naming rule the entries the user names share - an Output Schema, and a preset's slug where a
 * pair names it - so that a name accepted for one is accepted for the other.
 *
 * The set is letters, digits, underscores and hyphens, up to 64 of them. An Output Schema's name
 * is sent to whichever provider serves the request, and OpenAI's API accepts only that set; a
 * preset's slug becomes part of a model id a Consumer sends back, where the same set is safe.
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
        if (!isWellFormed(trimmed)) return RULE
        reserved.entries.firstOrNull { same(it.key, trimmed) }?.let { return it.value }
        val clash = existing.firstOrNull { same(it, trimmed) }
        return clash?.let { "$anEntry named '$it' already exists" }
    }

    /** Whether [name], trimmed, is spelled as the rule allows, whatever else it may clash with. */
    fun isWellFormed(name: String): Boolean = PATTERN.matches(name.trim())

    /**
     * Whether two names name the same entry. Every place that matches names - uniqueness, a
     * selection finding its entry, two selections being equal - goes through this.
     */
    fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    /** The key [same] compares by, for anything that hashes names. */
    fun key(name: String): String = name.trim().lowercase()
}
