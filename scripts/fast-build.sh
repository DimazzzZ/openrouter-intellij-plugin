#!/bin/bash
# Fast build script for openrouter-intellij-plugin
#
# Modes:
#   compile    — compileKotlin only (fastest; check syntax)
#   test       — unit tests only, skip detekt and kover (fast feedback loop)
#   check      — unit tests + detekt, skip kover (medium; for pre-commit)
#   verify     — plugin verifier (ADR-0002 gate). Uses the locally installed
#                IntelliJ IDEA when present (no IDE download), otherwise the
#                pinned IU-2025.3 build, downloaded once into ~/.pluginVerifier.
#                Override: VERIFIER_IDES=IU-2026.2 or VERIFIER_LOCAL_IDE=/path/IDE.app
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
  verify)
    # Community builds stopped at 2025.2, so the cheapest valid target for
    # pluginSinceBuild=253 is IU-2025.3 (see `./gradlew printProductsReleases`).
    # A local install skips the download entirely; the verifier's own
    # ~/.pluginVerifier cache makes repeat runs fast either way.
    LOCAL_IDE="${VERIFIER_LOCAL_IDE:-/Applications/IntelliJ IDEA.app}"
    if [ -z "${VERIFIER_IDES:-}" ] && [ -d "$LOCAL_IDE" ]; then
      echo "🔎 Verifying plugin against local IDE ${LOCAL_IDE} (searchable options skipped)..."
      ./gradlew verifyPlugin -PverifierLocalIde="${LOCAL_IDE}" --parallel
    else
      IDES="${VERIFIER_IDES:-IU-2025.3}"
      echo "🔎 Verifying plugin against ${IDES} (searchable options skipped)..."
      ./gradlew verifyPlugin -PverifierIdes="${IDES}" --parallel
    fi
    ;;
  full)
    echo "🚀 Full build (including kover)..."
    ./gradlew build
    ;;
  *)
    echo "Unknown mode: $MODE"
    echo "Usage: $0 [compile|test|check|verify|full]"
    exit 1
    ;;
esac

echo "✨ Done!"
