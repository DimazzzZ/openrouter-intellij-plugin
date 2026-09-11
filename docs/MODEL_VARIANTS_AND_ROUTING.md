# Model Variants & Provider Routing

This document describes two features:

1. **Model Variants** — first-class handling of OpenRouter's model-ID suffixes (`:free`, `:nitro`, `:exacto`, etc.) throughout the plugin UI.
2. **Provider Routing** — a settings page that lets you configure global defaults (order, fallbacks, sort, filters, etc.) that the plugin injects into outbound proxy requests when your client doesn't specify them.

---

## Model Variants

OpenRouter exposes many models under multiple variant suffixes. The same base model can appear with different pricing, latency, or capability trade-offs depending on the suffix. The plugin now recognizes these suffixes and surfaces them consistently.

### Recognized variants

| Suffix        | Display    | Meaning                                       |
|---------------|------------|-----------------------------------------------|
| `:free`       | Free       | Free tier — no cost                           |
| `:exacto`     | Exacto     | Quality-first provider sorting                |
| `:nitro`      | Nitro      | High-speed inference                          |
| `:floor`      | Floor      | Lowest-cost inference                         |
| `:batch`      | Batch      | Asynchronous batch processing — 24h window, discounted pricing |

Unknown suffixes are still tolerated (parsed into `unknownVariant`), but they don't get a color chip.

> **Retired suffixes:** OpenRouter no longer documents `:extended`, `:thinking`, or `:online` as model-ID variants. The plugin dropped them: they no longer parse into a known variant (they fall through to `unknownVariant`), get no chip, and are stripped from saved favorites on upgrade.

### Where variants appear

- **Model selector dropdown** (chat panel): each entry shows the base model with a colored chip for its variant, and the tooltip explains what the variant does.
- **Favorite Models table** (settings): the Model column uses the same chip renderer, so users can visually distinguish `grok-4-fast:free` from `grok-4-fast:nitro` at a glance. A context-help icon next to the page comment lists every chip and its meaning.
- **Pricing columns**: models priced at zero display "Free" instead of "$0.0000" — most commonly the `:free` variant, but it applies to any zero-priced entry.
- **Variant filter** (see below).

### Favorite Models page

Settings → Tools → OpenRouter → Favorite Models is a single catalog table (see [ADR-0004](adr/0004-single-table-favorites-picker.md)):

- The first column is a checkbox: ticking a row appends the model to the ordered favorites list, unticking removes it. Variants are separate rows (`grok-4-fast` and `grok-4-fast:free`), so picking a variant is just ticking its row.
- The toolbar holds the filters as drop-downs — Provider, Context, Capabilities (multi-select), Variant (Any / Base only / Free / Exacto / Nitro / Floor / Batch / Other) — plus Presets, Refresh, and Move Up / Move Down.
- **Favorites only** (the star toggle) shows the favorites in stored order with sorting and filters disabled; Move Up / Move Down and drag-and-drop reorder rows there. That order is what AI Assistant lists.

Favorites are stored as a flat, ordered list of model ids. [`FavoriteModelsManager.getGroups()`](../src/main/kotlin/org/zhavoronkov/openrouter/services/settings/FavoriteModelsManager.kt) can still derive a base-model → variants view on demand; the flat list remains authoritative for downstream consumers.

---

## Provider Routing

OpenRouter's chat-completion API accepts a `provider` block and a `models[]` fallback list. See [OpenRouter's provider routing docs](https://openrouter.ai/docs/provider-routing) for the upstream schema.

The plugin adds a **Provider Routing** sub-page under settings where you configure global defaults. When enabled, the proxy injects your settings into every outbound request — but only if the client didn't already specify them.

### Configurable fields

| Field                | Purpose                                                     |
|----------------------|-------------------------------------------------------------|
| `order`              | Ordered list of provider slugs to try                       |
| `allowFallbacks`     | Whether to fall through to other providers if `order` fails |
| `sort`               | `price`, `throughput`, or `latency`                         |
| `requireParameters`  | Only route to providers that support all request parameters |
| `dataCollection`     | `allow` or `deny` — provider data-collection policy         |
| `quantizations`      | Restrict to providers offering listed quantizations         |
| `only`               | Whitelist provider slugs                                    |
| `ignore`             | Blacklist provider slugs                                    |
| `fallbackModels`     | `models[]` fallback list injected into requests             |

### Injection invariant

The plugin never overwrites a client-supplied `provider` or `models[]` block. Concretely:

- If the outbound JSON already has a `provider` key, the plugin skips provider injection entirely and logs `Client sent provider; skipping settings injection` at DEBUG.
- If the outbound JSON already has a `models` key, the plugin skips fallback injection and logs `Client sent models[]; skipping settings injection`.
- Otherwise, when routing is enabled and the settings have any non-default field, the plugin serializes them into a `ProviderRoutingPreferences` block and adds it to the request.

This means:

- **AI Assistant** and other clients that never send a `provider` block get whatever you configured in settings.
- **Advanced clients** that construct their own routing block can rely on the plugin leaving it untouched.

### JSON shape (what gets injected)

Given settings with `order = ["Anthropic", "OpenAI"]`, `sort = "price"`, `dataCollection = "deny"`, and `fallbackModels = ["meta-llama/llama-3.1-70b"]`, an outbound request like:

```json
{
  "model": "openai/gpt-4o",
  "messages": [...]
}
```

becomes:

```json
{
  "model": "openai/gpt-4o",
  "messages": [...],
  "provider": {
    "order": ["Anthropic", "OpenAI"],
    "sort": "price",
    "data_collection": "deny"
  },
  "models": ["meta-llama/llama-3.1-70b"]
}
```

Fields that are unset (or match OpenRouter's default) are omitted from the injected block.

### Disabling injection

Uncheck the "Enable global provider routing" checkbox at the top of the settings page. When disabled, the plugin never adds `provider` or `models[]` to outbound requests, regardless of what's configured below.

---

## Implementation notes

- **Parsing**: [`ModelProviderUtils.parseModelId()`](../src/main/kotlin/org/zhavoronkov/openrouter/utils/ModelProviderUtils.kt) turns `x-ai/grok-4-fast:free` into a `ModelId(provider, baseName, variant, unknownVariant)`.
- **Chip rendering**: [`ModelVariantChipRenderer`](../src/main/kotlin/org/zhavoronkov/openrouter/ui/ModelVariantChipRenderer.kt) is the single source of truth for variant colors and tooltips.
- **Storage**: Favorites are stored flat and ordered; [`FavoriteModelsManager`](../src/main/kotlin/org/zhavoronkov/openrouter/services/settings/FavoriteModelsManager.kt) computes a grouped view on demand via `getGroups()`.
- **Filtering**: [`ModelFilterCriteria.matches()`](../src/main/kotlin/org/zhavoronkov/openrouter/settings/ModelFilterCriteria.kt) is the single predicate behind the Favorite Models filters; [`VariantFilter`](../src/main/kotlin/org/zhavoronkov/openrouter/settings/favorites/VariantFilter.kt) covers the variant dimension.
- **Page logic**: [`FavoriteModelsPageState`](../src/main/kotlin/org/zhavoronkov/openrouter/settings/favorites/FavoriteModelsPageState.kt) holds the ordered favorites, mode, criteria and catalog; the Swing panel only renders it.
- **Injection**: [`ProviderRoutingInjector.inject()`](../src/main/kotlin/org/zhavoronkov/openrouter/proxy/routing/ProviderRoutingInjector.kt) is the sole entry point — the servlet delegates to it, and its behavior is unit-tested across ~10 scenarios in `ProviderRoutingInjectorTest`.
