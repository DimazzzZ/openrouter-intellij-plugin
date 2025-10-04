#!/bin/bash

# Test script to verify model mapping is working correctly
# Tests that gpt-4o-mini maps to openai/gpt-4o-mini, not openai/gpt-4

echo "=== Model Mapping Test ==="
echo "Testing that gpt-4o-mini maps correctly to openai/gpt-4o-mini"
echo ""

# Check if proxy server is running
if ! curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "❌ Proxy server is not running on port 8080"
    echo "Please restart the proxy server to pick up the model mapping fix:"
    echo "  1. Stop the current development IDE"
    echo "  2. Restart with: ./gradlew runIde --args=\"--proxy-server\" -Dopenrouter.force.proxy=true"
    exit 1
fi

echo "✅ Proxy server is running"
echo ""

# Test gpt-4o-mini mapping
echo "🧪 Testing gpt-4o-mini model mapping"
echo "Request: gpt-4o-mini"
echo "Expected OpenRouter model: openai/gpt-4o-mini"
echo ""

CHAT_REQUEST='{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "user", 
      "content": "What model are you?"
    }
  ],
  "max_tokens": 20,
  "temperature": 0.1
}'

echo "Sending request..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d "$CHAT_REQUEST" \
  http://127.0.0.1:8080/v1/chat/completions)

echo "Response:"
echo "$RESPONSE" | jq . 2>/dev/null

# Check the model in response
RESPONSE_MODEL=$(echo "$RESPONSE" | jq -r '.model' 2>/dev/null)
echo ""
echo "📊 Results:"
echo "Requested model: gpt-4o-mini"
echo "Response model: $RESPONSE_MODEL"

if [ "$RESPONSE_MODEL" = "gpt-4o-mini" ] || [ "$RESPONSE_MODEL" = "openai/gpt-4o-mini" ]; then
    echo "✅ Model mapping is CORRECT!"
    echo "The proxy correctly mapped gpt-4o-mini to openai/gpt-4o-mini"
elif [ "$RESPONSE_MODEL" = "gpt-4" ] || [ "$RESPONSE_MODEL" = "openai/gpt-4" ]; then
    echo "❌ Model mapping is INCORRECT!"
    echo "The proxy incorrectly mapped gpt-4o-mini to openai/gpt-4"
    echo ""
    echo "🔧 Fix needed:"
    echo "1. The proxy server needs to be restarted to pick up the model mapping fix"
    echo "2. Stop the development IDE and restart with proxy enabled"
else
    echo "⚠️  Unexpected model in response: $RESPONSE_MODEL"
fi

echo ""

# Test gpt-4o mapping as well
echo "🧪 Testing gpt-4o model mapping"
echo "Request: gpt-4o"
echo "Expected OpenRouter model: openai/gpt-4o"

CHAT_REQUEST_4O='{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "user",
      "content": "What model are you?"
    }
  ],
  "max_tokens": 20,
  "temperature": 0.1
}'

RESPONSE_4O=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d "$CHAT_REQUEST_4O" \
  http://127.0.0.1:8080/v1/chat/completions)

RESPONSE_MODEL_4O=$(echo "$RESPONSE_4O" | jq -r '.model' 2>/dev/null)
echo ""
echo "📊 Results for gpt-4o:"
echo "Requested model: gpt-4o"
echo "Response model: $RESPONSE_MODEL_4O"

if [ "$RESPONSE_MODEL_4O" = "gpt-4o" ] || [ "$RESPONSE_MODEL_4O" = "openai/gpt-4o" ]; then
    echo "✅ gpt-4o mapping is CORRECT!"
elif [ "$RESPONSE_MODEL_4O" = "gpt-4" ] || [ "$RESPONSE_MODEL_4O" = "openai/gpt-4" ]; then
    echo "❌ gpt-4o mapping is INCORRECT!"
else
    echo "⚠️  Unexpected model in response: $RESPONSE_MODEL_4O"
fi

echo ""
echo "=== Summary ==="
if ([ "$RESPONSE_MODEL" = "gpt-4o-mini" ] || [ "$RESPONSE_MODEL" = "openai/gpt-4o-mini" ]) && \
   ([ "$RESPONSE_MODEL_4O" = "gpt-4o" ] || [ "$RESPONSE_MODEL_4O" = "openai/gpt-4o" ]); then
    echo "✅ All model mappings are working correctly!"
    echo "🎯 Ready for accurate AI Assistant integration"
else
    echo "❌ Model mappings need fixing"
    echo "🔄 Restart the proxy server to apply the model mapping fix"
fi
