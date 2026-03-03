# AGENTS.md

## Launcher Development Contract

This file is the operating contract for launcher/apps-bar work in `termux-launcher`.

## Mission
Build a more intuitive Android launcher experience around the apps bar, without regressing terminal stability.

## Branch Lock
- Work only on `appsbar-dev`.

## Scope
- In scope:
  - Apps bar UX and interaction improvements.
  - App pinning, folders, app-launch discovery, and launcher search behavior.
  - Visual and gesture features directly tied to launcher/apps-bar usage.
  - Tests for launcher behavior and deterministic interaction outcomes.
- Out of scope:
  - Unrelated refactors.
  - Non-essential dependency churn.

## Safety Rules
- Keep launcher logic centralized under `app/src/main/java/com/termux/app/` and related settings/resources.
- Prefer typed models over comma-separated config strings for complex launcher state.
- Never block UI thread for package scans, ranking, or preference migrations.
- Preserve fallback behavior so launcher features degrade safely if data is missing/corrupt.
- Never run destructive git commands (`reset --hard`, force checkout, etc.).

## Mandatory Preflight Per Session
1. Confirm branch is `appsbar-dev`.
2. Scan merge markers:
   - `rg -n "<<<<<<<|=======|>>>>>>>" app/src/main app/build.gradle build.gradle settings.gradle gradle.properties`
3. If markers are found in critical paths, file blocker and stop.
4. Confirm baseline launcher files exist:
   - `app/src/main/java/com/termux/app/TermuxActivity.java`
   - `app/src/main/java/com/termux/app/SuggestionBarView.java`
   - `app/src/main/res/layout/activity_termux.xml`
   - `termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java`

## Reporting Contract
For each meaningful cycle, append concise notes to `work-logs/agent-log.md`:
- `Cycle: <n>`
- `Task:`
- `Root cause:`
- `Files changed:`
- `Build result: pass/fail`
- `Manual validation case(s):`
- `Next step:`

## Blockers
If blocked, append to `work-logs/roadblocks.md` and stop with:
- `Timestamp:`
- `Branch + Commit:`
- `Task:`
- `Blocker Type:`
- `Evidence (command + short output):`
- `Why decision needed:`
- `Requested human decision:`
