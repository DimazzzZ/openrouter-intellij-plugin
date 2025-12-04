# 🧪 Testing Guide

This document describes the testing infrastructure and procedures for the OpenRouter IntelliJ Plugin.

## 📑 Table of Contents
- [Test Overview](#-test-overview)
- [Running Tests](#-running-tests)
- [Testing First-Run Experience](#-testing-first-run-experience)
- [Test Architecture](#-test-architecture)
- [Debugging Tests](#-debugging-tests)

## 📊 Test Overview

The plugin features a comprehensive test suite with 465+ total tests (388 active, 77 disabled) ensuring reliability and stability:

| Test Suite | Tests | Status | Coverage | Focus Area |
|------------|-------|--------|----------|------------|
| **Unit Tests** | 109 | ✅ Active | Core functionality, data models | Business logic |
| **Integration Tests** | 65+ | ⏸️ Disabled | API communication, servlet logic | External integration |
| **E2E Tests** | 10+ | ⏸️ Disabled | Real OpenRouter API calls | End-to-end validation |
| **Request Builder Tests** | 12 | ✅ Active | HTTP request construction | Refactored code validation |
| **Favorite Models Tests** | 11 | ✅ Active | Favorite models management | User preferences |
| **Model Filtering Tests** | 64 | ✅ Active | Provider/capability/context filtering | Enhanced filtering (Phase 1) |
| **Proxy Configuration Tests** | 26+ | ✅ Active | Port selection, auto-start, settings validation | Proxy server configuration |
| **Settings Panel Tests** | 15+ | ✅ Active | UI configuration and immediate application | User interface |
| **UI Tests** | 2 | ⏸️ Disabled | UI component keyboard handling | User interface (headless skip) |
| **Encryption Tests** | 10 | ✅ Active | Secure credential storage | Security |
| **Settings Tests** | 15+ | ✅ Active | Configuration management | User settings |
| **Total Active** | **388** | **✅ All passing** | **Production functionality coverage** | **CI/CD ready** |
| **Total Disabled** | **77** | **⏸️ Available on-demand** | **Integration & E2E coverage** | **Manual testing** |
| **Grand Total** | **465+** | **Complete test coverage** | **Development to production** | **Full validation** |

### 🎯 Recent Major Improvements
- **Proxy Configuration Tests**: Added 26+ comprehensive tests for proxy server configuration, port selection, and settings validation
- **Immediate Settings Application**: Implemented and tested instant proxy settings application without requiring Apply/OK
- **Test Fixes**: Fixed all 11 failing FavoriteModelsService tests using dependency injection and mocking
- **Code Refactoring**: Eliminated duplicate headers across 12+ locations using centralized `OpenRouterRequestBuilder`
- **Test Infrastructure**: Fixed hanging tests and memory issues with proper timeouts and exclusions
- **Headless Compatibility**: All UI tests properly skip in headless environments for CI/CD pipeline compatibility
- **X-Title Header Fix**: Added missing attribution headers to all OpenRouter API requests
- **Memory Management**: Reduced test memory usage from 3.6GB+ to 512MB with proper configuration
- **Dependency Injection**: Made FavoriteModelsService testable with optional constructor parameters

### ✅ Test Status
- **Build Status**: ✅ All active tests passing (388+ tests, 0 failures, 77 disabled)
- **Coverage**: 🎯 Complete functionality coverage including proxy configuration improvements
- **Reliability**: 🔒 No more hanging tests or memory issues
- **Performance**: ⚡ Ultra-fast execution (3-8 seconds for active suite)

## 🔧 Disabled Tests (77 Tests)

The plugin includes 77 disabled tests that are **valuable but not run by default** for practical reasons:

### 📊 Disabled Test Categories

| Category | Count | Reason Disabled | When to Enable |
|----------|-------|-----------------|----------------|
| **Integration Tests** | ~65 | Memory issues in CI/CD | Local development, bug investigation |
| **E2E Tests** | ~10 | Consume real API credits ($$) | Major releases, critical bug verification |
| **UI Tests** | ~2 | Require graphical environment | Local UI testing, non-headless environments |

### 🚀 How to Enable Disabled Tests

#### **Method 1: Enable Specific Test Categories**

**Integration Tests** (for local development):
```bash
# Enable integration tests by removing @Disabled annotation
# Edit these files and comment out @Disabled lines:
# - src/test/kotlin/org/zhavoronkov/openrouter/ApiIntegrationTest.kt
# - src/test/kotlin/org/zhavoronkov/openrouter/services/OpenRouterServiceIntegrationTest.kt
# - src/test/kotlin/org/zhavoronkov/openrouter/integration/ProxyServerIntegrationTest.kt
# - src/test/kotlin/org/zhavoronkov/openrouter/proxy/servlets/*.kt

# Run integration tests
./gradlew test --tests "*Integration*"
```

**E2E Tests** (costs real API credits):
```bash
# 1. Create .env file with real OpenRouter credentials:
echo "OPENROUTER_API_KEY=sk-or-v1-your-real-key" > .env
echo "OPENROUTER_PROVISIONING_KEY=pk-your-real-provisioning-key" >> .env

# 2. Enable E2E tests by removing @Disabled annotation in:
# - src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt
# - src/test/kotlin/org/zhavoronkov/openrouter/integration/ProxyServerDuplicateTest.kt
# - src/test/kotlin/org/zhavoronkov/openrouter/integration/SimpleProxyTest.kt

# 3. Run E2E tests (⚠️ COSTS MONEY - real API calls)
./gradlew test --tests "*E2E*" --tests "*Duplicate*" --tests "*SimpleProxy*"
```

**UI Tests** (require graphical environment):
```bash
# Run in non-headless environment
export JAVA_AWT_HEADLESS=false
./gradlew test --tests "*UI*" --tests "*SettingsPanel*"
```

#### **Method 2: Enable via Test Tags**

```bash
# Run integration tests by tag
./gradlew test -Dgroups="integration"

# Run E2E tests by tag (⚠️ COSTS MONEY)
./gradlew test -Dgroups="e2e"
```

#### **Method 3: Temporary Enable All (Not Recommended)**

```bash
# ⚠️ WARNING: This will run E2E tests and cost money!
# Only do this if you have .env setup and understand the cost

# Find and temporarily comment out all @Disabled annotations
find src/test -name "*.kt" -exec sed -i.bak 's/@Disabled/@_Disabled_TEMP/g' {} \;

# Run all tests
./gradlew test

# Restore @Disabled annotations
find src/test -name "*.kt" -exec sed -i '' 's/@_Disabled_TEMP/@Disabled/g' {} \;
find src/test -name "*.bak" -delete
```

### 🎯 When to Use Disabled Tests

| Scenario | Tests to Enable | Reason |
|----------|----------------|---------|
| **Local Development** | Integration Tests | Verify HTTP server, servlet, and API client behavior |
| **Before Major Release** | Integration + E2E Tests | Full validation against real OpenRouter API |
| **Bug Investigation** | Relevant Integration Tests | Deep dive into specific component issues |
| **API Changes** | E2E Tests | Verify compatibility with OpenRouter API updates |
| **UI Development** | UI Tests | Validate keyboard handling and component behavior |
| **Performance Testing** | Integration Tests | Load testing and server behavior validation |

### ⚠️ Important Notes

1. **E2E Tests Cost Money**: They make real API calls to OpenRouter and consume credits
2. **Integration Tests Use Memory**: May cause issues in CI/CD with limited memory
3. **UI Tests Need Graphics**: Skip automatically in headless environments  
4. **All Disabled Tests Are Maintained**: They're kept up-to-date and valuable for manual testing

### 🔍 Finding Specific Tests

```bash
# Find all disabled integration tests
grep -r "@Disabled" src/test/ --include="*Integration*"

# Find all disabled E2E tests  
grep -r "@Disabled" src/test/ --include="*E2E*"

# Find all UI tests
grep -r "headless" src/test/ --include="*UI*"

# Count disabled tests per file
find src/test -name "*.kt" -exec sh -c 'echo "=== {} ==="; grep -c "@Disabled\|@DisabledIf" "{}"' \;
```

### 🎯 Phase 1-3 Testing (UI Enhancements)
- **Phase 1 Tests**: 64 unit tests for model filtering (ModelProviderUtils, ModelPresets, ModelFilterCriteria)
- **Phase 2 Tests**: N/A (code simplification, no new functionality)
- **Phase 3 Tests**: Manual testing required for first-run experience (welcome notification, setup wizard)
- **Testing Guide**: See "Testing First-Run Experience" section below

## 🏗️ Test Architecture

### Test Structure
```
src/test/kotlin/org/zhavoronkov/openrouter/
├── proxy/servlets/
│   ├── ChatCompletionServletTest.kt    # ✅ Real unit tests (15 tests)
│   │   ├── API Key Source Tests (4 tests) - Verify settings vs Authorization header
│   │   ├── Settings API Key Validation (3 tests) - Blank/null key handling
│   │   ├── Request Processing Tests (4 tests) - Chat completion logic
│   │   └── Error Handling Tests (4 tests) - 401 errors and edge cases
│   └── ApiKeyHandlingIntegrationTest.kt # ✅ Real integration tests (8 tests)
│       ├── API Key Source Integration (2 tests) - End-to-end key handling
│       ├── Settings Validation Integration (2 tests) - Complete validation flow
│       ├── Request Processing Integration (2 tests) - Full request lifecycle
│       └── Error Handling Integration (2 tests) - Complete error scenarios
├── SimpleUnitTest.kt                    # ✅ Unit tests (15 tests)
│   ├── Data model serialization/deserialization
│   ├── Settings validation and persistence
│   ├── Business logic and calculations
│   └── Error handling scenarios
├── ApiIntegrationTest.kt               # ✅ API integration tests (7 tests)
│   ├── Authentication validation
│   ├── API endpoint testing
│   ├── Response parsing verification
│   └── Error scenario handling
├── E2ETest.kt                          # ✅ End-to-end tests (122 tests, @Disabled)
│   ├── Complete workflow testing with real OpenRouter API
│   ├── Cost: ~$0.0007 per full test run
│   └── Manual execution for release validation
└── src/test/resources/mocks/           # 📁 Mock API responses
    ├── api-keys-list-response.json     # API keys list endpoint
    ├── api-key-create-response.json    # API key creation endpoint (placeholder keys)
    ├── api-key-delete-response.json    # API key deletion endpoint
    ├── key-info-response.json          # Key information endpoint
    └── error-response.json             # Error response scenarios
```

### Test Categories
- **🔧 Unit Tests**: Core business logic and data models
- **🌐 Integration Tests**: OpenRouter API communication
- **🤖 Proxy Tests**: AI Assistant integration proxy server (ChatCompletionServletTest)
- **🔑 API Key Tests**: Comprehensive API key handling validation (ApiKeyHandlingIntegrationTest)
- **🎨 UI Enhancement Tests**: Model filtering, presets, and criteria (Phase 1)
- **📋 Mock Tests**: Simulated API responses for reliability
- **🚨 Error Tests**: Edge cases and failure scenarios
- **🌍 E2E Tests**: Complete workflows with real API calls (disabled by default)
- **🔒 Security Tests**: API key encryption and placeholder validation

## 🚀 Running Tests

### Quick Test Commands

#### Recommended: Safe Unit Tests
```bash
# Run safe unit tests only (guaranteed to work)
./scripts/run-safe-tests.sh

# Or run full unit test suite (270+ tests in 3-8 seconds)
./gradlew test
```

#### Individual Test Suites
```bash
# 🔧 Core unit tests (109 tests)
./gradlew test --tests "SimpleUnitTest" --tests "EncryptionUtilTest" --tests "OpenRouterModelsTest"

# 🧪 Request builder tests (12 tests) - validates refactoring
./gradlew test --tests "OpenRouterRequestBuilderTest"

# 🎨 Phase 1 filtering tests (64 tests)
./gradlew test --tests "ModelProviderUtilsTest"
./gradlew test --tests "ModelPresetsTest"
./gradlew test --tests "ModelFilterCriteriaTest"

# 🔑 Settings and API key tests
./gradlew test --tests "ApiKeysTableModelTest" --tests "OpenRouterSettingsServiceTest"

# 🌐 Integration tests (manual enable required)
./gradlew integrationTest

# 🏃‍♂️ All tests (unit tests only, integration tests disabled)
./gradlew test
```

#### Development Testing
```bash
# 🧹 Clean build with tests
./gradlew clean test --no-daemon

# 🔍 Verbose test output
./gradlew test --info

# 📊 Test report generation
./gradlew test --continue
```

#### AI Assistant Integration Testing
```bash
# 🚀 Start development IDE with proxy server
./gradlew runIde --no-daemon

# 🧪 Test proxy server endpoints (manual scripts available)
./scripts/test-proxy.sh          # Basic proxy functionality
./scripts/test-chat.sh           # Chat completions endpoint  
./scripts/test-model-mapping.sh  # Model name translation

# 🔗 Test full AI Assistant integration
# 1. Configure OpenRouter plugin in development IDE
# 2. Start proxy server via status bar
# 3. Configure AI Assistant to use localhost:8080
# 4. Test chat completions through AI Assistant
```

### Expected Output
```
> Task :test
OpenRouter Plugin Unit Tests > Data Model Tests > Should serialize CreateApiKeyRequest correctly PASSED
OpenRouter Icons Tests > Icon Resources > Should have all required icon resources PASSED
OpenRouter Models Tests > API Key Models > Should deserialize ApiKeyInfo from JSON PASSED
OpenRouter Request Builder Tests > GET Request Tests > Should build GET request with API key authentication PASSED
OpenRouter Settings Service Tests > API Key Management > Should store and retrieve API key PASSED
Favorite Models Service Tests > Favorite Management > should add favorite model PASSED
Favorite Models Service Tests > Favorite Ordering > should reorder favorites PASSED
RequestTranslatorTest > translateChatCompletionRequest should pass through model name exactly() PASSED
... (207 tests total)

BUILD SUCCESSFUL in 3-8s
207 tests completed, 207 succeeded, 14 skipped ✅
```

### Safe Test Runner Output
```bash
./scripts/run-safe-tests.sh
🧪 Running safe unit tests only...
BUILD SUCCESSFUL in 5s
109 tests completed, 109 succeeded ✅
✅ Safe unit tests completed!
```

### Integration Tests (Manual Enable)
```
> Task :integrationTest
ProxyServerIntegrationTest > Should start and stop Jetty server PASSED
OpenRouterServiceIntegrationTest > Should handle API communication PASSED
... (50+ integration tests when enabled)

BUILD SUCCESSFUL in 15s
```

## Test Coverage

### Core Unit Tests (109 Tests)
- **Data Models**: Serialization/deserialization, null handling, API responses
- **Settings Management**: Configuration validation, persistence, encryption
- **Business Logic**: API key validation, currency formatting, quota calculations
- **Request Builder**: HTTP request construction with proper headers (validates refactoring)
- **Encryption**: Secure storage and retrieval of API keys
- **UI Components**: Table models, URL copying, icon loading

### Request Builder Tests (12 Tests) - **NEW**
- **GET Requests**: All authentication types (NONE, API_KEY, PROVISIONING_KEY)
- **POST Requests**: JSON body with authentication
- **DELETE Requests**: Provisioning key authentication
- **Header Validation**: X-Title, HTTP-Referer, Content-Type, Authorization
- **Configuration Access**: Centralized header management

### Integration Tests (50+ Tests, Disabled by Default)
- **Servlet Tests**: ChatCompletionServlet API key handling and request processing
- **API Integration**: OpenRouter API communication with MockWebServer
- **Proxy Server**: Jetty server lifecycle and endpoint testing
- **End-to-End**: Complete workflows with real API calls (manual enable)

### AI Assistant Integration Testing
- **Proxy Server**: Start/stop lifecycle, port allocation, health checks
- **API Endpoints**: OpenAI-compatible endpoint validation
- **Request Translation**: OpenAI to OpenRouter format conversion
- **Response Translation**: OpenRouter to OpenAI format conversion
- **Model Mapping**: Automatic model name translation
- **Error Handling**: Proxy error scenarios and fallbacks

### Proxy Configuration Testing (26+ Tests) - **NEW**
- **Settings Service Tests**: Proxy auto-start, port validation, range constraints
- **Port Selection Logic**: Specific port vs. range selection strategies
- **Configuration Integration**: Complete proxy setup scenarios
- **Settings Validation**: Port range constraints (1024-65535) and edge cases
- **UI Configuration Tests**: Settings panel proxy controls (headless-compatible)
- **Immediate Application**: "Start Proxy" applies current UI settings without Apply/OK
- **Auto-start Scenarios**: Configurable proxy startup on IDEA launch
- **Port Conflict Resolution**: Fallback from specific port to range selection

#### Test Categories
1. **OpenRouterSettingsServiceTest** (11 proxy tests)
   - Default proxy settings verification
   - Auto-start configuration and persistence
   - Port validation (specific port and ranges)
   - Port range constraint validation
   - Edge case handling for invalid configurations

2. **ProxyServerConfigurationTest** (11 configuration tests)
   - Port selection strategy validation
   - Configuration consistency across scenarios
   - Auto-start behavior verification
   - Port range validation and edge cases

3. **ProxySettingsPanelTest** (4+ UI tests)
   - Immediate settings application validation
   - Configuration model consistency
   - Headless environment compatibility
   - Complete configuration scenario testing

## 🔧 Code Refactoring Validation

### OpenRouterRequestBuilder Testing
The plugin underwent major refactoring to eliminate code duplication. Comprehensive tests validate the changes:

**Problem**: 12+ locations with duplicate header code (X-Title, HTTP-Referer, etc.)
**Solution**: Created centralized `OpenRouterRequestBuilder` utility with type-safe authentication
**Testing**: 12 new tests specifically validate the refactored request building functionality

### Test Implementation Approach
- **Type Safety**: Enum-based authentication types (NONE, API_KEY, PROVISIONING_KEY)
- **Header Validation**: Ensures all requests include proper attribution headers
- **Backward Compatibility**: Verifies same behavior as before refactoring
- **Configuration Testing**: Validates centralized header management

### Test Infrastructure Improvements
- **Memory Management**: Fixed OutOfMemoryError issues (3.6GB → 512MB)
- **Timeout Configuration**: Aggressive timeouts prevent hanging tests
- **Test Categorization**: Unit tests vs integration tests properly separated
- **Safe Test Runner**: `scripts/run-safe-tests.sh` for guaranteed execution

## Test Infrastructure

### Dependencies
- **JUnit 5**: Modern testing framework with nested test classes
- **MockWebServer**: HTTP API mocking for integration tests (disabled by default)
- **Mockito**: Object mocking and verification with Kotlin compatibility
- **Gson**: JSON serialization/deserialization for API responses
- **OkHttp**: HTTP client testing for request builder validation

### Test Configuration
Enhanced Gradle configuration prevents hanging and memory issues:
- **Memory Limit**: 512MB max heap (was unlimited)
- **Timeouts**: 10 seconds per test, 2 minutes total
- **Platform Prevention**: Disabled IntelliJ platform initialization
- **Integration Exclusion**: Heavy tests disabled by default

### Safe Test Execution
- **scripts/run-safe-tests.sh**: Guaranteed to work without hanging
- **Separate Integration Task**: `./gradlew integrationTest` for heavy tests
- **Fail Fast**: Stop on first failure for quick feedback

## CI/CD Integration

Tests are optimized for continuous integration:
- **Ultra-fast execution**: 3 seconds for full unit test suite
- **No external dependencies**: All unit tests are self-contained
- **Memory efficient**: 512MB max usage prevents OOM errors
- **Reliable results**: No more hanging or timeout issues

## Notes

- **Platform Dependencies**: Some test files exist but require IntelliJ platform classes not available in standard test environments
- **Mock Accuracy**: All mock data reflects actual OpenRouter API responses
- **Test Isolation**: Each test runs independently and can execute in any order

## 🎯 Testing Philosophy

### Quality Assurance Principles
- **🔒 Reliability First** - All core functionality must be thoroughly tested
- **⚡ Fast Feedback** - Tests should execute quickly for rapid development cycles
- **🔄 Deterministic Results** - Tests must produce consistent, predictable outcomes
- **🧪 Isolated Testing** - Each test runs independently without external dependencies
- **📊 Comprehensive Coverage** - Critical paths and edge cases are covered

### Test Strategy
- **Unit Tests** - Focus on business logic, data models, and core algorithms
- **Integration Tests** - Verify API communication and external service integration
- **Mock Testing** - Use realistic mock data based on actual OpenRouter API responses
- **Error Testing** - Comprehensive coverage of error scenarios and edge cases

## 🚀 Future Testing Enhancements

### Planned Improvements
- **🎭 UI Component Testing** - Automated testing of settings panel and dialogs
- **⚡ Performance Testing** - API response time monitoring and optimization
- **🔄 End-to-End Testing** - Complete workflow testing from setup to usage
- **📊 Load Testing** - Concurrent API request handling and rate limiting
- **🔍 Visual Regression Testing** - UI consistency across different IDE themes
- **🌐 Cross-Platform Testing** - Verification across Windows, macOS, and Linux

### Testing Tools Under Consideration
- **TestContainers** - For integration testing with containerized services
- **WireMock** - Enhanced API mocking capabilities
- **JMeter** - Performance and load testing
- **Selenium/Robot Framework** - UI automation testing
- **SonarQube** - Code quality and coverage analysis

## 📋 Testing Checklist

### Before Release
- [ ] All unit tests pass (15/15)
- [ ] All integration tests pass (7/7)
- [ ] Manual testing in development IDE
- [ ] Settings panel validation testing
- [ ] API key management testing
- [ ] Error scenario verification
- [ ] Cross-IDE compatibility check
- [ ] Performance baseline verification

### Continuous Integration
- [ ] Automated test execution on pull requests
- [ ] Test coverage reporting
- [ ] Performance regression detection
- [ ] Compatibility matrix testing

---

## 🚀 Testing First-Run Experience

The plugin includes a comprehensive first-run experience (Phase 3) that requires manual testing.

### Quick Start
```bash
# Start fresh development IDE with clean state
./gradlew clean runIde
```

### Components to Test

#### 1. Welcome Notification
- **Trigger**: Opens automatically on first project open
- **Actions**: Quick Setup, Open Settings, Dismiss
- **Verification**: Notification appears only once

#### 2. Setup Wizard
- **Step 0 (Welcome)**: Introduction with proper bullet formatting and icons
- **Step 1 (Provisioning Key)**:
  - Automatic validation with visual feedback (spinner → checkmark/error)
  - "Next" button disabled until valid key entered
  - Key encrypted and saved after validation
- **Step 2 (Favorite Models)**:
  - Embedded model selector with search
  - Checkboxes (not true/false text)
  - Selected count updates in real-time
  - Table sortable by clicking column headers
- **Step 3 (Completion)**:
  - Proxy server URL displayed (http://127.0.0.1:8080/v1/)
  - Copy button works
  - Link to AI Assistant setup guide

#### 3. Navigation
- **Back Button**: Returns to previous step (not close wizard)
- **Next Button**: Smart enable/disable based on validation
- **Skip/Close**: Properly closes wizard

### Testing Methods

**Method 1: Fresh Development IDE (Recommended)**
```bash
./gradlew clean runIde
# Opens new IDE instance with clean state
# Welcome notification appears automatically
```

**Method 2: Reset Settings File**
```bash
# Find settings file
find ~/Library/Application\ Support/JetBrains -name "openrouter.xml" | grep sandbox

# Edit and change:
# <option name="hasSeenWelcome" value="true" /> to false
# <option name="hasCompletedSetup" value="true" /> to false

# Restart development IDE
./gradlew runIde
```

**Method 3: Delete Settings (Clean Slate)**
```bash
# Find and delete
find ~/Library/Application\ Support/JetBrains -name "openrouter.xml" | grep sandbox | xargs rm

# Restart
./gradlew runIde
```

### Test Checklist

- [ ] Welcome notification appears on first project open
- [ ] "Quick Setup" button opens wizard
- [ ] Step 0: Proper layout with icons and separators
- [ ] Step 1: Invalid key shows error, valid key shows checkmark
- [ ] Step 1: "Next" disabled until validation succeeds
- [ ] Step 2: Checkboxes render properly (not true/false)
- [ ] Step 2: Selected count updates when checking models
- [ ] Step 2: Table sorts by clicking "Model" header
- [ ] Step 2: Search filters models in real-time
- [ ] Step 3: Proxy URL shows 127.0.0.1 (not localhost)
- [ ] Step 3: Copy button copies correct URL
- [ ] Back button navigates to previous step
- [ ] Wizard can be skipped
- [ ] Setup completion tracked correctly

---

## 🐛 Debugging Test Failures

For comprehensive debugging information, see [DEBUGGING.md](DEBUGGING.md):
- **Log Analysis**: How to read and interpret plugin logs
- **Common Issues**: Solutions for frequent problems
- **Debug Logging**: Enabling detailed logging for troubleshooting
- **Production Debugging**: Debugging in production environments

### Quick Debug Commands
```bash
# Debug test failures with detailed output
./gradlew test --info --tests "*ChatCompletionServletTest*"

# Debug API key handling specifically
./gradlew test --debug --tests "*ApiKeyHandlingIntegrationTest*"

# Monitor logs during development testing
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep "OpenRouter"
```

## 📚 Resources

- **Debugging Guide**: [DEBUGGING.md](DEBUGGING.md) - Comprehensive debugging procedures
- **Development Setup**: [DEVELOPMENT.md](DEVELOPMENT.md) - Complete development guide
- **Plugin Architecture**: [DEVELOPMENT.md#project-architecture](DEVELOPMENT.md#project-architecture) - Code organization
- **Production Logging**: [docs/PRODUCTION_LOGGING.md](docs/PRODUCTION_LOGGING.md) - Production debugging guide
- **API Documentation**: [OpenRouter API Docs](https://openrouter.ai/docs) - External API reference
- **IntelliJ Testing**: [IntelliJ Platform Testing](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) - Platform testing guide
