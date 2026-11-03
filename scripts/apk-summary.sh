#!/usr/bin/env bash
# Prints the parts of an APK that the Gradle and build.sh paths must agree on:
# package, version, SDK levels, permissions and the app label.
# Usage: scripts/apk-summary.sh <aapt2> <apk>
set -euo pipefail
"$1" dump badging "$2" \
  | grep -E "^(package:|minSdkVersion|targetSdkVersion|uses-permission|application-label:)" \
  | sed -E "s/ platformBuildVersion[A-Za-z]*='[^']*'//g; s/ compileSdkVersion[A-Za-z]*='[^']*'//g" \
  | sort
