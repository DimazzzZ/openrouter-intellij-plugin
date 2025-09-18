# 🧪 Testing Guide

This document describes the testing infrastructure and procedures for the OpenRouter IntelliJ Plugin.

## 📊 Test Overview

The plugin features a robust test suite ensuring reliability and stability across two main test suites:

| Test Suite | Tests | Coverage | Focus Area |
|------------|-------|----------|------------|
| **SimpleUnitTest** | 15 | Data models, settings, business logic | Core functionality |
| **ApiIntegrationTest** | 7 | OpenRouter API endpoints | External integration |
| **Total** | **22** | **Complete core functionality** | **Production ready** |

### ✅ Test Status
- **Build Status**: ✅ All tests passing
- **Coverage**: 🎯 Core functionality fully covered
- **Reliability**: 🔒 Deterministic and isolated tests
- **Performance**: ⚡ Fast execution (< 5 seconds)

## 🏗️ Test Architecture

### Test Structure
```
src/test/kotlin/org/zhavoronkov/openrouter/
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
└── src/test/resources/mocks/           # 📁 Mock API responses
    ├── api-keys-list-response.json     # API keys list endpoint
    ├── api-key-create-response.json    # API key creation endpoint
    ├── api-key-delete-response.json    # API key deletion endpoint
    ├── key-info-response.json          # Key information endpoint
    └── error-response.json             # Error response scenarios
```

### Test Categories
- **🔧 Unit Tests**: Core business logic and data models
- **🌐 Integration Tests**: OpenRouter API communication
- **📋 Mock Tests**: Simulated API responses for reliability
- **🚨 Error Tests**: Edge cases and failure scenarios

## 🚀 Running Tests

### Quick Test Commands

#### All Core Tests (Recommended)
```bash
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest" --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"
```

#### Individual Test Suites
```bash
# 🔧 Unit tests only (15 tests)
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest"

# 🌐 API integration tests only (7 tests)
./gradlew test --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"

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

### Expected Output
```
> Task :test
SimpleUnitTest > testDataModelSerialization() PASSED
SimpleUnitTest > testSettingsValidation() PASSED
ApiIntegrationTest > testApiKeysList() PASSED
... (22 tests total)

BUILD SUCCESSFUL in 4s
22 tests completed, 22 succeeded ✅
```

## Test Coverage

### Unit Tests (SimpleUnitTest.kt)
- **Data Models**: Serialization/deserialization, null handling
- **Settings**: Configuration validation and persistence
- **Business Logic**: API key validation, currency formatting, quota calculations
- **Error Handling**: API error responses and edge cases

### API Integration Tests (ApiIntegrationTest.kt)
- **Authentication**: Provisioning key validation
- **API Endpoints**: List, create, delete API keys
- **Error Scenarios**: 401 unauthorized, 404 not found, validation errors
- **Response Parsing**: JSON deserialization and data validation

## Test Infrastructure

### Dependencies
- **JUnit 5**: Modern testing framework
- **MockWebServer**: HTTP API mocking
- **Mockito**: Object mocking and verification
- **AssertJ**: Fluent assertions
- **Gson**: JSON serialization/deserialization

### Mock Data
All mock responses are based on real OpenRouter API documentation:
- Realistic API key data with usage statistics
- Proper error response structures
- Edge cases and validation scenarios

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
