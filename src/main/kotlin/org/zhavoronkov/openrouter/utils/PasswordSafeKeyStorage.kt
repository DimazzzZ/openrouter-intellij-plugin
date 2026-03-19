package org.zhavoronkov.openrouter.utils

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * IMPORTANT: This class implements in-memory caching to avoid EDT violations.
 * PasswordSafe operations are slow and prohibited on EDT. The cache is:
 * - Populated on first access (async) and on startup via preloadKeys()
 * - Updated immediately when keys are set
 * - Read operations return cached values instantly (safe for EDT)
 */
@Suppress("TooManyFunctions")
object PasswordSafeKeyStorage {

    private const val SERVICE_NAME = "OpenRouter IntelliJ Plugin"
    private const val API_KEY = "apiKey"
    private const val PROVISIONING_KEY = "provisioningKey"

    // In-memory fallback for test environments where PasswordSafe is not available
    private val inMemoryStorage = mutableMapOf<String, String>()

    // Cached values - these are read on EDT without blocking
    @Volatile
    private var cachedApiKey: String? = null

    @Volatile
    private var cachedProvisioningKey: String? = null

    // Flags to track if cache has been initialized
    private val apiKeyCacheInitialized = AtomicBoolean(false)
    private val provisioningKeyCacheInitialized = AtomicBoolean(false)

    // Flag to track if preload has been triggered
    private val preloadTriggered = AtomicBoolean(false)

    /**
     * Creates credential attributes for a specific key type.
     * @throws IllegalStateException if IntelliJ environment is not available (e.g., in tests)
     */
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(generateServiceName(SERVICE_NAME, key))
    }

    /**
     * Preloads keys from PasswordSafe into cache on a background thread.
     * Should be called during plugin startup.
     */
    @Suppress("TooGenericExceptionCaught")
    fun preloadKeys() {
        if (!preloadTriggered.compareAndSet(false, true)) {
            // Already triggered
            return
        }

        try {
            val application = ApplicationManager.getApplication()
            if (application != null) {
                application.executeOnPooledThread {
                    loadApiKeyFromPasswordSafe()
                    loadProvisioningKeyFromPasswordSafe()
                }
            } else {
                // Test environment - load synchronously from in-memory storage
                apiKeyCacheInitialized.set(true)
                provisioningKeyCacheInitialized.set(true)
            }
        } catch (_: Exception) {
            // Test environment - load synchronously from in-memory storage
            apiKeyCacheInitialized.set(true)
            provisioningKeyCacheInitialized.set(true)
        }
    }

    /**
     * Loads API key from PasswordSafe into cache.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadApiKeyFromPasswordSafe() {
        try {
            val attributes = createCredentialAttributes(API_KEY)
            cachedApiKey = PasswordSafe.instance.getPassword(attributes)
            apiKeyCacheInitialized.set(true)
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            cachedApiKey = inMemoryStorage[API_KEY]
            apiKeyCacheInitialized.set(true)
        }
    }

    /**
     * Loads provisioning key from PasswordSafe into cache.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun loadProvisioningKeyFromPasswordSafe() {
        try {
            val attributes = createCredentialAttributes(PROVISIONING_KEY)
            cachedProvisioningKey = PasswordSafe.instance.getPassword(attributes)
            provisioningKeyCacheInitialized.set(true)
        } catch (_: Exception) {
            // Fallback to in-memory storage for test environments
            cachedProvisioningKey = inMemoryStorage[PROVISIONING_KEY]
            provisioningKeyCacheInitialized.set(true)
        }
    }

    /**
     * Gets the API key from cache (safe for EDT).
     *
     * If cache is not initialized, returns null and triggers async load.
     * For most use cases, preloadKeys() should be called on startup to ensure
     * the cache is populated before first access.
     *
     * @return The API key, or null if not stored or not yet loaded
     */
    fun getApiKey(): String? {
        // Return cached value immediately (safe for EDT)
        if (apiKeyCacheInitialized.get()) {
            return cachedApiKey
        }

        // Cache not initialized - trigger async load and return in-memory fallback
        triggerAsyncPreload()
        return inMemoryStorage[API_KEY]
    }

    /**
     * Stores the API key in PasswordSafe (async) and updates cache immediately.
     * @param apiKey The API key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setApiKey(apiKey: String) {
        // Update cache immediately (safe for EDT)
        cachedApiKey = apiKey.ifBlank { null }
        apiKeyCacheInitialized.set(true)

        // Also update in-memory storage for consistency
        if (apiKey.isBlank()) {
            inMemoryStorage.remove(API_KEY)
        } else {
            inMemoryStorage[API_KEY] = apiKey
        }

        // Write to PasswordSafe asynchronously (skip in test environments)
        try {
            val application = ApplicationManager.getApplication()
            application?.executeOnPooledThread {
                writeApiKeyToPasswordSafe(apiKey)
            }
        } catch (_: Exception) {
            // Test environment - skip PasswordSafe write
        }
    }

    /**
     * Writes API key to PasswordSafe.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun writeApiKeyToPasswordSafe(apiKey: String) {
        try {
            val attributes = createCredentialAttributes(API_KEY)
            if (apiKey.isBlank()) {
                PasswordSafe.instance.set(attributes, null)
            } else {
                PasswordSafe.instance.set(attributes, Credentials("", apiKey))
            }
        } catch (_: Exception) {
            // Silently fail - cache and in-memory storage are already updated
        }
    }

    /**
     * Gets the provisioning key from cache (safe for EDT).
     *
     * If cache is not initialized, returns null and triggers async load.
     * For most use cases, preloadKeys() should be called on startup to ensure
     * the cache is populated before first access.
     *
     * @return The provisioning key, or null if not stored or not yet loaded
     */
    fun getProvisioningKey(): String? {
        // Return cached value immediately (safe for EDT)
        if (provisioningKeyCacheInitialized.get()) {
            return cachedProvisioningKey
        }

        // Cache not initialized - trigger async load and return in-memory fallback
        triggerAsyncPreload()
        return inMemoryStorage[PROVISIONING_KEY]
    }

    /**
     * Stores the provisioning key in PasswordSafe (async) and updates cache immediately.
     * @param provisioningKey The provisioning key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setProvisioningKey(provisioningKey: String) {
        // Update cache immediately (safe for EDT)
        cachedProvisioningKey = provisioningKey.ifBlank { null }
        provisioningKeyCacheInitialized.set(true)

        // Also update in-memory storage for consistency
        if (provisioningKey.isBlank()) {
            inMemoryStorage.remove(PROVISIONING_KEY)
        } else {
            inMemoryStorage[PROVISIONING_KEY] = provisioningKey
        }

        // Write to PasswordSafe asynchronously (skip in test environments)
        try {
            val application = ApplicationManager.getApplication()
            application?.executeOnPooledThread {
                writeProvisioningKeyToPasswordSafe(provisioningKey)
            }
        } catch (_: Exception) {
            // Test environment - skip PasswordSafe write
        }
    }

    /**
     * Writes provisioning key to PasswordSafe.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun writeProvisioningKeyToPasswordSafe(provisioningKey: String) {
        try {
            val attributes = createCredentialAttributes(PROVISIONING_KEY)
            if (provisioningKey.isBlank()) {
                PasswordSafe.instance.set(attributes, null)
            } else {
                PasswordSafe.instance.set(attributes, Credentials("", provisioningKey))
            }
        } catch (_: Exception) {
            // Silently fail - cache and in-memory storage are already updated
        }
    }

    /**
     * Triggers async preload if not already triggered.
     */
    private fun triggerAsyncPreload() {
        preloadKeys()
    }

    /**
     * Clears all stored keys from PasswordSafe and cache.
     * Used for logout functionality.
     */
    fun clearAll() {
        setApiKey("")
        setProvisioningKey("")
    }

    /**
     * Resets the cache state. For testing purposes only.
     */
    internal fun resetCacheForTesting() {
        cachedApiKey = null
        cachedProvisioningKey = null
        apiKeyCacheInitialized.set(false)
        provisioningKeyCacheInitialized.set(false)
        preloadTriggered.set(false)
        inMemoryStorage.clear()
    }
}
