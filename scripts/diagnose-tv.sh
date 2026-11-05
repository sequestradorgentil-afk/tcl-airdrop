#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
source "$HERE/tv-common.sh" "${1:-192.168.15.3:5555}"
# Only our fixed-code/numeric diagnostic tag, never a system-wide logcat dump.
"$ADB" -s "$TV" logcat -d -v brief -s CinemaAir:I '*:S' </dev/null > "$DESTINATION/logs/cinemaair-events.txt"
"$ADB" -s "$TV" shell dumpsys meminfo com.gabriel.cinemaair </dev/null > "$DESTINATION/logs/cinemaair-memory.txt"
printf 'Cinema Air diagnostics saved under %s/logs. You can also read Diagnostics / last error on the TV.\n' "$DESTINATION"
