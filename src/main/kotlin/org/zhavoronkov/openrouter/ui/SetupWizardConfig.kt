package org.zhavoronkov.openrouter.ui

/**
 * Configuration constants for SetupWizardDialog
 * Centralizes magic numbers and configuration values for better maintainability
 */
object SetupWizardConfig {
    // Dialog dimensions
    const val DIALOG_WIDTH = 700
    const val DIALOG_HEIGHT = 500
    const val MODELS_TABLE_WIDTH = 650
    const val MODELS_TABLE_HEIGHT = 280

    // UI constants
    const val TABLE_ROW_HEIGHT = 28
    const val CHECKBOX_COLUMN_WIDTH = 40
    const val NAME_COLUMN_WIDTH = 400
    const val URL_LABEL_FONT_SIZE = 12

    // Timeouts (milliseconds)
    const val KEY_VALIDATION_DEBOUNCE_MS = 500L
    const val MODEL_LOADING_TIMEOUT_MS = 30000L

    // Default proxy port
    const val DEFAULT_PROXY_PORT = 8000

    // String truncation length
    const val API_KEY_TRUNCATE_LENGTH = 10

    // Wizard step identifiers
    const val STEP_WELCOME = 0
    const val STEP_PROVISIONING = 1
    const val STEP_MODELS = 2
    const val STEP_COMPLETION = 3

    // PKCE Configuration
    const val PKCE_PORT = 3000
    const val PKCE_KEY_MIN_LENGTH = 10
    const val PKCE_SERVER_TIMEOUT_MS = 120000 // 2 minutes

    // Logging levels
    const val LOGGING_ENABLED = true
    const val DEBUG_LOGGING_ENABLED = false
}
