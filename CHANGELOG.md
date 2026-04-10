# Changelog

All notable changes to the OpenRouter IntelliJ Plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.2] - 2026-04-10

### New Features

#### 💰 Model Pricing Display
- **Pricing Columns** - Input Price and Output Price columns added to model selection tables
- **Per-Model Spend** - Stats popup now shows spend breakdown by model (e.g., "• model/name — $0.1234")
- **Setup Wizard Updates** - Model selection table expanded to show pricing columns
- **ModelPricingFormatter** - New utility for consistent pricing display (USD per 1M tokens)

#### 📊 Total Models Count
- **Model Catalog Visibility** - Favorite Models settings now shows "X shown (Y total)"
- **All Modalities Support** - Count includes text, image, audio, and video models via `output_modalities=all`
- **Async Loading** - Model count fetched asynchronously when settings panel opens

### Bug Fixes

#### 💬 Chat Text Selection & Markdown Rendering
- **Fixed Chat Copy/Select** - Assistant output in chat is now selectable and copyable
- **Markdown Rendering** - Migrated assistant messages to JEditorPane with flexmark for proper Markdown display
- **Improved Alignment** - Better message alignment and font consistency
- **Markdown Features** - Support for tables, autolinks, strikethrough, task lists, and more

### Improvements

#### 🔒 Privacy & Marketplace Compliance
- **Plugin Verifier Gate** - Release workflow now enforces Plugin Verifier checks before publish
- **New Documentation** - Added Privacy Policy, EULA, and Marketplace Submission Checklist
- **Explicit Consent** - Settings UI label updated to make balance sharing consent clear

### Testing
- **MarkdownRendererTest** - Comprehensive test coverage for markdown rendering and wrappers
- **ModelPricingFormatterTest** - Pricing format validation tests
- **StatsPopupModelSpendTest** - Per-model spend breakdown tests
- **SetupWizardDialogTableModelTest** - Model table with pricing tests
- **FavoriteModelsTableModelsTest** - Updated for new pricing columns

## [0.5.1] - 2026-03-27

### New Features

#### 🎯 Custom Presets Support
- **New Settings Page** - `Tools → OpenRouter → Presets` for managing custom presets
- **Built-in Presets** - `openrouter/auto` and `openrouter/free` always available with descriptions
- **Custom Presets** - Add presets by entering the slug from your OpenRouter presets page
- **Slug Validation** - Automatic normalization (lowercase, alphanumeric + hyphens only)
- **Chat Integration** - Presets appear at the top of the model selector in chat panel
- **Prefix Display** - Custom presets shown with `@preset/` prefix (e.g., `@preset/email-copywriter`)

#### 🔌 BalanceProvider Extension Point API
- **New Extension Point** - Allows other plugins to receive OpenRouter balance data updates in real-time
- **BalanceProvider Interface** - Simple contract with `onBalanceUpdated()`, `onBalanceLoading()`, and `onBalanceError()` methods
- **BalanceData Class** - Immutable data transfer object with validation, formatting helpers, and derived calculations:
  - Core data: `totalCredits`, `totalUsage`, `remainingCredits`, `timestamp`, `todayUsage`
  - Helper methods: `formattedTotalCredits()`, `usagePercentage()`, `isLowBalance()`, `isCriticalBalance()`
- **User Control** - Toggle in Settings → Tools → OpenRouter to enable/disable balance sharing
- **Exception Isolation** - Misbehaving providers cannot crash the notification system
- **Plugin Integration** - Enables dependent plugins (like Token Pulse) to display balance without duplicate API calls

### Improvements

#### 🚀 Platform Compatibility
- **IntelliJ Platform Gradle Plugin 2.3.0** - Migrated from deprecated `org.jetbrains.intellij` 1.17.4
- **Removed Upper Version Bound** - No `untilBuild` limit, compatible with all future IDE versions
- **Extended IDE Support** - Now supports IntelliJ IDEA 2024.2+ through 2026.1+ and beyond
- **Java 21** - Updated from Java 17 (required by IntelliJ Platform 2024.2+)
- **Kotlin 2.0.21** - Updated for compatibility with IDE's Kotlin runtime

#### UI Improvements
- **Moved API Keys Group** - Now at bottom of main settings (only visible with Extended scope)
- **Compact Layout** - Adjustments in FavoriteModelsSettingsPanel
- **Improved Spacing** - Better filter panel spacing

### Testing

- **260+ New Tests** - Comprehensive test coverage across multiple areas:
  - `PresetsManagerTest` - 19 tests for add/remove/normalize/validate operations
  - `BalanceDataTest` - Validation, calculations, formatting tests
  - `BalanceProviderNotifierTest` - Provider notification tests
  - `BalanceProviderIntegrationTest` - Integration scenarios
  - Additional tests for actions, constants, models, utils, and UI components

### Compatibility Matrix

| IDE Version | Build Range | Status |
|-------------|-------------|--------|
| 2024.2+ | 242.* | ✅ Supported |
| 2025.1+ | 251.* | ✅ Supported |
| 2026.1+ | 261.* | ✅ Supported |
| Future | 270.*, 280.*, ... | ✅ Supported (no upper bound) |

## [0.5.0] - 2026-03-19

### New Features

#### 💬 Chat Tool Window
- **Multi-Chat Support** - Create, switch between, and delete multiple chat sessions directly in the IDE
- **Persistent Storage** - Chat history and model preferences saved to JSON files in the plugin config directory
- **Token Tracking** - Real-time token estimation for input and cumulative token count per session
- **Model Selection** - Choose from favorite models with persistence of last selected model
- **Smart Title Generation** - Chat titles auto-generated from first message (truncated to 50 chars)
- **Keyboard Shortcuts** - `Enter` to send message, `Cmd/Ctrl+Enter` to insert newline
- **Tabbed Interface** - "Status" tab for account info, "Chat" tab for conversations
- **Context Menu** - Right-click on chats for quick actions (Open, Delete)

#### 📊 Shared Stats Management
- **Centralized Cache** - New `OpenRouterStatsCache` service ensures consistent data across UI components
- **Pub/Sub Architecture** - Uses IntelliJ's message bus pattern for update notifications
- **Reduced API Calls** - Single fetch serves multiple consumers (status bar, popup, tool window)
- **Thread-Safe** - Atomic state handling for concurrent refresh requests

#### 🔐 Secure API Key Storage
- **OS-Native Storage** - Uses Keychain (macOS), Credential Manager (Windows), libsecret/KWallet (Linux)
- **Automatic Migration** - Legacy keys from encrypted XML settings automatically migrated to PasswordSafe
- **Backward Compatible** - Seamless upgrade from previous versions with no data loss
- **In-Memory Cache** - EDT-safe access with async persistence to PasswordSafe

#### 📈 Real-Time Cost Tracking
- **Accurate "Today" Statistics** - Local tracking when OpenRouter API data unavailable
- **Credit Usage History** - Periodic snapshots (every 5 minutes) with 48-hour retention
- **Days Remaining Estimate** - Calculated from yesterday's spending rate
- **Timezone-Aware** - Proper day boundary calculations using local timezone

### Security Fixes

- **CVE-2023-3635 (Okio)** - Enforced `okio-jvm:3.4.0` to address GZIPSource denial of service vulnerability
- **CVE-2020-15250 (JUnit)** - Enforced `junit:4.13.1` to address temporary file information disclosure

### Improvements

#### Dependency Updates
| Dependency | Previous | New | Notes |
|------------|----------|-----|-------|
| Jetty Server | 11.0.18 | 12.1.6 | Major version upgrade with package migration |
| Jetty Servlet | 11.0.18 | 12.1.6 (ee10) | New EE10 servlet package |
| Jakarta Servlet API | 5.0.0 | 6.0.0 | Compatible with Jetty 12 |
| AssertJ | 3.24.2 | 3.27.7 | Test framework update |

#### Bug Fixes
- **Duplicate API Key Handling** - Fixed issue where plugin only found first "IntelliJ IDEA Plugin" key
- **Key Cleanup** - Automatic cleanup of orphan/duplicate API keys while keeping valid matching key
- **Thread Safety** - Replaced `runBlocking` with proper coroutine scopes using `Dispatchers.IO`
- **EDT Compliance** - In-memory caching prevents EDT violations when accessing PasswordSafe

#### Code Quality
- **Detekt Refactoring** - Code quality improvements with static analysis
- **Test Coverage** - Enhanced tests for proxy server, multimodal validation, and OpenAI models
- **Resource Management** - `IntellijApiKeyManager` implements `Disposable` for proper cleanup

### Testing

- **Proxy Server Tests** - Server lifecycle, port selection, HTTP integration, servlet validation
- **Request/Response Translation** - Enhanced tests with JUnit 5 `@Nested` annotations
- **Multimodal Validation** - Expanded coverage for image, audio, video, and file content types
- **OpenAI Models** - New test coverage for all OpenAI API model classes
- **Credit History** - Tests for interpolation, pruning, and timezone handling

## [0.4.2] - 2026-01-20

### Improvements

- **Multimodal Support** - Added support for image, audio, and video content with AI models
- **Test Coverage** - Added automated integration tests for multimodal capabilities (images, audio, video)
- **Test Infrastructure** - Added TestMediaGenerator utility for automated test media generation

## [0.4.1] - 2025-12-23

### Bug Fixes

#### 🔧 SSE Streaming Format Compliance
- **Fixed AI Assistant Streaming** - Fixed SSE (Server-Sent Events) format to comply with specification
- **Blank Line Separators** - Added required blank lines after each SSE event to prevent JSON concatenation
- **Stream Termination** - Ensured all streams end with `[DONE]` marker followed by blank line
- **Error Handling** - Fixed error response SSE format to include proper event separators

**Root Cause**: SSE events were not separated by blank lines, causing the AI Assistant's JSON parser to receive concatenated JSON objects like `{"id":"1",...}{"id":"2",...}`, resulting in parsing error: "Expected EOF after parsing, but had { instead"

**Fix**: Added `writer.println()` after each SSE event in `StreamingResponseHandler.processStreamLine()` and error handlers to create the required blank line separator per SSE specification.

### Code Quality

#### 📝 Logging Improvements
- **Reduced Log Noise** - Moved verbose logging (getApiKey, getStoredApiKey, API key validation) from INFO to DEBUG level
- **Standardized Request IDs** - All log lines now use consistent `[Chat-XXXXXX]` format for easier log correlation
- **Fixed Log Levels** - Changed normal API request logging from WARN to DEBUG level
- **Request Duration Metrics** - Added request duration tracking to completion logs (e.g., `[Chat-000025] REQUEST COMPLETE (2118ms)`)

**Impact**: 78% reduction in INFO-level log volume, better performance visibility, no false warnings

#### 🧪 Testing
- **SSE Format Tests** - Added 11 tests for SSE format compliance
- **Regression Tests** - Added specific tests documenting the SSE parsing bug and fix
- **AI Assistant Compatibility** - Tests verify no JSON concatenation and proper event separation
- **Error Response Tests** - Tests ensure error responses follow SSE format with DONE marker

## [0.4.0] - 2025-12-22

### New Features

#### 🔄 Dynamic Plugin Support (NEW!)
- **No Restart Required** - Install, update, and uninstall plugin without restarting IDE
- **Disposable Services** - All 4 core services (ProxyService, OpenRouterService, SettingsService, GenerationTrackingService) implement Disposable
- **PluginLifecycleListener** - Tracks plugin load/unload events with detailed logging
- **Resource Cleanup** - Graceful shutdown of Jetty server (10s timeout), HTTP client connection pool, and async tasks
- **State Persistence** - Settings and generation tracking data saved before plugin unload
- **Memory Leak Prevention** - Proper cleanup prevents memory leaks and allows safe plugin reload
- **IntelliJ Compliance** - Meets all dynamic plugin requirements and restrictions

#### 🔐 OAuth/PKCE Authentication Flow
- **PKCE Authentication** - Implemented full PKCE (Proof Key for Code Exchange) authentication flow for secure API key generation
- **OAuth Code Exchange** - Secure OAuth code exchange mechanism for API key creation
- **PkceAuthHandler** - Dedicated 208-line OAuth/PKCE flow handler for improved security and maintainability
- **OAuth App Integration** - Added OAuth app name constant for improved authorization flow clarity

#### 🔑 Authentication Scope Management
- **Unified Authentication** - Unified API and provisioning key fields with improved loading feedback
- **Scope Management** - Added authentication scope management for API and provisioning keys
- **Property Accessors** - Refactored authentication scope management to use property accessors for better encapsulation
- **Synchronized Settings** - Synchronized authentication settings across UI components for consistency

#### ✅ Enhanced Key Validation
- **Comprehensive Validation** - Enhanced API key and provisioning key validation with improved error handling
- **Scope-Aware Validation** - Added key validation for current authentication scope
- **KeyValidator Utility** - Created 96-line KeyValidator utility for centralized validation logic
- **Centralized Error Messages** - Created 145-line ErrorMessages utility for consistent user feedback

### Code Quality & Architecture

#### Setup Wizard Refactoring
- **Extracted PkceAuthHandler** - 208-line dedicated OAuth/PKCE flow handler
- **Extracted SetupWizardConfig** - 44-line centralized configuration constants
- **Extracted SetupWizardErrorHandler** - 88-line standardized error handling with user-friendly messages
- **Extracted SetupWizardLogger** - 79-line controlled logging with different levels
- **Reduced Complexity** - Reduced SetupWizardDialog from 1,194 to 1,048 lines (146 lines removed)
- **Improved Testability** - Made OpenRouterService open with baseUrlOverride for better testing

#### Service Layer Improvements
- **Dynamic URL Generation** - Refactored endpoint methods in OpenRouterService for dynamic URL generation
- **Enhanced Logging** - Updated auth scope to REGULAR and enhanced logging for PKCE exchange process
- **Deprecated Method Removal** - Removed deprecated key validation methods and updated endpoint logging

#### UI/UX Enhancements
- **Event Listeners** - Implemented UI updates and event listeners for authentication scope changes
- **Null Safety** - Improved null safety and error handling in activity data processing and UI components
- **Welcome Notification** - Enhanced welcome notification formatting for better user experience
- **Proxy Settings** - Better proxy settings management and UI feedback

### Testing & Quality

#### Comprehensive Test Coverage
- **Authentication Tests** - Added comprehensive authentication tests for OpenRouterService (312 lines)
- **PKCE Tests** - PKCE auth code exchange tests with MockWebServer for realistic HTTP testing
- **Validation Tests** - API key and provisioning key validation tests
- **Network Error Tests** - Network error handling tests for robust error recovery
- **Integration Tests** - Added OpenRouterConfigurableIntegrationTest (161 lines)
- **Settings Panel Tests** - Enhanced OpenRouterSettingsPanelIntegrationTest and OpenRouterSettingsPanelUITest (243 lines)

### Technical Improvements

#### New Utility Classes
- **OpenRouterConstants** - 140-line constants file for OpenRouter API configuration
- **UIConstants** - 125-line constants file for UI configuration and magic numbers
- **ErrorMessages** - 145-line centralized error message management
- **KeyValidator** - 96-line validation utility for API and provisioning keys
- **OkHttpExtensions** - Enhanced HTTP client extensions for better request handling
- **OpenRouterRequestBuilder** - Improved request builder with better error handling

#### New Dynamic Plugin Support Files
- **PluginLifecycleListener** - 72-line listener for plugin load/unload events
- **DynamicPluginSupportTest** - 150-line test suite for dynamic plugin functionality
- **DYNAMIC_PLUGIN_SUPPORT.md** - 150-line comprehensive documentation with troubleshooting guide

#### Refactoring & Cleanup
- **Constants Extraction** - Replaced magic numbers with proper constants throughout codebase
- **Helper Classes** - Extracted helper classes from SetupWizardDialog for better separation of concerns
- **Improved Maintainability** - Better code organization and reduced method complexity
- **Enhanced Error Handling** - Standardized error handling across all components
- **Disposable Implementation** - All services properly implement Disposable for resource cleanup

### Statistics
- **38+ files changed**
- **3,800+ insertions(+), 550+ deletions(-)**
- **Net change: +3,250+ lines**
- **13 new files** - Utilities, handlers, listeners, tests, and documentation
- **10 new files created** (utilities, handlers, tests)

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
- **Deprecated API Updates** - Replaced deprecated PluginDescriptor.isEnabled() with PluginManagerCore.isDisabled() (negated)
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
