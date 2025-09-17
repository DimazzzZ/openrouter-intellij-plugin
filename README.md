# OpenRouter IntelliJ Plugin

A comprehensive IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai) - providing easy access to multiple AI models with usage monitoring and quota tracking.

## Features

🚀 **Enhanced Status Bar Widget** - Interactive status indicator with comprehensive popup menu
⚙️ **Settings Panel** - Easy API key configuration and preferences
📊 **Usage Monitoring** - Track token consumption and costs with accurate calculations
🔄 **Auto-refresh** - Automatic quota updates
🛠️ **Tool Window** - Detailed usage statistics and model information
🎯 **Quick Actions** - Fast access to OpenRouter features via Tools menu
🎨 **Official Branding** - Features the official OpenRouter logo throughout the interface
🔐 **Authentication Management** - Easy login/logout functionality
📚 **Documentation Access** - Quick links to OpenRouter docs and feedback

## Key Features

### Interactive Status Bar Widget
- **Real-time Status**: Color-coded connection indicators (🟢 Ready, 🟡 Connecting, 🔴 Error)
- **Comprehensive Menu**: Authentication, settings, documentation, and quota access
- **Accurate Usage Display**: Precise quota calculations and percentage tracking
- **Modal Quota View**: Centered popup window for detailed usage statistics

### Configuration & Monitoring
- **Easy Setup**: Simple API key configuration in Settings
- **Usage Tracking**: Detailed statistics and cost monitoring with correct calculations
- **Tool Window**: Comprehensive usage analytics
- **Direct Settings Access**: One-click access to plugin configuration

## Installation

### From Plugin Marketplace (Recommended)
1. Open IntelliJ IDEA → `Settings` → `Plugins`
2. Search for "OpenRouter" in Marketplace
3. Click `Install` and restart IDE

### Manual Installation
1. Download latest release or build from source:
   ```bash
   git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
   cd openrouter-intellij-plugin
   ./gradlew buildPlugin --no-daemon
   ```
2. `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select `build/distributions/openrouter-intellij-plugin-*.zip`

## Quick Setup

1. **Get API Key**: Visit [openrouter.ai/keys](https://openrouter.ai/keys)
2. **Configure**: `Settings` → `Tools` → `OpenRouter` → Enter API key
3. **Use**: Click status bar widget or press `⇧⌃C` for chat

## Usage

### Status Bar Widget
- **Click**: Open comprehensive popup menu with all features
- **Status Indicators**: 🟢 Ready, 🟡 Connecting, 🔴 Error, ⚪ Not configured
### Popup Menu Features
- **Status Display**: Real-time connection status with color indicators
- **View Quota Usage**: Centered modal with detailed statistics
- **Login/Logout**: Authentication management with confirmation
- **Settings**: Direct access to plugin configuration
- **Documentation**: Quick links to OpenRouter docs
- **Feedback**: Direct link to GitHub issues for bug reports

## Compatibility

**Supported IDEs**: IntelliJ IDEA, WebStorm, PyCharm, PhpStorm, RubyMine, CLion, Android Studio
**Versions**: 2023.2+ to 2025.2+ (Community and Ultimate editions)

## Development

See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed development setup and contribution guidelines.

### Quick Build
```bash
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin
./gradlew build --no-daemon
```

## Testing

The plugin includes a comprehensive test suite with covering core functionality:

```bash
# Run all core tests
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest" --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"
```

For detailed testing information, see [TESTING.md](TESTING.md).

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Documentation**: [OpenRouter API Docs](https://openrouter.ai/docs)
- **Issues**: [GitHub Issues](https://github.com/DimazzzZ/openrouter-intellij-plugin/issues)
- **OpenRouter Support**: [OpenRouter Discord](https://discord.gg/openrouter)

---

*This is an unofficial plugin and is not affiliated with OpenRouter.ai.*
