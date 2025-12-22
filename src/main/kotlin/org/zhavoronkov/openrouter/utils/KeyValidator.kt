package org.zhavoronkov.openrouter.utils

import org.zhavoronkov.openrouter.constants.OpenRouterConstants
import org.zhavoronkov.openrouter.models.AuthScope

/**
 * Utility class for validating OpenRouter API keys and provisioning keys
 *
 * Note: Both regular API keys and provisioning keys use the same format (sk-or-v1-...)
 * and cannot be distinguished by their string format alone. The difference is in their
 * permissions/scopes, which can only be verified via API calls.
 */
object KeyValidator {

    // Key format pattern - same for both regular and provisioning keys
    private const val API_KEY_PREFIX = "sk-or-v1-"

    /**
     * Validation result for API keys
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
        data class Warning(val message: String) : ValidationResult()
    }

    /**
     * Validates a key format (works for both API keys and provisioning keys)
     */
    fun validateKeyFormat(key: String): ValidationResult {
        if (key.isBlank()) {
            return ValidationResult.Invalid("Key cannot be empty")
        }

        if (key.length < OpenRouterConstants.MIN_KEY_LENGTH) {
            return ValidationResult.Invalid(
                "Key is too short (minimum ${OpenRouterConstants.MIN_KEY_LENGTH} characters)"
            )
        }

        if (!key.startsWith(API_KEY_PREFIX)) {
            return ValidationResult.Warning("OpenRouter keys should start with '$API_KEY_PREFIX'")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates an API key format
     * Note: Cannot distinguish from provisioning key by format alone
     */
    fun validateApiKey(key: String): ValidationResult {
        return validateKeyFormat(key)
    }

    /**
     * Validates a provisioning key format
     * Note: Cannot distinguish from regular API key by format alone
     */
    fun validateProvisioningKey(key: String): ValidationResult {
        return validateKeyFormat(key)
    }

    /**
     * Validates a key based on the authentication scope
     */
    fun validateKey(key: String, authScope: AuthScope): ValidationResult {
        return validateKeyFormat(key)
    }

    /**
     * Checks if a key looks like a valid OpenRouter key
     * Note: Cannot distinguish between regular API keys and provisioning keys by format
     */
    fun looksLikeOpenRouterKey(key: String): Boolean {
        return key.startsWith(API_KEY_PREFIX) && key.length >= OpenRouterConstants.MIN_KEY_LENGTH
    }

    /**
     * Checks if a key looks like a provisioning key
     * Note: This is deprecated as both key types have the same format.
     * Use looksLikeOpenRouterKey() instead.
     * @deprecated Cannot distinguish provisioning keys from regular keys by format alone
     */
    @Deprecated(
        "Cannot distinguish provisioning keys from regular keys by format alone",
        ReplaceWith("looksLikeOpenRouterKey(key)")
    )
    fun looksLikeProvisioningKey(key: String): Boolean {
        return looksLikeOpenRouterKey(key)
    }

    /**
     * Checks if a key looks like a regular API key
     * Note: This is deprecated as both key types have the same format.
     * Use looksLikeOpenRouterKey() instead.
     * @deprecated Cannot distinguish regular API keys from provisioning keys by format alone
     */
    @Deprecated(
        "Cannot distinguish regular API keys from provisioning keys by format alone",
        ReplaceWith("looksLikeOpenRouterKey(key)")
    )
    fun looksLikeApiKey(key: String): Boolean {
        return looksLikeOpenRouterKey(key)
    }

    /**
     * Gets a user-friendly message for validation result
     */
    fun getValidationMessage(result: ValidationResult): String? {
        return when (result) {
            is ValidationResult.Valid -> null
            is ValidationResult.Invalid -> result.message
            is ValidationResult.Warning -> result.message
        }
    }

    /**
     * Checks if validation result indicates an error (not just a warning)
     */
    fun isError(result: ValidationResult): Boolean {
        return result is ValidationResult.Invalid
    }
}
