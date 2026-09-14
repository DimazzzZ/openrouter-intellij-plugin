# ADR-0005: Raise Minimum Platform to 2025.3

**Status**: Accepted  
**Date**: 2025-11-01  
**Supersedes**: [ADR-0001](0001-raise-minimum-platform-to-2024-2.md) (version claims only; the rationale for using IPGP 2.x still holds)

## Context

ADR-0001 pinned the minimum platform at IntelliJ 2024.2 / build 242. Two
forces have since made that floor untenable:

1. **Unified IDE distribution.** Starting with 2025.3, JetBrains removed the
   `intellijIdeaCommunity()` / `intellijIdeaUltimate()` split. IPGP now exposes
   a single `intellijIdea(<version>)` helper. Continuing to target older
   platforms would require maintaining two distinct build wiring paths.
2. **Toolchain drift.** The build is on Gradle 9.7.1, IPGP 2.18.1, and Kotlin
   2.1.20 with a Java 21 toolchain. IPGP 2.18.x targets modern platform
   versions; back-porting to 242 introduces verifier failures against APIs
   that were reworked between 2024.2 and 2025.3.

## Decision

Set `pluginSinceBuild = 253` and `platformVersion = 2025.3.6`. Do not set an
`untilBuild` — the plugin remains forward-compatible.

Consequently:

- `gradle.properties`: `platformVersion = 2025.3.6`, `pluginSinceBuild = 253`.
- `build.gradle.kts`: use `intellijIdea(platformVersion)` (unified helper).
- `src/main/resources/META-INF/plugin.xml`: `<idea-version since-build="253"/>`.

## Consequences

- Users on IntelliJ 2024.x or earlier cannot install the plugin. This is the
  right trade-off: the shrinking cohort of 2024.x users is smaller than the
  cost of maintaining a compatibility fork.
- Build no longer needs the IC/IU distribution split; verifier IDEs can be
  targeted uniformly via `-PverifierIdes=IC-<version>` / `-PverifierIdes=IU-<version>`.
- ADR-0001's version claims (2024.2 / 242 / Gradle 9.4.0) are superseded.
  Its architectural rationale (adopt IPGP 2.x, drop deprecated Gradle IntelliJ
  Plugin 1.x, require Java 21) still stands.

## Related

- `gradle.properties`: `platformVersion = 2025.3.6`, `pluginSinceBuild = 253`
- `build.gradle.kts`: `intellijPlatform { create(...) { intellijIdea(platformVersion) } }`
- `src/main/resources/META-INF/plugin.xml`: `<idea-version since-build="253"/>`
- Superseded ADR: [ADR-0001](0001-raise-minimum-platform-to-2024-2.md)
