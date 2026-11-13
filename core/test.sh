#!/usr/bin/env bash
# Compiles core/ alone (Java 8 API, no Android) and runs every core/test/*Test.java.
# InstallTest needs real addon zips: pass a folder of them as the first argument to run it too.
#   core/test.sh [folder-with-zips]
set -euo pipefail
cd "$(dirname "$0")"

# core is shared by Android and desktop: no Android, org.json or resource references.
if grep -rnE '^import (android|org\.json)|\bR\.(string|drawable|plurals)' src; then
  echo "core/src must not use Android classes, org.json or R (see AGENTS.md)."
  exit 1
fi

OUT="${TMPDIR:-/tmp}/runeloader-core"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -d "$OUT" \
  $(find src -name '*.java') test/*.java

for t in test/*Test.java; do
  name=$(basename "$t" .java)
  if [ "$name" = InstallTest ]; then
    if [ $# -ge 1 ]; then
      java -cp "$OUT" app.wowforever.addons.InstallTest "$1" "$OUT/fakegame"
    else
      echo "InstallTest: compiled; pass a folder of addon zips to run it"
    fi
    continue
  fi
  echo "== $name"
  java -cp "$OUT" "app.wowforever.addons.$name"
done
echo "core: all tests passed"
