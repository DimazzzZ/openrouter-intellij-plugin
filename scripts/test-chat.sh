#!/bin/bash

# Test script to verify chat completions endpoint works correctly
# Tests both /v1/chat/completions and /chat/completions aliases

echo "=== OpenRouter Proxy Chat Completions Test ==="
echo "Testing chat completions with gpt-4o-mini model"
echo ""

# Check if proxy server is running
echo "🔍 Checking if proxy server is running on port 8080..."
if ! curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "❌ Proxy server is not running on port 8080"
    echo ""
    echo "Please start the proxy server first:"
    echo "  ./gradlew runIde --args=\"--proxy-server\" -Dopenrouter.force.proxy=true"
    echo ""
    echo "Or restart the development IDE with proxy enabled."
    exit 1
fi

echo "✅ Proxy server is running"
echo ""

# Test 1: Check models endpoint first
echo "🧪 Test 1: Verify models endpoint"
echo "GET /v1/models"
MODELS_RESPONSE=$(curl -s http://127.0.0.1:8080/v1/models)
MODEL_COUNT=$(echo "$MODELS_RESPONSE" | jq '.data | length' 2>/dev/null)

if [ "$MODEL_COUNT" -gt 0 ]; then
    echo "✅ Models endpoint working - found $MODEL_COUNT models"
    echo "Available models:"
    echo "$MODELS_RESPONSE" | jq -r '.data[].id' | head -5
else
    echo "❌ Models endpoint not working properly"
    echo "Response: $MODELS_RESPONSE"
    exit 1
fi

echo ""

# Test 2: Chat completion with /v1/chat/completions
echo "🧪 Test 2: Chat completion via /v1/chat/completions"
echo "POST /v1/chat/completions with gpt-4o-mini"

CHAT_REQUEST='{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user",
      "content": "Say hello in exactly 3 words."
    }
  ],
  "max_tokens": 10,
  "temperature": 0.1
}'

echo "Request payload:"
echo "$CHAT_REQUEST" | jq .
echo ""

CHAT_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d "$CHAT_REQUEST" \
  http://127.0.0.1:8080/v1/chat/completions)

echo "Response:"
echo "$CHAT_RESPONSE" | jq . 2>/dev/null

# Check if response is valid
if echo "$CHAT_RESPONSE" | jq -e '.choices[0].message.content' > /dev/null 2>&1; then
    CONTENT=$(echo "$CHAT_RESPONSE" | jq -r '.choices[0].message.content')
    echo ""
    echo "✅ Chat completion successful!"
    echo "Model response: '$CONTENT'"
else
    echo ""
    echo "❌ Chat completion failed or invalid response"
    echo "Raw response: $CHAT_RESPONSE"
fi

echo ""

# Test 3: Chat completion with /chat/completions (alias)
echo "🧪 Test 3: Chat completion via /chat/completions (alias)"
echo "POST /chat/completions with gpt-4o-mini"

CHAT_RESPONSE_ALIAS=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d "$CHAT_REQUEST" \
  http://127.0.0.1:8080/chat/completions)

echo "Response:"
echo "$CHAT_RESPONSE_ALIAS" | jq . 2>/dev/null

# Check if response is valid
if echo "$CHAT_RESPONSE_ALIAS" | jq -e '.choices[0].message.content' > /dev/null 2>&1; then
    CONTENT_ALIAS=$(echo "$CHAT_RESPONSE_ALIAS" | jq -r '.choices[0].message.content')
    echo ""
    echo "✅ Chat completion via alias successful!"
    echo "Model response: '$CONTENT_ALIAS'"
else
    echo ""
    echo "❌ Chat completion via alias failed or invalid response"
    echo "Raw response: $CHAT_RESPONSE_ALIAS"
fi

echo ""
echo "=== Test Summary ==="
echo "✅ Models endpoint: Working"
if echo "$CHAT_RESPONSE" | jq -e '.choices[0].message.content' > /dev/null 2>&1; then
    echo "✅ /v1/chat/completions: Working"
else
    echo "❌ /v1/chat/completions: Failed"
fi

if echo "$CHAT_RESPONSE_ALIAS" | jq -e '.choices[0].message.content' > /dev/null 2>&1; then
    echo "✅ /chat/completions: Working"
else
    echo "❌ /chat/completions: Failed"
fi

echo ""
echo "🎯 Ready for AI Assistant integration!"
echo "Configure AI Assistant with:"
echo "  - Base URL: http://127.0.0.1:8080"
echo "  - API Key: test-key (any value)"
echo "  - Model: gpt-4o-mini or gpt-4"
