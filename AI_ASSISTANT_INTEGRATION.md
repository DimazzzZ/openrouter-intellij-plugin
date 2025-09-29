# AI Assistant Integration

## Overview

The OpenRouter IntelliJ Plugin now provides seamless integration with JetBrains' AI Assistant plugin, allowing users to access 400+ AI models through the AI Assistant interface in IntelliJ IDEA.

## Features

### 🤖 Model Provider Integration
- **OpenRouter Provider**: Appears as "OpenRouter (400+ AI Models)" in AI Assistant's Models section
- **Popular Models**: Pre-configured access to 8 popular models suitable for code assistance:
  - OpenAI GPT-4o & GPT-4o Mini
  - Anthropic Claude 3.5 Sonnet & Claude 3 Haiku
  - Google Gemini Pro 1.5
  - Meta Llama 3.1 70B
  - Microsoft WizardLM-2 8x22B
  - Qwen 2.5 72B

### ⚙️ Configuration Management
- **Automatic Detection**: Plugin automatically detects if AI Assistant is installed
- **Status Monitoring**: Real-time configuration status in settings panel
- **Easy Setup**: Uses existing OpenRouter Provisioning Key setup
- **Validation**: Built-in connection testing and model validation

### 🎯 User Experience
- **Settings Integration**: Dedicated AI Assistant section in OpenRouter settings
- **Clear Instructions**: Step-by-step configuration guidance
- **Status Indicators**: Visual feedback on integration status
- **Help Links**: Direct links to AI Assistant plugin and documentation

## How to Use

### Prerequisites
1. **Install AI Assistant Plugin**: Get the [JetBrains AI Assistant](https://plugins.jetbrains.com/plugin/22282-jetbrains-ai-assistant) plugin
2. **Install OpenRouter Plugin**: This plugin must be installed and configured

### Setup Instructions

1. **Configure OpenRouter**:
   - Go to `Settings → Tools → OpenRouter`
   - Get a Provisioning Key from [OpenRouter Provisioning Keys](https://openrouter.ai/settings/provisioning-keys)
   - Enter your Provisioning Key and test the connection

2. **Access AI Assistant Models**:
   - Go to `Settings → Tools → AI Assistant → Models`
   - Look for "OpenRouter (400+ AI Models)" in the Provider dropdown
   - Select your preferred OpenRouter model
   - Start using AI Assistant with OpenRouter models!

### Available Models

The integration provides access to these carefully selected models:

| Model | Provider | Best For |
|-------|----------|----------|
| **GPT-4o** | OpenAI | Complex reasoning, multimodal tasks |
| **GPT-4o Mini** | OpenAI | Fast responses, cost-effective |
| **Claude 3.5 Sonnet** | Anthropic | Advanced analysis, complex tasks |
| **Claude 3 Haiku** | Anthropic | Quick responses, simple tasks |
| **Gemini Pro 1.5** | Google | Long context, advanced AI |
| **Llama 3.1 70B** | Meta | Open-source, excellent performance |
| **WizardLM-2 8x22B** | Microsoft | Instruction following |
| **Qwen 2.5 72B** | Alibaba | Multilingual, coding abilities |

## Technical Implementation

### Architecture

The integration consists of three main components:

#### 1. **OpenRouterModelProvider**
```kotlin
class OpenRouterModelProvider {
    fun getAvailableModels(): List<OpenRouterAIModel>
    fun isAvailable(): Boolean
    fun testConnection(): Boolean
}
```

#### 2. **OpenRouterChatModelProvider**
```kotlin
class OpenRouterChatModelProvider {
    fun sendChatRequest(modelId: String, messages: List<ChatMessage>): CompletableFuture<ChatResponse>
    fun sendCompletionRequest(modelId: String, prompt: String): CompletableFuture<CompletionResponse>
}
```

#### 3. **OpenRouterModelConfigurationProvider**
```kotlin
class OpenRouterModelConfigurationProvider {
    fun getModelConfigurations(): List<ModelConfiguration>
    fun validateModelConfiguration(modelId: String): ValidationResult
}
```

### Extension Points

The integration uses these extension points (defined in `ai-assistant-integration.xml`):

```xml
<extensions defaultExtensionNs="com.intellij">
    <ml.llm.modelProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterModelProvider"/>
    <ml.llm.chatModelProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterChatModelProvider"/>
    <ml.llm.modelConfigurationProvider implementation="org.zhavoronkov.openrouter.aiassistant.OpenRouterModelConfigurationProvider"/>
</extensions>
```

### Plugin Dependencies

The AI Assistant integration is declared as an optional dependency:

```xml
<depends optional="true" config-file="ai-assistant-integration.xml">com.intellij.ml.llm</depends>
```

This means:
- ✅ OpenRouter plugin works without AI Assistant installed
- ✅ Integration automatically activates when AI Assistant is present
- ✅ No conflicts if AI Assistant is not available

## Troubleshooting

### Common Issues

#### ❌ "OpenRouter not configured"
**Solution**: Configure your OpenRouter Provisioning Key in `Settings → Tools → OpenRouter`

#### ❌ "AI Assistant plugin not detected"
**Solution**: Install the [JetBrains AI Assistant plugin](https://plugins.jetbrains.com/plugin/22282-jetbrains-ai-assistant)

#### ❌ "Connection test failed"
**Solutions**:
- Verify your Provisioning Key is correct
- Check internet connection
- Ensure you have OpenRouter credits available

#### ❌ "Model not available"
**Solutions**:
- Refresh the OpenRouter settings
- Check if the model is still supported by OpenRouter
- Try a different model from the list

### Debug Information

Enable debug logging by adding this to your IDE's VM options:
```
-Dopenrouter.debug=true
```

Check the logs for entries starting with `[OpenRouter AI Assistant]`.

## Limitations

### Current Limitations
- **Model Selection**: Limited to 8 pre-selected models (can be expanded)
- **Streaming**: Implementation ready but depends on AI Assistant plugin support
- **Custom Models**: Only supports models pre-defined in the provider
- **Advanced Features**: Function calling, image processing depend on AI Assistant capabilities

### Future Enhancements
- **Dynamic Model Loading**: Load all available OpenRouter models
- **Model Filtering**: Filter models by capabilities (coding, chat, etc.)
- **Usage Analytics**: Track usage through AI Assistant
- **Custom Configurations**: Per-model temperature, token limits
- **Team Settings**: Shared configurations for development teams

## FAQ

### Q: Does this replace the existing OpenRouter features?
**A**: No! This adds AI Assistant integration while keeping all existing OpenRouter features (status bar, usage tracking, API management).

### Q: Do I need both plugins installed?
**A**: You need the OpenRouter plugin configured. The AI Assistant plugin is optional - if installed, you get the integration benefits.

### Q: Will this affect my OpenRouter usage/costs?
**A**: Usage through AI Assistant will consume your OpenRouter credits just like direct API calls. Monitor usage in the OpenRouter settings panel.

### Q: Can I use different models in AI Assistant vs. direct API calls?
**A**: Yes! The AI Assistant integration is independent of other OpenRouter usage. You can use different models in different contexts.

### Q: Is this an official JetBrains integration?
**A**: This is a community-developed integration. It's not officially endorsed by JetBrains but follows IntelliJ plugin development best practices.

## Contributing

### Improving the Integration

To enhance the AI Assistant integration:

1. **Add More Models**: Update the `SUPPORTED_MODELS` list in `OpenRouterModelProvider`
2. **Enhance Capabilities**: Extend `ModelCapabilities` for new features
3. **Improve UI**: Update the settings panel for better user experience
4. **Add Features**: Implement streaming, function calling, or other advanced features

### Testing

Test the integration by:

1. Installing both plugins in a development IntelliJ instance
2. Configuring OpenRouter with a valid Provisioning Key
3. Checking that OpenRouter appears in AI Assistant → Models
4. Testing actual model usage through AI Assistant

## Support

For issues related to:
- **OpenRouter Plugin**: Create an issue in the OpenRouter plugin repository
- **AI Assistant Plugin**: Contact JetBrains support or check their documentation
- **Integration Issues**: Check this documentation first, then create an issue with both plugin versions

---

**Last Updated**: 2025-01-20  
**Plugin Version**: Compatible with OpenRouter Plugin v1.0.0+  
**AI Assistant Version**: Compatible with JetBrains AI Assistant 2023.3+
