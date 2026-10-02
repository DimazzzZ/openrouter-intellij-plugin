package org.zhavoronkov.openrouter.utils

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener
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
 *
 * Writes get the same treatment for the same reason: the cache and the in-memory map are updated
 * on the calling thread, so the new value is readable the instant a setter returns, and only the
 * hop into the OS credential store is moved off the EDT. See [persist].
 */
@Suppress("TooManyFunctions")
object PasswordSafeKeyStorage {

    // One thread, so credential store access keeps the order it was requested in - see persist().
    private val credentialStoreExecutor =
        AppExecutorUtil.createBoundedApplicationPoolExecutor("OpenRouter Credential Store", 1)

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

    /** A reader on the EDT was told "not known yet", and must hear when the key is known. */
    private val edtAnsweredCold = AtomicBoolean(false)

    /**
     * Creates credential attributes for a specific key type.
     * @throws IllegalStateException if IntelliJ environment is not available (e.g., in tests)
     */
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SERVICE_NAME, key)
        )
    }

    /**
     * Warms the cache from PasswordSafe, off the EDT.
     *
     * Its only caller is the dynamic-plugin listener, and the platform loads plugins "in EDT and
     * under write action", so reading the credential store straight from there was the same
     * violation the setters used to commit - two of them, one per key, on every install, update or
     * enable without a restart. The comment at that call site already promised a background
     * thread; this is where the promise is kept, so no caller has to arrange it.
     *
     * Nothing waits on the result. A reader that arrives before the warm-up finishes falls back to
     * loading the key it needs itself, which is the behaviour it had anyway.
     */
    @Suppress("TooGenericExceptionCaught")
    fun preloadKeys() {
        if (!preloadTriggered.compareAndSet(false, true)) {
            return
        }

        val application = ApplicationManager.getApplication()
        if (application == null) {
            apiKeyCacheInitialized.set(true)
            provisioningKeyCacheInitialized.set(true)
            return
        }

        if (application.isDispatchThread) {
            credentialStoreExecutor.execute { loadBothKeys() }
        } else {
            loadBothKeys()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadBothKeys() {
        try {
            loadApiKeyFromPasswordSafe()
            loadProvisioningKeyFromPasswordSafe()
        } catch (_: Exception) {
            apiKeyCacheInitialized.set(true)
            provisioningKeyCacheInitialized.set(true)
        }
        if (edtAnsweredCold.getAndSet(false)) announceKeysLoaded()
    }

    /**
     * Tells the UI the keys are known now: a reader on the EDT that arrived before the warm-up was
     * told "not known yet", and asks again when settings change.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun announceKeysLoaded() {
        val application = ApplicationManager.getApplication() ?: return
        application.invokeLater {
            try {
                application.messageBus.syncPublisher(OpenRouterSettingsListener.TOPIC).onSettingsChanged()
            } catch (_: Exception) {
                // An application shutting down has nobody left to tell
            }
        }
    }

    /**
     * The EDT never reads the credential store: before the cache is warm it starts the warm-up
     * and is told the key is not known yet, which [announceKeysLoaded] corrects.
     */
    private fun onEdt(): Boolean = ApplicationManager.getApplication()?.isDispatchThread == true

    private fun answerColdOnEdt() {
        edtAnsweredCold.set(true)
        // A warm-up already started announces when it finishes; one not started yet is started
        preloadKeys()
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
     * If the cache is not initialized, reads PasswordSafe directly (blocking) - except on the
     * EDT, which starts the warm-up and gets null until it finishes. preloadKeys() runs when
     * the settings service starts, so that is rare.
     *
     * @return The API key, or null if not stored
     */
    fun getApiKey(): String? {
        if (apiKeyCacheInitialized.get()) {
            return cachedApiKey
        }
        if (onEdt()) {
            answerColdOnEdt()
            return null
        }

        // Cache not initialized - read synchronously from PasswordSafe
        loadApiKeyFromPasswordSafe()
        return cachedApiKey
    }

    /**
     * Stores the API key in PasswordSafe synchronously and updates cache.
     * @param apiKey The API key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setApiKey(apiKey: String) {
        cachedApiKey = apiKey.ifBlank { null }
        apiKeyCacheInitialized.set(true)

        if (apiKey.isBlank()) {
            inMemoryStorage.remove(API_KEY)
        } else {
            inMemoryStorage[API_KEY] = apiKey
        }

        // Write to PasswordSafe synchronously to ensure persistence
        writeApiKeyToPasswordSafe(apiKey)
    }

    /**
     * Writes API key to PasswordSafe.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun writeApiKeyToPasswordSafe(apiKey: String) {
        try {
            val attributes = createCredentialAttributes(API_KEY)
            persist(attributes, if (apiKey.isBlank()) null else Credentials("", apiKey))
        } catch (_: Exception) {
            // Silently fail - cache and in-memory storage are already updated
        }
    }

    /**
     * Gets the provisioning key from cache (safe for EDT).
     *
     * If the cache is not initialized, reads PasswordSafe directly (blocking) - except on the
     * EDT, which starts the warm-up and gets null until it finishes. preloadKeys() runs when
     * the settings service starts, so that is rare.
     *
     * @return The provisioning key, or null if not stored
     */
    fun getProvisioningKey(): String? {
        if (provisioningKeyCacheInitialized.get()) {
            return cachedProvisioningKey
        }
        if (onEdt()) {
            answerColdOnEdt()
            return null
        }

        // Cache not initialized - read synchronously from PasswordSafe
        loadProvisioningKeyFromPasswordSafe()
        return cachedProvisioningKey
    }

    /**
     * Stores the provisioning key in PasswordSafe synchronously and updates cache.
     * @param provisioningKey The provisioning key to store. If blank, removes the stored key.
     */
    @Suppress("TooGenericExceptionCaught")
    fun setProvisioningKey(provisioningKey: String) {
        cachedProvisioningKey = provisioningKey.ifBlank { null }
        provisioningKeyCacheInitialized.set(true)

        if (provisioningKey.isBlank()) {
            inMemoryStorage.remove(PROVISIONING_KEY)
        } else {
            inMemoryStorage[PROVISIONING_KEY] = provisioningKey
        }

        // Write to PasswordSafe synchronously to ensure persistence
        writeProvisioningKeyToPasswordSafe(provisioningKey)
    }

    /**
     * Writes provisioning key to PasswordSafe.
     * This is a blocking operation and should NOT be called on EDT.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun writeProvisioningKeyToPasswordSafe(provisioningKey: String) {
        try {
            val attributes = createCredentialAttributes(PROVISIONING_KEY)
            persist(attributes, if (provisioningKey.isBlank()) null else Credentials("", provisioningKey))
        } catch (_: Exception) {
            // Silently fail - cache and in-memory storage are already updated
        }
    }

    /**
     * Hands a credential to the OS store, off the EDT.
     *
     * `PasswordSafe.set` reaches the macOS Keychain, Windows Credential Manager or libsecret, so
     * the platform forbids it on the EDT and logs "Slow operations are prohibited on EDT" for every
     * call. Both writers used to call it inline, which meant any caller running on the EDT - the
     * status-bar logout, the setup wizard's step transition - produced that error without either
     * of them doing anything obviously wrong.
     *
     * Only the store hop is deferred. The cache and the in-memory map are already updated by the
     * time this runs, so a setter's value is readable the moment it returns, and nothing observes
     * the difference except the credential store itself.
     *
     * A single-threaded executor rather than a plain pooled task: two writes to the SAME key,
     * dispatched in order and completing out of order, would persist the older value. Serialising
     * them keeps the store's final state equal to the last value set.
     *
     * Off the EDT the write stays inline, so background callers keep their existing ordering and
     * a test that drives them sees the effect without waiting.
     */
    private fun persist(attributes: CredentialAttributes, credentials: Credentials?) {
        val application = ApplicationManager.getApplication()
        if (application == null || !application.isDispatchThread) {
            PasswordSafe.instance.set(attributes, credentials)
            return
        }

        credentialStoreExecutor.execute {
            try {
                PasswordSafe.instance.set(attributes, credentials)
            } catch (e: RuntimeException) {
                PluginLogger.Service.debug("Deferred credential write failed: ${e.message}")
            }
        }
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
        edtAnsweredCold.set(false)
        inMemoryStorage.clear()
    }
}
