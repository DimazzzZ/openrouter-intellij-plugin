# 🧪 Testing Guide

This document describes the testing infrastructure and procedures for the OpenRouter IntelliJ Plugin.

## 📊 Test Overview

The plugin features a comprehensive test suite with 145+ tests ensuring reliability and stability:

| Test Suite | Tests | Coverage | Focus Area |
|------------|-------|----------|------------|
| **ChatCompletionServletTest** | 15 | API key handling, servlet logic | Core proxy functionality |
| **ApiKeyHandlingIntegrationTest** | 8 | Integration flows, error scenarios | API key management |
| **SimpleUnitTest** | 15 | Data models, settings, business logic | Core functionality |
| **ApiIntegrationTest** | 7 | OpenRouter API endpoints | External integration |
| **E2E Tests** | 122 | Complete workflows, real API calls | End-to-end validation |
| **Total** | **167** | **Complete functionality coverage** | **Production ready** |

### 🎯 Recent Test Improvements
- **Real Functional Tests**: Replaced documentation placeholders with actual behavior verification
- **API Key Handling**: Comprehensive testing of the 401 error fix implementation
- **Security Testing**: Validation of encrypted storage and placeholder usage
- **Integration Testing**: Full proxy server and AI Assistant integration coverage

### ✅ Test Status
- **Build Status**: ✅ All tests passing
- **Coverage**: 🎯 Core functionality fully covered
- **Reliability**: 🔒 Deterministic and isolated tests
- **Performance**: ⚡ Fast execution (< 5 seconds)

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
- **📋 Mock Tests**: Simulated API responses for reliability
- **🚨 Error Tests**: Edge cases and failure scenarios
- **🌍 E2E Tests**: Complete workflows with real API calls (disabled by default)
- **🔒 Security Tests**: API key encryption and placeholder validation

## 🚀 Running Tests

### Quick Test Commands

#### All Core Tests (Recommended)
```bash
# Run all functional tests (38 tests)
./gradlew test --tests "*ChatCompletionServletTest*" --tests "*ApiKeyHandlingIntegrationTest*" --tests "*SimpleUnitTest*" --tests "*ApiIntegrationTest*"
```

#### Individual Test Suites
```bash
# 🔧 Unit tests only (15 tests)
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest"

# 🌐 API integration tests only (7 tests)
./gradlew test --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"

# 🤖 Proxy servlet tests (15 tests)
./gradlew test --tests "*ChatCompletionServletTest*"

# 🔑 API key handling tests (8 tests)
./gradlew test --tests "*ApiKeyHandlingIntegrationTest*"

# 🌍 E2E tests (122 tests, requires API keys)
./gradlew test --tests "*E2ETest*"

# 🏃‍♂️ All tests (includes platform-dependent tests)
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
ChatCompletionServletTest > API Key Source Tests > testUsesApiKeyFromSettings() PASSED
ChatCompletionServletTest > Settings API Key Validation Tests > testReturns401WhenApiKeyIsBlank() PASSED
ApiKeyHandlingIntegrationTest > API Key Source Integration > testIgnoresAuthorizationHeaderAndUsesSettings() PASSED
SimpleUnitTest > testDataModelSerialization() PASSED
SimpleUnitTest > testSettingsValidation() PASSED
ApiIntegrationTest > testApiKeysList() PASSED
... (38 core tests total)

BUILD SUCCESSFUL in 6s
38 tests completed, 38 succeeded ✅
```

### E2E Test Output (When Enabled)
```
> Task :test
E2ETest > testCompleteWorkflow() PASSED
E2ETest > testModelListRetrieval() PASSED
E2ETest > testChatCompletionFlow() PASSED
... (122 E2E tests total)

BUILD SUCCESSFUL in 45s
167 tests completed, 167 succeeded ✅
Cost: ~$0.0007 for full E2E test run
```

## Test Coverage

### Proxy Servlet Tests (ChatCompletionServletTest.kt) - 15 Tests
- **API Key Source Tests** (4 tests): Verify servlet uses settings API key, not Authorization header
- **Settings API Key Validation** (3 tests): Verify 401 errors when API key is blank/null
- **Request Processing Tests** (4 tests): Chat completion request handling and response generation
- **Error Handling Tests** (4 tests): Comprehensive error scenario coverage

### API Key Integration Tests (ApiKeyHandlingIntegrationTest.kt) - 8 Tests
- **API Key Source Integration** (2 tests): End-to-end verification of API key source priority
- **Settings Validation Integration** (2 tests): Complete validation flow testing
- **Request Processing Integration** (2 tests): Full request lifecycle with proper API key usage
- **Error Handling Integration** (2 tests): Complete error scenario integration testing

### Unit Tests (SimpleUnitTest.kt) - 15 Tests
- **Data Models**: Serialization/deserialization, null handling
- **Settings**: Configuration validation and persistence
- **Business Logic**: API key validation, currency formatting, quota calculations
- **Error Handling**: API error responses and edge cases

### API Integration Tests (ApiIntegrationTest.kt) - 7 Tests
- **Authentication**: Provisioning key validation
- **API Endpoints**: List, create, delete API keys
- **Error Scenarios**: 401 unauthorized, 404 not found, validation errors
- **Response Parsing**: JSON deserialization and data validation

### E2E Tests (E2ETest.kt) - 122 Tests (Disabled by Default)
- **Complete Workflows**: Full plugin functionality with real OpenRouter API
- **Cost Monitoring**: Tracks API usage costs (~$0.0007 per full run)
- **Manual Execution**: Enabled only for release validation
- **Environment Setup**: Requires `.env` file with real API keys

### AI Assistant Integration Testing
- **Proxy Server**: Start/stop lifecycle, port allocation, health checks
- **API Endpoints**: OpenAI-compatible endpoint validation
- **Request Translation**: OpenAI to OpenRouter format conversion
- **Response Translation**: OpenRouter to OpenAI format conversion
- **Model Mapping**: Automatic model name translation
- **Error Handling**: Proxy error scenarios and fallbacks

## 🔑 API Key Handling Testing

### The 401 Error Fix
The plugin previously suffered from 401 Unauthorized errors due to incorrect API key handling. Comprehensive tests now verify the fix:

**Problem**: Plugin was using invalid API key from AI Assistant's Authorization header ("raspberry", 9 chars)
**Solution**: Modified servlets to use API key from `OpenRouterSettingsService.getInstance().getApiKey()`
**Testing**: 23 new tests specifically validate this fix across unit and integration levels

### Test Implementation Approach
- **Composition Pattern**: Used helper classes instead of inheritance to test final servlet classes
- **Mockito Integration**: Proper mocking with `\`when\`()` syntax for Kotlin compatibility
- **Real Behavior Verification**: Tests actually verify servlet logic, not just documentation placeholders

### Security Testing
- **Placeholder Validation**: All tests use placeholder API keys (e.g., `sk-or-v1-test-key-placeholder`)
- **Real Key Detection**: Automated checks prevent real API keys in test files
- **Encryption Testing**: Validates secure storage of credentials

## Test Infrastructure

### Dependencies
- **JUnit 5**: Modern testing framework with nested test classes
- **MockWebServer**: HTTP API mocking for integration tests
- **Mockito**: Object mocking and verification with Kotlin compatibility
- **AssertJ**: Fluent assertions for readable test code
- **Gson**: JSON serialization/deserialization for API responses

### Mock Data
All mock responses are based on real OpenRouter API documentation:
- Realistic API key data with usage statistics
- Proper error response structures
- Edge cases and validation scenarios
- **Security**: All mock data uses placeholder values, no real API keys

## CI/CD Integration

Tests are designed to run in continuous integration environments:
- No external dependencies required
- Isolated test execution
- Deterministic results
- Fast execution (< 5 seconds)

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

## 📚 Resources

- **Development Setup**: [DEVELOPMENT.md](DEVELOPMENT.md) - Complete development guide
- **Plugin Architecture**: [DEVELOPMENT.md#project-architecture](DEVELOPMENT.md#project-architecture) - Code organization
- **API Documentation**: [OpenRouter API Docs](https://openrouter.ai/docs) - External API reference
- **IntelliJ Testing**: [IntelliJ Platform Testing](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) - Platform testing guide
