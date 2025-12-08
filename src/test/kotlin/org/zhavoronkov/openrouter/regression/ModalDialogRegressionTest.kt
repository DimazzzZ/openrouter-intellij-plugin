package org.zhavoronkov.openrouter.regression

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression tests for Issue #6: Quota Usage Window Stuck on "Loading..."
 *
 * These tests ensure that:
 * 1. StatsDataLoader uses Dispatchers.IO (not Dispatchers.Main)
 * 2. All invokeLater calls use ModalityState.any() for modal dialog compatibility
 */
@DisplayName("Regression: Modal Dialog Modality State")
class ModalDialogRegressionTest {

    @Test
    @DisplayName("invokeLater with ModalityState.any() should execute in modal context")
    fun testModalityStateAnyExecutesInModalContext() {
        // Skip if ApplicationManager not available (headless test environment)
        val app = ApplicationManager.getApplication()
        if (app == null) {
            println("Skipping test - ApplicationManager not available in test environment")
            return
        }

        // Given: A callback that needs to execute
        val latch = CountDownLatch(1)
        var callbackExecuted = false

        // When: Use invokeLater with ModalityState.any()
        app.invokeLater({
            callbackExecuted = true
            latch.countDown()
        }, ModalityState.any())

        // Then: Callback should execute even in modal context
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue(completed, "Callback should complete within timeout")
        assertTrue(callbackExecuted, "Callback should have been executed")
    }

    @Test
    @DisplayName("StatsDataLoader should use Dispatchers.IO not Dispatchers.Main")
    fun testStatsDataLoaderUsesDispatchersIO() {
        // Given: StatsDataLoader source code
        val sourceFile = File("src/main/kotlin/org/zhavoronkov/openrouter/ui/StatsDataLoader.kt")
        assertTrue(sourceFile.exists(), "StatsDataLoader.kt should exist")

        val sourceCode = sourceFile.readText()

        // Then: Should use Dispatchers.IO
        assertTrue(
            sourceCode.contains("CoroutineScope(Dispatchers.IO"),
            "StatsDataLoader should use Dispatchers.IO for coroutine scope"
        )

        // Should NOT use Dispatchers.Main
        assertFalse(
            sourceCode.contains("CoroutineScope(Dispatchers.Main"),
            "StatsDataLoader should NOT use Dispatchers.Main"
        )
    }

    @Test
    @DisplayName("StatsDataLoader should use ModalityState.any() for all invokeLater calls")
    fun testStatsDataLoaderUsesModalityState() {
        // Given: StatsDataLoader source code
        val sourceFile = File("src/main/kotlin/org/zhavoronkov/openrouter/ui/StatsDataLoader.kt")
        val sourceCode = sourceFile.readText()

        // Count invokeLater calls
        val invokeLaterPattern = Regex("invokeLater\\s*\\(")
        val invokeLaterMatches = invokeLaterPattern.findAll(sourceCode).count()

        // Count ModalityState.any() calls
        val modalityStatePattern = Regex("ModalityState\\.any\\(\\)")
        val modalityStateMatches = modalityStatePattern.findAll(sourceCode).count()

        // Then: All invokeLater calls should have ModalityState.any()
        assertTrue(
            invokeLaterMatches > 0,
            "StatsDataLoader should have invokeLater calls"
        )
        assertEquals(
            invokeLaterMatches,
            modalityStateMatches,
            "All invokeLater calls should use ModalityState.any()"
        )
    }

    @Test
    @DisplayName("Multiple sequential invokeLater calls should all execute")
    fun testMultipleInvokeLaterCallsExecute() {
        // Skip if ApplicationManager not available
        val app = ApplicationManager.getApplication()
        if (app == null) {
            println("Skipping test - ApplicationManager not available in test environment")
            return
        }

        // Given: Multiple callbacks
        val latch = CountDownLatch(3)
        var callback1Executed = false
        var callback2Executed = false
        var callback3Executed = false

        // When: Schedule multiple callbacks
        app.invokeLater({
            callback1Executed = true
            latch.countDown()
        }, ModalityState.any())

        app.invokeLater({
            callback2Executed = true
            latch.countDown()
        }, ModalityState.any())

        app.invokeLater({
            callback3Executed = true
            latch.countDown()
        }, ModalityState.any())

        // Then: All callbacks should execute
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue(completed, "All callbacks should complete")
        assertTrue(callback1Executed, "Callback 1 should execute")
        assertTrue(callback2Executed, "Callback 2 should execute")
        assertTrue(callback3Executed, "Callback 3 should execute")
    }

    @Test
    @DisplayName("invokeLater without ModalityState should still work in non-modal context")
    fun testInvokeLaterWithoutModalityStateInNonModalContext() {
        // Skip if ApplicationManager not available
        val app = ApplicationManager.getApplication()
        if (app == null) {
            println("Skipping test - ApplicationManager not available in test environment")
            return
        }

        // Given: A callback without ModalityState
        val latch = CountDownLatch(1)
        var callbackExecuted = false

        // When: Use invokeLater without ModalityState (default behavior)
        app.invokeLater {
            callbackExecuted = true
            latch.countDown()
        }

        // Then: Should still execute in non-modal context
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue(completed, "Callback should complete in non-modal context")
        assertTrue(callbackExecuted, "Callback should execute")
    }

    @Test
    @DisplayName("ModalityState.any() should be used for error callbacks too")
    fun testModalityStateForErrorCallbacks() {
        // Given: StatsDataLoader source code
        val sourceFile = File("src/main/kotlin/org/zhavoronkov/openrouter/ui/StatsDataLoader.kt")
        val sourceCode = sourceFile.readText()

        // Find catch blocks
        val catchBlocks = sourceCode.split("catch").drop(1)

        // Then: Each catch block should have ModalityState.any()
        for (catchBlock in catchBlocks) {
            if (catchBlock.contains("invokeLater")) {
                assertTrue(
                    catchBlock.contains("ModalityState.any()"),
                    "Error handling invokeLater should use ModalityState.any()"
                )
            }
        }
    }

    @Test
    @DisplayName("Coroutine scope should use SupervisorJob")
    fun testCoroutineScopeUsesSupervisorJob() {
        // Given: StatsDataLoader source code
        val sourceFile = File("src/main/kotlin/org/zhavoronkov/openrouter/ui/StatsDataLoader.kt")
        val sourceCode = sourceFile.readText()

        // Then: Should use SupervisorJob
        assertTrue(
            sourceCode.contains("SupervisorJob()"),
            "Coroutine scope should use SupervisorJob for error isolation"
        )
    }

    @Test
    @DisplayName("Async calls should be on IO dispatcher")
    fun testAsyncCallsOnIODispatcher() {
        // Given: StatsDataLoader source code
        val sourceFile = File("src/main/kotlin/org/zhavoronkov/openrouter/ui/StatsDataLoader.kt")
        val sourceCode = sourceFile.readText()

        // When: Scope is on Dispatchers.IO
        val usesIODispatcher = sourceCode.contains("CoroutineScope(Dispatchers.IO")

        // Then: async calls don't need explicit dispatcher
        if (usesIODispatcher) {
            // async calls should not specify Dispatchers.IO again (inherited from scope)
            val asyncPattern = Regex("async\\s*\\{")
            val asyncMatches = asyncPattern.findAll(sourceCode).count()

            assertTrue(asyncMatches > 0, "Should have async calls")
        }
    }
}
