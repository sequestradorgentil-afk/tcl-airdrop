#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
source "$HERE/tv-common.sh" "${1:-192.168.15.3:5555}"
launch_app com.gabriel.cinemaair/.MainActivity
