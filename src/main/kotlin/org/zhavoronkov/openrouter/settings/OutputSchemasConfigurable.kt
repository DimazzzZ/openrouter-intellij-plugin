package org.zhavoronkov.openrouter.settings

import org.zhavoronkov.openrouter.settings.schemas.OutputSchemasSettingsPanel

/**
 * Configurable for saved Output Schemas.
 * Appears as a sub-page under Tools → OpenRouter → Output Schemas.
 */
class OutputSchemasConfigurable :
    PageConfigurable<OutputSchemasSettingsPanel>("Output Schemas", { OutputSchemasSettingsPanel() })
