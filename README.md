# OpenRouter IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/28520)
[![Version](https://img.shields.io/badge/version-0.2.0-blue.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai), providing access to 400+ AI models with usage monitoring, quota tracking, and seamless JetBrains AI Assistant integration.

## Features

- **🤖 AI Assistant Proxy** - Local proxy server to connect JetBrains AI Assistant with OpenRouter's 400+ models
- **📊 Status Bar Widget** - Real-time usage display with comprehensive popup menu
- **🔑 API Key Management** - Secure provisioning key support with automatic API key creation
- **📈 Usage Analytics** - Track token consumption, costs, and model performance
- **🔴 Real-time Monitoring** - Live connection status with color-coded indicators
- **📋 Statistics Popup** - Detailed usage analytics in modal dialog
- **⚙️ Settings Panel** - Configuration with validation and testing
- **🌐 OpenAI Compatibility** - Full OpenAI API compatibility layer for custom integrations
- **🔒 Security** - Encrypted API key storage with localhost-only proxy access
- **🧪 Comprehensive Testing** - 207+ tests covering unit, integration, and E2E scenarios
- **🛠️ Developer-Friendly** - Extensive documentation and debugging capabilities
- **⭐ Favorite Models** - Quick access to your preferred AI models
- **🎨 Code Quality** - Maintained with detekt static analysis and comprehensive refactoring

## Installation

### From Plugin Marketplace
1. Open IntelliJ IDEA → `Settings` → `Plugins`
2. Search for "OpenRouter" in Marketplace
3. Click `Install` and restart IDE

### Manual Installation
1. Download the latest release from [GitHub](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
2. `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select the downloaded ZIP file

## Setup

1. **Get Provisioning Key**: Visit [OpenRouter Provisioning Keys](https://openrouter.ai/settings/provisioning-keys)
2. **Configure**: `Settings` → `Tools` → `OpenRouter` → Enter Provisioning Key
3. **Start Using**: Click status bar widget to access features

The plugin automatically creates and configures an API key when you provide a provisioning key.

## 🤖 AI Assistant Integration

**NEW**: Connect JetBrains AI Assistant to OpenRouter's 400+ AI models using the plugin's local proxy server!

### Quick Start
1. **Configure OpenRouter Plugin** (as above)
2. **Start Proxy Server**: The server starts automatically when you configure your Provisioning Key
3. **Configure AI Assistant**: Settings → Tools → AI Assistant → Models → Add custom model
   - **Provider**: Custom
   - **Server URL**: Copy from OpenRouter settings (e.g., `http://127.0.0.1:8080`)
   - **API Key**: Any text (not validated by proxy)
   - **Model**: Choose from OpenRouter's model catalog
4. **Start Using**: Access 400+ models through AI Assistant

📖 **[Complete Setup Guide](docs/AI_ASSISTANT_SETUP.md)** - Step-by-step instructions with screenshot placeholders

### Supported Models
- **OpenAI**: GPT-4, GPT-4 Turbo, GPT-3.5 Turbo
- **Anthropic**: Claude 3 Opus, Claude 3 Sonnet, Claude 3 Haiku  
- **Meta**: Llama 2 70B, Code Llama
- **Google**: Gemini Pro, PaLM 2
- **Mistral**: Mistral 7B, Mixtral 8x7B
- **And 390+ more models from 40+ providers!**

### Benefits
- ✅ **Unified Interface** - Use AI Assistant's familiar chat interface
- ✅ **Cost Control** - Transparent pricing and real-time usage monitoring  
- ✅ **Model Switching** - Easy switching between different AI providers
- ✅ **Local Proxy** - Secure localhost-only communication
- ✅ **No Limits** - Access models beyond AI Assistant's default options

## Usage

### Status Bar Widget
- **Click**: Open popup menu with plugin features
- **Status Indicators**: Color-coded connection status (Ready, Connecting, Error, Not Configured)
- **Usage Display**: Current quota usage and cost information

### Popup Menu
- **AI Assistant Integration**: Start/stop proxy server with status display
- **View Quota Usage**: Detailed statistics and analytics
- **Settings**: Direct access to plugin configuration
- **Configuration Instructions**: Setup guide for AI Assistant integration
- **Documentation**: Links to OpenRouter API documentation
- **Logout**: Clear stored credentials

## Architecture

### Core Components
- **Jetty Proxy Server** - Embedded HTTP server (ports 8080-8090) for AI Assistant integration
- **OpenRouter API Client** - Handles authentication, quota tracking, and model access
- **Settings Management** - Encrypted credential storage with validation
- **Status Bar Integration** - Real-time monitoring with minimal UI footprint

### API Key Handling
The plugin uses a sophisticated API key management system:
- **Provisioning Keys** - Primary authentication for quota/usage data
- **API Keys** - Automatically created for chat completions
- **Security** - All keys encrypted using IntelliJ's credential store
- **Validation** - Real-time key testing and status verification

### Recent Fixes
- **401 Error Resolution** - Fixed API key handling to use settings instead of Authorization headers
- **Model Name Compatibility** - Updated to use full OpenRouter model names (e.g., `openai/gpt-4-turbo`)
- **Security Cleanup** - Removed all real API keys from codebase and documentation

## Compatibility

**Supported IDEs**: IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, RubyMine, CLion, Android Studio, GoLand, Rider
**IDE Versions**: 2023.2+ to 2025.2+
**Requirements**: OpenRouter.ai account with Provisioning Key

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for development setup and contribution guidelines.

### Building
```bash
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin
./gradlew build
```

### Testing
```bash
# Run all tests (207+ tests)
./gradlew test

# Run core functionality tests only
./gradlew test --tests "*ChatCompletionServletTest*" --tests "*FavoriteModelsServiceTest*"

# Run with development IDE for manual testing
./gradlew runIde --no-daemon
```

**Test Coverage**:
- **Unit Tests** - 109 tests covering data models, settings, and business logic
- **Integration Tests** - 50+ tests for API key handling and proxy server functionality
- **Request Builder Tests** - 12 tests validating refactored HTTP request construction
- **Favorite Models Tests** - 11 tests for favorite models management
- **Security Tests** - Comprehensive validation of API key handling and encryption
- **Total** - 207+ tests with 100% pass rate

For detailed testing information, see [TESTING.md](TESTING.md).

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## Support

- **Documentation**: [OpenRouter API Docs](https://openrouter.ai/docs)
- **Issues**: [GitHub Issues](https://github.com/DimazzzZ/openrouter-intellij-plugin/issues)
- **OpenRouter Community**: [OpenRouter Discord](https://discord.gg/openrouter)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*This is an unofficial plugin and is not affiliated with OpenRouter.ai.*
