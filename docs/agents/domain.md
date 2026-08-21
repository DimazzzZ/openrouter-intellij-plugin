# Domain Glossary

The ubiquitous language for the OpenRouter IntelliJ plugin. When these terms
appear in code, tests, docs, ADRs, or commit messages, they mean exactly what
this file says.

## Core concepts

- **Provisioning Key** — long-lived OpenRouter admin key (`sk-or-v1-...`),
  scoped to a user account. Used to mint short-lived API keys programmatically.
- **API Key** — short-lived key minted from a provisioning key. Sent as
  `Authorization: Bearer` on outbound OpenRouter requests.
- **Model** — an OpenRouter model identifier (e.g. `openai/gpt-4o`,
  `anthropic/claude-3.5-sonnet`). Vendor-prefixed, always lowercase.
- **Proxy Server** — local Jetty server that translates AI-Assistant-style
  OpenAI requests into OpenRouter requests. Runs on 127.0.0.1 only.
- **AI Assistant** — JetBrains' built-in AI feature (`com.intellij.ml.llm`).
  Points at the proxy via custom base URL.

## Service boundaries

- **Settings layer** — persisted plugin state (keys, model preferences, proxy
  port). Backed by IntelliJ's `PersistentStateComponent`.
- **Proxy layer** — HTTP request/response translation. Stateless per request.
- **Service layer** — OpenRouter API clients (models list, key management,
  usage stats). Uses OkHttp.
- **UI layer** — settings panel, tool window, status bar widget, notifications.

## Test taxonomy

See ADR-0003 and TESTING.md. In short:

- **unit** — pure logic tests. Default `./gradlew test`.
- **functional** — external-service tests. `@Tag("functional")`, opt-in.
- **platformTest** — tests needing IntelliJ TestApplication. Runs via
  `intellijPlatformTesting.testIde`.

## ADR layout

Each ADR is one file in `docs/adr/NNNN-title-kebab-case.md` with sections:

- **Status**: Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
- **Date**: YYYY-MM-DD
- **Context**: what's the situation that forced a decision
- **Decision**: what was decided (imperative voice)
- **Consequences**: what changes as a result
- **Related**: files, tickets, or other ADRs

ADRs are append-only. Don't edit accepted ADRs — supersede them with a new one.
