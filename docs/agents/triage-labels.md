# Triage Labels

Canonical GitHub label vocabulary for openrouter-intellij-plugin issues and PRs.
Labels compose freely — an issue can have status + type + priority + domain.

## Status labels (mutually exclusive)

- `needs-triage` — new issue, not yet reviewed by a maintainer.
- `needs-info` — waiting on the reporter for reproduction steps, logs, etc.
- `ready-for-agent` — well-scoped, has enough context for an AI agent to work on.
- `ready-for-human` — needs human judgment (design decision, breaking change).
- `in-progress` — someone is actively working on it.
- `wontfix` — closed without a fix; explain why in a comment.

## Type labels

- `type:bug` — something is broken.
- `type:feature` — new capability.
- `type:refactor` — internal change with no user-visible behavior.
- `type:docs` — documentation only.
- `type:build` — Gradle, CI, tooling.
- `type:test` — test-only change.
- `type:security` — CVE or security concern.

## Priority labels

- `priority:critical` — production is broken; drop everything.
- `priority:high` — should be in the next release.
- `priority:medium` — should be in the next few releases.
- `priority:low` — nice to have; no release deadline.

## Domain labels

- `domain:proxy` — the local Jetty proxy server.
- `domain:ai-assistant` — AI Assistant integration.
- `domain:settings` — settings panel and persistence.
- `domain:ui` — tool window, status bar, notifications.
- `domain:service` — OpenRouter API clients.
- `domain:auth` — provisioning keys, API key management.
- `domain:build` — Gradle, CI, release automation.

## Usage notes

- Always add one `status:*`, one `type:*`, and one `domain:*` at minimum.
- `priority:*` is optional; default to unlabeled = medium.
- `needs-triage` is removed once the issue has a type + domain + priority.
- `ready-for-agent` implies the issue has clear acceptance criteria.
