package org.zhavoronkov.openrouter.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.api.BalanceData
import org.zhavoronkov.openrouter.api.BalanceProvider

/**
 * Unit tests for [BalanceProviderNotifier] service.
 *
 * Tests cover:
 * - Service structure and Disposable implementation
 * - Method existence verification
 * - BalanceProvider interface contract
 *
 * Note: These tests verify class structure without requiring IntelliJ platform initialization.
 * Integration tests with actual extension point registration require IDE environment.
 */
@DisplayName("BalanceProviderNotifier Tests")
class BalanceProviderNotifierTest {

    companion object {
        private const val DEFAULT_TIMESTAMP = 1711526400000L
    }

    @Nested
    @DisplayName("Service Structure Tests")
    inner class ServiceStructureTests {

        @Test
        @DisplayName("BalanceProviderNotifier should implement Disposable")
        fun implementsDisposable() {
            assertTrue(
                com.intellij.openapi.Disposable::class.java.isAssignableFrom(BalanceProviderNotifier::class.java),
                "BalanceProviderNotifier must implement Disposable for dynamic plugin support"
            )
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have getInstance method")
        fun hasGetInstanceMethod() {
            val method = BalanceProviderNotifier.Companion::class.java
                .getDeclaredMethod("getInstance")
            assertNotNull(method, "BalanceProviderNotifier should have getInstance() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have getInstanceOrNull method")
        fun hasGetInstanceOrNullMethod() {
            val method = BalanceProviderNotifier.Companion::class.java
                .getDeclaredMethod("getInstanceOrNull")
            assertNotNull(method, "BalanceProviderNotifier should have getInstanceOrNull() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have notifyBalanceUpdated method")
        fun hasNotifyBalanceUpdatedMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("notifyBalanceUpdated", BalanceData::class.java)
            assertNotNull(method, "BalanceProviderNotifier should have notifyBalanceUpdated() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have notifyLoading method")
        fun hasNotifyLoadingMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("notifyLoading")
            assertNotNull(method, "BalanceProviderNotifier should have notifyLoading() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have notifyError method")
        fun hasNotifyErrorMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("notifyError", String::class.java)
            assertNotNull(method, "BalanceProviderNotifier should have notifyError() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have hasProviders method")
        fun hasHasProvidersMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("hasProviders")
            assertNotNull(method, "BalanceProviderNotifier should have hasProviders() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have getProviderCount method")
        fun hasGetProviderCountMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("getProviderCount")
            assertNotNull(method, "BalanceProviderNotifier should have getProviderCount() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have getProviderNames method")
        fun hasGetProviderNamesMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("getProviderNames")
            assertNotNull(method, "BalanceProviderNotifier should have getProviderNames() method")
        }

        @Test
        @DisplayName("BalanceProviderNotifier should have dispose method")
        fun hasDisposeMethod() {
            val method = BalanceProviderNotifier::class.java
                .getDeclaredMethod("dispose")
            assertNotNull(method, "BalanceProviderNotifier should have dispose() method")
        }
    }

    @Nested
    @DisplayName("BalanceProvider Interface Tests")
    inner class BalanceProviderInterfaceTests {

        @Test
        @DisplayName("BalanceProvider should have onBalanceUpdated method")
        fun hasOnBalanceUpdatedMethod() {
            val method = BalanceProvider::class.java
                .getDeclaredMethod("onBalanceUpdated", BalanceData::class.java)
            assertNotNull(method, "BalanceProvider should have onBalanceUpdated() method")
        }

        @Test
        @DisplayName("BalanceProvider should have onBalanceLoading method")
        fun hasOnBalanceLoadingMethod() {
            val method = BalanceProvider::class.java
                .getDeclaredMethod("onBalanceLoading")
            assertNotNull(method, "BalanceProvider should have onBalanceLoading() method")
            // Kotlin default methods work - verified by MinimalProvider tests
        }

        @Test
        @DisplayName("BalanceProvider should have onBalanceError method")
        fun hasOnBalanceErrorMethod() {
            val method = BalanceProvider::class.java
                .getDeclaredMethod("onBalanceError", String::class.java)
            assertNotNull(method, "BalanceProvider should have onBalanceError() method")
            // Kotlin default methods work - verified by MinimalProvider tests
        }
    }

    /**
     * A test implementation of BalanceProvider for unit testing.
     */
    private class TestBalanceProvider : BalanceProvider {
        var lastBalanceData: BalanceData? = null
        var loadingCalled = false
        var lastError: String? = null
        var updateCount = 0

        override fun onBalanceUpdated(data: BalanceData) {
            lastBalanceData = data
            updateCount++
        }

        override fun onBalanceLoading() {
            loadingCalled = true
        }

        override fun onBalanceError(error: String) {
            lastError = error
        }

        fun reset() {
            lastBalanceData = null
            loadingCalled = false
            lastError = null
            updateCount = 0
        }
    }

    @Nested
    @DisplayName("Mock Provider Tests")
    inner class MockProviderTests {

        @Test
        @DisplayName("Test provider should receive balance updates")
        fun testProviderReceivesUpdates() {
            val provider = TestBalanceProvider()
            val testData = createTestBalanceData()

            provider.onBalanceUpdated(testData)

            assertNotNull(provider.lastBalanceData)
            assertEquals(testData, provider.lastBalanceData)
            assertEquals(1, provider.updateCount)
        }

        @Test
        @DisplayName("Test provider should receive loading notifications")
        fun testProviderReceivesLoading() {
            val provider = TestBalanceProvider()

            assertFalse(provider.loadingCalled)
            provider.onBalanceLoading()
            assertTrue(provider.loadingCalled)
        }

        @Test
        @DisplayName("Test provider should receive error notifications")
        fun testProviderReceivesError() {
            val provider = TestBalanceProvider()

            val errorMessage = "Test error message"
            provider.onBalanceError(errorMessage)

            assertEquals(errorMessage, provider.lastError)
        }

        @Test
        @DisplayName("Test provider should track multiple updates")
        fun testProviderTracksMultipleUpdates() {
            val provider = TestBalanceProvider()

            repeat(5) {
                provider.onBalanceUpdated(createTestBalanceData())
            }

            assertEquals(5, provider.updateCount)
        }

        @Test
        @DisplayName("Test provider reset should clear state")
        fun testProviderReset() {
            val provider = TestBalanceProvider()

            provider.onBalanceUpdated(createTestBalanceData())
            provider.onBalanceLoading()
            provider.onBalanceError("error")

            provider.reset()

            assertFalse(provider.loadingCalled)
            assertEquals(null, provider.lastBalanceData)
            assertEquals(null, provider.lastError)
            assertEquals(0, provider.updateCount)
        }

        private fun createTestBalanceData(): BalanceData {
            return BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )
        }
    }

    /**
     * A provider that throws exceptions for testing isolation.
     */
    private class ThrowingProvider : BalanceProvider {
        override fun onBalanceUpdated(data: BalanceData) {
            throw RuntimeException("Test exception from onBalanceUpdated")
        }

        override fun onBalanceLoading() {
            throw RuntimeException("Test exception from onBalanceLoading")
        }

        override fun onBalanceError(error: String) {
            throw RuntimeException("Test exception from onBalanceError")
        }
    }

    @Nested
    @DisplayName("Exception Handling Provider Tests")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("Throwing provider should not prevent interface implementation")
        fun throwingProviderImplementsInterface() {
            val provider = ThrowingProvider()
            assertTrue(provider is BalanceProvider)
        }

        @Test
        @DisplayName("Throwing provider onBalanceUpdated should throw expected exception")
        fun throwingProviderThrows() {
            val provider = ThrowingProvider()
            val testData = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )

            var exceptionThrown = false
            try {
                provider.onBalanceUpdated(testData)
            } catch (e: RuntimeException) {
                exceptionThrown = true
                assertTrue(e.message?.contains("Test exception") == true)
            }
            assertTrue(exceptionThrown, "Expected RuntimeException to be thrown")
        }
    }

    /**
     * A minimal provider that only implements the required method.
     */
    private class MinimalProvider : BalanceProvider {
        var lastData: BalanceData? = null

        override fun onBalanceUpdated(data: BalanceData) {
            lastData = data
        }
        // onBalanceLoading and onBalanceError use default implementations
    }

    @Nested
    @DisplayName("Default Implementation Tests")
    inner class DefaultImplementationTests {

        @Test
        @DisplayName("Minimal provider should work with default onBalanceLoading")
        fun minimalProviderDefaultLoading() {
            val provider = MinimalProvider()
            // Should not throw - uses default empty implementation
            provider.onBalanceLoading()
        }

        @Test
        @DisplayName("Minimal provider should work with default onBalanceError")
        fun minimalProviderDefaultError() {
            val provider = MinimalProvider()
            // Should not throw - uses default empty implementation
            provider.onBalanceError("Test error")
        }

        @Test
        @DisplayName("Minimal provider should receive balance updates")
        fun minimalProviderReceivesUpdates() {
            val provider = MinimalProvider()
            val testData = BalanceData(
                totalCredits = 100.0,
                totalUsage = 25.0,
                remainingCredits = 75.0,
                timestamp = DEFAULT_TIMESTAMP
            )

            provider.onBalanceUpdated(testData)
            assertEquals(testData, provider.lastData)
        }
    }
}
