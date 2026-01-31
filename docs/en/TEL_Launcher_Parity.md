## TEL Launcher Parity Contract

This document locks the UI/behavior parity targets for the Termux-Homer launcher work relative to TEL (the reference implementation under `tel/`). It is intentionally testable and ordered by priority.

Definitions:

- TEL: Reference launcher/UI behavior (see `tel/app/`).
- Termux-Homer: Target app to implement parity in (see `termux-homer/app/`).
- "HOME launcher capability": The app can be selected as the system Home app and responds correctly to the Home intent.

Non-goal (explicit): TEL `StatusView` is NOT required and must not be implemented.

### Priority Order (must implement in this order)

1) HOME launcher capability
2) Applications Bar above extra keys with Monet blur/no seam
3) Robust TEL-style search behavior
4) Wallpaper: system wallpaper setting (prefer Termux:API; fallback to `WallpaperManager`)

## 1) HOME launcher capability

Goal: Termux-Homer can be set as the device's Home app and behaves like a launcher when the user presses the Home button.

Requirements:

- Manifest exposes a Home-capable activity.
- The Home-capable activity must include an intent filter with:
  - action: `android.intent.action.MAIN`
  - categories: `android.intent.category.HOME` and `android.intent.category.DEFAULT`
- The app remains discoverable in the app drawer (i.e., still has a launcher entry), either by also declaring `android.intent.category.LAUNCHER` or by having a separate entry activity.
- When selected as the default Home app:
  - Pressing the system Home button always opens Termux-Homer.
  - Back navigation from the root launcher surface does not exit into an unintended blank/finished state (i.e., the root surface remains stable).

Acceptance criteria (objectively verifiable):

- `termux-homer/app/src/main/AndroidManifest.xml` includes an activity with the exact HOME intent filter (MAIN + HOME + DEFAULT).
- On a device/emulator, Settings -> Apps -> Default apps -> Home app shows Termux-Homer as a selectable option.
- With Termux-Homer set as Home app, pressing the Home button from another app opens Termux-Homer.

## 2) Applications Bar above extra keys with Monet blur/no seam

Goal: The Applications Bar sits directly above the extra keys row, visually integrated (no seam), and uses Monet-aware blurred styling consistent with TEL.

Placement and layout requirements:

- The Applications Bar is part of the main terminal activity layout (anchor: `termux-homer/app/src/main/res/layout/activity_termux.xml`).
- The Applications Bar must be positioned immediately above the extra keys view/container.
- The Applications Bar and extra keys must share a unified visual surface:
  - no visible gap line between them
  - no mismatched corner radius or elevation causing a seam

Styling requirements:

- Colors follow Monet/dynamic color when available.
- Background uses a blur effect consistent with TEL's appearance, or the closest available equivalent on the Android versions supported by Termux-Homer.
- The blur and color treatment must be consistent across orientation changes and when the keyboard is shown/hidden.

Acceptance criteria (objectively verifiable):

- In `termux-homer/app/src/main/res/layout/activity_termux.xml`, the Applications Bar view/container is the direct sibling immediately above the extra keys container in the view hierarchy.
- In `termux-homer/app/src/main/res/layout/activity_termux.xml`, the Applications Bar and extra keys are rendered on a shared background/blur surface:
  - Both regions are descendants of the same background container (e.g., `@id/extrakeys_background`) and, when blur is enabled, share the same blur surface (e.g., `@id/extrakeys_backgroundblur`).
  - No dedicated divider/separator view exists between the two regions.

## 3) Robust TEL-style search behavior

Goal: Search behaves like TEL: fast, forgiving matching and predictable ordering.

Scope:

- Search queries installed applications (launchable activities).
- Search results update as the user types.
- Search query extraction is terminal-driven for TEL parity (TEL-equivalent `splitChar` + `getCurrentInput` behavior).
- Fuzzy matching and ranking matches TEL behavior and uses TEL's fuzzy dependency (`me.xdrop:fuzzywuzzy`).

Behavior requirements:

- Matching is case-insensitive.
- Leading/trailing whitespace in the query is ignored.
- Empty query shows the default launcher surface (no "no results" state).
- Result ordering (highest first) must be deterministic and TEL-like:
  - Primary ranking uses fuzzy scores from `me.xdrop:fuzzywuzzy` with TEL-equivalent tolerance.
  - Ties are broken deterministically in this order:
    1. Exact label match
    2. Label prefix match (starts-with)
    3. Word-start match (any word in label starts-with)
    4. Substring match (label contains)
    5. Fallback stable ordering (e.g., label sort)
- Launch action:
  - Activating a result (tap or Enter on focused top result) launches the selected app.
  - If there are no results, Enter does nothing and does not close the launcher.

Acceptance criteria (objectively verifiable):

- Given apps "Termux", "Termux:API", and "Telegram", the query `te` orders "Telegram" and "Termux" above "Termux:API" (prefix before word-start/substring).
- The query `  termux  ` produces the same results as `termux`.
- With an empty query, the launcher shows the default surface and does not render a "no results" placeholder.
- A unit test can be written that, given a fixed app list and query, asserts the top-N results match TEL's fuzzy ranking based on `me.xdrop:fuzzywuzzy` scoring and TEL-equivalent tolerance.

## 4) Wallpaper: system wallpaper setting

Goal: The launcher can set the system wallpaper. Prefer Termux:API when available; otherwise use Android's `WallpaperManager`.

Requirements:

- Provide a launcher-visible action to set wallpaper from an image source available to the app (exact UI placement is flexible, but must be reachable from the launcher surface).
- Preferred path: Termux:API (if installed and available).
  - Use Termux:API to set wallpaper.
  - If Termux:API is missing or fails, fall back.
- Fallback path: Android `WallpaperManager`.
- Error handling:
  - User-visible failure message when wallpaper cannot be set.
  - No crash on missing permissions, missing Termux:API, or invalid image input.

Acceptance criteria (objectively verifiable):

- On a device with Termux:API installed, wallpaper setting succeeds via the Termux:API path.
- On a device without Termux:API installed, wallpaper setting succeeds via `WallpaperManager`.
- Failure cases (e.g., invalid image) show a user-visible error and do not crash.

## Explicit exclusions

- TEL `StatusView` must not be added.
- No new third-party dependencies are introduced to implement this parity, except:
  - `me.xdrop:fuzzywuzzy` to match TEL fuzzy search behavior.
