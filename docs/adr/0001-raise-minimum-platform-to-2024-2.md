# ADR-0001: Raise Minimum Platform to 2024.2

**Status**: Accepted  
**Date**: 2025-09-01  

## Context

IntelliJ Platform 2024.2 requires Java 21 and introduces the IntelliJ Platform
Gradle Plugin 2.x (IPGP). Staying on older platforms means staying on the
deprecated Gradle IntelliJ Plugin 1.x, which is no longer maintained and
incompatible with Gradle 9.x.

## Decision

Set `pluginSinceBuild = 242` and require Java 21 toolchain. Adopt IPGP 2.x as
the build plugin. Drop support for IntelliJ 2023.x and earlier.

## Consequences

- Users on IntelliJ 2023.x or earlier cannot install the plugin.
- Build uses Gradle 9.4.0 + IPGP 2.18.1, which enables configuration cache,
  modern test framework wiring, and plugin verification against multiple IDEs.
- Java 21 toolchain is enforced via `gradle/gradle-daemon-jvm.properties`.

## Related

- `gradle.properties`: `platformVersion = 2024.2`, `pluginSinceBuild = 242`
- `build.gradle.kts`: `intellijPlatform { pluginConfiguration { ideaVersion { sinceBuild = "242" } } }`
