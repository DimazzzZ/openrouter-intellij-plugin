# Development Guide

This guide covers development setup, building, testing, and contributing to the OpenRouter IntelliJ Plugin.

## Prerequisites

- **JDK 17 or higher** - Required for IntelliJ Platform development
- **IntelliJ IDEA** - Ultimate or Community Edition with Plugin Development support
- **Git** - For version control
- **ImageMagick** (optional) - For icon processing

## Quick Start

### 1. Clone and Setup
```bash
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin
chmod +x gradlew
```

### 2. Build and Test
```bash
# Build the plugin
./gradlew build --no-daemon

# Run in development IDE
./gradlew runIde --no-daemon

# Run tests
./gradlew test --no-daemon
```

### 3. Install Locally
```bash
# Build distribution
./gradlew buildPlugin --no-daemon

# Install in IntelliJ IDEA:
# Settings > Plugins > Install from Disk > select build/distributions/openrouter-intellij-plugin-*.zip
```

## Version Management

### Single Source of Truth
All plugin metadata is centralized in `gradle.properties`:
```properties
pluginVersion = 0.1.2
pluginName = OpenRouter
pluginGroup = org.zhavoronkov
pluginId = org.zhavoronkov.openrouter
```

### Update Version
```bash
# Recommended: Use update script
./scripts/update-version.sh 1.2.0

# Alternative: Gradle task
./gradlew updateVersion -PnewVersion=1.2.0 --no-configuration-cache

# Check current version
./gradlew showVersion --no-configuration-cache
```

## Building and Testing

### Build Commands
```bash
# Full build with verification
./gradlew clean build --no-daemon

# Build distribution only
./gradlew buildPlugin --no-daemon

# Verify plugin compatibility
./gradlew verifyPlugin --no-daemon
```

### Development IDE
```bash
# Run plugin in development IDE
./gradlew runIde --no-daemon

# Run with specific IntelliJ version
./gradlew runIde --no-daemon -PplatformVersion=2024.1

# Run tests
./gradlew test --no-daemon
```

### Troubleshooting
```bash
# Configuration cache issues
./gradlew build --no-configuration-cache

# Clean build
./gradlew clean build --no-daemon

# Check compatibility
./gradlew verifyPlugin --no-daemon
```

## Project Architecture

```
openrouter-intellij-plugin/
├── build.gradle.kts              # Build configuration
├── gradle.properties             # Gradle properties
├── settings.gradle.kts           # Gradle settings
├── src/
│   ├── main/
│   │   ├── kotlin/com/openrouter/intellij/
│   │   │   ├── actions/          # Plugin actions
│   │   │   │   ├── OpenChatAction.kt
│   │   │   │   ├── OpenSettingsAction.kt
│   │   │   │   ├── RefreshQuotaAction.kt
│   │   │   │   └── ShowUsageAction.kt
│   │   │   ├── icons/            # Icon definitions
│   │   │   │   └── OpenRouterIcons.kt
│   │   │   ├── models/           # Data models
│   │   │   │   ├── ConnectionStatus.kt
│   │   │   │   └── OpenRouterModels.kt
│   │   │   ├── services/         # Core services
│   │   │   │   ├── OpenRouterService.kt
│   │   │   │   ├── OpenRouterSettingsService.kt
│   │   │   │   └── OpenRouterGenerationTrackingService.kt
│   │   │   ├── settings/         # Settings UI
│   │   │   │   ├── OpenRouterConfigurable.kt
│   │   │   │   └── OpenRouterSettingsPanel.kt
│   │   │   ├── statusbar/        # Enhanced status bar widget
│   │   │   │   ├── OpenRouterStatusBarWidget.kt
│   │   │   │   └── OpenRouterStatusBarWidgetFactory.kt
│   │   │   ├── toolwindow/       # Tool window
│   │   │   │   ├── OpenRouterToolWindowContent.kt
│   │   │   │   └── OpenRouterToolWindowFactory.kt
│   │   │   └── ui/               # UI components
│   │   │       ├── OpenRouterChatWindow.kt
│   │   │       └── OpenRouterStatsPopup.kt
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── plugin.xml    # Plugin configuration
│   │       │   ├── pluginIcon.png
│   │       │   └── pluginIcon@2x.png
│   │       └── icons/            # Official OpenRouter icons
│   │           ├── openrouter-logo.png
│   │           ├── openrouter-13.png
│   │           ├── openrouter-16.png
│   │           ├── openrouter-40.png
│   │           ├── pluginIcon.png
│   │           └── pluginIcon@2x.png
│   └── test/                     # Test files
└── docs/                         # Documentation
```

## Key Components

### Core Services
- **OpenRouterService**: API communication with OpenRouter (chat completions, key info, generation stats)
- **OpenRouterSettingsService**: Plugin settings and persistence with single source of truth
- **OpenRouterGenerationTrackingService**: Track and monitor API usage

### Enhanced UI Components
- **Status Bar Widget**: Interactive popup menu with connection status, authentication, and quick actions
- **Chat Window**: Integrated chat interface with OpenRouter models (⇧⌃C shortcut)
- **Settings Panel**: API key configuration and preferences
- **Tool Window**: Detailed usage statistics and model information

### Key Features
- **StatusBarWidget**: Shows quota info in the status bar
- **ToolWindow**: Detailed usage statistics view
- **Settings Panel**: Configuration interface

### Actions
- **ShowUsageAction**: Opens the tool window
- **RefreshQuotaAction**: Updates quota information
- **OpenSettingsAction**: Opens settings dialog

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused

### Error Handling
- Use proper exception handling
- Log errors appropriately using IntelliJ's Logger
- Provide user-friendly error messages
- Gracefully handle API failures

### Threading
- Use CompletableFuture for async operations
- Always update UI on EDT using ApplicationManager.invokeLater
- Don't block the UI thread with network calls

### Testing
- Write unit tests for business logic
- Test error conditions and edge cases
- Use mock objects for external dependencies

## API Integration

### OpenRouter API Endpoints
- **Chat Completions**: `POST /v1/chat/completions`
- **Generation Stats**: `GET /v1/generation?id={id}`

### Authentication
```kotlin
val request = Request.Builder()
    .url(endpoint)
    .addHeader("Authorization", "Bearer ${apiKey}")
    .addHeader("Content-Type", "application/json")
    .build()
```

### Error Handling
```kotlin
client.newCall(request).execute().use { response ->
    if (response.isSuccessful) {
        // Handle success
    } else {
        logger.warn("API call failed: ${response.code} ${response.message}")
        // Handle error
    }
}
```

## Debugging

### Enable Debug Logging
Add to your IDE's log configuration:
```
org.zhavoronkov.openrouter:DEBUG
```

### Common Debug Points
- API request/response logging
- Settings persistence
- Widget update cycles
- Error conditions

## Publishing

### Prepare for Release
1. Update version in `gradle.properties`
2. Update `CHANGELOG.md`
3. Test thoroughly
4. Build and verify plugin

### Build Distribution
```bash
./gradlew buildPlugin
```

### Publish to JetBrains Marketplace
```bash
./gradlew publishPlugin
```
(Requires PUBLISH_TOKEN environment variable)

## Contributing

### Pull Request Process
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Add tests if applicable
5. Ensure all tests pass: `./gradlew test`
6. Commit your changes: `git commit -m 'Add amazing feature'`
7. Push to the branch: `git push origin feature/amazing-feature`
8. Open a Pull Request

### Code Review Guidelines
- Ensure code follows project conventions
- Add appropriate tests
- Update documentation if needed
- Verify plugin works in development IDE

## Resources

- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/)
- [OpenRouter API Documentation](https://openrouter.ai/docs/api-reference)
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin)
