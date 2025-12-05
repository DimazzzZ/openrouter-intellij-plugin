# Changelog

All notable changes to the OpenRouter IntelliJ Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2025-12-05

### New Features

#### First-Time User Experience
- **Setup Wizard** - Interactive onboarding dialog guiding new users through configuration
- **Welcome Notifications** - Friendly introduction and quick-start tips upon plugin activation
- **Configuration Assistance** - Step-by-step setup process for provisioning keys and proxy configuration

#### Advanced Model Filtering
- **Provider-Based Filtering** - Filter AI models by provider (OpenAI, Anthropic, Google, etc.)
- **Context Window Filtering** - Sort and filter models by token limits (1K, 4K, 8K, 16K, 32K+)
- **Capabilities Filtering** - Filter models by special capabilities (vision, function calling, etc.)
- **Quick Preset Filters** - One-click filters for popular configurations (GPT-4 level, Claude equivalents)

#### Enhanced Statistics Display
- **Modal Statistics Dialog** - Statistics popup now opens as a proper modal dialog instead of popup balloon
- **Improved Data Loading** - Better handling of loading states and error conditions
- **Thread-Safe Operations** - Enhanced threading for reliable asynchronous data loading
- **Configuration Validation** - Comprehensive null handling and API response validation

### Testing & Quality

#### Test Coverage Expansion
- **100+ New Tests** - Comprehensive test suite additions bringing total to 300+ tests
- **Statistics Popup Testing** - 30+ new tests for popup functionality, configuration, and threading
- **Model Filtering Tests** - Complete test coverage for new filtering capabilities
- **Setup Wizard Tests** - 50+ tests for onboarding dialog functionality
- **Network Error Testing** - Dedicated test suite for robust error handling scenarios

#### Bug Fixes & Stability
- **Crash Prevention** - Fixed application crash when output tokens exceed 1000
- **Dialog Behavior** - Prevented statistics dialog from closing when pressing Enter in search field
- **Proxy Server Persistence** - Proxy config now persists correctly across IDE restarts without unwanted auto-start
- **Keyboard Navigation** - Improved accessibility and keyboard interaction handling

### Technical Improvements

#### Code Quality & Architecture
- **OpenAIBaseServlet** - New base servlet class for unified OpenAI API handling
- **Model Provider Utils** - Comprehensive utility functions for model management and filtering
- **Constants Extraction** - Proper constant definitions replacing magic numbers throughout codebase
- **Error Handling Enhancement** - Graceful degradation for network connectivity issues

#### User Interface Enhancements
- **Dialog Wrapper Implementation** - Statistics popup now extends DialogWrapper for native IntelliJ behavior
- **Loading State Management** - Improved user feedback during data loading operations
- **Component Synchronization** - Better alignment and spacing of UI components
- **Input Validation** - Enhanced field validation with immediate user feedback

### Compatibility & Integration

#### IDE Support Extension
- **JetBrains IDE 2025.3.X Support** - Extended compatibility for newest IDE versions including Rider
- **Deprecated API Updates** - Replaced deprecated PluginDescriptor.isEnabled() with PluginManagerCore.isPluginEnabled()
- **Backward Compatibility** - Seamless upgrade path for existing users from 2023.2+

#### Build System Updates
- **Kotlin Plugin Version** - Updated to latest stable version for improved compatibility
- **Java Toolchain Configuration** - Explicit toolchain setup for consistent builds across environments
- **Gradle Dependencies** - Dependency updates and security patches

## [0.2.1] - 2025-10-04

- fix: resolve JetBrains plugin verifier compatibility issues and optimize workflows

## [0.2.0] - 2025-10-04

### OpenRouter models support for 3rd-party AI Assistants

#### AI Assistant Integration
- **Local Proxy Server** - Embedded Jetty-based OpenAI-compatible proxy server (ports 8080-8090)
- **Custom Model Connection** - Allows JetBrains AI Assistant to connect to OpenRouter via custom server configuration
- **400+ Models Access** - Use OpenRouter's entire model catalog in AI Assistant through proxy
- **OpenAI Compatibility Layer** - Complete OpenAI API compatibility for seamless integration
- **CORS Support** - Proper cross-origin request handling for web-based integrations
- **Manual Configuration** - Requires setting up custom model/server in AI Assistant settings

#### Favorite Models Management
- **Favorites Panel** - Dedicated UI for managing favorite AI models
- **Quick Access** - Fast selection of preferred models
- **Drag & Drop Reordering** - Intuitive model organization
- **Persistent Storage** - Favorites saved across IDE sessions
- **Dual-Panel UI** - Available models and favorites side-by-side

### Enhancements

#### API Key Management
- **API Key Caching** - Reduced redundant API calls for better performance
- **Silent Regeneration** - Automatic "IntelliJ IDEA Plugin" key recreation when needed
- **Enhanced Validation** - Improved key verification during creation and updates
- **Better Error Handling** - More informative error messages and logging

#### UI/UX Improvements
- **IntelliJ UI DSL v2** - Modern UI components with better layout management
- **ApiKeyManager & ProxyServerManager** - Dedicated managers for cleaner code separation
- **Improved Status Updates** - Real-time proxy server state display
- **Better Popup Positioning** - Status bar popup now appears above widget
- **Consistent Panel Heights** - Aligned UI components for better visual consistency

#### Technical Improvements
- **OpenRouterRequestBuilder** - Centralized HTTP request construction (eliminated 12+ duplicate header patterns)
- **Duplicate Request Detection** - Request hash generation to prevent duplicate processing
- **Model Name Passthrough** - Simplified model handling by removing unnecessary mapping logic
- **HTTP Client Timeouts** - Proper timeout configuration for better reliability
- **Enhanced Logging** - Production-safe logging with configurable debug levels

### Testing & Quality

#### Test Coverage
- **207+ Tests** - Expanded from 22 to 207+ comprehensive tests (100% pass rate)
- **FavoriteModelsService Tests** - 11 new tests for favorites management
- **Encryption Tests** - 10 tests covering secure credential storage
- **Integration Tests** - 50+ tests for API communication and proxy server
- **Request Builder Tests** - 12 tests validating refactored HTTP request construction
- **Dependency Injection** - Made services testable with optional constructor parameters

#### Code Quality
- **Zero Critical Code Smells** - Fixed all 7 critical detekt issues
  - ComplexCondition: 1 fixed
  - NestedBlockDepth: 3 fixed
  - LongMethod: 3 fixed
- **Safe Refactoring Process** - Documented 11-step refactoring methodology
- **Extract Method Pattern** - Reduced method complexity across codebase
- **Early Returns** - Reduced nesting depth from 5+ to 2-3 levels
- **Wildcard Import Cleanup** - Replaced with specific imports

#### CI/CD
- **GitHub Actions Workflows** - Automated CI, extended tests, PR automation
- **Detekt Integration** - Automated code quality checks on PRs
- **Release Management** - Automated release workflow

### Bug Fixes
- **API Key Handling** - Fixed invalid API key usage from AI Assistant Authorization header
- **Memory Issues** - Disabled integration tests by default to prevent memory exhaustion
- **Test Hanging** - Improved timeout and memory settings for test execution
- **Proxy URL Handling** - Enhanced URL generation and clipboard copy functionality
- **Settings Persistence** - Streamlined settings save/load operations

### Documentation
- **Comprehensive Guides** - Added DEBUGGING.md, DEVELOPMENT.md, TESTING.md
- **Code Quality Guide** - Detekt usage and refactoring best practices
- **AI Assistant Setup** - Step-by-step integration instructions
- **Architecture Documentation** - Detailed component descriptions and diagrams
- **Troubleshooting Sections** - Common issues and solutions

### Refactoring
- **ChatCompletionServlet** - Extracted 13 helper methods, reduced from 83 to 22 lines
- **FavoriteModelsService** - Added dependency injection for testability
- **OpenRouterSettingsPanel** - Migrated to UI DSL v2 with manager classes
- **Request/Response Translation** - Simplified model name handling
- **Settings Panel Layout** - Improved component organization and alignment

### Architecture Changes
- **Service-Oriented Refactoring** - Better separation of concerns
- **Manager Pattern** - Dedicated managers for API keys and proxy server
- **Builder Pattern** - Centralized request building with OpenRouterRequestBuilder
- **Lazy Initialization** - Improved service initialization patterns

### Security
- **Production-Safe Logging** - Removed all real API keys from codebase
- **Localhost-Only Proxy** - Proxy server bound to 127.0.0.1 only
- **Enhanced Encryption** - Comprehensive encryption tests and validation

### Requirements
- **Java**: JDK 17 or higher (unchanged)
- **Platform**: Windows, macOS, Linux (unchanged)
- **IntelliJ Platform**: 2023.2+ (unchanged)
- **Optional**: JetBrains AI Assistant plugin for AI integration

---

## [0.1.0] - 2025-09-18

### Initial Release
First beta release of the OpenRouter IntelliJ Plugin with comprehensive OpenRouter.ai integration.

### Core Features
- **Interactive Status Bar Widget** - Real-time usage display with comprehensive popup menu
- **Advanced API Key Management** - Secure provisioning key support with automatic API key creation
- **Detailed Usage Analytics** - Track token consumption, costs, and model performance
- **Real-time Monitoring** - Live connection status with color-coded indicators
- **Statistics Popup** - Comprehensive usage analytics in modal dialog
- **Smart Authentication** - Intelligent login/logout with confirmation dialogs

### Technical Implementation
- **Provisioning Key Support** - Full integration with OpenRouter's provisioning key system
- **Automatic API Key Creation** - Plugin creates "IntelliJ IDEA Plugin" API key automatically
- **Secure Storage** - Encrypted credential storage using IntelliJ's secure storage
- **Asynchronous Operations** - Non-blocking API calls with CompletableFuture
- **Error Handling** - Graceful error recovery with user-friendly messages
- **Settings Validation** - Built-in API key testing and validation

### API Integration
- **Multiple Endpoints** - Support for `/api/v1/keys`, `/api/v1/credits`, `/api/v1/activity`
- **Authentication Patterns** - Proper handling of provisioning keys vs API keys
- **Response Parsing** - Robust JSON parsing with error handling
- **Connection Testing** - Built-in connectivity verification
- **Usage Tracking** - Comprehensive usage analytics and cost monitoring

### User Experience
- **Intuitive Interface** - Clean, professional UI matching IntelliJ design patterns
- **Contextual Tooltips** - Helpful information and usage summaries
- **Quick Actions** - Fast access via Tools menu and popup menu
- **Documentation Links** - Direct access to OpenRouter documentation
- **Feedback Integration** - Easy bug reporting and feature requests

### Security & Privacy
- **Secure Credential Storage** - Uses IntelliJ's built-in credential storage
- **API Key Encryption** - All sensitive data is encrypted at rest
- **No Data Collection** - Plugin doesn't collect or transmit user data
- **Local Processing** - All operations performed locally

### Architecture
- **Kotlin Implementation** - Modern, type-safe codebase
- **Service-Oriented Design** - Clean separation of concerns
- **IntelliJ Platform SDK** - Native integration with IDE features
- **OkHttp Client** - Reliable HTTP communication
- **Gson Serialization** - Robust JSON handling
- **Comprehensive Testing** - 22 passing tests covering core functionality

### UI Components
- **Status Bar Widget** - Minimal, informative display similar to GitHub Copilot
- **Settings Panel** - Comprehensive configuration with validation
- **Statistics Popup** - Detailed usage analytics in modal dialog

### Supported Platforms
- **IntelliJ IDEA** 2023.2+ (Community & Ultimate)
- **WebStorm** 2023.2+
- **PyCharm** 2023.2+ (Community & Professional)
- **PhpStorm** 2023.2+
- **RubyMine** 2023.2+
- **CLion** 2023.2+
- **Android Studio** 2023.2+
- **GoLand** 2023.2+
- **Rider** 2023.2+

### Requirements
- **Java**: JDK 17 or higher
- **Platform**: Windows, macOS, Linux
- **OpenRouter Account**: Free or paid account with API access
- **Internet Connection**: Required for API communication
