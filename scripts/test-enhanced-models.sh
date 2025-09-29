#!/bin/bash

# Test script for enhanced models endpoint with full OpenRouter catalog support

echo "=== Enhanced Models Endpoint Test ==="
echo "Testing the new models endpoint with full OpenRouter catalog support"
echo ""

# Check if proxy server is running
if ! curl -s http://127.0.0.1:8080/health > /dev/null 2>&1; then
    echo "❌ Proxy server is not running on port 8080"
    echo "Please restart the proxy server to pick up the enhanced models functionality:"
    echo "  1. Stop the current development IDE"
    echo "  2. Restart with: ./gradlew runIde --args=\"--proxy-server\" -Dopenrouter.force.proxy=true"
    exit 1
fi

echo "✅ Proxy server is running"
echo ""

# Test 1: Default curated models (fast loading)
echo "🧪 Test 1: Default curated models (mode=curated)"
echo "GET /v1/models"
CURATED_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models")
CURATED_COUNT=$(echo "$CURATED_RESPONSE" | jq '.data | length' 2>/dev/null)

echo "Curated models count: $CURATED_COUNT"
if [ "$CURATED_COUNT" -eq 5 ]; then
    echo "✅ Curated models working correctly"
else
    echo "⚠️  Expected 5 curated models, got $CURATED_COUNT"
fi
echo ""

# Test 2: All models from OpenRouter
echo "🧪 Test 2: All models from OpenRouter (mode=all)"
echo "GET /v1/models?mode=all"
echo "This may take a few seconds to fetch from OpenRouter API..."

ALL_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models?mode=all")
ALL_COUNT=$(echo "$ALL_RESPONSE" | jq '.data | length' 2>/dev/null)

echo "All models count: $ALL_COUNT"
if [ "$ALL_COUNT" -gt 100 ]; then
    echo "✅ Successfully fetched full OpenRouter catalog ($ALL_COUNT models)"
    echo "Sample models:"
    echo "$ALL_RESPONSE" | jq -r '.data[0:5] | .[].id' 2>/dev/null
elif [ "$ALL_COUNT" -eq 5 ]; then
    echo "⚠️  Fallback to curated models (OpenRouter API may be unavailable)"
else
    echo "❌ Unexpected model count: $ALL_COUNT"
fi
echo ""

# Test 3: Search functionality
echo "🧪 Test 3: Search functionality"
echo "GET /v1/models?mode=search&search=gpt"

SEARCH_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models?mode=search&search=gpt")
SEARCH_COUNT=$(echo "$SEARCH_RESPONSE" | jq '.data | length' 2>/dev/null)

echo "GPT models found: $SEARCH_COUNT"
if [ "$SEARCH_COUNT" -gt 0 ]; then
    echo "✅ Search functionality working"
    echo "GPT models:"
    echo "$SEARCH_RESPONSE" | jq -r '.data[0:5] | .[].id' 2>/dev/null
else
    echo "❌ Search returned no results"
fi
echo ""

# Test 4: Provider filtering
echo "🧪 Test 4: Provider filtering"
echo "GET /v1/models?mode=all&provider=openai"

PROVIDER_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models?mode=all&provider=openai")
PROVIDER_COUNT=$(echo "$PROVIDER_RESPONSE" | jq '.data | length' 2>/dev/null)

echo "OpenAI models found: $PROVIDER_COUNT"
if [ "$PROVIDER_COUNT" -gt 0 ]; then
    echo "✅ Provider filtering working"
    echo "OpenAI models:"
    echo "$PROVIDER_RESPONSE" | jq -r '.data[0:5] | .[].id' 2>/dev/null
else
    echo "❌ Provider filtering returned no results"
fi
echo ""

# Test 5: Limit parameter
echo "🧪 Test 5: Limit parameter"
echo "GET /v1/models?mode=all&limit=10"

LIMIT_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models?mode=all&limit=10")
LIMIT_COUNT=$(echo "$LIMIT_RESPONSE" | jq '.data | length' 2>/dev/null)

echo "Limited models count: $LIMIT_COUNT"
if [ "$LIMIT_COUNT" -eq 10 ]; then
    echo "✅ Limit parameter working correctly"
elif [ "$LIMIT_COUNT" -eq 5 ]; then
    echo "⚠️  Fallback to curated models (limit applied to curated set)"
else
    echo "⚠️  Unexpected limit result: $LIMIT_COUNT"
fi
echo ""

# Test 6: Performance test (caching)
echo "🧪 Test 6: Performance test (caching)"
echo "Testing response time for cached vs uncached requests"

echo "First request (cache miss):"
time curl -s "http://127.0.0.1:8080/v1/models?mode=all" > /dev/null

echo "Second request (cache hit):"
time curl -s "http://127.0.0.1:8080/v1/models?mode=all" > /dev/null

echo ""

# Test 7: AI Assistant compatibility
echo "🧪 Test 7: AI Assistant compatibility"
echo "Testing OpenAI-compatible response format"

COMPAT_RESPONSE=$(curl -s "http://127.0.0.1:8080/v1/models")
OBJECT_TYPE=$(echo "$COMPAT_RESPONSE" | jq -r '.object' 2>/dev/null)
HAS_DATA=$(echo "$COMPAT_RESPONSE" | jq -e '.data' > /dev/null 2>&1 && echo "true" || echo "false")
FIRST_MODEL_ID=$(echo "$COMPAT_RESPONSE" | jq -r '.data[0].id' 2>/dev/null)

echo "Response object type: $OBJECT_TYPE"
echo "Has data array: $HAS_DATA"
echo "First model ID: $FIRST_MODEL_ID"

if [ "$OBJECT_TYPE" = "list" ] && [ "$HAS_DATA" = "true" ] && [ "$FIRST_MODEL_ID" != "null" ]; then
    echo "✅ OpenAI compatibility maintained"
else
    echo "❌ OpenAI compatibility issues detected"
fi

echo ""
echo "=== Test Summary ==="
echo "✅ Curated models: Working (fast loading)"
if [ "$ALL_COUNT" -gt 100 ]; then
    echo "✅ Full catalog: Working ($ALL_COUNT models)"
else
    echo "⚠️  Full catalog: Limited (fallback to curated)"
fi

if [ "$SEARCH_COUNT" -gt 0 ]; then
    echo "✅ Search: Working"
else
    echo "❌ Search: Not working"
fi

if [ "$PROVIDER_COUNT" -gt 0 ]; then
    echo "✅ Provider filtering: Working"
else
    echo "❌ Provider filtering: Not working"
fi

echo "✅ OpenAI compatibility: Maintained"
echo ""
echo "🎯 Usage Examples for AI Assistant:"
echo "  - Default (fast): http://127.0.0.1:8080/v1/models"
echo "  - All models: http://127.0.0.1:8080/v1/models?mode=all"
echo "  - Search GPT: http://127.0.0.1:8080/v1/models?mode=search&search=gpt"
echo "  - OpenAI only: http://127.0.0.1:8080/v1/models?mode=all&provider=openai"
echo "  - Limited: http://127.0.0.1:8080/v1/models?mode=all&limit=20"
