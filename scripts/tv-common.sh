#!/usr/bin/env bash
# Sourced by the Mac helpers. No global TV settings are changed.
set -euo pipefail
ADB="$HOME/Downloads/platform-tools/adb"
TV=${1:-192.168.15.3:5555}
DESTINATION=${TV_LAUNCHER_DIR:-"$HOME/Downloads"}
mkdir -p "$DESTINATION/logs"
LOG="$DESTINATION/logs/$(date +%Y%m%d-%H%M%S)-$$.log"
trap 'result=$?; if [ "$result" -ne 0 ]; then printf "Failed (exit %s). Log: %s\n" "$result" "$LOG" >&2; fi' EXIT
fail() { printf '%s\n' "$*" | tee -a "$LOG" >&2; exit 1; }
[ -x "$ADB" ] || fail "ADB not found at $ADB. Install Google's platform-tools there."
# Never connect to an arbitrary public address. Accept only literal RFC1918 IPv4 + a port.
if ! [[ "$TV" =~ ^(10\.[0-9]+\.[0-9]+\.[0-9]+|192\.168\.[0-9]+\.[0-9]+|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]+\.[0-9]+):[0-9]+$ ]]; then
  fail 'Use the TV private IPv4 address and port, for example 192.168.15.3:5555.'
fi
printf 'Target %s\n' "$TV" >> "$LOG"
"$ADB" connect "$TV" </dev/null >> "$LOG" 2>&1 || fail 'ADB connection failed. Check the TV IP, debugging and authorization prompt.'
state=$("$ADB" -s "$TV" get-state </dev/null 2>> "$LOG") || fail 'TV is offline or unauthorized. Authorize this Mac on the TV.'
[ "$state" = device ] || fail "TV state is $state; expected device."
read_prop() { "$ADB" -s "$TV" shell getprop "$1" </dev/null | tr -d '\r'; }
MODEL=$(read_prop ro.product.model)
SDK=$(read_prop ro.build.version.sdk)
ABI=$(read_prop ro.product.cpu.abilist)
FW=$(read_prop ro.build.display.id)
printf 'model=%s api=%s abi=%s firmware=%s\n' "$MODEL" "$SDK" "$ABI" "$FW" | tee -a "$LOG"
[ "$MODEL" = BeyondTV2 ] && [ "$SDK" = 28 ] || fail 'Target does not match the supplied BeyondTV2 / Android 9 TV. Nothing was installed or stopped.'
launch_app() {
  local output result
  output=$("$ADB" -s "$TV" shell am start -W -n "$1" </dev/null 2>&1) || { result=$?; printf '%s\n' "$output" >> "$LOG"; exit "$result"; }
  printf '%s\n' "$output" | tee -a "$LOG"
  case "$output" in *'Error:'*|*Exception*) fail 'Android could not launch the requested activity.';; esac
  case "$output" in *'Status: ok'*) ;; *) fail 'Android did not report a successful activity launch.';; esac
}
