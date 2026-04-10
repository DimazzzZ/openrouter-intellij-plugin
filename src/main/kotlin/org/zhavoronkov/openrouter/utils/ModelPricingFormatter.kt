package org.zhavoronkov.openrouter.utils

import org.zhavoronkov.openrouter.models.ModelPricing
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo
import java.util.Locale

/**
 * Utility for formatting model pricing display strings.
 *
 * OpenRouter pricing is expressed as cost per token in USD (e.g., "0.0000015" = $0.0000015 per token).
 * This formatter converts to a human-readable "per 1M tokens" format.
 */
object ModelPricingFormatter {

    private const val TOKENS_PER_MILLION = 1_000_000.0
    private const val CURRENCY_DECIMAL_PLACES = 4
    private const val FALLBACK = "—"

    /**
     * Format input (prompt) price per 1M tokens.
     */
    fun formatInputPrice(pricing: ModelPricing?): String = formatPrice(pricing?.prompt)

    /**
     * Format output (completion) price per 1M tokens.
     */
    fun formatOutputPrice(pricing: ModelPricing?): String = formatPrice(pricing?.completion)

    /**
     * Format a single price value from OpenRouter's per-token pricing string.
     * Returns "—" if the price is null or unparseable.
     */
    private fun formatPrice(priceString: String?): String {
        val formatted = if (priceString.isNullOrBlank()) {
            null
        } else {
            priceString.toDoubleOrNull()?.let { perToken ->
                "$" + String.format(Locale.US, "%.${CURRENCY_DECIMAL_PLACES}f", perToken * TOKENS_PER_MILLION)
            }
        }

        if (formatted == null) {
            PluginLogger.Settings.debug("Failed to parse price: $priceString")
        }

        return formatted ?: FALLBACK
    }

    /**
     * Get a compact combined price label for display in combo boxes or single-line contexts.
     * Format: "$X.XXXX / $Y.YYYY per 1M tok"
     */
    fun formatCombinedPrice(model: OpenRouterModelInfo): String {
        val input = formatInputPrice(model.pricing)
        val output = formatOutputPrice(model.pricing)
        return "$input / $output per 1M tok"
    }
}
