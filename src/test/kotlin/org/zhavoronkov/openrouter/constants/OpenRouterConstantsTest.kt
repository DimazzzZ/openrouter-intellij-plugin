package org.zhavoronkov.openrouter.constants

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("OpenRouterConstants Tests")
class OpenRouterConstantsTest {

    @Nested
    @DisplayName("API Configuration")
    inner class ApiConfigurationTests {

        @Test
        fun `BASE_URL should point to OpenRouter API`() {
            assertEquals("https://openrouter.ai/api/v1", OpenRouterConstants.BASE_URL)
            assertTrue(OpenRouterConstants.BASE_URL.startsWith("https://"))
        }

        @Test
        fun `WEBSITE_URL should point to OpenRouter website`() {
            assertEquals("https://openrouter.ai", OpenRouterConstants.WEBSITE_URL)
        }

        @Test
        fun `DOCUMENTATION_URL should point to OpenRouter docs`() {
            assertEquals("https://openrouter.ai/docs", OpenRouterConstants.DOCUMENTATION_URL)
        }

        @Test
        fun `FEEDBACK_URL should point to GitHub repository`() {
            assertTrue(OpenRouterConstants.FEEDBACK_URL.contains("github.com"))
        }
    }

    @Nested
    @DisplayName("Timeout Configuration")
    inner class TimeoutConfigurationTests {

        @Test
        fun `CONNECT_TIMEOUT_SECONDS is 30`() {
            assertEquals(30L, OpenRouterConstants.CONNECT_TIMEOUT_SECONDS)
        }

        @Test
        fun `READ_TIMEOUT_SECONDS is 60`() {
            assertEquals(60L, OpenRouterConstants.READ_TIMEOUT_SECONDS)
        }

        @Test
        fun `WRITE_TIMEOUT_SECONDS is 60`() {
            assertEquals(60L, OpenRouterConstants.WRITE_TIMEOUT_SECONDS)
        }

        @Test
        fun `API_TIMEOUT_MS is 30 seconds`() {
            assertEquals(30000L, OpenRouterConstants.API_TIMEOUT_MS)
        }

        @Test
        fun `CONNECTION_TEST_TIMEOUT_MS is 10 seconds`() {
            assertEquals(10000L, OpenRouterConstants.CONNECTION_TEST_TIMEOUT_MS)
        }

        @Test
        fun `PKCE_SERVER_TIMEOUT_MS is 2 minutes`() {
            assertEquals(120000L, OpenRouterConstants.PKCE_SERVER_TIMEOUT_MS)
        }

        @Test
        fun `MODEL_LOADING_TIMEOUT_MS is 30 seconds`() {
            assertEquals(30000L, OpenRouterConstants.MODEL_LOADING_TIMEOUT_MS)
        }
    }

    @Nested
    @DisplayName("Cache Configuration")
    inner class CacheConfigurationTests {

        @Test
        fun `MODELS_CACHE_DURATION_MS is 5 minutes`() {
            assertEquals(300000L, OpenRouterConstants.MODELS_CACHE_DURATION_MS)
        }

        @Test
        fun `ACTIVITY_CACHE_TIMEOUT_MS is 10 seconds`() {
            assertEquals(10000L, OpenRouterConstants.ACTIVITY_CACHE_TIMEOUT_MS)
        }

        @Test
        fun `PROXY_MODELS_CACHE_TTL_MINUTES is 15 minutes`() {
            assertEquals(15L, OpenRouterConstants.PROXY_MODELS_CACHE_TTL_MINUTES)
        }
    }

    @Nested
    @DisplayName("Retry Configuration")
    inner class RetryConfigurationTests {

        @Test
        fun `RETRY_DELAY_SECONDS is 8`() {
            assertEquals(8L, OpenRouterConstants.RETRY_DELAY_SECONDS)
        }

        @Test
        fun `RETRY_DELAY_MS is 8000`() {
            assertEquals(8000L, OpenRouterConstants.RETRY_DELAY_MS)
        }

        @Test
        fun `KEY_VALIDATION_DEBOUNCE_MS is 500`() {
            assertEquals(500L, OpenRouterConstants.KEY_VALIDATION_DEBOUNCE_MS)
        }
    }

    @Nested
    @DisplayName("Port Configuration")
    inner class PortConfigurationTests {

        @Test
        fun `DEFAULT_PROXY_PORT is 8080`() {
            assertEquals(8080, OpenRouterConstants.DEFAULT_PROXY_PORT)
        }

        @Test
        fun `PKCE_PORT is 3000`() {
            assertEquals(3000, OpenRouterConstants.PKCE_PORT)
        }

        @Test
        fun `MIN_PORT is 1024`() {
            assertEquals(1024, OpenRouterConstants.MIN_PORT)
        }

        @Test
        fun `MAX_PORT is 65535`() {
            assertEquals(65535, OpenRouterConstants.MAX_PORT)
        }
    }

    @Nested
    @DisplayName("String Formatting")
    inner class StringFormattingTests {

        @Test
        fun `API_KEY_TRUNCATE_LENGTH is 10`() {
            assertEquals(10, OpenRouterConstants.API_KEY_TRUNCATE_LENGTH)
        }

        @Test
        fun `STRING_TRUNCATE_LENGTH is 10`() {
            assertEquals(10, OpenRouterConstants.STRING_TRUNCATE_LENGTH)
        }

        @Test
        fun `RESPONSE_PREVIEW_LENGTH is 500`() {
            assertEquals(500, OpenRouterConstants.RESPONSE_PREVIEW_LENGTH)
        }

        @Test
        fun `RESPONSE_PREVIEW_LENGTH_SMALL is 200`() {
            assertEquals(200, OpenRouterConstants.RESPONSE_PREVIEW_LENGTH_SMALL)
        }

        @Test
        fun `AUTH_HEADER_PREVIEW_LENGTH is 20`() {
            assertEquals(20, OpenRouterConstants.AUTH_HEADER_PREVIEW_LENGTH)
        }
    }

    @Nested
    @DisplayName("Validation Constants")
    inner class ValidationConstantsTests {

        @Test
        fun `MIN_KEY_LENGTH is 10`() {
            assertEquals(10, OpenRouterConstants.MIN_KEY_LENGTH)
        }

        @Test
        fun `PKCE_KEY_MIN_LENGTH equals MIN_KEY_LENGTH`() {
            assertEquals(OpenRouterConstants.MIN_KEY_LENGTH, OpenRouterConstants.PKCE_KEY_MIN_LENGTH)
        }
    }

    @Nested
    @DisplayName("UI Configuration")
    inner class UIConfigurationTests {

        @Test
        fun `DEFAULT_REFRESH_INTERVAL is 300 seconds`() {
            assertEquals(300, OpenRouterConstants.DEFAULT_REFRESH_INTERVAL)
        }

        @Test
        fun `MIN_REFRESH_INTERVAL is 60 seconds`() {
            assertEquals(60, OpenRouterConstants.MIN_REFRESH_INTERVAL)
        }

        @Test
        fun `MAX_REFRESH_INTERVAL is 3600 seconds`() {
            assertEquals(3600, OpenRouterConstants.MAX_REFRESH_INTERVAL)
        }

        @Test
        fun `DEFAULT_MAX_TOKENS is 8000`() {
            assertEquals(8000, OpenRouterConstants.DEFAULT_MAX_TOKENS)
        }

        @Test
        fun `MIN_MAX_TOKENS is 1`() {
            assertEquals(1, OpenRouterConstants.MIN_MAX_TOKENS)
        }

        @Test
        fun `MAX_MAX_TOKENS is 200000`() {
            assertEquals(200000, OpenRouterConstants.MAX_MAX_TOKENS)
        }

        @Test
        fun `DEFAULT_MAX_TRACKED_GENERATIONS is 100`() {
            assertEquals(100, OpenRouterConstants.DEFAULT_MAX_TRACKED_GENERATIONS)
        }
    }

    @Nested
    @DisplayName("Conversion Constants")
    inner class ConversionConstantsTests {

        @Test
        fun `NANOSECONDS_TO_MILLISECONDS is one million`() {
            assertEquals(1_000_000L, OpenRouterConstants.NANOSECONDS_TO_MILLISECONDS)
        }

        @Test
        fun `MILLIS_PER_SECOND is one thousand`() {
            assertEquals(1000L, OpenRouterConstants.MILLIS_PER_SECOND)
        }

        @Test
        fun `PERCENTAGE_MULTIPLIER is 100`() {
            assertEquals(100, OpenRouterConstants.PERCENTAGE_MULTIPLIER)
        }
    }
}
