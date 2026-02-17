#!/data/data/com.termux/files/usr/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
LOG_FILE="${1:-$SCRIPT_DIR/agent-log.md}"
MAX_LINES="${MAX_AGENT_LOG_LINES:-120}"
SUMMARY_LINES="${SUMMARY_LINES:-10}"
MAX_CYCLES="${MAX_AGENT_CYCLES:-12}"

[ -f "$LOG_FILE" ] || exit 0

TOTAL_LINES=$(wc -l < "$LOG_FILE" | tr -d ' ')
[ "$TOTAL_LINES" -le "$MAX_LINES" ] && exit 0

TMP_FILE="$LOG_FILE.tmp.$$"
trap 'rm -f "$TMP_FILE"' EXIT

CYCLE_START=$(awk '/^##[[:space:]]+Cycle/{print NR}' "$LOG_FILE" | tail -n "$MAX_CYCLES" | head -n 1)

{
  sed -n "1,${SUMMARY_LINES}p" "$LOG_FILE"
  echo
  echo "## Log Trim"
  echo "- Auto-trimmed to keep context small and preserve latest cycles."
  if [ -n "${CYCLE_START:-}" ]; then
    sed -n "${CYCLE_START},\$p" "$LOG_FILE"
  else
    tail -n "$((MAX_LINES - SUMMARY_LINES - 3))" "$LOG_FILE"
  fi
} | awk '
  NR <= max_lines {
    if ($0 ~ /^[[:space:]]*$/) {
      if (!blank) print
      blank = 1
    } else {
      print
      blank = 0
    }
  }
' max_lines="$MAX_LINES" > "$TMP_FILE"

mv "$TMP_FILE" "$LOG_FILE"
trap - EXIT
