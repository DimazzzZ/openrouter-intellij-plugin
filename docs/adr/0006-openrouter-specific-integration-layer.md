# ADR-0006: An OpenRouter-Specific Integration Layer, Not a Generic Gateway or an Agent

**Status**: Accepted
**Date**: 2026-09-27

## Context

Three recurring questions kept being answered ad hoc, each time from scratch, and each time slightly differently: should the plugin support other OpenAI-compatible providers, should it grow an agent mode of its own, and who is the plugin actually for. They are the same question wearing three hats, so they are settled here together.

The plugin already describes itself on the marketplace as "a lightweight OpenRouter integration layer" whose local proxy "other tools and agents can reuse", and it explicitly says it is "not intended to replace the native JetBrains AI or Android Studio AI Agent workflow". That position was never written down anywhere a contributor would find it, so the code and the issue tracker drifted away from it.

Two facts from outside the project bound the decision.

The generic-gateway space is occupied and mature. LiteLLM is an OpenAI-compatible self-hosted proxy in front of 100+ providers with virtual keys, per-team budgets, rate limits, fallbacks, caching and an admin UI, at roughly 59.7k stars. Bifrost, Envoy AI Gateway and Portkey cover the same ground. Nothing this project can do on that axis will be better than what a user gets by running one of them. What those gateways cannot do is go deep on any single provider: LiteLLM documents exactly three OpenRouter-specific passthrough parameters (`transforms`, `models`, `route`), and its spend figures are reconstructed from its own request logs rather than read from OpenRouter, so they do not reflect the account's real balance or what other tools spent against the same key.

The agent space is occupied even more thoroughly, and by the IDE vendor. Junie supports OpenRouter natively as a BYOK provider and can also point at custom endpoints; Cline, Aider, Roo Code and Copilot all reach OpenRouter directly. Meanwhile the supported way to get agentic coding through this plugin already exists and is documented: AI Assistant runs the agent loop, MCP servers execute the tools, and this plugin supplies inference. An agent loop of our own would duplicate the half that MCP already owns, and would compete with the IDE's own agent on its home ground.

## Decision

**The plugin is an OpenRouter-specific integration layer for JetBrains IDEs. Depth in one provider, not breadth across providers.** The defensible value is everything OpenRouter offers beyond bare OpenAI compatibility, delivered where the developer already works: the curated model list that becomes the host's model dropdown, model variants, provider routing, presets, and real credit and analytics figures read from OpenRouter itself.

**Consumers come first; our own UI serves them.** The proxy's users — AI Assistant's chat and completion surfaces, Android Studio's assistant, and any other tool pointed at it — are the primary audience. Our settings, status bar and chat window exist to configure, observe and verify that. A feature that improves only our own chat window and gives a consumer nothing is below the line.

**In-IDE hosts drive design; anything else is best-effort.** Design decisions are made for hosts running inside the IDE. Tools outside it can use the proxy and this is documented, but the port is ephemeral and tied to the IDE's lifetime, and neither is a guarantee. Users who need a real standalone gateway are better served by LiteLLM and the documentation says so.

**No agent loop of our own.** Requests for an agent mode are answered with the combination that already works: this plugin for inference, the host's agent for the loop, MCP servers for tool execution. Tool-call fidelity in the proxy — correctly carrying tool calls in both directions, including responses that contain a tool call and no text — is in scope and required, because tool calls are how consuming agents talk.

**The proxy's base URL selects among OpenRouter's own endpoints only**, so region-scoped routing is configurable, but pointing the plugin at a different provider is not supported. Every distinguishing feature — curation, variants, routing, presets, credits, analytics — is built on OpenRouter's API and would silently degrade against a foreign endpoint, leaving a worse generic gateway than the ones that already exist.

**The OpenRouter logic is extracted into a platform-independent core**, incrementally. The core carries the proxy, the translation layer and the OpenRouter client, with no IntelliJ dependency, and reaches the platform through narrow ports for configuration, model curation and secret storage. The IDE-specific shell implements those ports. This is adopted for testability and for the discipline of keeping provider logic free of platform coupling, and it happens gradually as features touch each service rather than as a separate refactoring effort. Shipping a standalone daemon built on that core remains possible but is not planned; nothing in the issue tracker asks for one.

## Consequences

The proxy and most services become testable without a running IDE, under the fast headless test task instead of the platform-test runner. This matters in practice: classes that could not be constructed outside the platform have previously been covered by hand-written simulators rather than real tests, and platform tests have proven sensitive to the host's font metrics.

Compatibility with consuming hosts becomes a tracked concern with its own priority, rather than something noticed when a user reports it. Support for a second host's model-list format is work the curation port is meant to absorb.

Requests to support other providers are declined, with a pointer to the gateways built for that. This closes off an audience deliberately.

The core's ports are a real constraint, not just indirection: logic in the core cannot reach into the IDE for the open file, the current branch or the editor selection. Enriching proxied requests with project context would require widening the ports each time, and the shell would grow accordingly.

The local proxy authenticates nothing today — any process on the machine can spend the account's credits through it. Treating tools outside the IDE as legitimate consumers makes that a deliberate decision rather than an oversight, and it is addressed separately by an opt-in proxy token.

## Related

- ADR-0002: No Non-Public Platform API — the core extraction reduces the platform surface this plugin touches at all.
- `docs/AI_ASSISTANT_SETUP.md` — the supported agentic-coding path (inference here, agent loop in AI Assistant, tools over MCP).
