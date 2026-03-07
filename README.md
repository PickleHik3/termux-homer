# Termux Launcher

Personal proof of concept Termux as an Android launcher which i vibe coded for myself. Heavily inspired by [TEL](https://github.com/t-e-l/tel), forked from [termux-monet](https://github.com/Termux-Monet/termux-monet). If you like the idea and you are a real developer, please i urge you to take this idea to make a real android launcher. :)

Download it from [releases](https://github.com/PickleHik3/termux-launcher/releases). The companion apps are available at [termux-styling](https://github.com/PickleHik3/termux-styling/releases) and [termux-api](https://github.com/PickleHik3/termux-api/releases/tag/v0.53.0).

It will work OOB with just with above apk's, if you want to take advantage of the shizuku backend for various stuff, run the bootstrap script from [tooie](https://github.com/PickleHik3/tooie). 

## Some Quirks
- if you exit out of the shell by typing "exit" while the app is set as the home launcher, it will become stuttery and unusable, to fix: either force stop termux:launcher from the android apps settings page or if you installed the [tooie](https://github.com/PickleHik3/tooie) then you can do 'tooie --restart' which does the same thing.
- Recommended to install [shizuku](https://github.com/rikkaapps/shizuku) to get the live system stats and other features (such as launching android apps from within the shell) from [tooie](https://github.com/PickleHik3/tooie).
- Recommeded to install [unexpected-keyboard](https://github.com/Julow/Unexpected-Keyboard) if you want to make your life easier working with tmux.
- for launching apps from the apps bar, shizuku is NOT required.
- by default, typing '/' into the shell will start the android apps search in the apps bar. you can change the specific character from settings > Appsbar > Input split character

## Screenshots

<p align="center">
  <img src="resources/assets/tooie.png" width="180" alt="Tooie mascot"/>
</p>

| Home | Apps Bar | Settings |
| --- | --- | --- |
| ![Home](screenshots/home-screen.png) | ![Apps Bar](screenshots/app-search.png) | ![Settings](screenshots/app-settings.png) |

## Phantom Process Killer

**NOTICE:**
> **Termux may be unstable on Android 12+.** Android OS will kill any (phantom) processes greater than 32 (limit is for all apps combined) and also kill any processes using excessive CPU. You may get `[Process completed (signal 9) - press Enter]` message in the terminal without actually exiting the shell process yourself. Check the related issue [#2366](https://github.com/termux/termux-app/issues/2366), [issue tracker](https://issuetracker.google.com/u/1/issues/205156966), [gist with details](https://gist.github.com/agnostic-apollo/dc7e47991c512755ff26bd2d31e72ca8) and [this TLDR comment](https://github.com/termux/termux-app/issues/2366#issuecomment-1009269410) on how to disable trimming of phantom processes.

#### Deactivation Instructions:
- If your phone supports it, the easiest method is to go to developer settings > enable "Disable child process restrictions" option. 

#### Deactivation Instructions (ADB):
- On an ADB console, paste the following commands on the following order:
```
adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
```
```
adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
```
```
adb shell settings put global settings_enable_monitor_phantom_procs false
```
## Shizuku Integration

Shizuku privileged backend support and Tooie local API endpoints are included in `main`.

Current Tooie endpoints include:
- `/v1/status`
- `/v1/apps`
- `/v1/system/resources` (CPU, memory, storage, battery, network, thermal, backend diagnostics)
- `/v1/media/now-playing`
- `/v1/media/art` (now-playing album art payload)
- `/v1/notifications`
- `/v1/system/brightness` (read/set screen brightness)
- `/v1/system/volume` (read/set stream volume)
- `/v1/exec` (policy-gated)
- `/v1/privileged/request-permission`
- `/v1/screen/lock`
- `/v1/auth/rotate`

Docs and policy notes:
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

## Acknowledgements

- `TEL`: launcher direction and launcher-focused Termux experimentation
  https://github.com/t-e-l/tel
- `termux-monet`: wallpaper-driven theming direction and related customization ideas
  https://github.com/Termux-Monet/termux-monet
- `termux-app`: upstream Android app base this fork builds on
  https://github.com/termux/termux-app

## Companion Apps (Forks)

Use below companion apps to avoid shared UID/signature incompatibility.

- Termux:API fork: https://github.com/PickleHik3/termux-api
- Termux:Styling fork: https://github.com/PickleHik3/termux-styling
