# OpenRouter Plugin - Production Logging Guide

## 📋 Overview

This guide explains how to access and configure logging for the OpenRouter IntelliJ plugin in production environments.

## 🔍 Log File Locations

### IntelliJ IDEA Log Files

The plugin writes to IntelliJ's standard log files:

**Windows:**
```
%APPDATA%\JetBrains\IntelliJIdea{version}\log\idea.log
```

**macOS:**
```
~/Library/Logs/JetBrains/IntelliJIdea{version}/idea.log
```

**Linux:**
```
~/.cache/JetBrains/IntelliJIdea{version}/log/idea.log
```

### Finding Your Version

Replace `{version}` with your IntelliJ version (e.g., `2023.2`, `2024.1`).

## 📊 Log Levels in Production

### Always Logged (INFO/WARN/ERROR)

These are logged even without debug mode:

- **API Key Usage:** `🔑 Using API key from plugin settings: sk-or-v1-xxx...`
- **Request Success:** `✅ Chat completion successful`
- **API Errors:** `❌ OpenRouter API Error: 401`
- **Connection Issues:** `❌ Failed to connect to OpenRouter`
- **Settings Changes:** `Settings state persisted successfully`

### Debug Logging (Optional)

Enable for detailed troubleshooting:

- **Request Bodies:** Full JSON request/response data
- **Model Loading:** Detailed model fetching process
- **UI Updates:** Settings panel state changes

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

### Method 3: System Properties

Add to your IDE startup script:
```bash
-Dopenrouter.debug=true
```

## 📖 Reading Logs

### Command Line (macOS/Linux)

**Follow live logs:**
```bash
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep "OpenRouter"
```

**Search for errors:**
```bash
grep -E "\[OpenRouter\].*❌|\[Chat-.*\].*❌" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

**Search for API key issues:**
```bash
grep -E "API key|🔑" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

### IntelliJ Built-in Tools

1. **Show Log in Explorer/Finder:**
   - Go to `Help` → `Show Log in Explorer/Finder`

2. **Diagnostic Tools:**
   - Go to `Help` → `Diagnostic Tools` → `Debug Log Settings`

## 🔍 Common Log Patterns

### Successful Request
```
[OpenRouter] [Chat-000001] 🔑 Using API key from plugin settings: sk-or-v1-xxx... (length: 73)
[OpenRouter] [Chat-000001] 📝 Model: 'openai/gpt-4o-mini'
[OpenRouter] [Chat-000001] ✅ Chat completion successful
```

### API Key Issues
```
[OpenRouter] [Chat-000002] ❌ No API key configured in OpenRouter plugin settings
[OpenRouter] [Chat-000003] ❌ OpenRouter API Error: 401
[OpenRouter] [Chat-000003] ❌ API key prefix: sk-or-v1-xxx... (length: 73)
```

### Connection Problems
```
[OpenRouter] [Chat-000004] ❌ Failed to connect to OpenRouter: timeout
[OpenRouter] [Chat-000004] ❌ Request URL: https://openrouter.ai/api/v1/chat/completions
```

## 🚨 Troubleshooting

### Issue: No Logs Appearing

1. **Check log file location** - ensure you're looking at the correct path
2. **Verify plugin is loaded** - check `Help` → `About` → `Plugins`
3. **Enable debug logging** - follow methods above
4. **Restart IntelliJ** - required after changing VM options

### Issue: Too Many Debug Logs

1. **Disable debug mode:**
   - Remove `-Dopenrouter.debug=true` from VM options
   - Remove `org.zhavoronkov.openrouter` from debug categories
2. **Restart IntelliJ**

### Issue: Can't Find Log Files

1. **Use IntelliJ's built-in tool:**
   - `Help` → `Show Log in Explorer/Finder`
2. **Check IntelliJ version:**
   - `Help` → `About` to see exact version number

## 📧 Support

When reporting issues, please include:

1. **Log excerpts** showing the error
2. **IntelliJ version** (`Help` → `About`)
3. **Plugin version** (Settings → Plugins → OpenRouter)
4. **Operating system** and version
5. **Steps to reproduce** the issue

## 🔒 Security Note

Log files may contain API key prefixes (first 15 characters) for troubleshooting. Full API keys are never logged. Ensure log files are kept secure and not shared publicly.
