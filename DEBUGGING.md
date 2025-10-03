# 🐛 Debugging Guide

This document provides comprehensive debugging information for the OpenRouter IntelliJ Plugin, covering both development and production environments.

## 📋 Quick Reference

| Issue Type | Log Pattern | Location | Action |
|------------|-------------|----------|---------|
| **API Key Issues** | `❌ OpenRouter API Error: 401` | idea.log | Check API key configuration |
| **Connection Problems** | `❌ Failed to connect to OpenRouter` | idea.log | Check network/proxy settings |
| **Request Failures** | `[Chat-XXXXX] ❌` | idea.log | Check request logs and API status |
| **Settings Issues** | `Settings state persisted` | idea.log | Verify settings persistence |
| **Model Loading** | `Loaded XXX models from OpenRouter` | idea.log | Check model fetching |
| **Test Hanging** | `OutOfMemoryError` | test output | Use safe test runner |
| **Duplicate Requests** | `🚨 DUPLICATE REQUEST DETECTED!` | idea.log | Check AI Assistant configuration |
| **Missing Headers** | `X-Title header missing` | request logs | Verify OpenRouterRequestBuilder usage |

## 🔍 Log File Locations

### Production Environments

**macOS:**
```bash
~/Library/Logs/JetBrains/IntelliJIdea{version}/idea.log
```

**Windows:**
```
%APPDATA%\JetBrains\IntelliJIdea{version}\log\idea.log
```

**Linux:**
```
~/.cache/JetBrains/IntelliJIdea{version}/log/idea.log
```

### Development Environment
```bash
# Console output during ./gradlew runIde
# Real-time debugging information
```

## 🛠️ Enabling Debug Logging

### Method 1: VM Options (Recommended)
1. Go to `Help` → `Edit Custom VM Options`
2. Add these lines:
   ```
   -Dopenrouter.debug=true
   -Didea.log.debug.categories=org.zhavoronkov.openrouter
   ```
3. Restart IntelliJ IDEA

### Method 2: Registry (IntelliJ 2020.3+)
1. Press `Ctrl+Shift+A` (or `Cmd+Shift+A` on Mac)
2. Type "Registry" and select "Registry..."
3. Find or add: `idea.log.debug.categories`
4. Set value to: `org.zhavoronkov.openrouter`
5. Restart IntelliJ IDEA

### Method 3: Development Mode
```bash
# Automatic debug logging during development
./gradlew runIde --console=plain
```

## 📊 Log Levels and Content

### Always Logged (INFO/WARN/ERROR)
- **API Key Usage:** `🔑 Using API key from plugin settings: sk-or-v1-xxx...`
- **Request Success:** `✅ Chat completion successful`
- **API Errors:** `❌ OpenRouter API Error: 401`
- **Connection Issues:** `❌ Failed to connect to OpenRouter`
- **Settings Changes:** `Settings state persisted successfully`
- **Model Loading:** `Loaded 328 models from OpenRouter`

### Debug Mode Only
- **Request Bodies:** `[DEBUG] Full request body: {...}`
- **Model Details:** `[DEBUG] First few models: [model1, model2, model3]`
- **UI Updates:** `[DEBUG] Setting 328 models to table`
- **API Responses:** `[DEBUG] OpenRouter API response: {...}`

## 🔍 Monitoring Commands

### Real-time Monitoring (macOS/Linux)
```bash
# Monitor all OpenRouter activity
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep "OpenRouter"

# Monitor API requests specifically
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep -E "\[Chat-.*\]"

# Monitor errors only
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep "❌"

# Monitor API key usage
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep "🔑"
```

### Historical Log Analysis
```bash
# Find API key issues
grep -E "API key|🔑" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Find authentication errors
grep -E "401|❌.*OpenRouter" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Find all errors
grep -E "\[OpenRouter\].*❌" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Find settings issues
grep -E "Settings|setApiKey|getApiKey" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Find model loading issues
grep -E "models|Loaded.*models" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

## 🚨 Common Issues and Solutions

### 1. API Key Problems

**Symptoms:**
```
[Chat-000001] ❌ OpenRouter API Error: 401
[Chat-000001] ❌ Error details: {"error":{"message":"User not found.","code":401}}
[Chat-000001] ❌ API key prefix: sk-or-v1-42c6cb... (length: 73)
```

**Debugging Steps:**
1. Check API key configuration in Settings → OpenRouter
2. Verify the correct API key is being used:
   ```bash
   grep "🔑 Using API key" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | tail -5
   ```
3. Check if API key was saved correctly:
   ```bash
   grep "setApiKey\|getApiKey" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | tail -10
   ```

**Solutions:**
- Delete and recreate the "IntelliJ IDEA Plugin" API key
- Verify provisioning key is correct
- Check OpenRouter account status

### 2. Connection Issues

**Symptoms:**
```
[Chat-000002] ❌ Failed to connect to OpenRouter: timeout
[Chat-000002] ❌ Request URL: https://openrouter.ai/api/v1/chat/completions
```

**Debugging Steps:**
1. Check network connectivity
2. Verify proxy settings
3. Test OpenRouter API directly:
   ```bash
   curl -H "Authorization: Bearer YOUR_API_KEY" https://openrouter.ai/api/v1/models
   ```

### 3. Model Loading Issues

**Symptoms:**
```
[OpenRouter][DEBUG] Not configured, clearing API keys table
[OpenRouter] Failed to load available models: null response
```

**Debugging Steps:**
1. Check if provisioning key is configured
2. Verify model loading logs:
   ```bash
   grep -E "Loading.*models|Loaded.*models" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
   ```

### 4. Settings Persistence Issues

**Symptoms:**
```
CRITICAL: API key was not saved correctly!
Expected: sk-or-v1-aba...
Got: sk-or-v1-42c6cb...
```

**Debugging Steps:**
1. Check settings persistence logs:
   ```bash
   grep "Settings state persisted\|notifyStateChanged" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
   ```
2. Verify settings file location:
   ```bash
   find ~ -name "openrouter.xml" 2>/dev/null
   ```

### **5. Duplicate Request Issues**

**Symptoms:**
```
[Chat-000096] Incoming POST /v1/chat/completions
[Chat-000097] Incoming POST /v1/chat/completions  ← Same timestamp!
🚨 DUPLICATE REQUEST DETECTED!
🚨 Time since first request: 1ms
```

**Debugging Steps:**
1. Check for duplicate request warnings:
   ```bash
   grep "🚨 DUPLICATE REQUEST" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
   ```
2. Monitor request patterns:
   ```bash
   grep -E "\[Chat-[0-9]+\] Incoming POST" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | tail -20
   ```

**Possible Causes:**
- AI Assistant client double-submitting requests
- Network layer duplication
- User double-clicking or rapid interactions
- Client-side retry logic issues

## 🔧 Development Debugging

### Console Debugging
```bash
# Start with full debug output
./gradlew runIde --console=plain --debug

# Monitor specific components
./gradlew runIde --console=plain | grep -E "OpenRouter|Chat-"
```

### IDE Debugging
1. Set breakpoints in key methods:
   - `ChatCompletionServlet.doPost()`
   - `OpenRouterSettingsService.getApiKey()`
   - `ApiKeyManager.createIntellijApiKeyOnce()`

2. Use IntelliJ's debugger with the development IDE instance

### Test Debugging
```bash
# Run tests with debug output
./gradlew test --info --tests "*ChatCompletionServletTest*"

# Debug specific test failures
./gradlew test --debug --tests "org.zhavoronkov.openrouter.SimpleUnitTest.testApiKeyValidation"
```

## 📋 Debugging Checklist

### Before Reporting Issues
- [ ] Check log files for error messages
- [ ] Verify API key configuration
- [ ] Test with debug logging enabled
- [ ] Check network connectivity
- [ ] Verify IntelliJ and plugin versions

### Information to Collect
- [ ] Log excerpts showing the error
- [ ] IntelliJ version (`Help` → `About`)
- [ ] Plugin version (Settings → Plugins → OpenRouter)
- [ ] Operating system and version
- [ ] Steps to reproduce the issue
- [ ] API key prefix (first 15 characters only)

## 🔒 Security Considerations

### Safe Information to Share
- ✅ Log excerpts with API key prefixes (first 15 characters)
- ✅ Error messages and stack traces
- ✅ Request URLs and HTTP status codes
- ✅ Plugin and IDE version information

### Never Share
- ❌ Complete API keys
- ❌ Provisioning keys
- ❌ Full request/response bodies (may contain sensitive data)
- ❌ Personal account information

## 📚 Related Documentation

- **Testing Guide**: [TESTING.md](TESTING.md) - Comprehensive testing procedures
- **Production Logging**: [docs/PRODUCTION_LOGGING.md](docs/PRODUCTION_LOGGING.md) - Detailed production logging guide
- **Development Setup**: [DEVELOPMENT.md](DEVELOPMENT.md) - Development environment setup
- **API Documentation**: [OpenRouter API Docs](https://openrouter.ai/docs) - External API reference

## 🆘 Getting Help

### Community Support
- **GitHub Issues**: Report bugs and feature requests
- **Discussions**: Ask questions and share experiences

### Professional Support
- **Enterprise Support**: Available for business users
- **Custom Development**: Plugin customization services

## 🔍 Advanced Debugging Techniques

### Request Flow Tracing
Each request gets a unique ID for tracing through the logs:
```
[Chat-000001] 🔑 Using API key from plugin settings: sk-or-v1-xxx...
[Chat-000001] 📝 Model: 'openai/gpt-4o-mini'
[Chat-000001] 🌊 STREAMING requested - handling SSE response
[Chat-000001] ✅ Chat completion successful
```

### API Key Lifecycle Debugging
Track API key creation and usage:
```bash
# Monitor API key creation
grep -E "Successfully created.*API key|About to save.*API key" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Monitor API key verification
grep -E "Verification.*saved key|matches=" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Monitor API key usage
grep -E "🔑.*Using API key|API key prefix" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

### Settings Debugging
```bash
# Monitor settings changes
grep -E "setApiKey called|setProvisioningKey|Settings state persisted" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Check encryption/decryption
grep -E "encrypted\.length|decrypted\.length" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

### Model Loading Debugging
```bash
# Track model loading process
grep -E "Starting to load models|Loaded.*models|Setting.*models to table" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Monitor search functionality
grep -E "Filtering models|Search text|AvailableModelsTableModel" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

## 🧪 Testing Integration

For comprehensive testing procedures, see [TESTING.md](TESTING.md):
- **Unit Tests**: Core functionality validation
- **Integration Tests**: API communication testing
- **E2E Tests**: Complete workflow validation
- **Mock Testing**: Isolated component testing

### Debug Test Failures
```bash
# Run tests with debug output
./gradlew test --info --tests "*RequestBuilderTest*"

# Debug test hanging issues
./scripts/run-safe-tests.sh  # Guaranteed to work

# Debug integration tests (manual enable required)
./gradlew integrationTest --info
```

## 🧪 Test Debugging

### Test Hanging Issues (FIXED)
**Problem**: Tests hanging indefinitely with OutOfMemoryError
**Solution**: Enhanced test configuration with proper timeouts and memory limits

```bash
# Safe test execution (recommended)
./scripts/run-safe-tests.sh

# Full test suite (unit tests only, 3 seconds)
./gradlew test

# Integration tests (manual enable)
./gradlew integrationTest
```

### Test Configuration
- **Memory Limit**: 512MB max heap
- **Timeouts**: 10 seconds per test, 2 minutes total
- **Platform Prevention**: Disabled IntelliJ platform initialization
- **Integration Exclusion**: Heavy tests disabled by default

### Duplicate Request Detection
Look for these log patterns:
```
🚨 DUPLICATE REQUEST DETECTED! Hash: abc123... (Chat-000096 & Chat-000097)
```

This indicates the AI Assistant client is sending duplicate requests.

## 🔧 Code Refactoring Debugging

### OpenRouterRequestBuilder Issues
If you see missing headers in API requests:

1. **Check Request Builder Usage**:
   ```kotlin
   // ✅ Correct usage
   val request = OpenRouterRequestBuilder.buildPostRequest(
       url = "https://openrouter.ai/api/v1/chat/completions",
       jsonBody = jsonBody,
       authType = OpenRouterRequestBuilder.AuthType.API_KEY,
       authToken = apiKey
   )

   // ❌ Old pattern (should be refactored)
   val request = Request.Builder()
       .url(url)
       .addHeader("X-Title", "OpenRouter IntelliJ Plugin")
       // ... manual header setup
   ```

2. **Verify Headers**:
   ```bash
   # Check if all requests include proper headers
   grep -r "X-Title" src/main/kotlin/
   ```

3. **Test Request Builder**:
   ```bash
   ./gradlew test --tests "OpenRouterRequestBuilderTest"
   ```

---

## 🔍 Code Quality and Static Analysis

### Running Detekt

Detekt is a static code analysis tool for Kotlin that helps identify code smells:

```bash
# Run full detekt analysis
./gradlew detekt --console=plain

# Check for specific issue types
./gradlew detekt --console=plain 2>&1 | grep "ComplexCondition\|NestedBlockDepth\|LongMethod"

# Count issues by type
./gradlew detekt --console=plain 2>&1 | grep -oE "\[.*\]$" | sort | uniq -c | sort -rn

# Check specific file
./gradlew detekt --console=plain 2>&1 | grep "FileName.kt"
```

### Code Quality Metrics

**Current Status** (as of October 2025):
- Total detekt issues: ~1,413
- Critical code smells: 0 ✅
- ComplexCondition: 0 ✅
- NestedBlockDepth: 0 ✅
- LongMethod: 0 ✅
- Test pass rate: 100% (207/207 tests)

**Acceptable Issues**:
- SwallowedException: 5 (all have proper logging)
- MagicNumber: ~800 (mostly UI constants and test data)
- TooManyFunctions: Some (acceptable for service classes)

### Verifying Refactoring Success

After refactoring code to fix detekt warnings, verify the changes:

```bash
# 1. Check if the specific issue is fixed
./gradlew detekt --console=plain 2>&1 | grep "FileName.kt:LineNumber"

# 2. Verify no new issues were introduced
./gradlew detekt --console=plain 2>&1 | grep "FileName.kt" | wc -l

# 3. Run tests to ensure no regressions
./gradlew test --tests "*FileNameTest*" --console=plain

# 4. Check compilation
./gradlew compileKotlin --console=plain

# 5. Run all tests
./gradlew test --console=plain 2>&1 | grep "tests completed"
```

### Refactoring Validation Checklist

After each refactoring:
- [ ] Code compiles without errors
- [ ] All tests pass (207/207)
- [ ] Detekt issue is resolved
- [ ] No new detekt issues introduced
- [ ] Code is more readable
- [ ] Functionality unchanged

---

**💡 Pro Tip**: Enable debug logging temporarily when troubleshooting, then disable it to avoid log file bloat in production.
