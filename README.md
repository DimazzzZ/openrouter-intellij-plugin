# OpenRouter IntelliJ Plugin

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)](https://github.com/DimazzzZ/openrouter-intellij-plugin/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai), providing access to 400+ AI models with usage monitoring and quota tracking.

## Features

**Status Bar Widget** - Real-time usage display with popup menu
**API Key Management** - Secure provisioning key support with automatic API key creation
**Usage Analytics** - Track token consumption, costs, and model performance
**Real-time Monitoring** - Live connection status with color-coded indicators
**Statistics Popup** - Detailed usage analytics in modal dialog
**Settings Panel** - Configuration with validation and testing

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

## Usage

### Status Bar Widget
- **Click**: Open popup menu with plugin features
- **Status Indicators**: Color-coded connection status (Ready, Connecting, Error, Not Configured)
- **Usage Display**: Current quota usage and cost information

### Popup Menu
- **View Quota Usage**: Detailed statistics and analytics
- **Settings**: Direct access to plugin configuration
- **Documentation**: Links to OpenRouter API documentation
- **Logout**: Clear stored credentials

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
./gradlew test
```

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
