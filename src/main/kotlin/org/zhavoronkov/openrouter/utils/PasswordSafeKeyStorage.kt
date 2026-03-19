package org.zhavoronkov.openrouter.utils

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Secure storage for API keys using IntelliJ's PasswordSafe.
 *
 * PasswordSafe uses the OS-native credential storage:
 * - macOS: Keychain
 * - Windows: Credential Manager
 * - Linux: libsecret (or KWallet)
 *
 * This is the recommended approach for storing sensitive data in IntelliJ plugins.
 *
 * In test environments where PasswordSafe is not available, this class falls back
 * to in-memory storage.
 */
object PasswordSafeKeyStorage {

    private const val SERVICE_NAME = "OpenRouter IntelliJ Plugin"
    private const val API_KEY = "apiKey"
    private const val PROVISIONING_KEY = "provisioningKey"

    // In-memory fallback for test environments where PasswordSafe is not available
    private val inMemoryStorage = mutableMapOf<String, String>()

    /**
     * Creates credential attributes for a specific key type.
     * @throws IllegalStateException if IntelliJ environment is not available (e.g., in tests)
     */
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName(SERVICE_NAME, key))
    }

    /**
     * Gets the API key from PasswordSafe, or from in-memory storage in test environments.
     * @return The API key, or null if not stored
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun getApiKey(): String? {
        return try {
            val attributes = createCredentialAttributes(API_KEY)
            PasswordSafe.instance.getPassword(attributes)
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            // Catches: IllegalStateException, NullPointerException, ExceptionInInitializerError
            // Exception is intentionally swallowed - this is expected fallback behavior
            inMemoryStorage[API_KEY]
        }
    }

    /**
     * Stores the API key in PasswordSafe, or in-memory storage in test environments.
     * @param apiKey The API key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun setApiKey(apiKey: String) {
        try {
            val attributes = createCredentialAttributes(API_KEY)
            if (apiKey.isBlank()) {
                PasswordSafe.instance.set(attributes, null)
            } else {
                PasswordSafe.instance.set(attributes, Credentials("", apiKey))
            }
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            // Exception is intentionally swallowed - this is expected fallback behavior
            if (apiKey.isBlank()) {
                inMemoryStorage.remove(API_KEY)
            } else {
                inMemoryStorage[API_KEY] = apiKey
            }
        }
    }

    /**
     * Gets the provisioning key from PasswordSafe, or from in-memory storage in test environments.
     * @return The provisioning key, or null if not stored
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun getProvisioningKey(): String? {
        return try {
            val attributes = createCredentialAttributes(PROVISIONING_KEY)
            PasswordSafe.instance.getPassword(attributes)
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            // Exception is intentionally swallowed - this is expected fallback behavior
            inMemoryStorage[PROVISIONING_KEY]
        }
    }

    /**
     * Stores the provisioning key in PasswordSafe, or in-memory storage in test environments.
     * @param provisioningKey The provisioning key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun setProvisioningKey(provisioningKey: String) {
        try {
            val attributes = createCredentialAttributes(PROVISIONING_KEY)
            if (provisioningKey.isBlank()) {
                PasswordSafe.instance.set(attributes, null)
            } else {
                PasswordSafe.instance.set(attributes, Credentials("", provisioningKey))
            }
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            // Exception is intentionally swallowed - this is expected fallback behavior
            if (provisioningKey.isBlank()) {
                inMemoryStorage.remove(PROVISIONING_KEY)
            } else {
                inMemoryStorage[PROVISIONING_KEY] = provisioningKey
            }
        }
    }

    /**
     * Clears all stored keys from PasswordSafe.
     * Used for logout functionality.
     */
    fun clearAll() {
        setApiKey("")
        setProvisioningKey("")
    }
}
