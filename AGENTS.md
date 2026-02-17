# AGENTS.md

## Goal
Run an autonomous fix loop for this Android project in Termux:
1. Build
2. Read error logs
3. Apply minimal code/build fix
4. Rebuild
5. Repeat until green or blocked

## Environment (Termux-specific)
- Project root: `/data/data/com.termux/files/home/files/android-dev/termux-launcher`
- Java and Gradle wrapper are available.
- Use ARM `aapt2` override automatically through `work-logs/build-errors.sh`.
- Use `COMPILE_SDK_OVERRIDE=34` for local Termux builds unless explicitly told otherwise.

## Required Build Command
Always run builds through:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:assembleDebug
```

Notes:
- This rewrites `work-logs/build-error.log` and `work-logs/build-last.log` every run.
- `work-logs/build-error.log` contains filtered failure lines.
- `work-logs/build-last.log` contains full stacktrace/details.

## Fast Loop (Default)
Use this as the default inner loop for quicker iterations:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:compileDebugJavaWithJavac
```

When Java compile passes, run a resource check:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:processDebugResources
```

Only after both pass, ask user before full assemble.

## Full Assemble Confirmation Rule
Before running full APK build, agent must prompt user and wait for approval:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:assembleDebug
```

Do not run full assemble automatically.

## Loop Protocol
On each iteration:

1. Run fast loop command first:
   - `:app:compileDebugJavaWithJavac`
2. If compile passes, run:
   - `:app:processDebugResources`
3. If exit code is `0`: stop and report success.
4. If exit code is non-zero:
   - Parse `work-logs/build-error.log` first.
   - Cluster repeated errors by root cause.
   - Fix the highest-leverage root cause first.
   - Prefer one-shot fixes for repeated patterns.
5. Re-run the same build command.
6. When both fast checks pass, ask user if full assemble should run.
7. Continue until success or hard blocker.

## Error Triage Rules
- Treat the first real compiler/resource failure as primary.
- Ignore repeated downstream “Cause 2..N” noise once root cause is known.
- Prioritize in this order:
  1. Syntax/merge-conflict/parsing failures
  2. Missing symbols/imports/dependencies
  3. Resource/linking issues
  4. Packaging/signing/lint issues

## Edit Rules
- Make the smallest valid change that resolves the current cluster.
- Do not refactor unrelated code.
- Keep existing project style and behavior.
- Do not remove functionality to silence errors.
- Never use destructive git commands (`reset --hard`, force checkout, etc.).
- Do not replace production classes with stubs/mocks just to make compile pass unless user explicitly approves.
- Do not comment out feature code paths (service calls, backend wiring, native config) unless user explicitly approves.
- If a fix degrades behavior, stop and ask user before applying.

## Functional Safety Gate
- A build is considered "fixed" only if compilation passes **without intentional feature disablement**.
- Treat these as temporary workarounds requiring user approval:
  - Disabling/removing privileged backend implementations.
  - Commenting out app/runtime behavior calls.
  - Removing native build blocks when module expects native sources.
- Preferred order for fixes:
  1. Correct dependency coordinates/versions/repositories.
  2. Align method signatures and constructor calls with current APIs.
  3. Fix source conflicts/syntax/import issues.
  4. Apply guarded fallback only with explicit user confirmation.

## Verification Rules
- After each fix, re-run:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:compileDebugJavaWithJavac
```

- If compile passes, then run:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:processDebugResources
```

- If still failing, continue loop with new top error cluster.
- If both pass, prompt user before:

```sh
COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:assembleDebug
```

## Tooling Strategy (Crush TUI)
- Prefer `filesystem` tools (`glob/read/patch`) for code and logs.
- Use `sequential-thinking` before risky or cross-file edits.
- Use `context7` when framework/library API behavior is uncertain.
- Use `github` tools only for repo/PR operations, not local-only debugging.
- Keep tool outputs summarized; do not paste large logs verbatim.

## Rolling Agent Log
- Maintain a rolling activity log at:
  - `termux-launcher/work-logs/agent-log.md`
- Update it after each fix+verify cycle.
- After each update, run:
  - `./work-logs/trim-agent-log.sh`
- Keep entries concise and structured:
  - `Cycle #`
  - `Root cause`
  - `Files changed`
  - `Decision`
  - `Build result`
  - `Next step`
- Hard size limits to control context usage:
  - Max `120` lines total
  - Max `12` recent cycles retained
- If limit is exceeded:
  - Preserve a short `Session Summary` at top (5-10 lines)
  - Drop oldest cycle entries first
  - Never paste raw stack traces into `work-logs/agent-log.md`

## MCP Debugging Policy
- Use MCP tools when local errors involve external APIs/libraries or unclear behavior.
- Prefer `context7` for authoritative API references before speculative code edits.
- For this project, prioritize context pull for:
  - Termux/termux-app related APIs and build patterns
  - Shizuku APIs, package names, and integration changes
- MCP usage rules:
  - Query only what is needed for the current error cluster.
  - Summarize findings in 2-5 lines inside the current cycle log entry.
  - Convert findings into exact local edits; avoid broad refactors.

## Reporting Format (each cycle)
- `Root cause: ...`
- `Files changed: ...`
- `Why this fix: ...`
- `Build result: pass/fail`
- `Next error (if any): ...`

## Blocker Handling
If blocked by environment/tooling (not code), report clearly with exact command(s):
- Missing SDK/NDK/package
- Broken local binary/toolchain
- Permission/storage/path issues

Then stop and wait for user action.
