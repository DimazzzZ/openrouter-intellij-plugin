# Development Guide

This guide covers how to set up the development environment and contribute to the OpenRouter IntelliJ Plugin.

## Prerequisites

- **JDK 17 or higher** - Required for IntelliJ Platform development
- **IntelliJ IDEA** - Ultimate or Community Edition with Plugin Development support
- **Git** - For version control

## Development Setup

### 1. Clone the Repository
```bash
git clone https://github.com/DimazzzZ/openrouter-intellij-plugin.git
cd openrouter-intellij-plugin
```

### 2. Import Project
1. Open IntelliJ IDEA
2. Choose "Open" and select the project directory
3. IntelliJ will automatically detect the Gradle build and import the project

### 3. Configure SDK
1. Go to `File > Project Structure > Project`
2. Set Project SDK to JDK 17 or higher
3. Set Project language level to 17

## Building and Running

### Build the Plugin
```bash
./gradlew buildPlugin
```
The built plugin will be in `build/distributions/openrouter-intellij-plugin-1.0.0.zip`

### Run in Development IDE
```bash
./gradlew runIde
```
This starts a new IntelliJ IDEA instance with the plugin installed for testing.

### Run Tests
```bash
./gradlew test
```

### Verify Plugin
```bash
./gradlew verifyPlugin
```
This checks for common plugin issues and compatibility.

## Project Structure

```
openrouter-intellij-plugin/
├── build.gradle.kts              # Build configuration
├── gradle.properties             # Gradle properties
├── settings.gradle.kts           # Gradle settings
├── src/
│   ├── main/
│   │   ├── kotlin/com/openrouter/intellij/
│   │   │   ├── actions/          # Plugin actions
│   │   │   │   ├── OpenSettingsAction.kt
│   │   │   │   ├── RefreshQuotaAction.kt
│   │   │   │   └── ShowUsageAction.kt
│   │   │   ├── icons/            # Icon definitions
│   │   │   │   └── OpenRouterIcons.kt
│   │   │   ├── models/           # Data models
│   │   │   │   └── OpenRouterModels.kt
│   │   │   ├── services/         # Core services
│   │   │   │   ├── OpenRouterService.kt
│   │   │   │   └── OpenRouterSettingsService.kt
│   │   │   ├── settings/         # Settings UI
│   │   │   │   ├── OpenRouterConfigurable.kt
│   │   │   │   └── OpenRouterSettingsPanel.kt
│   │   │   ├── statusbar/        # Status bar widget
│   │   │   │   ├── OpenRouterStatusBarWidget.kt
│   │   │   │   └── OpenRouterStatusBarWidgetFactory.kt
│   │   │   └── toolwindow/       # Tool window
│   │   │       ├── OpenRouterToolWindowContent.kt
│   │   │       └── OpenRouterToolWindowFactory.kt
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── plugin.xml    # Plugin configuration
│   │       └── icons/            # Icon files
│   │           ├── openrouter-16.svg
│   │           ├── refresh-16.svg
│   │           └── settings-16.svg
│   └── test/                     # Test files
└── docs/                         # Documentation
```

## Key Components

### Services
- **OpenRouterService**: Handles API communication with OpenRouter
- **OpenRouterSettingsService**: Manages plugin settings and persistence

### UI Components
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
com.openrouter.intellij:DEBUG
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
