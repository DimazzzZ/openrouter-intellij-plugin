#!/bin/bash

# OpenRouter Proxy E2E Test Runner
# This script helps run end-to-end tests with real OpenRouter API

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     OpenRouter Proxy E2E Test Runner                      ║${NC}"
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo ""

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${RED}❌ Error: .env file not found!${NC}"
    echo ""
    echo "Please create a .env file in the project root with your OpenRouter API keys:"
    echo ""
    echo "  OPENROUTER_PROVISIONING_KEY=\"sk-or-v1-your-provisioning-key\""
    echo "  OPENROUTER_API_KEY=\"sk-or-v1-your-api-key\""
    echo ""
    echo "Get your keys from: https://openrouter.ai/settings/keys"
    exit 1
fi

# Load .env file
echo -e "${GREEN}✅ Found .env file${NC}"
source .env

# Verify API keys are set
if [ -z "$OPENROUTER_API_KEY" ]; then
    echo -e "${RED}❌ Error: OPENROUTER_API_KEY not set in .env file${NC}"
    exit 1
fi

if [ -z "$OPENROUTER_PROVISIONING_KEY" ]; then
    echo -e "${RED}❌ Error: OPENROUTER_PROVISIONING_KEY not set in .env file${NC}"
    exit 1
fi

echo -e "${GREEN}✅ API keys loaded${NC}"
echo -e "   API Key: ${OPENROUTER_API_KEY:0:20}...${NC}"
echo -e "   Provisioning Key: ${OPENROUTER_PROVISIONING_KEY:0:20}...${NC}"
echo ""

# Warning about API costs
echo -e "${YELLOW}⚠️  WARNING: These tests will make REAL API calls to OpenRouter!${NC}"
echo -e "${YELLOW}   Estimated cost: ~\$0.0007 per full test run${NC}"
echo ""

# Check if tests are disabled
if grep -q '@Disabled.*Disabled by default' src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt; then
    echo -e "${YELLOW}⚠️  Tests are currently DISABLED${NC}"
    echo ""
    echo "To enable tests, you need to comment out the @Disabled annotation in:"
    echo "  src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt"
    echo ""
    read -p "Do you want to temporarily enable tests for this run? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        # Create a backup
        cp src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt \
           src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt.bak
        
        # Comment out @Disabled annotation
        sed -i.tmp 's/@Disabled("Disabled by default/\/\/ @Disabled("Disabled by default/' \
            src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt
        rm -f src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt.tmp
        
        echo -e "${GREEN}✅ Tests temporarily enabled${NC}"
        RESTORE_DISABLED=true
    else
        echo -e "${RED}❌ Tests remain disabled. Exiting.${NC}"
        exit 0
    fi
fi

echo ""
echo -e "${BLUE}Select test category to run:${NC}"
echo ""
echo "  1) All E2E tests (11 tests, ~14 API calls)"
echo "  2) Non-Streaming tests only (2 tests, ~2 API calls)"
echo "  3) Streaming tests only (2 tests, ~2 API calls)"
echo "  4) Model Normalization tests (1 test, ~3 API calls)"
echo "  5) Error Handling tests (2 tests, ~2 API calls)"
echo "  6) Performance tests (1 test, ~3 API calls)"
echo "  7) Streaming vs Non-Streaming comparison (1 test, ~2 API calls)"
echo "  8) Single test: No Duplicate Requests (CRITICAL - verifies fix)"
echo ""
read -p "Enter choice (1-8): " choice

case $choice in
    1)
        TEST_FILTER="OpenRouterProxyE2ETest"
        TEST_NAME="All E2E Tests"
        ;;
    2)
        TEST_FILTER="OpenRouterProxyE2ETest.NonStreamingTests"
        TEST_NAME="Non-Streaming Tests"
        ;;
    3)
        TEST_FILTER="OpenRouterProxyE2ETest.StreamingTests"
        TEST_NAME="Streaming Tests"
        ;;
    4)
        TEST_FILTER="OpenRouterProxyE2ETest.ModelNormalizationTests"
        TEST_NAME="Model Normalization Tests"
        ;;
    5)
        TEST_FILTER="OpenRouterProxyE2ETest.ErrorHandlingTests"
        TEST_NAME="Error Handling Tests"
        ;;
    6)
        TEST_FILTER="OpenRouterProxyE2ETest.PerformanceTests"
        TEST_NAME="Performance Tests"
        ;;
    7)
        TEST_FILTER="OpenRouterProxyE2ETest.StreamingComparisonTests"
        TEST_NAME="Streaming Comparison Tests"
        ;;
    8)
        TEST_FILTER="OpenRouterProxyE2ETest.StreamingTests.testNoDuplicateRequests"
        TEST_NAME="No Duplicate Requests Test"
        ;;
    *)
        echo -e "${RED}❌ Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}Running: $TEST_NAME${NC}"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."
echo ""

# Run the tests
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Starting tests...${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

./gradlew test --tests "$TEST_FILTER" --info 2>&1 | tee test-output.log

TEST_EXIT_CODE=${PIPESTATUS[0]}

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Test Results${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ All tests PASSED!${NC}"
    echo ""
    echo -e "${YELLOW}📊 Next Steps:${NC}"
    echo ""
    echo "1. Check OpenRouter Analytics:"
    echo "   https://openrouter.ai/activity"
    echo ""
    echo "2. Verify the fixes:"
    echo "   ✅ Only 1 request per streaming call (not 11 duplicates)"
    echo "   ✅ Correct model names appear (not always GPT-3.5 Turbo)"
    echo ""
    echo "3. Review test output:"
    echo "   cat test-output.log"
    echo ""
else
    echo -e "${RED}❌ Some tests FAILED${NC}"
    echo ""
    echo "Check the test output above for details."
    echo "Full output saved to: test-output.log"
    echo ""
fi

# Restore @Disabled annotation if we temporarily enabled it
if [ "$RESTORE_DISABLED" = true ]; then
    echo ""
    echo -e "${BLUE}Restoring @Disabled annotation...${NC}"
    mv src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt.bak \
       src/test/kotlin/org/zhavoronkov/openrouter/integration/OpenRouterProxyE2ETest.kt
    echo -e "${GREEN}✅ Tests disabled again${NC}"
fi

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Test run complete!${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"

exit $TEST_EXIT_CODE

