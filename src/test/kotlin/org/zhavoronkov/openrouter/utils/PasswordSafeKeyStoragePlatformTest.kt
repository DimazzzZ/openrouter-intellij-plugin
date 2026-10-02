package org.zhavoronkov.openrouter.utils

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The key storage off the EDT, and against a credential store that fails. Which thread the store is
 * reached on from the EDT is pinned in PasswordSafeKeyStorageEdtPlatformTest.
 */
class PasswordSafeKeyStoragePlatformTest : BasePlatformTestCase() {

    private val calls = CopyOnWriteArrayList<String>()
    private var failing = false

    override fun setUp() {
        super.setUp()
        val store = mock(
            PasswordSafe::class.java,
            Answer { invocation ->
                calls += invocation.method.name
                if (failing && invocation.method.name in setOf("set", "getPassword")) error("store unavailable")
                if (invocation.method.name == "getPassword") "stored-key" else null
            }
        )
        ApplicationManager.getApplication().replaceService(PasswordSafe::class.java, store, testRootDisposable)
        PasswordSafeKeyStorage.resetCacheForTesting()
    }

    override fun tearDown() {
        try {
            PasswordSafeKeyStorage.resetCacheForTesting()
        } finally {
            super.tearDown()
        }
    }

    private fun <T> offEdt(read: () -> T): T =
        ApplicationManager.getApplication().executeOnPooledThread<T> { read() }.get(WAIT_SECONDS, TimeUnit.SECONDS)

    fun testOffTheEdtAColdKeyIsReadFromTheStoreAtOnce() {
        assertEquals("stored-key", offEdt { PasswordSafeKeyStorage.getApiKey() })
        assertEquals("stored-key", offEdt { PasswordSafeKeyStorage.getProvisioningKey() })
    }

    fun testOnTheEdtAColdManagementKeyIsNotKnownYetAndIsReadInTheBackground() {
        assertNull("the EDT is told it is not known yet", PasswordSafeKeyStorage.getProvisioningKey())

        PlatformTestUtil.waitWithEventsDispatching(
            "the warm-up never finished",
            { offEdt { PasswordSafeKeyStorage.getProvisioningKey() } == "stored-key" },
            WAIT_SECONDS.toInt()
        )
    }

    fun testOffTheEdtAKeyIsWrittenAtOnce() {
        offEdt { PasswordSafeKeyStorage.setApiKey("new-key") }

        assertTrue("written on the calling thread: $calls", "set" in calls)
    }

    fun testAStoreThatFailsLeavesTheKeysKnownFromMemoryAndAWriteFromTheEdtUnharmed() {
        failing = true

        assertNull("no key, but known", offEdt { PasswordSafeKeyStorage.getApiKey() })
        PasswordSafeKeyStorage.setApiKey("kept-in-memory")
        PlatformTestUtil.waitWithEventsDispatching(
            "the deferred write never ran",
            { "set" in calls },
            WAIT_SECONDS.toInt()
        )

        assertEquals("kept-in-memory", PasswordSafeKeyStorage.getApiKey())
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
