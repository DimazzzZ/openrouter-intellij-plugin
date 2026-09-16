# Releasing

This project ships to two places: a **GitHub Release** (tag `vX.Y.Z` with the plugin zip attached) and the **JetBrains Marketplace** (plugin id `org.zhavoronkov.openrouter`, marketplace id `28520`). Both are driven by the `Release` workflow in [.github/workflows/release.yml](.github/workflows/release.yml).

## Cutting a new release

1. Bump `pluginVersion` in `gradle.properties` on `main` and update `CHANGELOG.md` / plugin `<change-notes>` as usual.
2. Go to **Actions → Release → Run workflow** on `main`.
3. Fill in the inputs:
   - `version` — the version you set in `gradle.properties` (e.g. `0.6.1`). The workflow's `validate-version` job fails fast if this does not match.
   - `publish_to_marketplace` — leave `true` to publish; set `false` to only cut the GitHub release.
4. Wait for all four jobs (`validate-version`, `build-and-test`, `create-release`, `publish-to-marketplace`) to finish green.

That's the whole normal path. No manual tagging, no manual asset upload, no manual Marketplace form.

## Re-running a release safely

The release workflow is **idempotent**: you can dispatch it again for the same version without deleting the tag, the GitHub release, or anything on the Marketplace. Nothing you can trigger from **Run workflow** will corrupt an existing release.

### What happens when you re-dispatch an already-released version

| Job | Behavior on re-run | Why |
|---|---|---|
| `validate-version` | Runs and passes if `gradle.properties` still matches the input. | Pure validation, always safe. |
| `build-and-test` | Runs and rebuilds the plugin zip. | Deterministic; produces the same artifact. |
| `create-release` | **Updates** the existing GitHub release and **overwrites** the attached `openrouter-intellij-plugin-<version>.zip`. Does not error on the duplicate tag. | Uses `softprops/action-gh-release@v2` with `overwrite_files: true`. |
| `publish-to-marketplace` | Queries the Marketplace API for the version. If it is already published, the sign+publish step is **skipped** and a GitHub Actions notice is emitted; the job still finishes green. If it is not yet published (e.g. a previous run failed after `create-release`), it signs and publishes normally. | JetBrains Marketplace rejects re-uploading an existing version; skipping is the only correct move. |

Concrete step-level outcome for a re-run of an already-published version, from run [35090190819](https://github.com/DimazzzZ/openrouter-intellij-plugin/actions/runs/35090190819):

- `Check if version already on Marketplace` → success — logs `Version <X.Y.Z> already exists on JetBrains Marketplace; skipping publish.`
- `Sign and publish plugin to JetBrains Marketplace` → **skipped**
- `Marketplace publish skipped (version already published)` → success — emits a `::notice::` on the run summary
- `Update release with marketplace link` → **skipped** (the link is already on the release from the first successful publish)

### When a re-run *does* re-publish

The Marketplace guard only skips when the exact version string is already listed in the Marketplace API. It re-publishes in these cases:

- The Marketplace API is unreachable at check time — the guard logs a warning and falls back to attempting publish, so an API outage cannot silently swallow a genuine release.
- A previous run failed *before* the Marketplace publish completed, so the version was tagged/released on GitHub but never uploaded to JetBrains. Re-dispatching finishes the job.

### What a re-run cannot do

Bumping the shipped code without bumping the version. JetBrains treats a Marketplace version as immutable once published, and this workflow correctly refuses to fight that. If you need to ship new bits, bump `pluginVersion` and cut a new release — that is the only supported way for users to receive an update.

## Related files

- [.github/workflows/release.yml](.github/workflows/release.yml) — the workflow itself.
- `gradle.properties` — `pluginVersion` is the source of truth for the version string.
- `build.gradle.kts` — `signPlugin` / `publishPlugin` configuration consumed by the workflow.
