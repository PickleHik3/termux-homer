# Apps Bar Evolution Project Plan

## Milestone 1: Pinned Apps Editor
- [x] Add typed pinned model and storage (`PinnedItem = App | Folder`).
- [x] Add V2 pinned-items preference keys and schema versioning.
- [x] Add migration fallback path from legacy comma string pins.
- [x] Rewire apps bar to read/write pinned structure from repository.
- [x] Implement long-press on empty slot to open pinned-app editor.
- [x] Implement bottom sheet to select/deselect pinned apps.
- [x] Persist ordered selection to structured storage.
- [x] Keep legacy default-buttons behavior as fallback.

## Milestone 2: Folders in Pinned Area
- [x] Add `PinnedFolderItem` model (id, title, rows/cols, tint override, apps).
- [x] Add long-press actions on pinned app: replace, unpin, move to folder, create folder.
- [x] Add long-press actions on folder: open, edit apps, settings, unpin.
- [x] Render folders as single pinned items.
- [x] Open folder popup grid on folder tap.
- [x] Add folder gear/settings flow for rows/cols and tint override.
- [x] Persist folder composition and settings.
- [x] Inherit app bar opacity by default when folder tint override is disabled.

## Milestone 3: A-Z Scrub Row (Optional Advanced)
- [x] Add A-Z strip view between apps bar and extra keys row.
- [x] Add preference flag (`app_launcher_az_row_enabled`) with default off.
- [x] Add scrub gesture handling (horizontal letter + vertical selection depth).
- [x] Add preview + commit integration with apps bar suggestions.
- [x] Add cancel handling to restore previous suggestions.
- [x] Guard visibility through toolbar and feature-toggle state.

## Architecture Refactor
- [x] Introduce launcher data provider (`LauncherAppDataProvider`) for app discovery and letter buckets.
- [x] Introduce ranking/filter engine (`LauncherRankingEngine`) for deterministic ranking.
- [x] Introduce config repository (`LauncherConfigRepository`) for typed persistence/migration.
- [x] Move apps-bar orchestration logic from raw TEL-like path into typed controller flow in `SuggestionBarView`.

## Tests
- [x] Add Robolectric test for pinned app persistence ordering.
- [x] Add Robolectric test for folder composition/settings persistence.
- [x] Add Robolectric test for A-Z scrub letter/selection mapping determinism.
- [ ] Stabilize unit test runtime environment for native library loading (`UnsatisfiedLinkError`) and rerun full suite.

## Validation Gates
- [x] `:app:compileDebugJavaWithJavac` (with local aapt2 override)
- [x] `:app:processDebugResources` (with local aapt2 override)
- [ ] `:app:testDebugUnitTest` (blocked by environment-wide `UnsatisfiedLinkError` in existing + new Robolectric tests)

## Follow-up Tasks
- [x] Add drag-reorder UX inside pinned bottom sheet.
- [x] Add dedicated visual blur container for folder popup.
- [ ] Add UI color picker widget for folder tint (currently hex input in settings dialog).
