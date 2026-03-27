package org.zhavoronkov.openrouter.api

import org.jetbrains.annotations.ApiStatus

/**
 * Immutable balance data provided by the OpenRouter plugin.
 *
 * This data class contains a snapshot of the user's OpenRouter account balance,
 * including total credits, usage, and remaining balance. All monetary values
 * are in USD (United States Dollars).
 *
 * ## Thread Safety
 * This class is immutable and safe to share across threads.
 *
 * ## Validation
 * The constructor validates that:
 * - [totalCredits] and [totalUsage] are non-negative
 * - [timestamp] is positive (Unix milliseconds)
 *
 * @property totalCredits Total credits purchased/available in USD
 * @property totalUsage Total credits used to date in USD
 * @property remainingCredits Calculated remaining balance (totalCredits - totalUsage)
 * @property timestamp Unix timestamp in milliseconds when this data was fetched
 * @property todayUsage Optional usage for the current day in USD (may be null if unavailable)
 * @property source Identifier of the data source, defaults to "openrouter"
 *
 * @throws IllegalArgumentException if validation fails
 * @see BalanceProvider
 * @since 0.6.0
 */
@ApiStatus.AvailableSince("0.6.0")
data class BalanceData(
    val totalCredits: Double,
    val totalUsage: Double,
    val remainingCredits: Double,
    val timestamp: Long,
    val todayUsage: Double? = null,
    val source: String = SOURCE_OPENROUTER
) {
    companion object {
        /** Default source identifier for OpenRouter data */
        const val SOURCE_OPENROUTER = "openrouter"
    }

    init {
        require(totalCredits >= 0) { "totalCredits must be non-negative, was: $totalCredits" }
        require(totalUsage >= 0) { "totalUsage must be non-negative, was: $totalUsage" }
        require(timestamp > 0) { "timestamp must be positive, was: $timestamp" }
        todayUsage?.let {
            require(it >= 0) { "todayUsage must be non-negative when provided, was: $it" }
        }
    }

    /**
     * Returns the remaining balance as a percentage of total credits.
     *
     * @return Percentage (0.0 to 100.0), or 100.0 if totalCredits is zero
     */
    fun remainingPercentage(): Double {
        return if (totalCredits > 0) {
            (remainingCredits / totalCredits) * 100.0
        } else {
            100.0
        }
    }

    /**
     * Returns the usage as a percentage of total credits.
     *
     * @return Percentage (0.0 to 100.0), or 0.0 if totalCredits is zero
     */
    fun usagePercentage(): Double {
        return if (totalCredits > 0) {
            (totalUsage / totalCredits) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Checks if the balance is critically low (less than 10% remaining).
     *
     * @return true if remaining balance is less than 10% of total credits
     */
    fun isLowBalance(): Boolean {
        return remainingPercentage() < LOW_BALANCE_THRESHOLD
    }

    /**
     * Returns a formatted string representation of the remaining balance.
     *
     * @return Formatted string like "$12.34"
     */
    fun formattedRemaining(): String {
        return String.format("$%.2f", remainingCredits)
    }

    /**
     * Returns a formatted string representation of today's usage.
     *
     * @return Formatted string like "$1.23" or "N/A" if unavailable
     */
    fun formattedTodayUsage(): String {
        return todayUsage?.let { String.format("$%.2f", it) } ?: "N/A"
    }

    override fun toString(): String {
        return "BalanceData(remaining=$${String.format("%.2f", remainingCredits)}, " +
            "used=$${String.format("%.2f", totalUsage)}, " +
            "total=$${String.format("%.2f", totalCredits)}, " +
            "today=${formattedTodayUsage()})"
    }
}

/** Threshold percentage below which balance is considered low */
private const val LOW_BALANCE_THRESHOLD = 10.0
