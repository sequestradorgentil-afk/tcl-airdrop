#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
BUNDLE=$(cd "$HERE/.." && pwd)
# Verify the delivered artifact before touching the TV. This is integrity, not publisher authentication.
cd "$BUNDLE"
[ -f CinemaAir-experimental-armv7.apk ] && [ -f APK-SHA256SUMS ] || { echo 'APK or checksum file missing.' >&2; exit 1; }
shasum -a 256 -c APK-SHA256SUMS || exit $?
source "$HERE/tv-common.sh" "${1:-192.168.15.3:5555}"
case ",$ABI," in *,armeabi-v7a,*) ;; *) fail 'This experimental APK requires armeabi-v7a. Do not install it on this target.';; esac
printf 'Installing only com.gabriel.cinemaair; launcher and system settings are unchanged.\n' | tee -a "$LOG"
"$ADB" -s "$TV" install -r "$BUNDLE/CinemaAir-experimental-armv7.apk" </dev/null >> "$LOG" 2>&1 || { result=$?; printf '%s\n' 'Installation failed. Inspect the log; no app was uninstalled.' >&2; exit "$result"; }
launch_app com.gabriel.cinemaair/.MainActivity
printf 'Select Start receiving using the TV remote. Logs: %s\n' "$LOG"
