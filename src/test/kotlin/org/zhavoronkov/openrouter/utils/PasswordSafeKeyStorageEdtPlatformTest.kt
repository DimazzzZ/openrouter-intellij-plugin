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
    private val writtenValues = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val writesSeen = java.util.concurrent.CountDownLatch(EXPECTED_WRITES)

    override fun setUp() {
        super.setUp()
        writingThreads.clear()
        PasswordSafeKeyStorage.resetCacheForTesting()

        // A blanket Answer rather than stubbed matchers: PasswordSafe.set takes a non-null Kotlin
        // parameter, and Mockito's any() hands back null for it.
        val recorder = mock(
            PasswordSafe::class.java,
            Answer { invocation ->
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
}
