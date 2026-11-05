#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
source "$HERE/tv-common.sh" "${1:-192.168.15.3:5555}"
"$ADB" -s "$TV" shell am force-stop com.gabriel.cinemaair </dev/null >> "$LOG" 2>&1
launch_app com.gabriel.cinemahome/.MainActivity
