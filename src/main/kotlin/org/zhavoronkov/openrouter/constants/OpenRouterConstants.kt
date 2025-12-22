package org.zhavoronkov.openrouter.constants

/**
 * Centralized constants for the OpenRouter plugin
 * This file consolidates magic numbers and configuration values used across the codebase
 */
object OpenRouterConstants {

    // ========== API Configuration ==========

    /** Base URL for OpenRouter API */
    const val BASE_URL = "https://openrouter.ai/api/v1"

    /** OpenRouter website URL */
    const val WEBSITE_URL = "https://openrouter.ai"

    /** OpenRouter documentation URL */
    const val DOCUMENTATION_URL = "https://openrouter.ai/docs"

    /** OpenRouter feedback repository URL */
    const val FEEDBACK_URL = "https://github.com/OpenRouterTeam/openrouter-feedback"

    // ========== Timeout Configuration ==========

    /** HTTP connection timeout in seconds */
    const val CONNECT_TIMEOUT_SECONDS = 30L

    /** HTTP read timeout in seconds */
    const val READ_TIMEOUT_SECONDS = 60L

    /** HTTP write timeout in seconds */
    const val WRITE_TIMEOUT_SECONDS = 60L

    /** API request timeout in milliseconds */
    const val API_TIMEOUT_MS = 30000L

    /** Connection test timeout in milliseconds */
    const val CONNECTION_TEST_TIMEOUT_MS = 10000L

    /** Model loading timeout in milliseconds */
    const val MODEL_LOADING_TIMEOUT_MS = 30000L

    /** PKCE server timeout in milliseconds (2 minutes) */
    const val PKCE_SERVER_TIMEOUT_MS = 120000L

    // ========== Cache Configuration ==========

    /** Models cache duration in milliseconds (5 minutes) */
    const val MODELS_CACHE_DURATION_MS = 300000L

    /** Activity cache timeout in milliseconds */
    const val ACTIVITY_CACHE_TIMEOUT_MS = 10000L

    /** Proxy models cache TTL in minutes */
    const val PROXY_MODELS_CACHE_TTL_MINUTES = 15L

    // ========== Retry and Delay Configuration ==========

    /** Retry delay in seconds */
    const val RETRY_DELAY_SECONDS = 8L

    /** Retry delay in milliseconds */
    const val RETRY_DELAY_MS = 8000L

    /** Key validation debounce delay in milliseconds */
    const val KEY_VALIDATION_DEBOUNCE_MS = 500L

    // ========== Port Configuration ==========

    /** Default proxy server port */
    const val DEFAULT_PROXY_PORT = 8080

    /** PKCE OAuth callback server port */
    const val PKCE_PORT = 3000

    /** Minimum allowed port number */
    const val MIN_PORT = 1024

    /** Maximum allowed port number */
    const val MAX_PORT = 65535

    // ========== String Formatting ==========

    /** Length for truncating API keys in logs */
    const val API_KEY_TRUNCATE_LENGTH = 10

    /** Length for truncating strings in logs */
    const val STRING_TRUNCATE_LENGTH = 10

    /** Response preview length for logging */
    const val RESPONSE_PREVIEW_LENGTH = 500

    /** Small response preview length for logging */
    const val RESPONSE_PREVIEW_LENGTH_SMALL = 200

    /** Auth header preview length for logging */
    const val AUTH_HEADER_PREVIEW_LENGTH = 20

    // ========== Validation ==========

    /** Minimum API key length for validation */
    const val PKCE_KEY_MIN_LENGTH = 10

    /** Minimum key length for validation */
    const val MIN_KEY_LENGTH = 10

    // ========== UI Configuration ==========

    /** Default refresh interval in seconds */
    const val DEFAULT_REFRESH_INTERVAL = 300

    /** Minimum refresh interval in seconds */
    const val MIN_REFRESH_INTERVAL = 60

    /** Maximum refresh interval in seconds */
    const val MAX_REFRESH_INTERVAL = 3600

    /** Default max tokens for requests */
    const val DEFAULT_MAX_TOKENS = 8000

    /** Minimum max tokens */
    const val MIN_MAX_TOKENS = 1

    /** Maximum max tokens */
    const val MAX_MAX_TOKENS = 200000

    /** Maximum tracked generations */
    const val DEFAULT_MAX_TRACKED_GENERATIONS = 100

    // ========== Conversion Constants ==========

    /** Nanoseconds to milliseconds conversion factor */
    const val NANOSECONDS_TO_MILLISECONDS = 1_000_000L

    /** Milliseconds per second */
    const val MILLIS_PER_SECOND = 1000L

    /** Percentage multiplier */
    const val PERCENTAGE_MULTIPLIER = 100
}
