# AGENTS.md

## Launcher Port Rebase Contract

This file is the operating contract for a less-capable execution agent working on launcher feature parity.

## Mission
Port launcher-specific behavior from `main` into `rebase-upstream-termux` without contaminating architecture or silently degrading functionality.

## Scope Lock
- In scope: Phase 2 from `project-roadmap.md` (launcher parity only).
- Out of scope: Shizuku integration, backend redesign, unrelated refactors.
- Branch lock: work only on `rebase-upstream-termux`.

## Decision Policy (Strict)
When a decision is high-impact or ambiguous, do not decide autonomously.

Hard-stop and record a blocker in `work-logs/roadblocks.md` for:
- API/architecture ambiguity across multiple valid approaches.
- Any change that disables, removes, or bypasses launcher functionality.
- Dependency swaps or version shifts not already used in this repo.
- Manifest behavior changes that affect launcher/home semantics.
- Risky conflict resolution where intent is unclear.

## Safety Rules
- Do the smallest parity-preserving change possible.
- Do not refactor unrelated code.
- Do not use stubs/mocks to fake completion.
- Do not comment out features to make builds pass.
- Never run destructive git commands (`reset --hard`, force checkout, etc.).
- If unsure, file blocker and stop.

## Mandatory Preflight Per Session
1. Confirm branch is `rebase-upstream-termux`.
2. Scan for merge markers before editing:
   - Launcher-critical paths (hard stop):
     - `rg -n "<<<<<<<|=======|>>>>>>>" app/src/main termux-shared/src/main terminal-emulator/src/main terminal-view/src/main app/build.gradle build.gradle settings.gradle gradle.properties .github`
   - Test/docs/non-critical paths (warn only):
     - `rg -n "<<<<<<<|=======|>>>>>>>" app/src/test termux-shared/src/test terminal-emulator/src/test terminal-view/src/test README.md docs project-docs`
3. If markers appear in launcher-critical paths, file blocker in `work-logs/roadblocks.md` and stop.
4. If markers appear only in test/docs/non-critical paths, append a warning note to `work-logs/agent-log.md` and continue Phase 2 work.

## Single Approved Build Wrapper
Use only:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh <gradle-task>
```

Default loop tasks:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:compileDebugJavaWithJavac
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:processDebugResources
```

Full assemble is approval-gated:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:assembleDebug
```

Do not run full assemble unless explicitly approved.

## Launcher Parity Checklist (Execution Order)
Complete in this order and mark progress in cycle reports.

1. Manifest launcher behavior parity
   - File: `app/src/main/AndroidManifest.xml`
   - Preserve HOME/DEFAULT launcher intent behavior and launcher entry semantics.
   - Preserve launcher-related permissions and task behavior used by this fork.

2. Suggestion/search behavior parity
   - Files:
     - `app/src/main/java/com/termux/app/SuggestionBarView.java`
     - `app/src/main/java/com/termux/app/TermuxActivity.java`
     - `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java`
   - Preserve terminal-driven input extraction and deterministic fuzzy ranking behavior.
   - Preserve app launch action from suggestions.

3. Launcher UI + extra-keys integration parity
   - Files:
     - `app/src/main/res/layout/activity_termux.xml`
     - `app/src/main/res/layout/suggestion_bar.xml`
   - Preserve suggestion bar placement above extra keys and shared blur/background surface behavior.

4. Wallpaper/background parity
   - Files:
     - `app/src/main/java/com/termux/app/style/TermuxBackgroundManager.java`
     - `app/src/main/java/com/termux/app/style/TermuxSystemWallpaperManager.java`
   - Preserve preferred path and fallback behavior for wallpaper setting without crashes.

5. Theme and dynamic color parity
   - Files:
     - `app/src/main/res/values/themes.xml`
     - `app/src/main/res/values-night/themes.xml`
     - `app/src/main/res/values-v31/themes.xml`
     - `app/src/main/res/values-night-v31/themes.xml`
   - Preserve Material You dynamic color behavior on API 31+ and stable fallback themes.

6. Dependency/config parity
   - Files:
     - `app/build.gradle`
     - `gradle.properties`
   - Keep launcher-required dependencies/config aligned with existing fork behavior.

## Error Triage Order
Prioritize root cause in this order:
1. Syntax/merge conflicts/parsing
2. Missing symbols/imports/dependencies
3. Resource/linking
4. Packaging/signing/lint

Always read `work-logs/build-error.log` first, then `work-logs/build-last.log` for details.

## Verification Gates
After each meaningful fix:
1. Run `:app:compileDebugJavaWithJavac`
2. If pass, run `:app:processDebugResources`
3. If both pass, report status and ask for assemble approval
4. If any fail, continue on top root-cause cluster only

A change is not accepted if it passes build by disabling launcher features.

## Reporting Contract
For each cycle, append concise notes to `work-logs/agent-log.md`:
- `Cycle: <n>`
- `Checklist item: <1..6>`
- `Root cause: ...`
- `Files changed: ...`
- `Why this fix: ...`
- `Build result: pass/fail`
- `Next step: ...`

Rules:
- Keep entries short.
- Do not paste raw stack traces.
- Use `work-logs/build-last.log` as the detailed trace source.

## Blocker Recording (Mandatory)
If blocked, append an entry to `work-logs/roadblocks.md` and stop. Do not continue with speculative fixes.

Required entry fields:
- `Timestamp:`
- `Branch + Commit:`
- `Task/Checklist Item:`
- `Blocker Type:`
- `Evidence (command + short output):`
- `Why decision needed:`
- `Requested human decision:`

## Completion Criteria (Phase 2)
Phase 2 launcher port is complete only when:
- Checklist items 1-6 are complete.
- Fast verification commands pass.
- No intentional feature disablement exists.
- Remaining issues are either resolved or explicitly logged as blockers for human decision.
