#!/usr/bin/env bash
# Captures every tab of the app at each screen preset on a connected emulator
# or device, for UI pull requests and README images.
# Usage: scripts/screenshots.sh [--apk <path>] [--out <dir>] [--delay <seconds>]
# Writes <out>/<preset>-<tab>.png (default out: screenshots/).
# Uses the adb on PATH; set ANDROID_SERIAL to pick one of several devices.
set -euo pipefail

PACKAGE=app.wowforever.addons
ACTIVITY=.MainActivity
# Tab order as MainActivity cycles them with R1. Add a new tab here.
TABS=(library browse settings)
# name:wm size:rotation (1 = landscape, 0 = portrait; reset = the device's own size)
PRESETS=(4x3:1080x1440:1 16x9:1080x1920:1 20x9:1080x2400:1 portrait:reset:0)

apk=""
out=screenshots
delay=2

usage() { sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    --apk) apk="${2:?--apk needs a path}"; shift 2 ;;
    --out) out="${2:?--out needs a folder}"; shift 2 ;;
    --delay) delay="${2:?--delay needs seconds}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

command -v adb >/dev/null || { echo "adb not found on PATH" >&2; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "No device connected (adb get-state failed)" >&2; exit 1; }

restore() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell cmd window user-rotation free >/dev/null 2>&1 || true
}
trap restore EXIT

if [ -n "$apk" ]; then
  [ -f "$apk" ] || { echo "APK not found: $apk" >&2; exit 1; }
  echo "Installing $apk"
  adb install -r "$apk" >/dev/null
fi

mkdir -p "$out"

for preset in "${PRESETS[@]}"; do
  IFS=: read -r name size rotation <<<"$preset"
  echo "Preset $name"
  adb shell wm size "$size" >/dev/null
  adb shell cmd window user-rotation lock "$rotation" >/dev/null
  # Restart so every preset starts on the first tab with a fresh layout.
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
  sleep "$delay"
  for i in "${!TABS[@]}"; do
    if [ "$i" -gt 0 ]; then
      adb shell input keyevent KEYCODE_BUTTON_R1
      sleep "$delay"
    fi
    file="$out/$name-${TABS[$i]}.png"
    adb exec-out screencap -p >"$file"
    echo "  $file"
  done
done
