# Roadblocks Log

Use this file when execution cannot continue safely without human decision.

## Entry Template
- Timestamp:
- Branch + Commit:
- Task/Checklist Item:
- Blocker Type:
- Evidence (command + short output):
- Why decision needed:
- Requested human decision:

---

## Entries

### Blocker Entry 1
- **Timestamp:** 2026-02-17
- **Branch + Commit:** rebase-upstream-termux @ d879619a
- **Task/Checklist Item:** Phase 2 launcher parity - Preflight check
- **Blocker Type:** Merge conflicts detected
- **Evidence (command + short output):** `rg -n "<<<<<<<|=======|>>>>>>>" app README.md .github` found merge markers in README.md (7 instances)
- **Why decision needed:** Unresolved merge conflicts must be resolved before any launcher parity work can proceed. These conflicts indicate incomplete rebase from upstream that requires human resolution to determine correct content.
- **Requested human decision:** Resolve merge conflicts in README.md to proceed with launcher parity checklist implementation.

### Blocker Entry 2
- **Timestamp:** 2026-02-19 08:05:37 +03
- **Branch + Commit:** rebase-upstream-termux @ d879619a
- **Task/Checklist Item:** Phase 2 launcher parity - Preflight check
- **Blocker Type:** Merge conflicts detected in launcher-critical paths
- **Evidence (command + short output):** `rg -n "<<<<<<<|=======|>>>>>>>" app termux-shared terminal-emulator terminal-view app/build.gradle build.gradle settings.gradle gradle.properties .github` found merge markers in terminal-emulator/src/test/java/com/termux/terminal/*Test.java (multiple files, 16 total conflict markers)
- **Why decision needed:** According to AGENTS.md contract, merge markers in launcher-critical paths (terminal-emulator directory) trigger hard-stop. These unresolved merge conflicts in test files must be resolved before launcher parity work can proceed safely.
- **Requested human decision:** Resolve merge conflicts in terminal-emulator test files (HistoryTest.java, TerminalTestCase.java, ControlSequenceIntroducerTest.java, TerminalTest.java, ResizeTest.java, ScrollRegionTest.java) or confirm test files are exempt from hard-stop policy.

