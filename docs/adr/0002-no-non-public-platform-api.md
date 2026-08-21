# ADR-0002: No Non-Public Platform API

**Status**: Accepted  
**Date**: 2025-09-01  

## Context

JetBrains penalizes plugins that use internal, experimental, or
scheduled-for-removal platform APIs. The Plugin Verifier can detect these
usages, but by default only `COMPATIBILITY_PROBLEMS` fails the build — all
other categories are printed and swallowed.

Version 0.4.0 of the sibling token-pulse plugin shipped with 2
scheduled-for-removal usages that were caught only by a post-release audit.

## Decision

Set `pluginVerification.failureLevel` to include:

- COMPATIBILITY_PROBLEMS
- DEPRECATED_API_USAGES
- SCHEDULED_FOR_REMOVAL_API_USAGES
- INTERNAL_API_USAGES
- EXPERIMENTAL_API_USAGES
- OVERRIDE_ONLY_API_USAGES
- NON_EXTENDABLE_API_USAGES

MISSING_DEPENDENCIES and NOT_DYNAMIC are deliberately excluded because they
report unavailable *optional* dependencies we do not control.

## Consequences

- Any use of non-public API fails the verifier task, blocking release.
- Developers must find public alternatives or file upstream issues.
- The plugin stays in good standing with JetBrains Marketplace review.

## Related

- `build.gradle.kts`: `pluginVerification { failureLevel = listOf(...) }`
