#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
source "$HERE/tv-common.sh" "${1:-192.168.15.3:5555}"
# Read-only diagnostics. No package or TV setting changes.
for property in ro.product.manufacturer ro.product.cpu.abi ro.product.cpu.abi2 ro.build.version.release ro.build.version.incremental; do
  printf '%s=%s\n' "$property" "$(read_prop "$property")" | tee -a "$LOG"
done
"$ADB" -s "$TV" shell wm size </dev/null | tee -a "$LOG"
"$ADB" -s "$TV" shell dumpsys display </dev/null > "$DESTINATION/logs/display.txt"
codec_status=0
"$ADB" -s "$TV" shell dumpsys media.codec </dev/null > "$DESTINATION/logs/media-codec.txt" 2>&1 || codec_status=$?
case "$(cat "$DESTINATION/logs/media-codec.txt")" in *"Can't find service:"*) codec_status=1;; esac
if [ "$codec_status" -ne 0 ]; then
  printf '%s\n' 'Codec-service diagnostics unavailable on this firmware. The app checks decoder availability separately.' | tee -a "$LOG"
fi
"$ADB" -s "$TV" shell cat /proc/meminfo </dev/null > "$DESTINATION/logs/memory.txt"
printf 'Diagnostics saved to %s/logs. Share model/API/ABI first; inspect full logs before sharing.\n' "$DESTINATION"
