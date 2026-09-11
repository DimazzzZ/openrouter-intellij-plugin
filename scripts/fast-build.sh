#!/bin/bash
# Fast build script for openrouter-intellij-plugin
#
# Modes:
#   compile    — compileKotlin only (fastest; check syntax)
#   test       — unit tests only, skip detekt and kover (fast feedback loop)
#   check      — unit tests + detekt, skip kover (medium; for pre-commit)
#   verify        — plugin verifier (ADR-0002 gate) against the pinned build in
#                   gradle.properties (verifierIdes) — the same target CI and
#                   releases use. First run downloads that IDE once.
#   verify local  — same check against the IntelliJ IDEA installed on this
#                   machine. Use it to see deprecations introduced by IDE
#                   versions newer than the pinned one. Point it elsewhere with
#                   VERIFIER_LOCAL_IDE=/path/to/IDE.app
#   full       — full build including kover (slow; for release)
#
# Usage:
#   ./scripts/fast-build.sh [mode]
#   ./scripts/fast-build.sh test          # default: unit tests only
#   ./scripts/fast-build.sh verify local  # verifier against the installed IDE

set -euo pipefail

MODE="${1:-test}"
TARGET="${2:-pinned}"

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
  verify)
    if [ "$TARGET" = "local" ]; then
      # Manual check against whatever IDE is installed here — typically newer
      # than the pinned build, so it surfaces upcoming deprecations early.
      # Findings are informational: the pinned build is what gates CI.
      LOCAL_IDE="${VERIFIER_LOCAL_IDE:-/Applications/IntelliJ IDEA.app}"
      if [ ! -d "$LOCAL_IDE" ]; then
        echo "No IDE at ${LOCAL_IDE}. Set VERIFIER_LOCAL_IDE=/path/to/IDE.app" >&2
        exit 1
      fi
      echo "🔎 Verifying plugin against the installed IDE ${LOCAL_IDE}..."
      ./gradlew verifyPlugin -PverifierLocalIde="${LOCAL_IDE}" --parallel
    else
      # No -PverifierIdes: picks up the pinned verifierIdes from gradle.properties,
      # the same target CI and releases use.
      echo "🔎 Verifying plugin against the pinned IDE from gradle.properties..."
      ./gradlew verifyPlugin --parallel
    fi
    ;;
  full)
    echo "🚀 Full build (including kover)..."
    ./gradlew build
    ;;
  *)
    echo "Unknown mode: $MODE"
    echo "Usage: $0 [compile|test|check|verify [local]|full]"
    exit 1
    ;;
esac

echo "✨ Done!"
