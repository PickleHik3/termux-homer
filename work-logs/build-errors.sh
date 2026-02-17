#!/data/data/com.termux/files/usr/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
cd "$ROOT_DIR"

DEFAULT_ANDROID_SDK="/data/data/com.termux/files/usr/opt/Android/sdk"
if [ -z "${ANDROID_HOME:-}" ]; then
  export ANDROID_HOME="$DEFAULT_ANDROID_SDK"
fi
if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

ERROR_LOG="${BUILD_ERROR_LOG:-$SCRIPT_DIR/build-error.log}"
FULL_LOG="${BUILD_FULL_LOG:-$SCRIPT_DIR/build-last.log}"

if [ "$#" -gt 0 ]; then
  TASKS="$*"
else
  TASKS=":app:assembleDebug"
fi

AAPT2_OVERRIDE="${AAPT2_OVERRIDE:-/data/data/com.termux/files/usr/bin/aapt2}"
AAPT2_PROP=""
if [ -x "$AAPT2_OVERRIDE" ]; then
  AAPT2_PROP="-Pandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
fi

COMPILE_SDK_OVERRIDE="${COMPILE_SDK_OVERRIDE:-}"
COMPILE_SDK_PROP=""
if [ -n "$COMPILE_SDK_OVERRIDE" ]; then
  COMPILE_SDK_PROP="-PcompileSdkVersion=$COMPILE_SDK_OVERRIDE"
fi

rm -f "$ERROR_LOG"
: > "$ERROR_LOG"

set +e
./gradlew $TASKS $AAPT2_PROP $COMPILE_SDK_PROP --console=plain --stacktrace >"$FULL_LOG" 2>&1
STATUS=$?
set -e

awk '
  BEGIN { in_what_went_wrong = 0 }

  /^FAILURE: Build failed with an exception\./ { print; next }
  /^\* What went wrong:/ { print; in_what_went_wrong = 1; next }

  in_what_went_wrong && /^> / { print; next }
  in_what_went_wrong && /^\* / { in_what_went_wrong = 0 }
  in_what_went_wrong && /^$/ { in_what_went_wrong = 0; next }

  /(^|[[:space:]])error:/ { print; next }
  /^e: / { print; next }
  /^Caused by:/ { print; next }
  /Exception:/ { print; next }
  /^BUILD FAILED/ { print; next }
' "$FULL_LOG" > "$ERROR_LOG"

printf 'Wrote filtered errors to %s\n' "$ERROR_LOG"
printf 'Saved full output to %s\n' "$FULL_LOG"
printf 'Gradle exit code: %s\n' "$STATUS"

exit "$STATUS"
