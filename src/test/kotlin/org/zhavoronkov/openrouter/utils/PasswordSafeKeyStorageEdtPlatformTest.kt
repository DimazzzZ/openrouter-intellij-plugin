package org.zhavoronkov.openrouter.utils

import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer

/**
 * Logout wrote to PasswordSafe on the EDT, which the platform refuses: every write logged
 * "Slow operations are prohibited on EDT". `writeApiKeyToPasswordSafe`'s own KDoc has always said
 * the call "is a blocking operation and should NOT be called on EDT" - the contract was written
 * down and then broken by the caller.
 *
 * The platform's own assertion cannot be the test: `SlowOperations` is disabled in unit-test mode,
 * so it stays silent here no matter what the code does. The same fact is observed directly instead,
 * by standing a recording double in for the PasswordSafe service and asking which thread reached it.
 */
class PasswordSafeKeyStorageEdtPlatformTest : BasePlatformTestCase() {

    private companion object {
        /** clearAll() writes the API key and the Management Key. */
        const val EXPECTED_WRITES = 2
        const val WRITE_TIMEOUT_SECONDS = 10L
    }

    private val writingThreads = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
    private val readingThreads = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
    private val readsSeen = java.util.concurrent.CountDownLatch(EXPECTED_WRITES)
    private val writtenValues = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val writesSeen = java.util.concurrent.CountDownLatch(EXPECTED_WRITES)

    override fun setUp() {
        super.setUp()
        writingThreads.clear()
        readingThreads.clear()
        PasswordSafeKeyStorage.resetCacheForTesting()

        // A blanket Answer rather than stubbed matchers: PasswordSafe.set takes a non-null Kotlin
        // parameter, and Mockito's any() hands back null for it.
        val recorder = mock(
            PasswordSafe::class.java,
            Answer { invocation ->
                if (invocation.method.name == "getPassword") {
                    readingThreads += ApplicationManager.getApplication().isDispatchThread
                    readsSeen.countDown()
                }
                if (invocation.method.name == "set") {
                    writingThreads += ApplicationManager.getApplication().isDispatchThread
                    writtenValues += (invocation.arguments[1] as? Credentials)?.getPasswordAsString().orEmpty()
                    writesSeen.countDown()
                }
                null
            }
        )

        ApplicationManager.getApplication()
            .replaceService(PasswordSafe::class.java, recorder, testRootDisposable)
    }

    fun testClearingAllKeysDoesNotWriteToPasswordSafeOnTheEdt() {
        assertTrue(
            "the test must start on the EDT, which is where the reported failure happened",
            ApplicationManager.getApplication().isDispatchThread
        )

        PasswordSafeKeyStorage.clearAll()

        // The write is deferred, so waiting for it is what makes a green result mean anything:
        // without this the assertions would pass simply because nothing had happened yet.
        assertTrue(
            "timed out waiting for the credential writes; $writingThreads seen",
            writesSeen.await(WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        )
        assertEquals("both keys should have been written", EXPECTED_WRITES, writingThreads.size)
        assertFalse(
            "PasswordSafe was written to on the EDT: $writingThreads",
            writingThreads.any { it }
        )
    }

    fun testSavingAKeyFromTheSetupWizardDoesNotWriteOnTheEdtEither() {
        // The setup wizard saves both keys from a dialog button handler, which is the EDT. Nobody
        // reported this one; it produced the same platform error as logout and went unnoticed.
        PasswordSafeKeyStorage.setApiKey("some-key")
        PasswordSafeKeyStorage.setProvisioningKey("")

        assertTrue(
            "timed out waiting for the credential writes; $writingThreads seen",
            writesSeen.await(WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        )
        assertFalse("PasswordSafe was written to on the EDT: $writingThreads", writingThreads.any { it })
    }

    fun testTheValueReadBackIsTheNewOneBeforeTheStoreHasCaughtUp() {
        // What deferring the write must NOT cost: the setter's value has to be readable the moment
        // it returns, or every caller would have to learn that saving a key is now asynchronous.
        PasswordSafeKeyStorage.setApiKey("fresh-key")

        assertEquals("fresh-key", PasswordSafeKeyStorage.getApiKey())
    }

    fun testPreloadingTheCacheDoesNotReadTheCredentialStoreOnTheEdt() {
        // preloadKeys() is called from the dynamic-plugin listener, and the platform loads plugins
        // on the EDT under a write action, so reading the store straight from there produced the
        // same error the setters did - twice, on every install, update or enable without restart.
        PasswordSafeKeyStorage.resetCacheForTesting()

        PasswordSafeKeyStorage.preloadKeys()

        assertTrue(
            "timed out waiting for the preload; $readingThreads seen",
            readsSeen.await(WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        )
        assertFalse("the credential store was read on the EDT: $readingThreads", readingThreads.any { it })
    }

    /**
     * Reported: the status bar asked whether the plugin is configured while it was being installed,
     * on the EDT, before anything had warmed the cache, and the key was read from the store right
     * there. The EDT is told "not known yet" instead, the store is read in the background, and the
     * settings listeners hear once the key is known.
     */
    fun testAKeyAskedForOnTheEdtBeforeTheCacheIsWarmIsReadInTheBackground() {
        val readOnEdt = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val stored = mock(
            PasswordSafe::class.java,
            Answer { invocation ->
                if (invocation.method.name == "getPassword") {
                    readOnEdt += ApplicationManager.getApplication().isDispatchThread
                    "stored-key"
                } else {
                    null
                }
            }
        )
        ApplicationManager.getApplication().replaceService(PasswordSafe::class.java, stored, testRootDisposable)
        PasswordSafeKeyStorage.resetCacheForTesting()
        val heard = java.util.concurrent.atomic.AtomicInteger()
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener.TOPIC,
            object : org.zhavoronkov.openrouter.listeners.OpenRouterSettingsListener {
                override fun onSettingsChanged() {
                    heard.incrementAndGet()
                }
            }
        )

        assertNull("not known yet, rather than read on the EDT", PasswordSafeKeyStorage.getApiKey())

        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching(
            "the key was never loaded",
            { PasswordSafeKeyStorage.getApiKey() != null && heard.get() > 0 },
            WRITE_TIMEOUT_SECONDS.toInt()
        )
        assertEquals("stored-key", PasswordSafeKeyStorage.getApiKey())
        assertFalse("the credential store was read on the EDT: $readOnEdt", readOnEdt.any { it })
    }
}
