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
- **Latest Model** — a `~author/family-latest` slug, which OpenRouter resolves to the newest model in that family at request time, so what answers can change while the id stays the same. The `~` is a marker of latest resolution, not part of the author: a Latest Model files, filters and groups under its real author, and carries a Latest chip beside the variant chips. It is a flag on the parsed id rather than a variant, because variants are suffixes and this is a prefix; a Latest Model can carry a variant as well.
- **Router** — an `openrouter/`-namespaced model slug that OpenRouter itself
  resolves to an underlying model at request time (e.g. `openrouter/auto`,
  `openrouter/fusion`, `openrouter/pareto-code`). A router may take one tunable
  parameter carried in a `plugins[]` block.
- **RouterCatalog** — the single declarative table (`proxy/routing`) of
  supported routers. The chat model list, the proxy `/v1/models` listing and
  the request builder all read from it; no router-specific branches live
  anywhere else.
- **Chat Exchange** — what the chat window sends to OpenRouter and what it reads back: the one pure module that turns the send-parameter controls' selections into a request's optional fields, and a response into the facts a reply reports about itself. It owns the labels those controls offer as well as their translation, so a label the request cannot express cannot exist. Distinct from the **Proxy layer**, which translates for a **Consumer**; this is the plugin's own chat window talking for the human.
- **Reply Summary** — what a reply reports about how it was produced, shown in the footer under it: the answering model (a **Router**'s reply keeps its "Routed to X" wording), the provider that served the call, what it cost, and why generation stopped, plus a marker when the request carried a **Web Search**. Provider and cost are left out when the response does not carry them; the stop reason becomes a warning only when it is not a normal stop. Built by the **Chat Exchange**.
- **PluginConfig** — one `{ id, params }` entry in a request's `plugins` array. The chat uses it only to pass a **Router**'s parameter (e.g. `auto-router` + `cost_tier`, emitted only when the user picks a valid value); **Web Search** is a server tool, not a plugin.
- **Web Search** — OpenRouter's `openrouter:web_search` server tool (`tools: [{"type": "openrouter:web_search", "parameters": {...}}]`), allowed per message by a checkbox in the chat's send-parameters popup. The model decides whether and how often to search, so the Reply Summary counts the searches the response reports (`usage.server_tool_use.web_search_requests`) rather than assuming one. Each search is charged on top of inference, which the control says where it sits. The deprecated `web` plugin is not sent. The toggle stays as the user left it across sends, like Reasoning and Verbosity. How a search is tuned — engine, result count, included and excluded domains, and a mode for the engines that take one — is a separate decision made once on the Web Search settings page; anything left at its default is left out of the request. The Proxy Server applies the same tuning to a web search a **Consumer** asked for itself — the server tool, or the deprecated `web` plugin in its own spelling — filling only the keys it left out; it never adds a search a Consumer did not ask for. The UI spells it "Web search", sentence case, like its sibling labels.
- **Output Mode** — what shape the user asked a reply to take, chosen per message in the chat's send-parameters popup: Off (no `response_format`), plain JSON (`{"type": "json_object"}`), or a saved **Output Schema**. Which modes a Model can serve is read from its `supported_parameters`, and the **Chat Exchange** alone decides it: `response_format` gates plain JSON, `structured_outputs` gates schemas, and neither implies the other. A Model whose declarations are not known — a **Router**, a preset, or any Model while the catalogue loads — is offered only Off. A mode the Model cannot serve is shown disabled with the reason; a selection that stops being servable when the Model changes is kept, marked, and blocks sending until the user resolves it — never silently reset, never sent anyway. The UI spells it "Output mode", sentence case.
- **Output Schema** — a named JSON Schema the user saved on the Output Schemas settings page, to ask for a reply in exactly that shape. It is the inner object of OpenRouter's `json_schema` response format: a name, a strict flag and the schema body. The name is both what the Output mode control lists and the `name` OpenRouter receives, so there is one name rather than two that can disagree; names are unique without regard to case, limited to letters, digits, `_` and `-` (at most 64) — the set OpenAI-served requests accept — and never "Off", the control's own entry. A body is saved only when it parses strictly as a JSON object. A **Consumer** can ask for a saved schema by naming it in a `json_schema` response format with no `schema` of its own; the Proxy Server fills in the body and, when absent, the strict flag.
- **Request record** — the facts about one request the plugin sent to OpenRouter, through the **Proxy Server** or from the chat: when, from where, who sent it, the requested id, and what the reply reported about itself (answering model, provider, tokens, cost, stop reason, web searches, generation id) or the error that ended it. Facts only: never the prompt, the reply or a header, and an error is kept as its message, never as the body that carried it. Read in no API shape's spelling, so every shape the proxy serves records the same thing. The sender is a **Consumer** named from its User-Agent through one table of known agents, an unknown one kept as it identifies itself, or "Chat" for the plugin's own chat window.
- **Requests** — the stored list of **Request records**, most recent first and capped (1000 by default), kept on disk across restarts and shown on the tool window's Requests tab. It is how the user sees what their **Consumers** actually got.
- **Clear error** — the plugin's own refusal of a **Consumer**'s request that the **Proxy Server** can tell will not work, answered before anything is sent — for a streaming request, before any SSE — instead of whatever OpenRouter would refuse it with. It is HTTP 400 in OpenAI's error shape, so every Consumer shows it, with a `code` per reason and a one-line message, prefixed "OpenRouter plugin:", naming what is wrong and the one settings page that fixes it. The reasons: a `json_schema` response format naming a saved **Output Schema** that does not exist, or whose body is broken, and carrying no schema of its own; a model the catalogue does not list, or — with a **Data Region** selected — one the region does not serve; and a response format the model does not declare, judged by the chat's own rule. Nothing about the model is checked while the catalogue has not loaded, and a model whose declarations cannot be known from it — a **Router**, a preset, a **Latest Model** — is never refused for what it declares. Every refusal is a **Request record** that keeps the page that fixes it, and raises the warning balloon — folded with other warnings in a burst — whose action opens that page.
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
