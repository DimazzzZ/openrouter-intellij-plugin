# ADR-0001: Raise Minimum Platform to 2024.2

**Status**: Superseded by [ADR-0005](0005-raise-minimum-platform-to-2025-3.md)  
**Date**: 2025-09-01  

> **Note:** The version claims in this ADR (2024.2 / build 242 / Gradle 9.4.0)
> are superseded by ADR-0005, which raises the floor to 2025.3 / build 253.
> The architectural rationale below (adopt IPGP 2.x, require Java 21, drop the
> deprecated Gradle IntelliJ Plugin 1.x) still stands. Per project convention
> ADRs are append-only; the original decision text is preserved verbatim.

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
