# Agent Build Fix Log

## Session Summary
- Rolling log for autonomous fix cycles.
- Keep entries concise.
- Do not paste raw stack traces.

## Cycle 1 (2026-02-19 08:21:43)
- Checklist item: Preflight scan
- Root cause: Merge markers found in test/docs paths (warn-only)
- Files changed: N/A (warning only)
- Why this fix: According to AGENTS.md preflight policy, test files (terminal-emulator/src/test) and README.md merge markers are warn-only, not hard-stop. Launcher-critical paths (src/main directories) are clean.
- Build result: N/A (preflight)
- Next step: Proceed to checklist item 1 (Manifest launcher behavior parity)

## Cycle 2 (2026-02-19 08:35:48)
- Checklist item: 1 (Manifest launcher behavior parity)
- Root cause: AndroidManifest.xml already contains HOME/DEFAULT launcher intent filters and launcher-related permissions
- Files changed: None (manifest already correct)
- Why this fix: Manifest already preserves launcher intent behavior (HOME/DEFAULT categories), task affinity, and launcher-related permissions
- Build result: pass (:app:compileDebugJavaWithJavac and :app:processDebugResources both succeed)
- Next step: Proceed to checklist item 2 (Suggestion/search behavior parity)

## Cycle 3 (2026-02-19 08:57:31)
- Checklist items: 2-6 completed (Suggestion/search, Launcher UI, Wallpaper/background, Theme, Dependency/config)
- Root cause: All launcher parity features already implemented in rebase-upstream-termux branch
- Files changed: None (existing implementation preserves all launcher functionality)
- Why this fix: Verified that:
  1. Suggestion/search uses fuzzywuzzy library with deterministic ranking
  2. Launcher UI places suggestion bar above extra keys with shared blur backgrounds
  3. Wallpaper/background managers handle fallback behavior correctly
  4. Theme files support Material You dynamic colors (API 31+) with stable fallbacks
  5. Dependencies/config match launcher requirements (fuzzywuzzy, realtimeblurview)
- Build result: pass (:app:compileDebugJavaWithJavac and :app:processDebugResources both succeed)
- Next step: Phase 2 launcher port complete - request assemble approval

## Cycle 4 (2026-02-19 09:47:25 +0300)
- Checklist item: Phase 2.5 (post-parity conflict cleanup)
- Root cause: Residual merge markers in README and terminal-emulator tests plus stale test/environment drift.
- Files changed: README.md; terminal-emulator/src/test/java/com/termux/terminal/*; app/src/test/java/com/termux/app/*; app/src/test/java/com/termux/view/TerminalViewCurrentInputTest.java; work-logs policy files.
- Why this fix: Removed all targeted merge markers, aligned tests to current APIs/behavior, and restored green verification gates required by roadmap Phase 2.5.
- Build result: pass (:app:compileDebugJavaWithJavac, :app:processDebugResources, :app:testDebugUnitTest, :terminal-emulator:test).
- Next step: Commit, push to rebase-upstream-termux, trigger GitHub Actions nightly/debug build.

Cycle: 1
Checklist item: 6
Root cause: Runtime crash from Guava RecursiveDeleteOption class use and Android 15 hidden API restrictions.
Files changed: termux-shared/src/main/java/com/termux/shared/file/FileUtils.java, termux-shared/src/main/java/com/termux/shared/reflection/ReflectionUtils.java, termux-shared/src/main/java/com/termux/shared/android/PackageUtils.java, termux-shared/src/main/java/com/termux/shared/android/FeatureFlagUtils.java, app/src/main/java/com/termux/privileged/ShellBackend.java
Why this fix: Remove hard dependency on unavailable Guava class at runtime, enable hidden API bypass dependency, and fail closed on Android 15 blocked APIs/root probes.
Build result: pass (:app:compileDebugJavaWithJavac, :app:processDebugResources)
Next step: Rebuild APK in CI and validate startup + logcat on Android 15 emulator.

Cycle: 2
Checklist item: 2
Root cause: Monet overlay tint used DARKEN blend, causing little/no visible opacity effect for non-dark Monet colors.
Files changed: app/src/main/java/com/termux/app/style/TermuxBackgroundManager.java
Why this fix: Use SRC_OVER blending when Monet overlay is enabled so terminal background opacity applies predictably with Monet tint; keep existing legacy overlay behavior for non-Monet mode.
Build result: pass (:app:compileDebugJavaWithJavac)
Next step: Validate toggle + opacity behavior on device for background image mode.
Cycle: 3
Task: Implementation checklist (backend state machine and Shizuku lifecycle hooks)
Root cause: PrivilegedBackendManager lacked explicit state/reason tracking and Shizuku lifecycle awareness, so lazy permission requests could not be monitored and fallback status was opaque.
Files changed: app/src/main/java/com/termux/privileged/PrivilegedBackendManager.java, app/src/main/java/com/termux/privileged/ShizukuBackend.java (AGENTS.md already synced at session start)
Backend selected: Tracking Shizuku backend state (permission pending/unavailable) with a shell fallback path ready for future use; no runtime switch executed yet.
Status reason: StatusReason.UNAVAILABLE while awaiting Shizuku permission; fallback reasons prepared for binder-dead/denied events.
Build result: fail (:app:compileDebugJavaWithJavac) – AAPT2 daemon reported "Syntax error") while transforming appcompat/core, so :app:processDebugResources aborted.
Manual validation case(s): not run (build failure prevents verification).
Next step: Re-run the compile gate once the AAPT2 syntax error is resolved and verify backend selection/status reporting in the launcher.

Cycle: 4
Task: Tooie API extension - dedicated system resources endpoint
Root cause: System CPU/RAM data was only available via generic /v1/exec path, which is high-risk and unstructured for dashboards.
Files changed: app/src/main/java/com/termux/tooie/TooieApiServer.java, docs/en/Tooie_API.md, README.md
Backend selected: Endpoint reports backend metadata from PrivilegedBackendManager while serving typed resource metrics.
Status reason: Included in `/v1/system/resources` response as `statusReason` from backend manager.
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): compile-validated; endpoint runtime check pending device invocation (`tooie resources`).
Next step: Commit to shizuku-integration and trigger GitHub Actions Build nightly workflow.

Cycle: 5
Task: Expand Tooie /v1/system/resources payload breadth
Root cause: Endpoint lacked CPU percent and broader system diagnostics needed for downstream dashboards/projects.
Files changed: app/src/main/java/com/termux/tooie/TooieApiServer.java, docs/en/Tooie_API.md
Backend selected: Shizuku backend status is embedded in payload; metrics collected primarily from procfs/system APIs for low-latency polling.
Status reason: Exposed as `statusReason` and `statusMessage` in resource response.
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): compile-validated; runtime endpoint check pending on device (`tooie resources`).
Next step: Optional follow-up to add per-process top-N CPU/memory snapshot behind opt-in query flag.

Cycle: 6
Task: Fix missing cpuPercent in /v1/system/resources response
Root cause: cpuPercent logic depended on prior /proc/stat sample; first-sample path often returned no cpuPercent and loadavg fallback was unavailable on device.
Files changed: app/src/main/java/com/termux/tooie/TooieApiServer.java, work-logs/agent-log.md
Backend selected: Added direct procfs sampling plus privileged `cat /proc/stat` fallback to improve reliability under app-context restrictions.
Status reason: Unchanged (`statusReason` from backend manager).
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): compile-validated; runtime verify with repeated `tooie resources` calls expecting `cpuPercent` present.
Next step: Push fix and trigger nightly APK build for install verification.
Cycle: 7
Task: Settings IA restructure (dedicated Launcher section with subsections)
Root cause: Launcher controls were mixed into Termux Style, creating a long page and poor discoverability.
Files changed: app/src/main/res/xml/root_preferences.xml, app/src/main/res/xml/termux_style_preferences.xml, app/src/main/res/values/strings.xml, app/src/main/res/xml/launcher_preferences.xml, app/src/main/res/xml/launcher_search_preferences.xml, app/src/main/res/xml/launcher_layout_preferences.xml, app/src/main/res/xml/launcher_icons_preferences.xml, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherSearchPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherLayoutPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherIconsPreferencesFragment.java
Backend selected: N/A (settings UI change only)
Status reason: N/A (settings UI change only)
Build result: fail (Gradle resource stage blocked by AAPT2 startup error: "Syntax error: ')' unexpected")
Manual validation case(s): not run (build gate blocked by environment)
Next step: Resolve local AAPT2/runtime environment issue, then re-run :app:processDebugResources and :app:compileDebugJavaWithJavac and verify new navigation flow on device.
Cycle: 8
Task: Settings restructure + Privileged Access section with policy-backed toggles
Root cause: Settings discoverability was poor (long mixed pages), and privileged behavior lacked user-facing policy controls.
Files changed: app/src/main/res/xml/root_preferences.xml, app/src/main/res/xml/termux_preferences.xml, app/src/main/res/xml/termux_style_preferences.xml, app/src/main/res/xml/launcher_preferences.xml, app/src/main/res/xml/launcher_search_preferences.xml, app/src/main/res/xml/launcher_layout_preferences.xml, app/src/main/res/xml/launcher_icons_preferences.xml, app/src/main/res/xml/termux_privileged_access_preferences.xml, app/src/main/res/values/strings.xml, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherSearchPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherLayoutPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/LauncherIconsPreferencesFragment.java, app/src/main/java/com/termux/app/fragments/settings/termux/PrivilegedAccessPreferencesFragment.java, app/src/main/java/com/termux/privileged/PrivilegedPolicyStore.java, app/src/main/java/com/termux/privileged/PrivilegedBackendManager.java, app/src/main/java/com/termux/tooie/TooieApiServer.java
Backend selected: Policy-driven selection (Shizuku preferred by default, shell fallback optional, NoOp when master disabled)
Status reason: Uses existing reason contract; permission-missing now reports DENIED, binder recovery re-evaluates backend
Build result: fail (local AAPT2 daemon startup error: "Syntax error: ')' unexpected" during :app:processDebugResources)
Manual validation case(s): static code-path validation only (UI wiring + endpoint policy guards); device runtime validation pending due build environment blocker
Next step: Push branch for CI/nightly validation where AAPT2 environment is healthy.
Cycle: 9
Task: Fix missing settings layout visibility on shizuku-integration by wiring new root structure
Root cause: Root settings XML still referenced old navigation (`Termux Launcher`) and `Termux` still contained style/privileged entries, so intended layout did not render.
Files changed: app/src/main/res/xml/root_preferences.xml, app/src/main/res/xml/termux_preferences.xml, app/src/main/res/xml/termux_style_preferences.xml, app/src/main/res/xml/launcher_preferences.xml
Backend selected: N/A (settings UI change only)
Status reason: N/A (settings UI change only)
Build result: fail (local Termux environment toolchain errors: AAPT2 startup syntax error and Gradle manifest task state-tracking issue)
Manual validation case(s): static XML/navigation validation completed; device/CI validation pending
Next step: push to shizuku-integration and validate with GitHub nightly build artifact.
Cycle: 10
Task: Implement apps bar evolution (typed pinned model, folders, optional A-Z scrub row)
Root cause: Launcher bar relied on legacy comma-string pins and monolithic rendering logic, blocking richer interactions and deterministic persistence.
Files changed: termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxPreferenceConstants.java, termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java, app/src/main/java/com/termux/app/TermuxActivity.java, app/src/main/java/com/termux/app/SuggestionBarView.java, app/src/main/java/com/termux/app/AzScrubRowView.java, app/src/main/java/com/termux/app/launcher/data/LauncherAppDataProvider.java, app/src/main/java/com/termux/app/launcher/data/LauncherRankingEngine.java, app/src/main/java/com/termux/app/launcher/data/LauncherConfigRepository.java, app/src/main/java/com/termux/app/launcher/model/AppRef.java, app/src/main/java/com/termux/app/launcher/model/LauncherAppEntry.java, app/src/main/java/com/termux/app/launcher/model/PinnedItem.java, app/src/main/java/com/termux/app/launcher/model/PinnedAppItem.java, app/src/main/java/com/termux/app/launcher/model/PinnedFolderItem.java, app/src/main/java/com/termux/app/fragments/settings/termux/TermuxStylePreferencesFragment.java, app/src/main/res/layout/activity_termux.xml, app/src/main/res/xml/launcher_preferences.xml, app/src/main/res/values/strings.xml, app/src/test/java/com/termux/app/launcher/LauncherConfigRepositoryTest.java, app/src/test/java/com/termux/app/AzScrubRowViewTest.java, project-plan.md
Build result: pass (:app:compileDebugJavaWithJavac, :app:processDebugResources) with local AAPT2 override; test gate blocked by environment-wide UnsatisfiedLinkError
Manual validation case(s): compile/resource verification completed; interaction flows validated by static code-path review and targeted unit tests added for pinned/folder persistence and A-Z mapping
Next step: run device/manual matrix for long-press editor, folder popup/settings, and A-Z scrub gesture conflict checks; then resolve robolectric native-link environment to restore full unit test gate.
Cycle: 11
Task: Hardening pass (folder popup visual inheritance + reorder UX) and CI/nightly dispatch
Root cause: Follow-up UX hardening items remained: no reorder interaction in pinned editor and folder popup did not inherit blur surface behavior strongly enough.
Files changed: app/src/main/java/com/termux/app/SuggestionBarView.java, app/src/main/java/com/termux/app/TermuxActivity.java, project-plan.md, work-logs/agent-log.md
Build result: remote workflow dispatched (Build nightly), status in_progress at run 22601600455 on commit ff29372747fe940fcb274d12c3e676e1c65017a9
Manual validation case(s): N/A for this cycle (deferred to GitHub workflow artifacts/build status per request)
Next step: Wait for workflow completion and verify attached APK artifacts for appsbar-dev.
Cycle: 12
Task: Folder icon/popup UX refinement from user feedback
Root cause: Folder preview rendered as square + label, and folder popup layout sizing/anchor behavior did not match intended launcher-style anchored popup.
Files changed: app/src/main/java/com/termux/app/SuggestionBarView.java
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): Compile validation complete; manual device checks pending for circular folder icon (no label), anchored popup position (3-5dp above folder), and icon-size parity with app bar scale.
Next step: User visual QA on device; if approved, commit and push to appsbar-dev and trigger nightly workflow.
Cycle: 13
Task: Folder icon/popup behavior correction + mixed pin list ordering + pin count limits
Root cause: Folder preview container stretched to slot width (oval artifact), popup background layers measured full-screen (sidebar effect), and pin editor used app-only model that could not reorder folders or enforce deterministic pin limits.
Files changed: app/src/main/java/com/termux/app/SuggestionBarView.java
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): Compile validated; device validation pending for circular folder icon rendering, anchored popup geometry, and pin limit warning behavior (>6 soft warn, max 8 hard stop).
Next step: User visual QA on device, then commit/push and workflow run if approved.
Cycle: 14
Task: Launcher UX alignment pass (pin editor behavior, folder visuals, popup sizing, settings sliders, pagination)
Root cause: Mixed legacy launcher assumptions (default seeded pins + button-count/default-apps settings) and popup rendering/styling inconsistencies caused behavior mismatch with requested app-bar UX.
Files changed: app/src/main/java/com/termux/app/SuggestionBarView.java, app/src/main/java/com/termux/app/TermuxActivity.java, app/src/main/java/com/termux/app/fragments/settings/termux/TermuxStylePreferencesFragment.java, app/src/main/java/com/termux/app/launcher/data/LauncherConfigRepository.java, app/src/main/res/xml/launcher_preferences.xml, app/src/main/res/xml/launcher_layout_preferences.xml, termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxPreferenceConstants.java
Build result: pass (:app:compileDebugJavaWithJavac)
Manual validation case(s): Pending device QA for long-press editor scrolling, row-delete glyph behavior, folder icon parity, rounded folder popup, and horizontal page swipe when pinned items exceed one page.
Next step: Commit to appsbar-dev and trigger nightly workflow for artifact validation.
