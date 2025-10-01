#!/bin/bash

# Simple test script to verify the proxy server models endpoint fix

echo "=== OpenRouter Proxy Server Test ==="
echo "Testing the models endpoint fix for AI Assistant integration"
echo ""

# Test 1: Models endpoint without authentication (should work now)
echo "🧪 Test 1: Models endpoint without authentication"
echo "curl -v http://127.0.0.1:8080/v1/models"
echo ""

# Wait for user to start the proxy server
echo "Please start the proxy server first:"
echo "  ./gradlew runIde --args=\"--proxy-server\" -Dopenrouter.force.proxy=true"
echo ""
echo "Then press Enter to run the test..."
read -r

# Test the models endpoint
echo "Testing models endpoint..."
curl -s http://127.0.0.1:8080/v1/models | jq '.data | length' 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Models endpoint is working!"
    echo ""
    echo "🧪 Test 2: Checking model list format"
    curl -s http://127.0.0.1:8080/v1/models | jq '.data[0:3] | .[].id' 2>/dev/null
    echo ""
    echo "✅ AI Assistant should now be able to load models!"
    echo ""
    echo "📋 Next steps:"
    echo "1. Install AI Assistant plugin in the development IDE"
    echo "2. Configure AI Assistant with:"
    echo "   - URL: http://127.0.0.1:8080"
    echo "   - API Key: test-key (any value)"
    echo "   - Model: gpt-3.5-turbo or gpt-4"
    echo "3. Test the connection"
else
    echo "❌ Models endpoint is not responding"
    echo "Make sure the proxy server is running on port 8080"
fi
