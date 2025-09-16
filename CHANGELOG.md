# Changelog

All notable changes to the OpenRouter IntelliJ Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-09-16

### Added
- Initial release of OpenRouter IntelliJ Plugin
- Status bar widget for real-time quota and usage display
- Settings panel for API key configuration and preferences
- Tool window with detailed usage statistics
- Auto-refresh functionality for quota information
- Support for multiple display modes (cost/percentage)
- Integration with IntelliJ's Tools menu
- Connection testing and error handling
- Support for IntelliJ IDEA 2023.2+

### Features
- **Status Bar Integration**: Real-time display similar to GitHub Copilot
- **API Integration**: Full OpenRouter API support with authentication
- **Usage Monitoring**: Track token consumption and costs
- **Settings Management**: Secure API key storage and configuration
- **Tool Window**: Comprehensive usage statistics view
- **Actions**: Quick access via Tools menu and keyboard shortcuts

### Technical Details
- Built with Kotlin and IntelliJ Platform SDK
- Uses OkHttp for HTTP client
- Gson for JSON parsing
- Secure credential storage
- Asynchronous API calls with CompletableFuture

### Supported IDEs
- IntelliJ IDEA 2023.2+ (Community and Ultimate)
- WebStorm 2023.2+
- PyCharm 2023.2+
- PhpStorm 2023.2+
- RubyMine 2023.2+
- CLion 2023.2+
- Android Studio 2023.2+
