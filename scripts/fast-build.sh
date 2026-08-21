#!/bin/bash
# Fast build script for openrouter-intellij-plugin
#
# Modes:
#   compile    — compileKotlin only (fastest; check syntax)
#   test       — unit tests only, skip detekt and kover (fast feedback loop)
#   check      — unit tests + detekt, skip kover (medium; for pre-commit)
#   full       — full build including kover (slow; for release)
#
# Usage:
#   ./scripts/fast-build.sh [mode]
#   ./scripts/fast-build.sh test    # default: unit tests only

set -euo pipefail

MODE="${1:-test}"

case "$MODE" in
  compile)
    echo "🔨 Compiling Kotlin..."
    ./gradlew compileKotlin --parallel
    ;;
  test)
    echo "🧪 Running unit tests (skipping detekt and kover)..."
    ./gradlew test -x detekt -x koverVerify --parallel
    ;;
  check)
    echo "✅ Running tests + detekt (skipping kover)..."
    ./gradlew check -x koverVerify --parallel
    ;;
  full)
    echo "🚀 Full build (including kover)..."
    ./gradlew build
    ;;
  *)
    echo "Unknown mode: $MODE"
    echo "Usage: $0 [compile|test|check|full]"
    exit 1
    ;;
esac

echo "✨ Done!"
