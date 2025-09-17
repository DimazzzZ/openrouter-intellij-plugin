# Testing Guide

This document describes the testing infrastructure and procedures for the OpenRouter IntelliJ Plugin.

## Test Overview

The plugin has a comprehensive test suite covering core functionality with **22 passing tests** across two main test suites:

| Test Suite | Tests | Coverage |
|------------|-------|----------|
| **SimpleUnitTest** | 15 | Data models, settings, business logic |
| **ApiIntegrationTest** | 7 | OpenRouter API endpoints |
| **Total** | **22** | **Core functionality** |

## Test Structure

```
src/test/kotlin/org/zhavoronkov/openrouter/
├── SimpleUnitTest.kt                    # ✅ Unit tests (15 tests)
├── ApiIntegrationTest.kt               # ✅ API integration tests (7 tests)
└── src/test/resources/mocks/           # Mock API responses
    ├── api-keys-list-response.json
    ├── api-key-create-response.json
    ├── api-key-delete-response.json
    ├── key-info-response.json
    └── error-response.json
```

## Running Tests

### All Core Tests
```bash
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest" --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"
```

### Individual Test Suites
```bash
# Unit tests only
./gradlew test --tests "org.zhavoronkov.openrouter.SimpleUnitTest"

# API integration tests only
./gradlew test --tests "org.zhavoronkov.openrouter.ApiIntegrationTest"
```

### Expected Output
```
BUILD SUCCESSFUL in 4s
Total: 22 tests passed ✅
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

## Future Enhancements

Potential test expansions:
- Performance testing for API response times
- UI component testing for settings panel
- End-to-end workflow testing
- Load testing for concurrent API requests

---

For development setup and building instructions, see [DEVELOPMENT.md](DEVELOPMENT.md).
