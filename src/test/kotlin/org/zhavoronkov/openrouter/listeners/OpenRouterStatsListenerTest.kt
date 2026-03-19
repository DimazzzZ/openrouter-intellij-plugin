package org.zhavoronkov.openrouter.listeners

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ActivityData
import org.zhavoronkov.openrouter.models.CreditsData

/**
 * Tests for OpenRouterStatsListener interface
 */
@DisplayName("OpenRouterStatsListener Tests")
class OpenRouterStatsListenerTest {

    @Nested
    @DisplayName("Topic Configuration Tests")
    inner class TopicConfigurationTests {

        @Test
        @DisplayName("TOPIC should be available and non-null")
        fun testTopicIsAvailable() {
            assertNotNull(OpenRouterStatsListener.TOPIC, "TOPIC should not be null")
        }

        @Test
        @DisplayName("TOPIC should have correct display name")
        fun testTopicDisplayName() {
            val topicName = OpenRouterStatsListener.TOPIC.displayName
            assertTrue(
                topicName.contains("OpenRouter") && topicName.contains("Stats"),
                "Topic display name should contain 'OpenRouter' and 'Stats'"
            )
        }
    }

    @Nested
    @DisplayName("Listener Interface Tests")
    inner class ListenerInterfaceTests {

        @Test
        @DisplayName("Listener should accept onStatsUpdated with credits and activity")
        fun testOnStatsUpdated() {
            var callbackInvoked = false
            val credits = CreditsData(totalCredits = 100.0, totalUsage = 25.0)
            val activity = listOf(
                ActivityData(
                    date = "2025-01-01",
                    model = "test-model",
                    modelPermaslug = "test-model",
                    endpointId = "endpoint-1",
                    providerName = "test-provider",
                    usage = 5.0,
                    byokUsageInference = null,
                    requests = 10,
                    promptTokens = 100,
                    completionTokens = 50,
                    reasoningTokens = null
                )
            )

            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    callbackInvoked = true
                }
            }

            listener.onStatsUpdated(credits, activity)

            assertTrue(callbackInvoked, "onStatsUpdated should be invoked")
        }

        @Test
        @DisplayName("Listener should accept onStatsUpdated with null activity")
        fun testOnStatsUpdatedWithNullActivity() {
            var callbackInvoked = false
            val credits = CreditsData(totalCredits = 100.0, totalUsage = 25.0)

            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    callbackInvoked = true
                }
            }

            listener.onStatsUpdated(credits, null)

            assertTrue(callbackInvoked, "onStatsUpdated should be invoked with null activity")
        }

        @Test
        @DisplayName("onStatsLoading should have default empty implementation")
        fun testOnStatsLoadingDefaultImplementation() {
            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    // intentionally empty - testing default implementation
                }
            }

            // Should not throw - default implementation is empty
            listener.onStatsLoading()
        }

        @Test
        @DisplayName("onStatsError should have default empty implementation")
        fun testOnStatsErrorDefaultImplementation() {
            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    // intentionally empty - testing default implementation
                }
            }

            // Should not throw - default implementation is empty
            listener.onStatsError("Test error message")
        }

        @Test
        @DisplayName("Listener should allow custom onStatsLoading implementation")
        fun testCustomOnStatsLoading() {
            var loadingCalled = false

            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    // intentionally empty - testing custom onStatsLoading
                }
                override fun onStatsLoading() {
                    loadingCalled = true
                }
            }

            listener.onStatsLoading()

            assertTrue(loadingCalled, "Custom onStatsLoading should be invoked")
        }

        @Test
        @DisplayName("Listener should allow custom onStatsError implementation")
        fun testCustomOnStatsError() {
            var errorMessage: String? = null

            val listener = object : OpenRouterStatsListener {
                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    // intentionally empty - testing custom onStatsError
                }
                override fun onStatsError(message: String) {
                    errorMessage = message
                }
            }

            listener.onStatsError("Network error")

            assertTrue(errorMessage == "Network error", "Custom onStatsError should receive the error message")
        }
    }

    @Nested
    @DisplayName("Integration Pattern Tests")
    inner class IntegrationPatternTests {

        @Test
        @DisplayName("Listener should support full lifecycle: loading -> success")
        fun testFullLifecycleSuccess() {
            var state = "idle"
            val credits = CreditsData(totalCredits = 100.0, totalUsage = 25.0)

            val listener = object : OpenRouterStatsListener {
                override fun onStatsLoading() {
                    state = "loading"
                }

                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    state = "success"
                }

                override fun onStatsError(errorMessage: String) {
                    state = "error"
                }
            }

            listener.onStatsLoading()
            assertTrue(state == "loading", "State should be loading")

            listener.onStatsUpdated(credits, null)
            assertTrue(state == "success", "State should be success")
        }

        @Test
        @DisplayName("Listener should support full lifecycle: loading -> error")
        fun testFullLifecycleError() {
            var state = "idle"

            val listener = object : OpenRouterStatsListener {
                override fun onStatsLoading() {
                    state = "loading"
                }

                override fun onStatsUpdated(credits: CreditsData, activity: List<ActivityData>?) {
                    state = "success"
                }

                override fun onStatsError(errorMessage: String) {
                    state = "error"
                }
            }

            listener.onStatsLoading()
            assertTrue(state == "loading", "State should be loading")

            listener.onStatsError("Network timeout")
            assertTrue(state == "error", "State should be error")
        }
    }
}
