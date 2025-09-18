# Changelog

All notable changes to the OpenRouter IntelliJ Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-09-18

### 🎉 Initial Release
First beta release of the OpenRouter IntelliJ Plugin with comprehensive OpenRouter.ai integration.

### ✨ Core Features
- **Interactive Status Bar Widget** - Real-time usage display with comprehensive popup menu
- **Advanced API Key Management** - Secure provisioning key support with automatic API key creation
- **Detailed Usage Analytics** - Track token consumption, costs, and model performance
- **Real-time Monitoring** - Live connection status with color-coded indicators
- **Statistics Popup** - Comprehensive usage analytics in modal dialog
- **Smart Authentication** - Intelligent login/logout with confirmation dialogs

### 🔧 Technical Implementation
- **Provisioning Key Support** - Full integration with OpenRouter's provisioning key system
- **Automatic API Key Creation** - Plugin creates "IntelliJ IDEA Plugin" API key automatically
- **Secure Storage** - Encrypted credential storage using IntelliJ's secure storage
- **Asynchronous Operations** - Non-blocking API calls with CompletableFuture
- **Error Handling** - Graceful error recovery with user-friendly messages
- **Settings Validation** - Built-in API key testing and validation

### 🌐 API Integration
- **Multiple Endpoints** - Support for `/api/v1/keys`, `/api/v1/credits`, `/api/v1/activity`
- **Authentication Patterns** - Proper handling of provisioning keys vs API keys
- **Response Parsing** - Robust JSON parsing with error handling
- **Connection Testing** - Built-in connectivity verification
- **Usage Tracking** - Comprehensive usage analytics and cost monitoring

### 🎯 User Experience
- **Intuitive Interface** - Clean, professional UI matching IntelliJ design patterns
- **Contextual Tooltips** - Helpful information and usage summaries
- **Quick Actions** - Fast access via Tools menu and popup menu
- **Documentation Links** - Direct access to OpenRouter documentation
- **Feedback Integration** - Easy bug reporting and feature requests

### 🔒 Security & Privacy
- **Secure Credential Storage** - Uses IntelliJ's built-in credential storage
- **API Key Encryption** - All sensitive data is encrypted at rest
- **No Data Collection** - Plugin doesn't collect or transmit user data
- **Local Processing** - All operations performed locally

### 🏗️ Architecture
- **Kotlin Implementation** - Modern, type-safe codebase
- **Service-Oriented Design** - Clean separation of concerns
- **IntelliJ Platform SDK** - Native integration with IDE features
- **OkHttp Client** - Reliable HTTP communication
- **Gson Serialization** - Robust JSON handling
- **Comprehensive Testing** - 22 passing tests covering core functionality

### 🎨 UI Components
- **Status Bar Widget** - Minimal, informative display similar to GitHub Copilot
- **Settings Panel** - Comprehensive configuration with validation
- **Statistics Popup** - Detailed usage analytics in modal dialog

### 🔧 Supported Platforms
- **IntelliJ IDEA** 2023.2+ (Community & Ultimate)
- **WebStorm** 2023.2+
- **PyCharm** 2023.2+ (Community & Professional)
- **PhpStorm** 2023.2+
- **RubyMine** 2023.2+
- **CLion** 2023.2+
- **Android Studio** 2023.2+
- **GoLand** 2023.2+
- **Rider** 2023.2+

### 📋 Requirements
- **Java**: JDK 17 or higher
- **Platform**: Windows, macOS, Linux
- **OpenRouter Account**: Free or paid account with API access
- **Internet Connection**: Required for API communication
