# Termux Launcher

Android launcher and terminal app fork based on Termux, focused on launcher UX and privileged integrations.

This repository now contains only Android app source and related app assets.
Shell dotfiles/config bundles (tmux, fish, nvim) are intentionally not part of this repo.

## Screenshots

<p align="center">
  <img src="resources/assets/tooie.png" width="180" alt="Tooie mascot"/>
</p>

| Home | Search | Settings |
| --- | --- | --- |
| ![Home](screenshots/home-screen.png) | ![Search](screenshots/app-search.png) | ![Settings](screenshots/app-settings.png) |

| Blur Home | Shelf | Terminal Environment |
| --- | --- | --- |
| ![Blur Home](screenshots/home-screen-blur.png) | ![Tooie Shelf](screenshots/TUI-tooie-shelf.png) | ![Shell Env](screenshots/shell-env.png) |

## Download APK

Use GitHub Actions artifacts.

1. Non-Shizuku build (default)
- Download artifact from the latest successful run on `main`.
- Workflow: `Build nightly` (`.github/workflows/debug_build.yml`).

2. Shizuku integration build
- Download artifact from the latest successful run on `shizuku-integration`.
- Workflow: `Build nightly` (`.github/workflows/debug_build.yml`).

Note: action artifacts require a logged-in GitHub account.

## Shizuku Integration

`shizuku-integration` branch includes privileged backend support and Tooie local API endpoints.

Current Tooie endpoints include:
- `/v1/status`
- `/v1/apps`
- `/v1/system/resources` (CPU, memory, storage, battery, network, thermal, backend diagnostics)
- `/v1/media/now-playing`
- `/v1/notifications`
- `/v1/exec` (policy-gated)
- `/v1/screen/lock`

Developer/security notes:
- `docs/en/Tooie_API.md`

## Build (local)

```sh
COMPILE_SDK_OVERRIDE=34 ./gradlew \
  -PcompileSdkVersion=34 \
  -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
  :app:compileDebugJavaWithJavac
```

## Upstream Base

- Upstream Termux app: https://github.com/termux/termux-app
