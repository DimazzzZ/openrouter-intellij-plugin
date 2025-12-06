# AI Assistant Integration - Technical Guide

## Overview

This document provides technical details for developers who need to maintain, extend, or understand the AI Assistant integration implementation in the OpenRouter IntelliJ Plugin.

## Integration Architecture

### Key Design Decisions

1. **Optional Dependency Pattern**: The integration uses IntelliJ's optional dependency mechanism to ensure the OpenRouter plugin works independently while providing enhanced functionality when AI Assistant is available.

2. **Separate Configuration File**: AI Assistant-specific extensions are defined in a separate `ai-assistant-integration.xml` file to keep the main plugin configuration clean.

3. **Bridge Pattern**: The integration classes act as bridges between OpenRouter's API and the AI Assistant plugin's expected interfaces.

4. **Educational Guess Approach**: Since JetBrains hasn't publicly documented the AI Assistant extension points, the implementation uses educated guesses based on common IntelliJ plugin patterns.

## File Structure

```
src/main/kotlin/org/zhavoronkov/openrouter/aiassistant/
├── OpenRouterModelProvider.kt              # Main model provider interface
├── OpenRouterChatModelProvider.kt          # Chat/completion handler  
├── OpenRouterModelConfigurationProvider.kt # Configuration management
└── [Future extensions can be added here]

src/main/resources/META-INF/
├── plugin.xml                              # Main plugin configuration
└── ai-assistant-integration.xml            # AI Assistant extensions
```

## Implementation Details

### 1. Plugin Configuration (`plugin.xml`)

```xml
<!-- Optional dependency declaration -->
<depends optional="true" config-file="ai-assistant-integration.xml">com.intellij.ml.llm</depends>
```

**Key Points:**
- `optional="true"`: Plugin works without AI Assistant installed
- `config-file`: Separate configuration loaded only when dependency is available
- `com.intellij.ml.llm`: AI Assistant plugin ID (verified from JetBrains documentation)

### 2. Extension Points (`ai-assistant-integration.xml`)

```xml
<extensions defaultExtensionNs="com.intellij">
    <ml.llm.modelProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterModelProvider"/>
    <ml.llm.chatModelProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterChatModelProvider"/>
    <ml.llm.modelConfigurationProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterModelConfigurationProvider"/>
</extensions>
```

**Extension Point Analysis:**
- These extension point names are educated guesses based on common AI/ML plugin patterns
- They follow IntelliJ's naming conventions for ML-related extensions
- If incorrect, they can be easily updated when actual documentation becomes available

### 3. Model Provider (`OpenRouterModelProvider.kt`)

**Purpose**: Main interface between OpenRouter and AI Assistant

**Key Methods:**
- `getAvailableModels()`: Returns curated list of 8 popular models
- `isAvailable()`: Checks if OpenRouter is configured and working
- `testConnection()`: Validates connectivity using existing OpenRouter service

**Model Selection Strategy:**
```kotlin
private val SUPPORTED_MODELS = listOf(
    "openai/gpt-4o",
    "openai/gpt-4o-mini", 
    "anthropic/claude-3.5-sonnet",
    "anthropic/claude-3-haiku",
    "google/gemini-pro-1.5",
    "meta-llama/llama-3.1-70b-instruct",
    "microsoft/wizardlm-2-8x22b",
    "qwen/qwen-2.5-72b-instruct"
)
```

**Rationale**: Limited to 8 models to avoid overwhelming users while covering major AI providers and use cases.

### 4. Chat Provider (`OpenRouterChatModelProvider.kt`)

**Purpose**: Handles actual AI requests from AI Assistant

**Key Features:**
- Converts AI Assistant requests to OpenRouter API format
- Supports both chat and completion requests
- Implements proper error handling and response parsing
- Uses existing OpenRouter service infrastructure

**Request Flow:**
1. AI Assistant sends request → `sendChatRequest()`
2. Convert to OpenRouter format → `createChatRequestBody()`
3. Make API call → `makeOpenRouterRequest()`
4. Parse response → `parseOpenRouterResponse()`
5. Return standardized response → `ChatResponse`

### 5. Configuration Provider (`OpenRouterModelConfigurationProvider.kt`)

**Purpose**: Manages configuration aspects and settings integration

**Key Responsibilities:**
- Model validation and status checking
- Configuration instructions for users
- Provider activation/deactivation handling
- Integration with OpenRouter settings panel

### 6. Settings UI Integration

**Location**: `OpenRouterSettingsPanel.kt` (lines 175-283)

**Features Added:**
- AI Assistant detection (`checkAIAssistantInstalled()`)
- Status display with visual indicators
- User guidance and help links
- Integration information panel

**UI Components:**
```kotlin
private fun createAIAssistantIntegrationPanel(): JPanel {
    // Title, status, information, and help links
    // Visual feedback based on AI Assistant availability
    // Click-through links to AI Assistant plugin
}
```

## Testing Strategy

### Manual Testing Approach

Since the AI Assistant plugin's actual extension points are unknown, testing must be done empirically:

1. **Install both plugins** in a development IntelliJ instance
2. **Configure OpenRouter** with valid credentials
3. **Check AI Assistant settings** for OpenRouter provider
4. **Test model usage** through AI Assistant interface
5. **Monitor logs** for integration-related messages

### Automated Testing Limitations

- Cannot create automated tests for AI Assistant integration without actual extension point documentation
- Current tests focus on the bridge classes' internal logic
- Future tests should be added once extension points are confirmed

## Maintenance Guide

### Common Maintenance Tasks

#### 1. Adding New Models

**File**: `OpenRouterModelProvider.kt`
**Location**: `SUPPORTED_MODELS` list

```kotlin
// Add new model to the list
private val SUPPORTED_MODELS = listOf(
    // ... existing models
    "new-provider/new-model"
)

// Update display name mapping
private fun getModelDisplayName(modelId: String): String {
    return when {
        // ... existing mappings
        modelId.contains("new-model") -> "New Model Display Name"
        // ...
    }
}
```

#### 2. Updating Extension Points

If JetBrains releases official documentation:

**File**: `ai-assistant-integration.xml`

```xml
<!-- Update extension point names as needed -->
<ml.llm.correctExtensionPointName implementation="..."/>
```

#### 3. Enhancing Model Capabilities

**File**: `OpenRouterModelConfigurationProvider.kt`

```kotlin
// Update ModelCapabilities data class
data class ModelCapabilities(
    val supportsChat: Boolean,
    val supportsCompletion: Boolean,
    val supportsStreaming: Boolean,
    val maxContextLength: Int,
    val supportsImages: Boolean = false,
    val supportsCodeGeneration: Boolean = true,
    val supportsFunction: Boolean = false,
    // Add new capabilities here
    val newCapability: Boolean = false
)
```

### Debugging Integration Issues

#### 1. Enable Debug Logging

```kotlin
// Add to PluginLogger calls
PluginLogger.Service.debug("[AI Assistant] Integration status: ...")
```

#### 2. Check Plugin Detection

```kotlin
private fun checkAIAssistantInstalled(): Boolean {
    return try {
        val pluginId = com.intellij.openapi.extensions.PluginId.getId("com.intellij.ml.llm")
        val aiPlugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
        val isEnabled = aiPlugin != null && !com.intellij.ide.plugins.PluginManagerCore.isDisabled(pluginId)
        PluginLogger.Settings.debug("AI Assistant plugin: $aiPlugin, enabled: $isEnabled")
        isEnabled
    } catch (e: Exception) {
        PluginLogger.Settings.debug("Could not check AI Assistant plugin status: ${e.message}")
        false
    }
}
```

#### 3. Monitor Extension Loading

Check IntelliJ's log files for:
- Extension point registration messages
- Plugin loading errors  
- Missing dependency warnings

## Future Enhancement Opportunities

### 1. Dynamic Model Loading

Replace hardcoded model list with dynamic loading from OpenRouter API:

```kotlin
// Future implementation
fun getAvailableModels(): List<OpenRouterAIModel> {
    return openRouterService.getProviders().get()
        ?.data?.flatMap { provider -> 
            getModelsForProvider(provider) 
        } ?: fallbackToStaticModels()
}
```

### 2. Streaming Support

Implement streaming responses when AI Assistant supports them:

```kotlin
// Future streaming implementation
fun sendStreamingChatRequest(
    modelId: String, 
    messages: List<ChatMessage>
): Flow<ChatResponse> {
    // Streaming implementation
}
```

### 3. Advanced Configuration

Add per-model configuration options:

```kotlin
// Future configuration structure
data class ModelConfiguration(
    val id: String,
    val displayName: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
    val customInstructions: String = "",
    // Additional configuration options
)
```

### 4. Usage Analytics Integration

Connect AI Assistant usage with OpenRouter's existing analytics:

```kotlin
// Future analytics integration
fun trackAIAssistantUsage(modelId: String, tokens: Int, cost: Double) {
    openRouterTrackingService.trackGeneration(
        GenerationTrackingInfo(
            generationId = generateId(),
            model = modelId,
            timestamp = System.currentTimeMillis(),
            totalTokens = tokens,
            totalCost = cost,
            source = "AI_ASSISTANT"
        )
    )
}
```

## Error Handling Strategy

### Graceful Degradation

The integration is designed to fail gracefully:

1. **Missing AI Assistant**: Plugin works normally, integration panel shows status
2. **Configuration Issues**: Clear error messages with remediation steps
3. **API Failures**: Fallback to error responses with helpful context
4. **Unknown Extension Points**: Silent failure with debug logging

### Error Response Format

```kotlin
// Standardized error responses
data class ChatResponse(
    val content: String?,
    val error: String?,
    val usage: Usage? = null
) {
    companion object {
        fun error(message: String) = ChatResponse(null, message)
    }
}
```

## Security Considerations

### API Key Handling

- Uses existing OpenRouter credential management
- No additional credential storage required
- Leverages IntelliJ's secure storage mechanisms

### Request Validation

- Input validation for all AI Assistant requests
- Rate limiting through OpenRouter's existing mechanisms
- Error message sanitization to avoid information leakage

## Performance Considerations

### Lazy Loading

- Model lists are loaded on-demand
- Connection tests are cached for reasonable periods
- Heavy operations are performed asynchronously

### Memory Management

- No large objects stored in memory
- Proper cleanup of HTTP connections
- Efficient JSON parsing for responses

## Compatibility Matrix

| OpenRouter Plugin | AI Assistant Plugin | IntelliJ Version | Status |
|------------------|-------------------|------------------|---------|
| 1.0.0+          | 2023.3+          | 2023.2+         | ✅ Supported |
| 1.0.0+          | Not installed    | 2023.2+         | ✅ Degraded (no integration) |
| < 1.0.0         | Any              | Any             | ❌ Not supported |

## Conclusion

This integration represents a best-effort approach to connecting OpenRouter with JetBrains AI Assistant. The implementation is designed to be:

- **Robust**: Works with or without AI Assistant
- **Maintainable**: Clear separation of concerns and documentation
- **Extensible**: Easy to add new models and features
- **User-Friendly**: Clear setup instructions and error messages

As JetBrains releases official AI Assistant extension point documentation, this implementation can be refined and enhanced accordingly.

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-20  
**Maintainer**: OpenRouter Plugin Development Team
