# CLAUDE.md — Agent Onboarding

You are working on the **openrouter-intellij-plugin**, a JetBrains IDE plugin
that provides OpenRouter API integration and a local proxy for AI Assistant.

Before making changes:

1. **Read the domain glossary** — `docs/agents/domain.md` defines the
   ubiquitous language. Use these terms exactly in code, tests, and commits.
2. **Check for ADRs** — `docs/adr/*.md` records accepted architectural
   decisions. Do not violate them; propose a new ADR to supersede one instead.
3. **Read TESTING.md** — the test taxonomy is strict. Every new test must be
   tagged (unit / functional / platformTest) and go into the right task.
4. **Read DEVELOPMENT.md** — build, run, and debug workflows. Use
   `./scripts/fast-build.sh test` for the day-to-day loop.

## Local scratchpad

If you're doing multi-turn work, use `.scratch/<feature>/` per
`docs/agents/issue-tracker.md`. This directory is gitignored — it's your
local memory.

## Triage labels

When creating or triaging GitHub issues, use the canonical label vocabulary
in `docs/agents/triage-labels.md`. Every issue should have a status, type,
and domain label at minimum.

## Style

- Kotlin, 4-space indent, 120-column wrap (see `config/detekt/detekt.yml`).
- No non-public IntelliJ platform APIs (see ADR-0002). If a compilation
  requires one, stop and file an issue for maintainer discussion.
- Prefer public alternatives; if none exists, add a `@Suppress` with a
  detailed justification and a linked issue.

## Commits

- One logical change per commit.
- Conventional prefixes: `feat:`, `fix:`, `build:`, `test:`, `docs:`,
  `refactor:`, `ci:`, `style:`, `chore:`.
- Reference issues in the body, not the subject line.
