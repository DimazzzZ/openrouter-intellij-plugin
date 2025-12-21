package org.zhavoronkov.openrouter.services.settings

import org.zhavoronkov.openrouter.models.AuthScope
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.utils.EncryptionUtil
import org.zhavoronkov.openrouter.utils.PluginLogger

/**
 * Manages API key and provisioning key settings
 */
class ApiKeySettingsManager(
    private val settings: OpenRouterSettings,
    private val onStateChanged: () -> Unit
) {

    fun getAuthScope(): AuthScope = settings.authScope

    fun setAuthScope(scope: AuthScope) {
        settings.authScope = scope
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

    fun getStoredApiKey(): String? {
        val apiKey = getApiKey()
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
}
