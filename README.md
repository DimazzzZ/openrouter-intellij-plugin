# OpenRouter IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/28520)
[![Version](https://img.shields.io/badge/version-0.5.0-blue.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
[![CI](https://github.com/DimazzzZ/openrouter-intellij-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai), providing access to 400+ AI models with usage monitoring, quota tracking, and seamless JetBrains AI Assistant integration.

## Features

- **💬 Chat Tool Window** - Multi-chat sessions directly in IDE sidebar with persistent history, token tracking, and model selection
- **🤖 AI Assistant Proxy** - Configurable local proxy server (ports 8880-8899 by default) with auto-start control and flexible port selection
- **📊 Status Bar Widget** - Real-time usage display with comprehensive popup menu
- **🔑 Flexible Authentication** - OAuth/PKCE or Provisioning Key support with secure credential storage
- **📈 Usage Analytics** - Track token consumption, costs, and model performance with real-time "Today" statistics
- **🔴 Real-time Monitoring** - Live connection status with color-coded indicators
- **📋 Statistics Popup** - Detailed usage analytics in modal dialog with days remaining estimate
- **⚙️ Settings Panel** - Configuration with validation and testing
- **🌐 OpenAI Compatibility** - Full OpenAI API compatibility layer for custom integrations
- **🔒 Security** - OS-native credential storage (Keychain, Credential Manager, libsecret) with localhost-only proxy access
- **🧪 Comprehensive Testing** - 500+ tests covering unit, integration, and E2E scenarios
- **🛠️ Developer-Friendly** - Extensive documentation and debugging capabilities
- **⭐ Favorite Models** - Quick access to your preferred AI models with advanced filtering:
  - Filter by provider (OpenAI, Anthropic, Google, Meta, etc.)
  - Filter by capabilities (Vision, Audio, Tools, Image Generation)
  - Filter by context length (< 32K, 32K-128K, > 128K)
  - Quick presets (Popular, Coding, Multimodal, Cost-Effective)
  - Real-time search across 400+ models
- **🚀 First-Run Experience** - Welcome notification and setup wizard for easy onboarding
- **💡 Contextual Help** - GotIt tooltips guide you through key features
- **🎨 Code Quality** - Maintained with detekt static analysis and comprehensive refactoring

## Installation

### From Plugin Marketplace
1. Open IntelliJ IDEA → `Settings` → `Plugins`
2. Search for "OpenRouter" in Marketplace
3. Click `Install` (no restart required!)

### Manual Installation
1. Download the latest release from [GitHub](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
2. `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select the downloaded ZIP file

## Setup

### First-Time Setup (Recommended)

When you first install the plugin, a **welcome notification** will appear with a "Quick Setup" button. This launches a step-by-step wizard that guides you through:

1. **Welcome** - Introduction to OpenRouter and what you'll need
2. **Authentication** - Choose your authentication method:
   - **Regular API Key** - OAuth/PKCE flow (one-click browser authorization)
   - **Provisioning Key** - Extended mode with full monitoring capabilities
3. **Favorite Models** - Select your preferred models with embedded search and filtering
4. **Completion** - Copy proxy server URL and configure AI Assistant

The wizard makes setup quick and easy, especially for first-time users!

### Manual Setup

1. **Open Settings**: `Settings` → `Tools` → `OpenRouter`
2. **Choose Authentication Method**:
   - **Regular API Key**: Click "Connect to OpenRouter" for OAuth/PKCE browser authorization
   - **Provisioning Key**: Get from [OpenRouter Provisioning Keys](https://openrouter.ai/settings/provisioning-keys) and paste
3. **Select Models**: `Settings` → `Tools` → `OpenRouter` → `Favorite Models` → Choose your models
4. **Start Using**: Click status bar widget to access features

## 🤖 AI Assistant Integration

**NEW**: Connect JetBrains AI Assistant to OpenRouter's 400+ AI models using the plugin's local proxy server!

### Quick Start
1. **Configure OpenRouter Plugin** (as above)
2. **Configure Proxy Server**: Auto-start is disabled by default - manually start via Settings or enable auto-start
3. **Configure AI Assistant**: Settings → Tools → AI Assistant → Models → Add custom model
   - **Provider**: Custom
   - **Server URL**: Copy from OpenRouter settings (e.g., `http://127.0.0.1:8880`)
   - **API Key**: Any text (not validated by proxy)
   - **Model**: Choose from OpenRouter's model catalog
4. **Start Using**: Access 400+ models through AI Assistant

📖 **[Complete Setup Guide](docs/AI_ASSISTANT_SETUP.md)** - Step-by-step instructions with screenshot placeholders

### Supported Models
- **OpenAI**: GPT-4o, GPT-4 Turbo, o1, o3-mini
- **Anthropic**: Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Haiku
- **Meta**: Llama 3.3, Llama 3.1, Code Llama
- **Google**: Gemini 2.0 Flash, Gemini 1.5 Pro
- **Mistral**: Mistral Large, Mixtral 8x22B
- **DeepSeek**: DeepSeek V3, DeepSeek R1
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

### Chat Tool Window
Access via the right sidebar (`View` → `Tool Windows` → `OpenRouter`):

- **Chat Tab** - Multi-chat interface for conversations with AI models
- **Status Tab** - Account info, quota display, and usage statistics
- **Create New Chat** - Click "+ New Chat" to start a conversation
- **Model Selection** - Choose from your favorite models per chat session
- **Persistent History** - Chats saved locally and restored on IDE restart
- **Token Tracking** - Real-time token estimation for input and cumulative counts
- **Keyboard Shortcuts** - `Enter` to send, `Cmd/Ctrl+Enter` for newline
- **Context Menu** - Right-click on chats for quick actions (Open, Delete)

### Proxy Server Configuration
Access via `Settings` → `Tools` → `OpenRouter` → `Proxy Server` section:

- **Auto-start**: Control whether proxy starts automatically on IDEA launch (disabled by default)
- **Port Selection**: Choose specific port or auto-select from configurable range
- **Port Range**: Configure port range for auto-selection (default: 8880-8899)
- **Immediate Application**: Start Proxy button applies current settings without requiring Apply/OK
- **Conflict Avoidance**: Default range avoids common development ports (8080, 8000, etc.)
- **Manual Control**: Start/stop proxy server on demand from settings panel

#### Configuration Options
```kotlin
proxyAutoStart = false           // Auto-start on IDEA launch
proxyPort = 0                   // Specific port (0 = auto-select)  
proxyPortRangeStart = 8880      // Range start for auto-selection
proxyPortRangeEnd = 8899        // Range end for auto-selection
```

## Architecture

### Core Components
- **Jetty Proxy Server** - Configurable embedded HTTP server (default ports 8880-8899) with flexible port selection and auto-start control
- **OpenRouter API Client** - Handles authentication, quota tracking, and model access
- **Settings Management** - Encrypted credential storage with validation
- **Status Bar Integration** - Real-time monitoring with minimal UI footprint

### Authentication Methods
The plugin supports two authentication methods:
- **Regular API Key** (OAuth/PKCE) - One-click browser authorization, minimal permissions, no usage monitoring
- **Provisioning Key** (Extended) - Full functionality with quota tracking, usage monitoring, and API key management
- **Security** - All keys encrypted using IntelliJ's credential store
- **Validation** - Real-time key testing and status verification

## Compatibility

**Supported IDEs**: IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, RubyMine, CLion, Android Studio, GoLand, Rider
**IDE Versions**: 2024.1+ to 2025.3+
**Requirements**: OpenRouter.ai account (free or paid)

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
# Run all tests (500+ tests)
./gradlew test

# Run core functionality tests only
./gradlew test --tests "*ChatCompletionServletTest*" --tests "*FavoriteModelsServiceTest*"

# Run with development IDE for manual testing
./gradlew runIde --no-daemon
```

### Testing Dynamic Plugin Support

The plugin supports installation, updates, and uninstallation **without IDE restart** (dynamic plugin).

**Quick Test** (Enable/Disable):
```bash
# Start development IDE and test enable/disable
./scripts/test-enable-disable.sh

# In the IDE: Settings → Plugins → Toggle OpenRouter OFF/ON
# Should work without restart ✅
```

**Full Test** (Multiple Versions):
```bash
# Build 3 test versions for upgrade testing
./scripts/build-test-versions.sh

# Install first version (may require restart on first install)
# Update to next version (should NOT require restart)
# Update again (should NOT require restart)
```

**Test Coverage**: 500+ tests with 100% pass rate
- Unit tests for data models, settings, and business logic
- Integration tests for API key handling and proxy server functionality
- Authentication tests with MockWebServer for realistic HTTP testing
- Security tests for API key handling and encryption

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
