# Domain Glossary

The ubiquitous language for the OpenRouter IntelliJ plugin. When these terms
appear in code, tests, docs, ADRs, or commit messages, they mean exactly what
this file says.

## Core concepts

- **Management Key** — long-lived OpenRouter admin key (`sk-or-v1-...`),
  scoped to a user account. Used to mint short-lived API keys programmatically,
  and the only key that may read account-level data: `/credits`, `/activity`,
  `/keys` and `/analytics/*` all reject an ordinary API key. OpenRouter used to
  call this a *provisioning key*; the name in the product is now **Management
  Key**, but the settings page still lives at
  `openrouter.ai/settings/provisioning-keys`. Say "Management Key" in code,
  docs and UI copy; keep the old word only where it is a URL or a stored
  identifier (`provisioningKey`, `AuthType.PROVISIONING_KEY`).
- **API Key** — short-lived key minted from a Management Key. Sent as
  `Authorization: Bearer` on outbound OpenRouter requests. **A Management Key
  reads the account; an API key reads itself.** It can spend
  (`/chat/completions`) and it can read `/key`, which describes the key making
  the request — its own usage, spend cap and rate limit — but nothing
  account-wide: the balance, the activity history, the key list and analytics
  all need the Management Key. Note the singular and plural are different
  endpoints with different requirements: `/key` answers any key, `/keys`
  answers only a Management Key. Both key types share the `sk-or-v1-` prefix,
  so a key's scope is discoverable only by calling a Management-only endpoint
  and seeing whether it answers.
- **Model** — an OpenRouter model identifier (e.g. `openai/gpt-4o`,
  `anthropic/claude-3.5-sonnet`). Vendor-prefixed, always lowercase.
- **Router** — an `openrouter/`-namespaced model slug that OpenRouter itself
  resolves to an underlying model at request time (e.g. `openrouter/auto`,
  `openrouter/fusion`, `openrouter/pareto-code`). A router may take one tunable
  parameter carried in a `plugins[]` block.
- **RouterCatalog** — the single declarative table (`proxy/routing`) of
  supported routers. The chat model list, the proxy `/v1/models` listing and
  the request builder all read from it; no router-specific branches live
  anywhere else.
- **PluginConfig** — the `{ id, params }` block attached to a request's
  `plugins` field to pass a router's parameter (e.g. `auto-router` +
  `cost_tier`). Emitted only when the user picks a valid value.
- **Proxy Server** — local Jetty server that translates AI-Assistant-style
  OpenAI requests into OpenRouter requests. Runs on 127.0.0.1 only.
- **AI Assistant** — JetBrains' built-in AI feature (`com.intellij.ml.llm`).
  Points at the proxy via custom base URL.

## Who the plugin serves

These three were used informally for a long time before they were pinned down. See ADR-0006 for the positioning they express.

- **Consumer** — any tool that sends requests to the plugin's local OpenAI-compatible proxy. AI Assistant's chat and completion surfaces are consumers; so is Android Studio's assistant, and so is a CLI pointed at `http://127.0.0.1:<port>`. A consumer is never the human: the human configures the plugin, the consumer uses it. Consumers come first — the plugin's own settings, status bar and chat window exist to configure, observe and verify what consumers get.
- **Host** — the IDE the plugin is installed in, and the consumers that ship inside it. Hosts drive design decisions; consumers outside the host are best-effort, since the proxy's port is ephemeral and tied to the IDE's lifetime. "Host" is about where a consumer runs, not who wrote it.
- **Curation** — the user's chosen subset of OpenRouter's catalogue, in their chosen order, as it is served to a consumer. Favorites are the storage; curation is what `/v1/models` emits and therefore what the consumer's model dropdown shows. Order is load-bearing, not cosmetic. Curation is the plugin's most distinctive job: pointed straight at OpenRouter, a consumer sees several hundred unfiltered models instead.

## Data regions

- **Data Region** — the OpenRouter endpoint every request is pinned to: `global` (no pinning), `europe` or `us`. One region, three spellings, which is why the code has a `DataRegion` type rather than a string: `allowed_data_regions` says `europe`, the `/models?region=` parameter says `eu`, and the host is `eu.openrouter.ai`. Say **Data Region** in code, docs and UI copy; the OpenRouter feature it belongs to is **In-Region Routing**. `global` is a value, not an absence - the API names it - so the type has no null.
- **In-Region Routing** — OpenRouter's feature for keeping a request inside a region for its whole lifecycle. A Business/Enterprise entitlement, reported per key by `allowed_data_regions` on `GET /api/v1/key`, which already folds in both the account entitlement and any guardrail policy on the key. Selecting a region moves the whole plugin, not just inference.

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
- **platformTest** — tests needing IntelliJ TestApplication. Selected by a
  `*PlatformTest` / `*SmokeTest` class-name suffix (not a `@Tag`); runs via
  `intellijPlatformTesting.testIde` and is excluded from the default `test` task.

## ADR layout

Each ADR is one file in `docs/adr/NNNN-title-kebab-case.md` with sections:

- **Status**: Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
- **Date**: YYYY-MM-DD
- **Context**: what's the situation that forced a decision
- **Decision**: what was decided (imperative voice)
- **Consequences**: what changes as a result
- **Related**: files, tickets, or other ADRs

ADRs are append-only. Don't edit accepted ADRs — supersede them with a new one.
