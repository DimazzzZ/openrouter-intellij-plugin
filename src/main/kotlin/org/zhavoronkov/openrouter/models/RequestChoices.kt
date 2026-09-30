package org.zhavoronkov.openrouter.models

/**
 * The reasoning efforts and verbosities a request can be given, by the labels the user picks them
 * by. The chat's send-parameters popup and the preset dialog offer them, and
 * [org.zhavoronkov.openrouter.toolwindow.chat.ChatExchange] translates them into a request - all
 * from these lists, so a label nothing can translate cannot exist. A preset stores OpenRouter's
 * value, never the label, so a label can be renamed here freely.
 */
object RequestChoices {

    /** How a control spells "the user changed nothing", and the first choice each offers. */
    const val UNCHANGED = "Default"

    /**
     * Each reasoning effort's label and OpenRouter's value for it. Listed rather than lowercased
     * blindly: every label happens to be its value in upper case, but that is a coincidence of the
     * current list, and an unrecognised label must reach OpenRouter as nothing rather than as a
     * guess.
     */
    val REASONING_EFFORTS: Map<String, String> = linkedMapOf(
        "None" to "none",
        "Minimal" to "minimal",
        "Low" to "low",
        "Medium" to "medium",
        "High" to "high",
        "XHigh" to "xhigh"
    )

    /** Every verbosity, in the order the controls offer them. */
    val VERBOSITIES: List<String> = listOf("Low", "Medium", "High", "XHigh", "Max")

    /** OpenRouter's value for the reasoning effort labelled [label], or null for one not listed. */
    fun reasoningEffort(label: String?): String? = REASONING_EFFORTS[label]

    /** OpenRouter's value for the verbosity labelled [label]: the label in lower case, when it is one listed. */
    fun verbosity(label: String?): String? = label?.takeIf { it in VERBOSITIES }?.lowercase()
}
