# Local Issue Tracker

For internal, in-flight work that doesn't warrant a public GitHub issue,
use `.scratch/<feature>/` as a local tracker.

## Layout

```
.scratch/
  feature-name/
    README.md           # what/why, one paragraph
    NOTES.md            # rolling notes, timestamped
    tasks.md            # bulleted checklist of what's done/pending
    <feature>.md        # design or investigation doc
```

## Rules

- **Never commit .scratch/** — it's in `.gitignore`. Local-only.
- **One directory per feature** — don't dump everything in a shared file.
- **Prefer markdown** — grep-friendly, no formatting deps.
- **Timestamp notes** — `## [YYYY-MM-DD HH:MM] event` for the notes log.
- **Promote to GitHub when the work becomes public-facing** — once it's on the
  roadmap or has stakeholders outside the maintainer, open an issue and link
  to it from README.md.

## When to use vs GitHub issues

Use `.scratch/` when:

- You're exploring a design and don't want half-formed thoughts in public.
- You're tracking your own to-do for a multi-day investigation.
- You're capturing notes an AI agent might need to pick up next session.

Use GitHub issues when:

- A user or contributor should be able to comment on it.
- It's ready for review or handoff.
- It represents work that will affect a release.

## For AI agents

If an agent is spawned on a topic that has a `.scratch/<feature>/` directory,
the agent should read the README.md and tasks.md first, then append to NOTES.md
with a fresh `## [YYYY-MM-DD HH:MM] agent: <summary>` entry describing what
it did.
