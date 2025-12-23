package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.KeyValidator
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Manages API key and provisioning key settings
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

    fun getApiKey(): String {
        val encrypted = settings.apiKey
        val decrypted = if (EncryptionUtil.isEncrypted(encrypted)) {
            EncryptionUtil.decrypt(encrypted)
        } else {
            encrypted
        }
        PluginLogger.Service.info(
            "getApiKey: encrypted.length=${encrypted.length}, encrypted.isEmpty=${encrypted.isEmpty()}, " +
                "decrypted.length=${decrypted.length}, decrypted.isEmpty=${decrypted.isEmpty()}"
        )
        return decrypted
    }

    fun setApiKey(apiKey: String) {
        PluginLogger.Service.info(
            "setApiKey called: apiKey.length=${apiKey.length}, apiKey.isEmpty=${apiKey.isEmpty()}"
        )

        val encryptedKey = if (apiKey.isNotBlank()) {
            EncryptionUtil.encrypt(apiKey)
        } else {
            apiKey
        }

        PluginLogger.Service.info(
            "setApiKey: encrypted.length=${encryptedKey.length}, encrypted.isEmpty=${encryptedKey.isEmpty()}"
        )

        val oldApiKey = settings.apiKey
        settings.apiKey = encryptedKey

        PluginLogger.Service.info(
            "setApiKey: settings.apiKey changed from length ${oldApiKey.length} to ${settings.apiKey.length}"
        )
        PluginLogger.Service.info("setApiKey: settings.apiKey.isEmpty=${settings.apiKey.isEmpty()}")

        onStateChanged()

        val retrieved = getApiKey()
        PluginLogger.Service.info(
            "setApiKey: verification after persistence - retrieved.length=${retrieved.length}, " +
                "retrieved.isEmpty=${retrieved.isEmpty()}"
        )
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
        PluginLogger.Service.debug(
            "getStoredApiKey: authScope=${settings.authScope}, " +
                "apiKey.length=${apiKey.length}, apiKey.isNotBlank=${apiKey.isNotBlank()}"
        )

        return if (apiKey.isNotBlank()) apiKey else null
    }

    fun getProvisioningKey(): String {
        val encrypted = settings.provisioningKey
        return if (EncryptionUtil.isEncrypted(encrypted)) {
            EncryptionUtil.decrypt(encrypted)
        } else {
            encrypted
        }
    }

    fun setProvisioningKey(provisioningKey: String) {
        val encryptedKey = if (provisioningKey.isNotBlank()) {
            EncryptionUtil.encrypt(provisioningKey)
        } else {
            provisioningKey
        }
        settings.provisioningKey = encryptedKey
        onStateChanged()
    }

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
}
