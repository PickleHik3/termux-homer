# Tooie API (Local Shell Bridge)

## Overview
Tooie is a localhost API bridge for exposing Android/app data to shell tools without high-frequency `adb`/`rish` polling loops.

- Server: in app process, bound to `127.0.0.1` on random port.
- Auth: bearer token from `~/.tooie/token`.
- Endpoint URL: `~/.tooie/endpoint`.
- CLI: `$PREFIX/bin/tooie` (auto-installed on app startup).

## Files and Components

- Server implementation:
  - `app/src/main/java/com/termux/tooie/TooieApiServer.java`
- Notification/media source:
  - `app/src/main/java/com/termux/tooie/TooieNotificationListener.java`
- App startup wiring:
  - `app/src/main/java/com/termux/app/TermuxApplication.java`
- Manifest service entry:
  - `app/src/main/AndroidManifest.xml`

Runtime files under `$HOME/.tooie`:

- `token`: API bearer token.
- `endpoint`: local base URL (`http://127.0.0.1:<port>`).
- `config.json`: server policy (created automatically if missing).

## Endpoints (v1)

### `GET /v1/status`
Returns backend + Tooie runtime status.

### `GET /v1/apps`
Returns installed apps (package, label, system flag).

### `GET /v1/system/resources`
Returns a system resource snapshot:
- CPU metrics:
  - `cpuPercent` (from `/proc/stat` delta, with load-average fallback)
  - `cpuCores`
  - `loadAvg1m`, `loadAvg5m`, `loadAvg15m`
- Memory metrics:
  - top-level compatibility fields:
    - `memTotalBytes`, `memAvailableBytes`, `memFreeBytes`, `memUsedBytes`
  - nested `memory` object with additional meminfo-derived fields:
    - `buffersBytes`, `cachedBytes`, `swapCachedBytes`, `activeBytes`, `inactiveBytes`,
      `shmemBytes`, `slabBytes`, `swapTotalBytes`, `swapFreeBytes`
- Runtime/heap metrics:
  - `javaHeapUsedBytes`, `javaHeapMaxBytes`, `javaHeapFreeBytes`, `javaHeapTotalBytes`
  - nested `runtime` object
- Uptime metrics:
  - nested `uptime` object (`systemUptimeSec`, `systemUptimeMs`, `processUptimeMs`, `processUptimeSec`)
- Storage metrics:
  - nested `storage` array with per-path totals/used/free/available bytes
- Battery metrics:
  - nested `battery` object (`levelPercent`, charging state, plug type, temperature, voltage, health)
- Network metrics:
  - nested `network` array with per-interface `rx/tx` bytes, packets, errors, drops
- Thermal metrics:
  - nested `thermal` array from `/sys/class/thermal/thermal_zone*`
- Backend diagnostics:
  - `backendType`, `backendState`, `statusReason`, `statusMessage`, `isPrivilegedAvailable`
  - `execPolicy`

### `GET /v1/media/now-playing`
Returns cached now-playing media session data.
Requires notification listener access.

### `GET /v1/media/art`
Returns cached now-playing album art snapshot as base64-encoded JPEG payload.
Requires notification listener access.

### `GET /v1/notifications`
Returns cached notification list.
Requires notification listener access.

### `POST /v1/system/brightness`
Reads current brightness and optionally sets a new value.
- Request body (optional): `{"brightness": <0..255>}` or `{"value": <0..255>}`
- Empty body performs read-only snapshot.

### `POST /v1/system/volume`
Reads current volume and optionally sets a new value.
- Request body (optional): `{"volume": <int>, "stream": <int>}` or `{"value": <int>}`
- Empty body performs read-only snapshot for music stream.

### `POST /v1/exec`
Runs a privileged command through `PrivilegedBackendManager`.
Subject to strict policy in `~/.tooie/config.json`.

### `POST /v1/privileged/request-permission`
Requests privileged permission when Shizuku is available but not yet granted.
Returns backend status fields to help diagnose permission state.

### `POST /v1/screen/lock`
Attempts screen lock key event (`223`, fallback `26`) via privileged backend.

### `POST /v1/auth/rotate`
Rotates API token and rewrites `~/.tooie/token` and `~/.tooie/endpoint`.

## CLI Usage

```sh
tooie status
tooie apps
tooie resources
tooie media
tooie art
tooie notifications
tooie brightness
tooie brightness 128
tooie volume
tooie volume 8
tooie volume 6 3
tooie lock
tooie exec id
tooie permission
tooie token rotate
```

Note: `tooie-status` is not a command. Use `tooie status`.

## Security Model

### Attack Surface
- Localhost API reachable from local device processes.
- Token theft enables API calls.
- `exec` endpoint is highest-risk capability.
- Notification/media endpoints may expose sensitive user content.

### Mitigations Implemented
- Bearer token auth, startup-generated random token.
- Constant-time token comparison.
- Bounded worker pool (prevents unbounded thread growth).
- HTTP parser limits:
  - request line size,
  - header line size/count,
  - max body size.
- Endpoint rate limiting (`429` on abuse).
- `exec` policy gate (`config.json`) with allowlist prefixes.
- `exec` disabled by default.
- Token rotation endpoint.
- Sensitive files written owner-only.

### Remaining Security Considerations
- Localhost token auth still depends on local process trust.
- If same app UID ecosystem is compromised, token can be read.
- Consider Unix domain sockets for tighter local access boundaries in future.
- Consider endpoint toggles for media/notifications if privacy requirements increase.

## `config.json` Policy
Default generated policy:

```json
{
  "execEnabled": false,
  "allowedCommandPrefixes": [
    "id",
    "pm list packages",
    "cmd package list packages"
  ]
}
```

To enable exec for controlled commands:

```json
{
  "execEnabled": true,
  "allowedCommandPrefixes": [
    "id",
    "pm list packages",
    "cmd package list packages",
    "input keyevent"
  ]
}
```

Rules:
- Command must exactly match a prefix or start with `"<prefix> "`.
- Commands with unsupported control chars are rejected.
- Overlong commands are rejected.
- If Shizuku permission is missing, `/v1/exec` returns `permission_required`
  and attempts to trigger a permission request.

## Notification and Media Data

`tooie media` and `tooie notifications` require notification listener permission for the app.
`tooie art` also requires notification listener permission.

If not granted, responses include:
- `listenerConnected: false`
- a `hint` message.

## Troubleshooting

### `tooie exec` returns forbidden/disabled
- Check `~/.tooie/config.json` and set `"execEnabled": true`.
- Ensure command matches `allowedCommandPrefixes`.

### `tooie media`/`notifications` empty
- Grant notification access for the app in Android settings.

### Token errors (`401`)
- Run `tooie token rotate`.
- Re-run command after the token file is rewritten.

## Performance Notes

- Tooie is event-driven for notifications/media and avoids constant polling loops.
- `/v1/apps` can be heavier than status queries; avoid frequent tight loops.
- `/v1/system/resources` is designed for periodic dashboard polling.
- `/v1/exec` cost depends on spawned privileged command frequency.
