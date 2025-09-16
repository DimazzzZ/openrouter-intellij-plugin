# OpenRouter IntelliJ Plugin

A comprehensive IntelliJ IDEA plugin for integrating with [OpenRouter.ai](https://openrouter.ai) - providing easy access to multiple AI models with usage monitoring and quota tracking.

## Features

🚀 **Status Bar Integration** - Real-time quota and usage display (similar to GitHub Copilot)  
⚙️ **Settings Panel** - Easy API key configuration and preferences  
📊 **Usage Monitoring** - Track token consumption and costs  
🔄 **Auto-refresh** - Automatic quota updates  
🛠️ **Tool Window** - Detailed usage statistics and model information  
🎯 **Quick Actions** - Fast access to OpenRouter features via Tools menu  

## Screenshots

### Status Bar Widget
The plugin adds a status bar widget that shows your current OpenRouter usage, similar to how GitHub Copilot displays its status.

### Settings Panel
Configure your API key and preferences in `Settings > Tools > OpenRouter`.

### Tool Window
Access detailed usage statistics and model information via the OpenRouter tool window.

## Installation

### From IntelliJ Plugin Repository (Recommended)
1. Open IntelliJ IDEA
2. Go to `File > Settings > Plugins` (or `IntelliJ IDEA > Preferences > Plugins` on macOS)
3. Click on `Marketplace` tab
4. Search for "OpenRouter"
5. Click `Install` and restart IntelliJ IDEA

### Manual Installation (Development)
1. Clone this repository:
   ```bash
   git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
   cd openrouter-intellij-plugin
   ```

2. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

3. Install the plugin:
   - Go to `File > Settings > Plugins`
   - Click the gear icon and select `Install Plugin from Disk...`
   - Select the generated ZIP file from `build/distributions/`
   - Restart IntelliJ IDEA

## Configuration

1. **Get your OpenRouter API key**:
   - Visit [openrouter.ai/keys](https://openrouter.ai/keys)
   - Create a new API key

2. **Configure the plugin**:
   - Go to `Settings > Tools > OpenRouter`
   - Enter your API key
   - Configure your preferred default model (default: `openai/gpt-4o`)
   - Set up auto-refresh preferences

3. **Test the connection**:
   - The plugin will automatically test your API key when you save settings
   - Check the status bar widget for connection status

## Usage

### Status Bar Widget
- **Left click**: Open OpenRouter settings
- **Right click**: Context menu with refresh and settings options
- **Display modes**: 
  - Cost view: `$0.50/$100.00` (used/total)
  - Percentage view: `50.0% used`

### Tool Window
- Access via `View > Tool Windows > OpenRouter`
- Shows detailed usage statistics
- Real-time connection status
- Model information

### Actions Menu
Access OpenRouter features via `Tools > OpenRouter`:
- **Show Usage Statistics**: Open the tool window
- **Refresh Quota**: Update quota information
- **Settings...**: Open configuration panel

## Supported IntelliJ Versions

- **IntelliJ IDEA**: 2023.2+ to 2025.2+ (Community and Ultimate)
- **WebStorm**: 2023.2+ to 2025.2+
- **PyCharm**: 2023.2+ to 2025.2+
- **PhpStorm**: 2023.2+ to 2025.2+
- **RubyMine**: 2023.2+ to 2025.2+
- **CLion**: 2023.2+ to 2025.2+
- **Android Studio**: 2023.2+ to 2025.2+

## Development

### Prerequisites
- JDK 17 or higher
- IntelliJ IDEA with Plugin Development support

### Building from Source
```bash
# Clone the repository
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin

# Build the plugin
./gradlew buildPlugin

# Run in development IDE
./gradlew runIde

# Run tests
./gradlew test
```

### Project Structure
```
src/main/kotlin/com/openrouter/intellij/
├── actions/           # Plugin actions (refresh, settings, etc.)
├── icons/             # Icon definitions
├── models/            # Data models for API responses
├── services/          # Core services (API client, settings)
├── settings/          # Settings UI components
├── statusbar/         # Status bar widget implementation
└── toolwindow/        # Tool window content
```

## API Integration

The plugin integrates with OpenRouter's REST API:

- **Chat Completions**: `/v1/chat/completions` - For testing connectivity
- **Generation Stats**: `/v1/generation` - For usage tracking (when available)
- **Authentication**: Bearer token via API key

### Rate Limiting
The plugin respects OpenRouter's rate limits and includes:
- Configurable auto-refresh intervals (default: 5 minutes)
- Error handling for API failures
- Graceful degradation when API is unavailable

## Privacy & Security

- **API Key Storage**: Securely stored using IntelliJ's credential storage
- **Local Processing**: All data processing happens locally
- **No Data Collection**: The plugin doesn't collect or transmit user data
- **Open Source**: Full source code available for audit

## Troubleshooting

### Common Issues

**"Not configured" in status bar**
- Ensure you've entered a valid API key in settings
- Check that your API key has sufficient permissions

**"Connection failed" status**
- Verify your internet connection
- Check if your API key is valid and active
- Ensure OpenRouter service is available

**Status bar widget not showing**
- Go to `View > Appearance > Status Bar Widgets`
- Enable "OpenRouter" widget

### Debug Information
Enable debug logging by adding this to your IDE's log configuration:
```
com.openrouter.intellij:DEBUG
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Setup
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [OpenRouter.ai](https://openrouter.ai) for providing the API
- [JetBrains](https://jetbrains.com) for the IntelliJ Platform
- Inspired by the GitHub Copilot plugin's status bar integration

## Support

- **Issues**: [GitHub Issues](https://github.com/DimazzzZ/openrouter-intellij-plugin/issues)
- **Documentation**: [Plugin Documentation](https://github.com/DimazzzZ/openrouter-intellij-plugin/wiki)
- **OpenRouter Support**: [OpenRouter Documentation](https://openrouter.ai/docs)

---

**Note**: This is an unofficial plugin and is not affiliated with OpenRouter.ai.
