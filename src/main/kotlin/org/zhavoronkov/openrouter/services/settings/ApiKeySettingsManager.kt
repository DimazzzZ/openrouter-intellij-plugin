package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.KeyValidator
import org.zhavoronkov.openrouter.utils.PasswordSafeKeyStorage
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Safely log a message, ignoring exceptions if logger is not available (e.g., in tests).
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private inline fun logSafely(level: String = "debug", message: () -> String) {
    try {
        when (level) {
            "info" -> PluginLogger.Service.info(message())
            "warn" -> PluginLogger.Service.warn(message())
            "error" -> PluginLogger.Service.error(message())
            else -> PluginLogger.Service.debug(message())
        }
    } catch (_: Exception) {
        // Intentionally swallowed - logging errors are ignored in test environments
    }
}

/**
 * Manages API key and provisioning key settings.
 *
 * Keys are stored securely using IntelliJ's PasswordSafe (OS-native credential storage).
 * For backwards compatibility, legacy keys stored in XML settings are automatically
 * migrated to PasswordSafe on first access.
 */
class ApiKeySettingsManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    var authScope: AuthScope
        get() = settings.authScope
        set(value) {
            settings.authScope = value
            onStateChanged()
        }

    /**
     * Gets the API key from secure storage.
     *
     * Retrieval order:
     * 1. Try PasswordSafe (preferred, secure storage)
     * 2. If not found, check legacy encrypted storage in XML settings
     * 3. If legacy key exists, migrate it to PasswordSafe and clear from settings
     *
     * @return The API key, or empty string if not configured
     */
    @Suppress("ReturnCount")
    fun getApiKey(): String {
        // First, try PasswordSafe
        val passwordSafeKey = PasswordSafeKeyStorage.getApiKey()
        if (!passwordSafeKey.isNullOrBlank()) {
            logSafely { "getApiKey: Retrieved from PasswordSafe (length: ${passwordSafeKey.length})" }
            return passwordSafeKey
        }

        // Check for legacy key in settings (encrypted in XML)
        val legacyEncrypted = settings.apiKey
        if (legacyEncrypted.isNotBlank()) {
            val decrypted = if (EncryptionUtil.isEncrypted(legacyEncrypted)) {
                EncryptionUtil.decrypt(legacyEncrypted)
            } else {
                legacyEncrypted
            }

            if (decrypted.isNotBlank()) {
                logSafely("info") {
                    "getApiKey: Found legacy API key, migrating to PasswordSafe (length: ${decrypted.length})"
                }
                // Migrate to PasswordSafe
                PasswordSafeKeyStorage.setApiKey(decrypted)
                // Clear from legacy storage
                settings.apiKey = ""
                onStateChanged()
                return decrypted
            }
        }

        logSafely { "getApiKey: No API key found" }
        return ""
    }

    /**
     * Stores the API key in secure storage (PasswordSafe).
     *
     * @param apiKey The API key to store
     */
    fun setApiKey(apiKey: String) {
        logSafely("info") { "setApiKey called: apiKey.length=${apiKey.length}, apiKey.isEmpty=${apiKey.isEmpty()}" }

        // Store in PasswordSafe
        PasswordSafeKeyStorage.setApiKey(apiKey)

        // Clear any legacy storage
        if (settings.apiKey.isNotBlank()) {
            logSafely { "setApiKey: Clearing legacy API key from settings" }
            settings.apiKey = ""
        }

        onStateChanged()

        // Verify storage
        val retrieved = getApiKey()
        logSafely("info") {
            "setApiKey: verification after storage - retrieved.length=${retrieved.length}, " +
                "matches=${retrieved == apiKey}"
        }
    }

    /**
     * Gets the API key to use for chat completions.
     *
     * In REGULAR mode: Returns the user-provided API key from settings.apiKey
     * In EXTENDED mode: Returns the auto-created "IntelliJ IDEA Plugin" API key from settings.apiKey
     *
     * Note: Both modes use settings.apiKey, but the key source differs:
     * - REGULAR: User manually enters their API key
     * - EXTENDED: Plugin auto-creates a key via provisioning key and stores it
     *
     * @return The API key to use, or null if not configured
     */
    fun getStoredApiKey(): String? {
        // In EXTENDED mode, we need to ensure the stored API key is valid
        // The IntellijApiKeyManager.ensureIntellijApiKeyExists() handles this
        // by validating and regenerating the key if needed
        val apiKey = getApiKey()

        // Log which mode we're in for debugging
        logSafely {
            "getStoredApiKey: authScope=${settings.authScope}, " +
                "apiKey.length=${apiKey.length}, apiKey.isNotBlank=${apiKey.isNotBlank()}"
        }

        return if (apiKey.isNotBlank()) apiKey else null
    }

    /**
     * Gets the provisioning key from secure storage.
     *
     * Retrieval order:
     * 1. Try PasswordSafe (preferred, secure storage)
     * 2. If not found, check legacy encrypted storage in XML settings
     * 3. If legacy key exists, migrate it to PasswordSafe and clear from settings
     *
     * @return The provisioning key, or empty string if not configured
     */
    @Suppress("ReturnCount")
    fun getProvisioningKey(): String {
        // First, try PasswordSafe
        val passwordSafeKey = PasswordSafeKeyStorage.getProvisioningKey()
        if (!passwordSafeKey.isNullOrBlank()) {
            logSafely { "getProvisioningKey: Retrieved from PasswordSafe (length: ${passwordSafeKey.length})" }
            return passwordSafeKey
        }

        // Check for legacy key in settings (encrypted in XML)
        val legacyEncrypted = settings.provisioningKey
        if (legacyEncrypted.isNotBlank()) {
            val decrypted = if (EncryptionUtil.isEncrypted(legacyEncrypted)) {
                EncryptionUtil.decrypt(legacyEncrypted)
            } else {
                legacyEncrypted
            }

            if (decrypted.isNotBlank()) {
                logSafely("info") {
                    "getProvisioningKey: Found legacy key, migrating to PasswordSafe (length: ${decrypted.length})"
                }
                // Migrate to PasswordSafe
                PasswordSafeKeyStorage.setProvisioningKey(decrypted)
                // Clear from legacy storage
                settings.provisioningKey = ""
                onStateChanged()
                return decrypted
            }
        }

        logSafely { "getProvisioningKey: No provisioning key found" }
        return ""
    }

    /**
     * Stores the provisioning key in secure storage (PasswordSafe).
     *
     * @param provisioningKey The provisioning key to store
     */
    fun setProvisioningKey(provisioningKey: String) {
        logSafely("info") {
            "setProvisioningKey called: length=${provisioningKey.length}, isEmpty=${provisioningKey.isEmpty()}"
        }

        // Store in PasswordSafe
        PasswordSafeKeyStorage.setProvisioningKey(provisioningKey)

        // Clear any legacy storage
        if (settings.provisioningKey.isNotBlank()) {
            logSafely { "setProvisioningKey: Clearing legacy key from settings" }
            settings.provisioningKey = ""
        }

        onStateChanged()

        // Verify storage
        val retrieved = getProvisioningKey()
        logSafely("info") {
            "setProvisioningKey: verification after storage - retrieved.length=${retrieved.length}, " +
                "matches=${retrieved == provisioningKey}"
        }
    }

    /**
     * Checks if the plugin is configured with the necessary key for the current auth scope.
     *
     * @return true if configured, false otherwise
     */
    fun isConfigured(): Boolean {
        return when (settings.authScope) {
            AuthScope.REGULAR -> getApiKey().isNotBlank()
            AuthScope.EXTENDED -> getProvisioningKey().isNotBlank()
        }
    }

    /**
     * Validates that the configured key matches the current auth scope
     * Returns null if valid, or an error message if invalid
     *
     * Note: Both regular API keys and provisioning keys have the same format (sk-or-v1-...),
     * so we can only validate that a key is present and has the correct format.
     * The actual key type/permissions can only be verified via API calls.
     */
    fun validateKeyForCurrentScope(): String? {
        return when (settings.authScope) {
            AuthScope.REGULAR -> {
                val apiKey = getApiKey()
                if (apiKey.isBlank()) {
                    "API key is not configured"
                } else {
                    val validationResult = KeyValidator.validateApiKey(apiKey)
                    if (KeyValidator.isError(validationResult)) {
                        KeyValidator.getValidationMessage(validationResult)
                    } else {
                        null
                    }
                }
            }
            AuthScope.EXTENDED -> {
                val provKey = getProvisioningKey()
                if (provKey.isBlank()) {
                    "Provisioning key is not configured"
                } else {
                    val validationResult = KeyValidator.validateProvisioningKey(provKey)
                    if (KeyValidator.isError(validationResult)) {
                        KeyValidator.getValidationMessage(validationResult)
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Clears all stored keys from both PasswordSafe and legacy storage.
     * Used for logout functionality.
     */
    fun clearAllKeys() {
        PasswordSafeKeyStorage.clearAll()
        settings.apiKey = ""
        settings.provisioningKey = ""
        onStateChanged()
        logSafely("info") { "Cleared all API keys" }
    }
}
