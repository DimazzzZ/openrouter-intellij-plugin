# 🛠️ Development Guide

This guide covers development setup, building, testing, and contributing to the OpenRouter IntelliJ Plugin.

## 📋 Prerequisites

### Required Tools
- **JDK 17 or higher** - Required for IntelliJ Platform development
- **IntelliJ IDEA** - Ultimate or Community Edition with Plugin Development support
- **Git** - For version control and collaboration

### Optional Tools
- **ImageMagick** - For icon processing and optimization
- **Docker** - For containerized testing environments
- **Postman/Insomnia** - For API testing and development

### OpenRouter Account
- **Free Account** - Sign up at [OpenRouter.ai](https://openrouter.ai)
- **Provisioning Key** - Get from [Provisioning Keys](https://openrouter.ai/settings/provisioning-keys)
- **API Documentation** - Familiarize with [OpenRouter API](https://openrouter.ai/docs)

## 🚀 Quick Start

### 1. Repository Setup
```bash
# Clone the repository
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin

# Make gradlew executable (Unix/macOS)
chmod +x gradlew

# Verify Java version
java -version  # Should be JDK 17+
```

### 2. Build and Verify
```bash
# 🏗️ Build the plugin
./gradlew build --no-daemon

# 🧪 Run tests to verify setup
./gradlew test --no-daemon

# 🔍 Verify plugin structure
./gradlew verifyPlugin --no-daemon
```

### 3. Development Testing
```bash
# 🚀 Run in development IDE (recommended for testing)
./gradlew runIde --no-daemon

# 📦 Build distribution for manual installation
./gradlew buildPlugin --no-daemon

# 📁 Distribution will be in: build/distributions/openrouter-intellij-plugin-*.zip
```

### 4. Local Installation
```bash
# Option 1: Install from disk in IntelliJ IDEA
# Settings > Plugins > ⚙️ > Install Plugin from Disk > select ZIP file

# Option 2: Use development IDE (safer for testing)
./gradlew runIde --no-daemon
```

## 📝 Version Management

### Single Source of Truth
All plugin metadata is centralized in `gradle.properties` for consistency:

```properties
# Core plugin information
pluginVersion = 0.1.0
pluginName = OpenRouter
pluginGroup = org.zhavoronkov
pluginId = org.zhavoronkov.openrouter

# Metadata
pluginDisplayName = OpenRouter
pluginVendorName = Dmitry Zhavoronkov
pluginVendorEmail = openrouter-plugin@zhavoronkov.org
pluginDescription = OpenRouter plugin for IntelliJ IDEA...

# Compatibility
pluginSinceBuild = 232        # IntelliJ 2023.2+
pluginUntilBuild = 252.*      # IntelliJ 2025.2+
```

### Version Update Process
```bash
# 🔄 Update version (if update script exists)
./scripts/update-version.sh 0.1.0

# 📝 Manual update in gradle.properties
# Edit pluginVersion = 0.1.0

# ✅ Verify version
./gradlew properties | grep pluginVersion

# 🏗️ Build with new version
./gradlew clean build --no-daemon
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

## 🏗️ Project Architecture

### Directory Structure
```
openrouter-intellij-plugin/
├── 📁 build.gradle.kts              # Build configuration & dependencies
├── 📁 gradle.properties             # Plugin metadata & versions
├── 📁 settings.gradle.kts           # Gradle settings
├── 📁 src/main/kotlin/org/zhavoronkov/openrouter/
│   ├── 🎯 actions/                  # Plugin actions & menu items
│   │   ├── OpenSettingsAction.kt    # Open settings dialog
│   │   ├── RefreshQuotaAction.kt    # Refresh quota information
│   │   └── ShowUsageAction.kt       # Show usage statistics
│   ├── 🎨 icons/                    # Icon definitions & resources
│   │   └── OpenRouterIcons.kt       # Icon constants & loading
│   ├── 📊 models/                   # Data models & DTOs
│   │   ├── ConnectionStatus.kt      # Connection state enum
│   │   └── OpenRouterModels.kt      # API response models
│   ├── ⚙️ services/                 # Core business logic
│   │   ├── OpenRouterService.kt     # API communication service
│   │   ├── OpenRouterSettingsService.kt # Settings persistence
│   │   └── OpenRouterGenerationTrackingService.kt # Usage tracking
│   ├── 🔧 settings/                 # Settings UI components
│   │   ├── OpenRouterConfigurable.kt # Settings page configuration
│   │   └── OpenRouterSettingsPanel.kt # Settings UI panel
│   ├── 📍 statusbar/                # Status bar integration
│   │   ├── OpenRouterStatusBarWidget.kt # Main status bar widget
│   │   └── OpenRouterStatusBarWidgetFactory.kt # Widget factory
│   ├── 🛠️ toolwindow/               # Tool window components (disabled in v1.0)
│   │   ├── OpenRouterToolWindowContent.kt # Tool window content (future feature)
│   │   └── OpenRouterToolWindowFactory.kt # Tool window factory (future feature)
│   ├── 🎭 ui/                       # UI components & dialogs
│   │   └── OpenRouterStatsPopup.kt  # Statistics popup dialog
│   └── 🔧 utils/                    # Utility classes
│       └── PluginLogger.kt          # Logging utilities
├── 📁 src/main/resources/
│   ├── 📁 META-INF/
│   │   ├── plugin.xml               # Plugin configuration
│   │   ├── pluginIcon.png           # Plugin icon (16x16)
│   │   └── pluginIcon@2x.png        # Plugin icon (32x32)
│   └── 📁 icons/                    # UI icons & branding
│       ├── openrouter-logo.png      # Official OpenRouter logo
│       ├── openrouter-13.png        # Status bar icon (13x13)
│       ├── openrouter-16.png        # Menu icon (16x16)
│       └── openrouter-40.png        # Large icon (40x40)
├── 📁 src/test/kotlin/              # Test suites
│   ├── SimpleUnitTest.kt            # Unit tests (15 tests)
│   ├── ApiIntegrationTest.kt        # API tests (7 tests)
│   └── 📁 resources/mocks/          # Mock API responses
└── 📁 docs/                         # Documentation files
    ├── README.md                    # Main documentation
    ├── DEVELOPMENT.md               # This file
    ├── TESTING.md                   # Testing guide
    └── CHANGELOG.md                 # Version history
```

## 🔧 Key Components

### 🏢 Core Services (Application-Level)
- **OpenRouterService** - Central API communication hub
  - Handles all OpenRouter API endpoints (`/keys`, `/credits`, `/activity`)
  - Manages authentication patterns (provisioning keys vs API keys)
  - Provides async operations with CompletableFuture
  - Includes connection testing and error handling

- **OpenRouterSettingsService** - Configuration management
  - Secure credential storage using IntelliJ's credential store
  - Settings validation and persistence
  - Single source of truth for all configuration
  - Automatic migration and compatibility handling

- **OpenRouterGenerationTrackingService** - Usage analytics
  - Tracks API calls and token usage
  - Maintains generation history and statistics
  - Provides cost analysis and performance metrics
  - Configurable tracking limits and retention

### 🎨 UI Components
- **OpenRouterStatusBarWidget** - Main user interface
  - Real-time status display with color-coded indicators
  - Comprehensive popup menu with all features
  - Smart tooltips with usage summaries
  - Minimal footprint similar to GitHub Copilot

- **OpenRouterSettingsPanel** - Configuration interface
  - Provisioning key and API key management
  - API key creation and validation
  - Settings testing and verification
  - User-friendly error messages and guidance

- **OpenRouterStatsPopup** - Detailed analytics
  - Modal dialog with comprehensive usage statistics
  - Real-time data fetching and display
  - Cost analysis and budget tracking
  - Activity summaries and model usage patterns

- **OpenRouterToolWindowContent** - Extended monitoring (Future Feature)
  - Currently disabled in plugin.xml
  - Planned for future release with persistent analytics
  - Will provide historical usage data and trends
  - Performance metrics and diagnostics capabilities

### 🎯 Actions & Integration
- **ShowUsageAction** - Opens usage statistics popup (tool window disabled in v1.0)
- **RefreshQuotaAction** - Manually refreshes quota and usage data
- **OpenSettingsAction** - Direct access to plugin configuration
- **Tools Menu Integration** - Native IntelliJ menu integration
- **Status Bar Integration** - Seamless IDE status bar integration

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
