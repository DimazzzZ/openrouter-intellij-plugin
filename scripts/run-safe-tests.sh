#!/bin/bash

# Safe test runner that only runs unit tests that don't require IntelliJ platform
# This prevents memory issues and hanging tests

echo "🧪 Running safe unit tests only..."

./gradlew test \
  --tests "SimpleUnitTest" \
  --tests "EncryptionUtilTest" \
  --tests "OpenRouterModelsTest" \
  --tests "OpenRouterRequestBuilderTest" \
  --tests "ApiKeysTableModelTest" \
  --tests "ProxyUrlCopyTest" \
  --tests "DuplicateRequestsTest" \
  --tests "OpenRouterIconsTest" \
  --tests "RequestTranslatorTest" \
  --console=plain

echo "✅ Safe unit tests completed!"
