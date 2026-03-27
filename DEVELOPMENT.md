# 🛠️ Development Guide

This guide covers development setup, building, testing, and contributing to the OpenRouter IntelliJ Plugin.

## 📋 Prerequisites

### Required Tools
- **JDK 21 or higher** - Required for IntelliJ Platform 2024.2+ development
- **IntelliJ IDEA** - Ultimate or Community Edition with Plugin Development support
- **Git** - For version control and collaboration

### Optional Tools
- **ImageMagick** - For icon processing and optimization
- **Docker** - For containerized testing environments
- **Postman/Insomnia** - For API testing and development
- **JetBrains AI Assistant Plugin** - For testing AI Assistant integration

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

# 🧪 Run active tests (388 tests, excludes disabled integration/E2E tests)
./gradlew test --no-daemon

# 🧹 Run with clean state (tests first-run experience)
./gradlew clean runIde --no-daemon

# 🔧 Run integration tests (requires enabling @Disabled annotations)
# See TESTING.md for details on enabling 77 disabled tests

# 📦 Build distribution for manual installation
./gradlew buildPlugin --no-daemon

# 📁 Distribution will be in: build/distributions/openrouter-intellij-plugin-*.zip
```

**Note**: The project includes 77 disabled tests (integration, E2E, UI) that are valuable but not run by default. See [TESTING.md](TESTING.md) for details on when and how to enable them.

**Note**: Use `./gradlew clean runIde` to test the first-run experience (welcome notification and setup wizard) with a fresh state.

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
pluginVersion = 0.2.0
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
./scripts/update-version.sh 0.2.0

# 📝 Manual update in gradle.properties
# Edit pluginVersion = 0.2.0

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

#### Common Build Issues
```bash
# Configuration cache issues
./gradlew build --no-configuration-cache

# Clean build
./gradlew clean build --no-daemon

# Check compatibility
./gradlew verifyPlugin --no-daemon

# Test compilation issues
./gradlew compileTestKotlin --info
```

#### API Key Issues
- **401 Errors**: Ensure API key is configured in settings, not relying on Authorization headers
- **Invalid Keys**: Use provisioning keys for quota data, API keys for chat completions
- **Security**: Never commit real API keys - use placeholder values in tests and documentation

#### Proxy Server Issues
```bash
# Check proxy server status
curl http://localhost:8080/health

# Test model endpoint
curl http://localhost:8080/v1/models

# Debug proxy logs
# Enable debug logging: org.zhavoronkov.openrouter:DEBUG
```

#### Test Failures
```bash
# Run active tests only (default - excludes 77 disabled tests)
./gradlew test

# Run specific test categories
./gradlew test --tests "*ChatCompletionServletTest*"
./gradlew test --tests "*ApiKeyHandlingIntegrationTest*"

# Check for real API keys in tests
grep -r "sk-or-v1-" src/test/ --exclude-dir=mocks

# If you see "77 skipped" - those are disabled integration/E2E tests
# See TESTING.md for how to enable them when needed
```

#### Disabled Tests (77 Tests)
```bash
# Check why tests are disabled
grep -r "@Disabled" src/test/ --include="*.kt"

# Count disabled tests
find src/test -name "*.kt" -exec grep -l "@Disabled\|@DisabledIf" {} \; | wc -l

# Enable integration tests for deep testing (edit files to remove @Disabled)
# ⚠️ Enable E2E tests only with real API keys in .env (costs money)
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
│   ├── 🤖 integration/              # AI Assistant integration
│   │   └── AIAssistantIntegrationHelper.kt # AI Assistant setup utilities
│   ├── 📊 models/                   # Data models & DTOs
│   │   ├── ConnectionStatus.kt      # Connection state enum
│   │   └── OpenRouterModels.kt      # API response models
│   ├── 🌐 proxy/                    # OpenAI-compatible proxy server
│   │   ├── OpenRouterProxyServer.kt # Jetty-based HTTP server
│   │   ├── CorsFilter.kt           # CORS filter for cross-origin requests
│   │   ├── models/                 # Proxy-specific models
│   │   │   └── OpenAIModels.kt     # OpenAI API compatibility models
│   │   ├── servlets/               # HTTP request handlers
│   │   │   ├── ChatCompletionServlet.kt # Chat completions endpoint
│   │   │   ├── ModelsServlet.kt    # Models list endpoint
│   │   │   ├── HealthCheckServlet.kt # Health check endpoint
│   │   │   ├── RootServlet.kt      # Root endpoint handler
│   │   │   ├── EnginesServlet.kt   # OpenAI engines compatibility
│   │   │   └── OrganizationServlet.kt # Organization info endpoint
│   │   └── translation/            # Request/response translation
│   │       ├── RequestTranslator.kt # OpenAI to OpenRouter format
│   │       └── ResponseTranslator.kt # OpenRouter to OpenAI format
│   ├── ⚙️ services/                 # Core business logic
│   │   ├── OpenRouterService.kt     # API communication service
│   │   ├── OpenRouterSettingsService.kt # Settings persistence
│   │   ├── OpenRouterProxyService.kt # AI Assistant proxy server management
│   │   └── OpenRouterGenerationTrackingService.kt # Usage tracking
│   ├── 🚀 startup/                  # Startup activities
│   │   ├── ProxyServerStartupActivity.kt # Auto-start proxy server
│   │   └── WelcomeNotificationActivity.kt # First-run welcome notification (Phase 3)
│   ├── 🔧 settings/                 # Settings UI components
│   │   ├── OpenRouterConfigurable.kt # Settings page configuration
│   │   ├── OpenRouterSettingsPanel.kt # Main settings UI panel
│   │   ├── FavoriteModelsSettingsPanel.kt # Favorite models selector with filtering (Phase 1)
│   │   ├── ModelPresets.kt          # Predefined model lists (Phase 1)
│   │   └── ModelFilterCriteria.kt   # Filter state management (Phase 1)
│   ├── 📍 statusbar/                # Status bar integration
│   │   ├── OpenRouterStatusBarWidget.kt # Main status bar widget
│   │   └── OpenRouterStatusBarWidgetFactory.kt # Widget factory
│   ├── 🛠️ toolwindow/               # Tool window components (disabled in v1.0)
│   │   ├── OpenRouterToolWindowContent.kt # Tool window content (future feature)
│   │   └── OpenRouterToolWindowFactory.kt # Tool window factory (future feature)
│   ├── 🎭 ui/                       # UI components & dialogs
│   │   ├── OpenRouterStatsPopup.kt  # Statistics popup dialog
│   │   └── SetupWizardDialog.kt     # First-run setup wizard (Phase 3)
│   └── 🔧 utils/                    # Utility classes
│       ├── PluginLogger.kt          # Logging utilities
│       ├── ModelProviderUtils.kt    # Model filtering utilities (Phase 1)
│       └── EncryptionUtil.kt        # API key encryption
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
├── 📁 src/test/kotlin/              # Test suites (270+ tests)
│   ├── SimpleUnitTest.kt            # Unit tests (15 tests)
│   ├── ApiIntegrationTest.kt        # API tests (7 tests)
│   ├── ModelProviderUtilsTest.kt    # Filtering tests (28 tests, Phase 1)
│   ├── ModelPresetsTest.kt          # Preset tests (16 tests, Phase 1)
│   ├── ModelFilterCriteriaTest.kt   # Filter criteria tests (20 tests, Phase 1)
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

- **OpenRouterProxyService** - AI Assistant integration proxy
  - Manages local HTTP proxy server (Jetty-based)
  - Handles server lifecycle (start/stop/status)
  - Automatic port allocation (8080-8090 range)
  - OpenAI-compatible API endpoint exposure

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

## 🤖 AI Assistant Integration

### Proxy Server Architecture
The plugin includes a configurable local HTTP proxy server that enables JetBrains AI Assistant to access OpenRouter's 400+ models:

- **Technology**: Eclipse Jetty 11 embedded HTTP server
- **Port Configuration**: Configurable ports (default 8880-8899, avoids common conflicts)
- **Auto-start Control**: Configurable auto-start behavior (disabled by default)
- **Port Selection**: Specific port or auto-selection within configurable range
- **Protocol**: OpenAI-compatible REST API
- **Security**: Localhost-only (127.0.0.1), no external access
- **Authentication**: Handled transparently via OpenRouter plugin

### Supported Endpoints
```
GET  /health                    # Health check endpoint
GET  /v1/models                 # List available models  
POST /v1/chat/completions       # Chat completions (main AI endpoint)
GET  /v1/engines               # OpenAI engines compatibility
GET  /v1/organizations/org-*    # Organization info compatibility
```

### Request/Response Translation
The proxy server translates between OpenAI and OpenRouter formats:

**Request Translation** (`RequestTranslator.kt`):
- Converts OpenAI chat completion requests to OpenRouter format
- Maps model names (e.g., `gpt-4` → `openai/gpt-4`)
- Handles authentication with stored OpenRouter API keys
- Preserves all OpenAI request parameters

**Response Translation** (`ResponseTranslator.kt`):
- Converts OpenRouter responses to OpenAI-compatible format  
- Maintains consistent response structure and timing
- Handles error responses appropriately
- Preserves usage statistics and metadata

### Development Testing
```bash
# Start development IDE with proxy server
./gradlew runIde --no-daemon

# Note: Default port range is now 8880-8899 (configurable in settings)
# Check actual port in OpenRouter Settings > Proxy Server section

# Test proxy endpoints directly (replace 8880 with actual port)
curl http://localhost:8880/health
curl http://localhost:8880/v1/models

# Test chat completion (requires OpenRouter configuration)
curl -X POST http://localhost:8880/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-3.5-turbo","messages":[{"role":"user","content":"Hello"}]}'

# Test immediate settings application
# 1. Change proxy port in settings UI
# 2. Click "Start Proxy" (no need to click Apply first)
# 3. Verify proxy starts on new port
```

### Integration Components
- **OpenRouterProxyServer**: Jetty server management and configuration
- **Servlet Classes**: Handle different endpoint types (chat, models, health)
- **CorsFilter**: Enable cross-origin requests for web-based IDEs
- **ProxyServerStartupActivity**: Auto-start proxy server on IDE startup
- **AIAssistantIntegrationHelper**: Utilities for setup and configuration

## 🏗️ Setup Wizard Development

### Quick Wizard Testing
```bash
# Start fresh development IDE (recommended for testing first-run experience)
./gradlew clean runIde

# Test welcome notification + setup wizard
# Wizard appears on first project open automatically
```

### Setup Wizard Architecture
- **SetupWizardDialog.kt** - Multi-step onboarding dialog with embedded model selection
- **CardLayout Navigation** - Step-by-step flow with validation
- **Validation System** - Real-time provisioning key validation with visual feedback
- **Model Selection** - Embedded table with search, filtering, and checkbox selection
- **Configuration Saving** - Automatic settings persistence and completion tracking

## 🔍 Advanced Model Filtering Architecture

### Core Components
- **ModelFilterCriteria.kt** - Filter state management with active filter counting
- **ModelPresets.kt** - Predefined filter combinations (Multimodal, Coding, Cost-Effective)
- **ModelProviderUtils.kt** - Core filtering logic for provider, context, and capability filtering
- **ContextRange Enum** - Smart date parsing for context length filtering (1K, 4K, 8K, 16K, 32K+)

### Filtering Capabilities
- **Provider Filtering**: OpenAI, Anthropic, Google, Meta, Mistral, etc.
- **Context Filtering**: Automatic parsing of context lengths from model descriptions
- **Capability Filtering**: Vision, Audio, Tools, Image Generation detection
- **Quick Presets**: One-click filters for common use cases
- **Real-time Search**: Fuzzy matching across model names and descriptions

## 📊 Enhanced Statistics Dialog Development

- **DialogWrapper Migration** - Refactored from JBPopup to proper IntelliJ DialogWrapper
- **Native Modal Behavior** - Better IDE integration with standard modal dialog patterns
- **Asynchronous Data Loading** - Thread-safe UI updates with proper EDT handling
- **Configuration Validation** - Comprehensive error handling and null safety

## 🔔 Whats New Notification System

### Version-Specific Notifications
- **WhatsNewNotificationActivity.kt** - Automatic version update notifications
- **Once-Per-Version Logic** - Shows only for upgrades, not fresh installs
- **Actionable Links** - Direct access to Settings and Changelog
- **Persistent Settings** - Tracks last seen version to prevent duplicate notifications

### Update Process
```kotlin
// Update version for new releases
companion object {
    private const val CURRENT_VERSION = "0.3.0"  // ← Update for each release
}
```

### Recent Architecture Changes

#### API Key Handling Fix (Major)
**Problem**: Plugin was using invalid API key from AI Assistant's Authorization header ("raspberry", 9 chars)
**Solution**: Modified servlets to use API key from `OpenRouterSettingsService.getInstance().getApiKey()`
**Impact**: Resolved 401 Unauthorized errors in chat completions

**Files Modified**:
- `ChatCompletionServlet.kt` - Updated to use settings API key
- `ModelsServlet.kt` - Consistent API key handling
- Test files - Comprehensive test coverage for API key scenarios

#### Model Name Normalization Removal
**Rationale**: Simplified implementation by removing model name translation
**Changes**:
- Removed `MODEL_MAPPINGS` and `normalizeModelName()` function
- Updated curated models list to use full OpenRouter names (e.g., `openai/gpt-4-turbo`)
- Direct model name passthrough from AI Assistant to OpenRouter

#### Security Enhancements
**Cleanup**: Removed all real API keys from codebase
**Files Affected**: Test files, documentation, mock data
**Replacements**: All real keys replaced with secure placeholder values
**Best Practices**: Added security guidelines and validation checks

#### Proxy Configuration Improvements (v0.3.0)
**Problem**: Proxy always auto-started on IDEA restart using port 8080, causing conflicts
**Solution**: Complete proxy configuration overhaul with user control

**Key Changes**:
- **Configurable Auto-start**: Disabled by default, user-controllable
- **Better Port Range**: Changed default from 8080-8090 to 8880-8899
- **Flexible Port Selection**: Specific port or auto-select within range
- **Immediate Application**: Settings applied instantly when starting proxy
- **Comprehensive UI**: Full configuration panel with validation

**Files Modified**:
- `OpenRouterProxyServer.kt` - Enhanced port selection logic
- `OpenRouterSettingsService.kt` - Added proxy configuration methods
- `OpenRouterSettingsPanel.kt` - Added proxy UI and immediate application
- `OpenRouterModels.kt` - Updated default settings
- `OpenRouterConfigurable.kt` - Integrated proxy settings

**Development Impact**:
- **Testing**: 26+ new tests covering all proxy configuration scenarios
- **User Experience**: No more unwanted proxy startup, better port selection
- **Backward Compatibility**: Existing installations use improved defaults

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused (max 60 lines)
- Avoid deep nesting (max 4 levels)
- Extract complex conditions to well-named methods

### Code Quality
- **Static Analysis**: Run detekt before committing
  ```bash
  ./gradlew detekt --console=plain
  ```
- **Code Smells**: Address ComplexCondition, NestedBlockDepth, and LongMethod warnings
- **Refactoring**: Follow the 11-step safe refactoring process (see below)
- **Test Coverage**: Maintain 100% test pass rate

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
- Use dependency injection for testability (constructor parameters)
- Run tests before and after refactoring

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

## Code Refactoring

### Safe Refactoring Process (11 Steps)

When refactoring code to fix detekt warnings or improve code quality:

1. **Identify the Issue**: Run `./gradlew detekt` to find code smells
2. **Understand the Code**: Read and comprehend what the code does
3. **Find Related Tests**: Check if tests exist for the code
4. **Run Baseline Tests**: Record current test results
5. **Plan the Refactoring**: Choose appropriate refactoring pattern
6. **Make ONE Change**: Fix one issue at a time
7. **Compile**: Ensure code compiles after each change
8. **Run Tests Again**: Verify no regressions
9. **Verify with Detekt**: Confirm the issue is fixed
10. **Review Changes**: Check the diff before committing
11. **Commit**: Write descriptive commit message

### Common Refactoring Patterns

**Extract Method** - Break long methods into smaller ones:
```kotlin
// Before: 80+ lines
fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    // ... 80 lines of code
}

// After: 20 lines
fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    val requestId = generateId()
    logRequestStart(requestId)
    try {
        processRequest(req, resp, requestId)
    } catch (e: Exception) {
        handleException(e, resp, requestId)
    } finally {
        logRequestComplete(requestId)
    }
}
```

**Extract Condition** - Simplify complex boolean expressions:
```kotlin
// Before
if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size) {
    return
}

// After
if (!areIndicesValid(fromIndex, toIndex, size)) {
    return
}

private fun areIndicesValid(from: Int, to: Int, size: Int): Boolean {
    return from in 0 until size && to in 0 until size
}
```

**Early Returns** - Reduce nesting depth:
```kotlin
// Before: 4 levels of nesting
fun validate(id: String): Result {
    if (isConfigured()) {
        val model = getModel(id)
        if (model != null) {
            if (testConnection()) {
                return Result(true, "Valid")
            }
        }
    }
    return Result(false, "Invalid")
}

// After: 1-2 levels of nesting
fun validate(id: String): Result {
    if (!isConfigured()) return Result(false, "Not configured")
    val model = getModel(id) ?: return Result(false, "Model not found")
    if (!testConnection()) return Result(false, "Connection failed")
    return Result(true, "Valid")
}
```

### Dependency Injection for Testing

Make services testable by accepting dependencies via constructor:

```kotlin
// Before: Hard to test
@Service
class FavoriteModelsService {
    private val settingsService = OpenRouterSettingsService.getInstance()
    // ... methods using settingsService
}

// After: Testable with dependency injection
@Service
class FavoriteModelsService(
    private val settingsService: OpenRouterSettingsService? = null
) {
    private val settings: OpenRouterSettingsService by lazy {
        settingsService ?: OpenRouterSettingsService.getInstance()
    }
    // ... methods using settings
}

// In tests:
val mockSettings = mock(OpenRouterSettingsService::class.java)
val service = FavoriteModelsService(mockSettings)
```

## Release Management

### Updating "What's New" Notification

When releasing a new version (e.g., 0.2.0 → 0.3.0), update the notification system to inform users about new features.

**The notification will automatically show to users who upgrade from a previous version, but NOT to fresh installs.**

---

#### **Complete Checklist for New Release**

Use this checklist when preparing a new version release:

- [ ] **1. Update version in WhatsNewNotificationActivity.kt**
- [ ] **2. Update notification message in WhatsNewNotificationActivity.kt**
- [ ] **3. Update plugin.xml change-notes (add new version at top, keep history)**
- [ ] **4. Update CHANGELOG.md (add new version at top)**
- [ ] **5. Update plugin version in build.gradle.kts**
- [ ] **6. Test notification with simulated upgrade**
- [ ] **7. Verify fresh install doesn't show notification**
- [ ] **8. Build and verify plugin**

---

#### **1. Update Version Number**

**File:** `src/main/kotlin/org/zhavoronkov/openrouter/startup/WhatsNewNotificationActivity.kt`

```kotlin
companion object {
    private const val CURRENT_VERSION = "0.3.0"  // ← Update this to new version
    private const val CHANGELOG_URL = "https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/CHANGELOG.md"
}
```

**Example:** When releasing 0.3.0, change `"0.2.0"` to `"0.3.0"`

---

#### **2. Update Notification Message**

**File:** `src/main/kotlin/org/zhavoronkov/openrouter/startup/WhatsNewNotificationActivity.kt`

Edit the `showWhatsNewNotification()` method to highlight the most important new features:

```kotlin
.createNotification(
    "OpenRouter Plugin Updated to v$CURRENT_VERSION",
    """
    <b>🎉 New Features:</b><br/>
    • <b>Your New Feature 1</b> - Brief description (one line)<br/>
    • <b>Your New Feature 2</b> - Brief description (one line)<br/>
    • <b>Improvements</b> - What was improved (one line)<br/>
    <br/>
    Click below to explore the new features!
    """.trimIndent(),
    NotificationType.INFORMATION
)
```

**Guidelines:**
- Keep it concise - max 3-4 bullet points
- Use `<b>` tags for feature names
- Each line should be one sentence
- Focus on user-facing features, not technical details
- Use emoji sparingly (🎉 for major features, ⭐ for favorites, 🐛 for fixes)

**Example for 0.3.0:**
```kotlin
"""
<b>🎉 New Features:</b><br/>
• <b>Model Search</b> - Quickly find models by name or provider<br/>
• <b>Cost Tracking</b> - Real-time cost estimates for generations<br/>
• <b>Performance</b> - 50% faster API response times<br/>
<br/>
Click below to explore the new features!
""".trimIndent()
```

---

#### **3. Update plugin.xml Change Notes**

**File:** `src/main/resources/META-INF/plugin.xml`

**⚠️ IMPORTANT:** Add new version at the **top**, keep all previous versions for history!

```xml
<change-notes><![CDATA[
    <h3>🎉 Version 0.3.0 - Your Release Title (2025-XX-XX)</h3>
    <p><strong>New Features:</strong></p>
    <ul>
        <li><strong>Feature 1</strong> - Detailed description</li>
        <li><strong>Feature 2</strong> - Detailed description</li>
        <li><strong>Feature 3</strong> - Detailed description</li>
    </ul>

    <p><strong>Improvements:</strong></p>
    <ul>
        <li>Improvement 1 - What changed</li>
        <li>Improvement 2 - What changed</li>
    </ul>

    <p><strong>Bug Fixes:</strong></p>
    <ul>
        <li>Fixed issue with X</li>
        <li>Fixed issue with Y</li>
    </ul>

    <p><a href="https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/docs/AI_ASSISTANT_SETUP.md">AI Assistant Setup</a> | <a href="https://github.com/DimazzzZ/openrouter-intellij-plugin/blob/main/CHANGELOG.md">Full Changelog</a></p>

    <hr/>

    <!-- Keep ALL previous version history below - DO NOT DELETE -->
    <h3>Version 0.2.0 - Major Update (2025-10-03)</h3>
    <p><strong>New Features:</strong></p>
    <ul>
        <li><strong>🤖 AI Assistant Proxy</strong> - Local OpenAI-compatible proxy server to connect JetBrains AI Assistant with OpenRouter's 400+ models</li>
        <li><strong>⭐ Favorite Models</strong> - Manage and organize your preferred AI models</li>
        <li><strong>🌐 Custom Server Support</strong> - Configure AI Assistant to use OpenRouter via custom model connection</li>
    </ul>
    <!-- ... rest of 0.2.0 notes ... -->

    <hr/>

    <h3>Version 0.1.0 - Initial Release (2025-09-18)</h3>
    <!-- ... 0.1.0 notes ... -->
]]></change-notes>
```

**Guidelines:**
- More detailed than notification message
- Include all features, improvements, and bug fixes
- Use proper HTML formatting
- Keep previous versions intact (users can see full history)
- Add date in format: `(YYYY-MM-DD)`

---

#### **4. Update CHANGELOG.md**

**File:** `CHANGELOG.md`

Add a new version section at the **top** of the file:

```markdown
## [0.3.0] - 2025-XX-XX

### 🎉 Major Features
- **Feature 1** - Detailed description with technical details
- **Feature 2** - Detailed description with technical details

### ✨ Enhancements
- Enhancement 1 - What improved and why
- Enhancement 2 - What improved and why

### 🐛 Bug Fixes
- Fixed issue #123 - Description of the fix
- Fixed issue #456 - Description of the fix

### 🔧 Technical Changes
- Internal refactoring details
- Dependency updates
- Performance improvements

### 📚 Documentation
- Updated setup guide
- Added troubleshooting section

---

## [0.2.0] - 2025-10-03
<!-- Keep all previous versions below -->
```

**Guidelines:**
- Most detailed changelog (include everything)
- Reference issue numbers if applicable
- Include technical changes and internal improvements
- Use emoji for section headers
- Keep all previous versions

---

#### **5. Update Plugin Version**

**File:** `build.gradle.kts`

```kotlin
version = "0.3.0"  // ← Update this
```

---

#### **6. Test the Notification**

Before releasing, test that the notification works correctly:

##### **Test Upgrade Scenario (Should Show Notification)**

1. **Simulate upgrade from previous version:**

   Edit `src/main/kotlin/org/zhavoronkov/openrouter/models/OpenRouterModels.kt`:
   ```kotlin
   var lastSeenVersion: String = "0.2.0"  // Simulate user on previous version
   ```

2. **Run development IDE:**
   ```bash
   ./gradlew clean runIde
   ```

3. **Open any project** - notification should appear

4. **Verify:**
   - [ ] Notification appears in bottom-right corner
   - [ ] Shows correct version number (0.3.0)
   - [ ] Shows correct features
   - [ ] "Open Settings" button works
   - [ ] "View Changelog" button opens browser
   - [ ] Console shows: `"Showing What's New notification for version 0.3.0 (last seen: 0.2.0)"`

5. **Revert the test change:**
   ```kotlin
   var lastSeenVersion: String = ""  // Back to default
   ```

##### **Test Fresh Install (Should NOT Show Notification)**

1. **Ensure default is empty:**
   ```kotlin
   var lastSeenVersion: String = ""  // Default for fresh installs
   ```

2. **Run development IDE:**
   ```bash
   ./gradlew clean runIde
   ```

3. **Open any project** - notification should NOT appear

4. **Verify:**
   - [ ] No notification shown
   - [ ] Console shows: `"First install detected, setting version to 0.3.0"`

---

#### **7. Build and Verify**

```bash
# Clean build
./gradlew clean

# Run tests
./gradlew test

# Build plugin
./gradlew buildPlugin

# Verify plugin builds successfully
ls -lh build/distributions/
```

**Expected output:**
```
openrouter-intellij-plugin-0.3.0.zip
```

---

#### **8. Release Checklist**

Before publishing the new version:

- [ ] All tests pass (`./gradlew test`)
- [ ] Plugin builds successfully (`./gradlew buildPlugin`)
- [ ] Version updated in `WhatsNewNotificationActivity.kt`
- [ ] Notification message updated with new features
- [ ] `plugin.xml` change-notes updated (new version at top, history preserved)
- [ ] `CHANGELOG.md` updated (new version at top)
- [ ] `build.gradle.kts` version updated
- [ ] Tested upgrade scenario (notification appears)
- [ ] Tested fresh install (no notification)
- [ ] `lastSeenVersion` default is `""` (not a test value)
- [ ] No test/debug code left in production files
- [ ] Documentation updated if needed

---

#### **How It Works in Production**

**For users upgrading from 0.2.0 to 0.3.0:**
1. User has `lastSeenVersion = "0.2.0"` stored in their settings
2. Plugin updates to 0.3.0
3. On next IDE startup, `WhatsNewNotificationActivity` runs
4. Detects `lastSeenVersion ("0.2.0") != CURRENT_VERSION ("0.3.0")`
5. Shows notification with new features
6. Updates `lastSeenVersion` to `"0.3.0"`
7. User won't see notification again until next version

**For fresh installs:**
1. User installs plugin for first time
2. `lastSeenVersion = ""` (empty, default value)
3. On first IDE startup, `WhatsNewNotificationActivity` runs
4. Detects empty `lastSeenVersion`
5. Silently sets `lastSeenVersion = "0.3.0"` (no notification)
6. User won't see notification (good UX - they didn't upgrade)

---

#### **Example: Complete Release Process for 0.3.0**

Here's a complete example of updating from 0.2.0 to 0.3.0:

**1. WhatsNewNotificationActivity.kt:**
```kotlin
companion object {
    private const val CURRENT_VERSION = "0.3.0"  // Changed from "0.2.0"
    // ...
}

// In showWhatsNewNotification():
.createNotification(
    "OpenRouter Plugin Updated to v0.3.0",  // Updated version
    """
    <b>🎉 New Features:</b><br/>
    • <b>Model Search</b> - Find models instantly by name or provider<br/>
    • <b>Cost Tracking</b> - Real-time cost estimates<br/>
    • <b>Performance</b> - 50% faster response times<br/>
    """.trimIndent(),
    // ...
)
```

**2. plugin.xml (add at top of change-notes):**
```xml
<h3>🎉 Version 0.3.0 - Enhanced Features (2025-11-15)</h3>
<p><strong>New Features:</strong></p>
<ul>
    <li><strong>Model Search</strong> - Instant search across 400+ models</li>
    <li><strong>Cost Tracking</strong> - Real-time cost estimates for all generations</li>
</ul>
<!-- ... -->
<hr/>
<!-- Keep 0.2.0 and 0.1.0 below -->
```

**3. CHANGELOG.md (add at top):**
```markdown
## [0.3.0] - 2025-11-15

### 🎉 Major Features
- **Model Search** - Instant search with fuzzy matching across all 400+ models
- **Cost Tracking** - Real-time cost estimates with detailed breakdown

### ✨ Enhancements
- Improved API response time by 50%
- Enhanced error messages with actionable suggestions

### 🐛 Bug Fixes
- Fixed proxy server port conflict on Windows
- Fixed favorite models not persisting after IDE restart
```

**4. build.gradle.kts:**
```kotlin
version = "0.3.0"  // Changed from "0.2.0"
```

**5. Test, build, and release!**

---

### Summary

The "What's New" notification system:
- ✅ **Automatically shows** to users upgrading from previous versions
- ✅ **Never shows** to fresh installs (good UX)
- ✅ **Shows only once** per version
- ✅ **Non-intrusive** balloon notification
- ✅ **Actionable** with Settings and Changelog links
- ✅ **Easy to update** for each release

Follow the checklist above for each new version release to ensure users are properly informed about new features!

---

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
