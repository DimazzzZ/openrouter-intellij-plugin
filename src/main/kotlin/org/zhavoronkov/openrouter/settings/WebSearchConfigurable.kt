package org.zhavoronkov.openrouter.settings

/**
 * Configurable for tuning Web Search.
 * Appears as a sub-page under Tools → OpenRouter → Web Search.
 */
class WebSearchConfigurable : PageConfigurable<WebSearchSettingsPanel>("Web Search", { WebSearchSettingsPanel() })
