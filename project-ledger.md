# Project Ledger

## 2026-02-05
- Implemented app list caching with a 60s TTL and invalidation on app install/remove/change.
- Added input buffer tracking for search and Ctrl+C/U/L/X reset behavior.
- Added search heuristics: alternate buffer, multi-word/separators, and max token length reset to defaults.
- Replaced search tolerance with strict/balanced/loose toggle and reorganized launcher settings sections.
- Repurposed “Sync system wallpaper” to control system wallpaper syncing when setting in-app background; removed context-menu “Set system wallpaper”.
- Added blur/opacity controls: per-surface blur sliders (sessions, extra keys/app bar, terminal), app bar opacity slider, and terminal blur (Android 12+).
- Added Monet overlay toggle and wired terminal opacity slider to update `termux.properties` background overlay color.
