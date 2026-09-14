# ADR-0003: Disabled Tests → Tagged Taxonomy

**Status**: Accepted  
**Date**: 2025-08-19  

> **Correction (2025-11-01):** the platform-test selector is class-name-based,
> not tag-based. `platformTest` picks up classes whose names end in
> `*PlatformTest` or `*SmokeTest`; there is no `@Tag("platformTest")`
> annotation in use. The default `test` task excludes only
> `@Tag("functional")`. ADRs are append-only, so the decision text below is
> preserved verbatim; see `build.gradle.kts` and `TESTING.md` for the
> authoritative selectors.

## Context

The project had 12 tests annotated with `@Disabled`, mostly integration tests
that require API keys or consume credits. These tests provided zero CI signal —
they bit-rotted silently with no feedback loop.

## Decision

Replace `@Disabled` with JUnit 5 tags and Gradle task separation:

- `@Tag("functional")` — integration tests requiring external services (API keys,
  network). Run via `./gradlew functionalTest -Pfunctional`. Excluded from
  default `test` task.
- `@Tag("platformTest")` — tests requiring IntelliJ's shared TestApplication.
  Run via `./gradlew platformTest`. Wired through
  `intellijPlatformTesting.testIde { register("platformTest") }`.

The default `./gradlew test` runs only fast unit tests. `./gradlew check`
runs unit tests + platformTest. Functional tests are opt-in.

## Consequences

- CI gets signal from platform tests on every PR.
- Integration tests remain opt-in but are discoverable and runnable.
- No more `@Disabled` — tests are either in-scope for a task or not.
- New tests must choose a tag; the taxonomy is documented in TESTING.md.

## Related

- `build.gradle.kts`: `test { excludeTags("functional") }`, `functionalTest`, `platformTest`
- `TESTING.md`: test taxonomy documentation
