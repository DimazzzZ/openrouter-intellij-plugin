# OpenRouter IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/28520)
[![Version](https://img.shields.io/badge/version-0.5.1-blue.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
[![CI](https://github.com/DimazzzZ/openrouter-intellij-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai), providing access to 400+ AI models with usage monitoring, quota tracking, and seamless JetBrains AI Assistant integration.

## What's New in v0.5.1 🎉

- **🎯 Custom Presets** - Use OpenRouter presets (`openrouter/auto`, `openrouter/free`) and your own custom presets directly in chat
- **🔌 Extension API** - New BalanceProvider extension point allows other plugins (like Token Pulse) to receive balance data in real-time
- **🚀 Future-Proof** - Compatible with all future IDE versions (no upper version limit)
- **☕ Platform Updates** - Java 21 and Kotlin 2.0.21 for IntelliJ 2024.2+

## Key Features

| Feature | Description |
|---------|-------------|
| **💬 Chat Tool Window** | Multi-chat sessions in IDE sidebar with persistent history and token tracking |
| **🤖 AI Assistant Proxy** | Local OpenAI-compatible proxy connecting AI Assistant to 400+ models |
| **🎯 Custom Presets** | Built-in and custom OpenRouter presets for quick model selection |
| **📊 Usage Analytics** | Real-time cost tracking, quota monitoring, and spending estimates |
| **⭐ Favorite Models** | Quick access with filtering by provider, capabilities, and context length |
| **🔐 Secure Storage** | OS-native credential storage (Keychain, Credential Manager, libsecret) |
| **🔌 Plugin API** | Extension point for other plugins to receive balance data |

## Installation

### From Plugin Marketplace
1. Open IntelliJ IDEA → `Settings` → `Plugins`
2. Search for "OpenRouter" in Marketplace
3. Click `Install` (no restart required!)

### Manual Installation
1. Download the latest release from [GitHub Releases](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
2. `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select the downloaded ZIP file

## Quick Start

### First-Time Setup

When you first install the plugin, a **welcome notification** will appear with a "Quick Setup" button. The wizard guides you through:

1. **Authentication** - Choose OAuth/PKCE (one-click) or Provisioning Key
2. **Favorite Models** - Select your preferred models with search and filtering
3. **Proxy Setup** - Configure AI Assistant integration

### Manual Setup

1. **Open Settings**: `Settings` → `Tools` → `OpenRouter`
2. **Authenticate**: Click "Connect to OpenRouter" for OAuth/PKCE, or paste a [Provisioning Key](https://openrouter.ai/settings/provisioning-keys)
3. **Select Models**: Go to `Favorite Models` tab and choose your models
4. **Start Using**: Click the status bar widget to access features

### AI Assistant Integration

Connect JetBrains AI Assistant to OpenRouter's 400+ models:

1. Start the proxy server in `Settings` → `Tools` → `OpenRouter`
2. In AI Assistant: `Settings` → `Tools` → `AI Assistant` → `Models` → Add custom model
   - **Server URL**: Copy from OpenRouter settings (e.g., `http://127.0.0.1:8880`)
   - **API Key**: Any text (not validated)
   - **Model**: Any model from [OpenRouter's catalog](https://openrouter.ai/models)

📖 **[Complete Setup Guide](docs/AI_ASSISTANT_SETUP.md)** with screenshots

## Features

### Chat Tool Window

Access via `View` → `Tool Windows` → `OpenRouter`:

- **Multi-Chat** - Create and manage multiple conversation sessions
- **Model Selection** - Choose from favorites or use presets
- **Persistent History** - Chats saved locally and restored on restart
- **Token Tracking** - Real-time estimation for input and cumulative counts
- **Keyboard Shortcuts** - `Enter` to send, `Cmd/Ctrl+Enter` for newline

### Custom Presets

Manage presets in `Settings` → `Tools` → `OpenRouter` → `Presets`:

- **Built-in Presets** - `openrouter/auto` (best model for task) and `openrouter/free` (free models only)
- **Custom Presets** - Add your own presets created at [OpenRouter Presets](https://openrouter.ai/settings/presets)
- **Chat Integration** - Presets appear at the top of model selector with `@preset/` prefix

### Usage Monitoring

- **Status Bar Widget** - Real-time usage display with color-coded connection status
- **Statistics Popup** - Detailed analytics with days remaining estimate
- **Cost Tracking** - Accurate "Today" statistics with local tracking

### Extension API (for Plugin Developers)

Other plugins can receive balance updates via the `balanceProvider` extension point:

```xml
<extensions defaultExtensionNs="org.zhavoronkov.openrouter">
    <balanceProvider implementation="com.example.MyBalanceProvider"/>
</extensions>
```

See the [CHANGELOG](CHANGELOG.md#051---2026-03-27) for API details and the `BalanceProvider` interface in the source code.

## Compatibility

| | |
|---|---|
| **Supported IDEs** | IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, RubyMine, CLion, Android Studio, GoLand, Rider |
| **IDE Versions** | 2024.2+ and all future versions |
| **Requirements** | Java 21+, [OpenRouter.ai](https://openrouter.ai) account (free or paid) |

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for build instructions, testing, and contribution guidelines.

## Support

- **Issues**: [GitHub Issues](https://github.com/DimazzzZ/openrouter-intellij-plugin/issues)
- **OpenRouter Docs**: [openrouter.ai/docs](https://openrouter.ai/docs)
- **Community**: [OpenRouter Discord](https://discord.gg/openrouter)

## License

MIT License - see [LICENSE](LICENSE) for details.

---

*This is an unofficial plugin and is not affiliated with OpenRouter.ai or JetBrains.*
