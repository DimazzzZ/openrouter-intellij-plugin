# JetBrains Marketplace Submission Checklist

This checklist aligns with the **JetBrains Marketplace Approval Guidelines v1.3 (March 31, 2026)** and should be completed before every plugin submission.

## 1. Plugin Metadata (Guideline 1.x)

- [ ] **Plugin name** follows naming conventions (no "JetBrains", no misleading terms)
- [ ] **Plugin name length** is reasonable (displayable in UI without truncation)
- [ ] **Vendor name** is present and valid (individual or organization)
- [ ] **Plugin ID** is unique and follows reverse-DNS convention (`org.zhavoronkov.openrouter`)
- [ ] **Version** follows semantic versioning
- [ ] **Description** is substantial, in English, and describes functionality clearly
- [ ] **Change notes** are present and describe what's new in this version
- [ ] **Plugin icon** is custom (not default) and renders properly at all sizes

## 2. Technical Requirements (Guideline 2.x)

- [ ] **Plugin Verifier** passes without critical errors (`./gradlew verifyPlugin`)
- [ ] **No internal API usage** — plugin does not use `com.intellij.internal.*`, `com.intellij.ide.impl.*`, or `@ApiStatus.Internal` APIs (except where explicitly allowed)
- [ ] **Plugin is functional** — core features work as described
- [ ] **No crashes on startup** — plugin loads without exceptions
- [ ] **Thread safety** — no EDT violations, long-running operations run in background
- [ ] **Resource cleanup** — disposables are properly managed, no memory leaks
- [ ] **No bundled third-party libraries with incompatible licenses**

## 3. Legal & Privacy (Guideline 3.x)

- [ ] **Privacy Policy** is available and linked (see `PRIVACY_POLICY.md`)
- [ ] **EULA / Terms of Service** is available and linked (see `EULA.md`)
- [ ] **External links** in plugin.xml and README are valid and accessible
- [ ] **No trademark violations** — third-party names/brands used appropriately with disclaimers
- [ ] **User data handling** is documented and minimal
- [ ] **No hidden telemetry** — any data collection is disclosed and user-controlled

## 4. Quality & UX (Guideline 4.x)

- [ ] **Settings UI** is accessible and functional
- [ ] **Error messages** are user-friendly and actionable
- [ ] **No spammy notifications** — notifications are relevant and non-intrusive
- [ ] **Plugin doesn't block IDE** — no UI freezes or blocking operations on EDT
- [ ] **Documentation** is available (README, docs folder)

## 5. Pre-Submission Verification

- [ ] [ ] Run `./gradlew build` — passes without errors
- [ ] Run `./gradlew test` — all tests pass
- [ ] Run `./gradlew detekt` — no critical issues
- [ ] Run `./gradlew verifyPlugin` — no compatibility issues
- [ ] Check all URLs in `plugin.xml` and `README.md` are accessible
- [ ] Verify plugin installs and loads in target IDE version
- [ ] Test core functionality manually in target IDE

## 6. Marketplace Listing

- [ ] **Plugin name** is set correctly
- [ ] **Description** is substantial and English-first
- [ ] **Change notes** describe this version's changes
- [ ] **Screenshots** are high quality and show actual plugin UI
- [ ] **Video preview** (optional) demonstrates key features
- [ ] **Tags** are relevant and accurate
- [ ] **Category** is appropriate (e.g., "AI", "Integration")
- [ ] **License** is specified (MIT)
- [ ] **Source code URL** points to GitHub repository
- [ ] **Privacy Policy URL** points to `docs/PRIVACY_POLICY.md`
- [ ] **EULA / Terms URL** points to `docs/EULA.md`

## 7. Post-Submission

- [ ] Monitor JetBrains Marketplace review status
- [ ] Respond to any reviewer questions within 48 hours
- [ ] If rejected, address feedback and resubmit
- [ ] If approved, verify listing displays correctly

---

**Last updated:** April 9, 2026  
**Guidelines version:** v1.3 (March 31, 2026)
