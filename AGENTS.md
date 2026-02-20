# AGENTS.md

## Shizuku Integration Contract

This file is the operating contract for an execution agent implementing native Shizuku integration in `termux-launcher`.

## Mission
Implement full privileged backend integration with Shizuku as primary backend, shell fallback as secondary backend, and no-op backend as final fallback, without regressing launcher behavior.

## Scope Lock
- In scope:
  - Full `PrivilegedBackend` integration for all declared operations.
  - Concrete `ShizukuBackend` implementation.
  - Backend manager selection policy and lifecycle hardening.
  - Lazy permission UX on first privileged action.
  - Status reporting with exact backend and reason.
  - Safe shell bridge contract for future terminal/TUI consumption.
- Out of scope:
  - New launcher UI redesign unrelated to privileged flow.
  - Arbitrary refactors outside privileged/backend paths.
  - Non-essential dependency churn.
- Branch lock:
  - Work only on `shizuku-integration`.

## Product Decisions (Locked)
- Product scope: full backend integration now, not partial.
- Permission UX: lazy prompt on first privileged action.
- Backend selection order: `Shizuku -> Shell(su/rish) -> NoOp`.
- Fallback behavior: status text must expose exact backend and reason.
- Validation policy: requirement-driven for Shizuku integration, not Android-version-specific checklists.

## Decision Policy (Strict)
When a decision is high-impact or ambiguous, do not decide autonomously.

Hard-stop and record blocker in `work-logs/roadblocks.md` for:
- API ambiguity where multiple security-sensitive choices exist.
- Any proposal to allow unrestricted arbitrary privileged command execution from shell/UI.
- Manifest/provider changes with unclear security implications.
- Any behavior that could bypass explicit user permission expectations.
- Any change that weakens fallback determinism or error visibility.

## Safety Rules
- Keep privileged logic centralized under `app/src/main/java/com/termux/privileged/`.
- Prefer typed privileged operations over ad-hoc shell text commands.
- Treat raw `executeCommand()` as high risk and gate it behind explicit policy.
- Never block UI thread on backend initialization or permission calls.
- Never remove shell fallback; Shizuku unavailable must not break app startup.
- Never run destructive git commands (`reset --hard`, force checkout, etc.).

## Mandatory Preflight Per Session
1. Confirm branch is `shizuku-integration`.
2. Scan merge markers in critical paths:
   - `rg -n "<<<<<<<|=======|>>>>>>>" app/src/main app/build.gradle build.gradle settings.gradle gradle.properties`
3. If markers are found in critical paths, file blocker and stop.
4. Confirm baseline files exist:
   - `app/src/main/java/com/termux/privileged/PrivilegedBackend.java`
   - `app/src/main/java/com/termux/privileged/PrivilegedBackendManager.java`
   - `app/src/main/java/com/termux/privileged/ShellBackend.java`
   - `app/src/main/java/com/termux/app/TermuxApplication.java`

## Required Architecture

### 1. Backend State Model
Implement explicit state handling, minimum states:
- `UNINITIALIZED`
- `INITIALIZING`
- `READY`
- `PERMISSION_DENIED`
- `SERVICE_NOT_RUNNING`
- `FALLBACK_SHELL`
- `UNAVAILABLE`

Transitions must be synchronized and deterministic.

### 2. Backend Selection Policy
Use this exact order:
1. Attempt Shizuku backend initialization.
2. If unavailable/denied/not-running, attempt shell backend (`su`, then `rish`).
3. If both fail, use NoOp backend.

No silent backend switching without status update.

### 3. Lazy Permission UX
- Do not prompt during app startup.
- On first privileged operation:
  - If Shizuku binder/service exists and permission is missing, request permission.
  - Resume operation only after permission callback success.
- If denied, expose clear status and fallback behavior.

### 4. Shizuku Provider and Binder Requirements
Provider contract must remain correct in manifest:
- `android:name="rikka.shizuku.ShizukuProvider"`
- `android:authorities="${TERMUX_PACKAGE_NAME}.shizuku"`
- `android:exported="true"`
- `android:multiprocess="false"`
- `android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"`

Binder lifecycle requirements:
- Register binder received/dead listeners.
- Handle binder death by downgrading state and re-evaluating backend.
- Never assume binder remains valid for app lifetime.

### 5. Status Reporting Contract
Expose machine- and human-readable status including:
- Current backend: `SHIZUKU|SHELL|NONE`
- Reason: `granted|denied|service-not-running|binder-dead|fallback-shell|unavailable`
- Permission state and readiness.

## Security Boundary Contract
- Privileged entrypoints must be capability-based, not free-form remote root shell.
- If `executeCommand()` remains exposed, enforce policy checks and caller restrictions.
- Do not log full sensitive command payloads/output by default.
- Use structured failures and safe user-facing messages.
- Any shell bridge for future TUI must consume approved operations/capabilities.

## Implementation Checklist (Execution Order)
1. Add `ShizukuBackend` implementation under `app/src/main/java/com/termux/privileged/`.
2. Wire `PrivilegedBackendManager` to use required selection order and state model.
3. Implement lazy permission request and callback-safe continuation.
4. Harden binder lifecycle handling (received/dead/reconnect behavior).
5. Keep and validate shell fallback behavior (`su` then `rish`).
6. Implement status reason reporting contract.
7. Apply security boundary controls for privileged operation entrypoints.
8. Ensure startup path in `TermuxApplication` is non-blocking and stable.

## Validation Gates (Shizuku Requirement-Driven)

Run after meaningful fixes:
1. `COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:compileDebugJavaWithJavac`
2. `COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:processDebugResources`
3. `COMPILE_SDK_OVERRIDE=34 ./work-logs/build-errors.sh :app:testDebugUnitTest`

Manual functional matrix (required):
- Shizuku running + permission granted.
- Shizuku running + permission denied.
- Shizuku not running.
- Binder dies/restarts while app is active.
- Shell fallback available (`su` or `rish`) with Shizuku unavailable.
- Neither Shizuku nor shell root available (NoOp path).

For each case verify:
- Selected backend type is correct.
- Reason/status text is correct.
- Operation behavior is deterministic.
- No crash/regression in launcher startup or normal use.

## Reporting Contract
For each cycle, append concise notes to `work-logs/agent-log.md`:
- `Cycle: <n>`
- `Task: <checklist item>`
- `Root cause:`
- `Files changed:`
- `Backend selected:`
- `Status reason:`
- `Build result: pass/fail`
- `Manual validation case(s):`
- `Next step:`

Keep entries short and actionable. Do not paste large traces.

## Blocker Recording (Mandatory)
If blocked, append to `work-logs/roadblocks.md` and stop.

Required fields:
- `Timestamp:`
- `Branch + Commit:`
- `Task:`
- `Blocker Type:`
- `Evidence (command + short output):`
- `Why decision needed:`
- `Requested human decision:`

## Completion Criteria
Shizuku integration is complete only when:
- Full backend surface in `PrivilegedBackend` is implemented and wired.
- Selection order and lazy permission policy are functioning.
- Status reason contract is implemented and accurate.
- Shell and NoOp fallbacks remain functional.
- Validation gates pass and manual matrix is completed.
- No launcher regression is introduced.
