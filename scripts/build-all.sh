#!/usr/bin/env bash
# Builds and tests Atlas for every Minecraft version in versions/ (or the ones
# given as arguments) and collects the mod jars in build/dist.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "$#" -gt 0 ]; then
  versions=("$@")
else
  mapfile -t versions < <(ls versions/*.properties | xargs -n1 basename | sed 's/\.properties$//' | sort -V)
fi

rm -rf build/dist
mkdir -p build/dist

for mc in "${versions[@]}"; do
  echo "=== Minecraft $mc"
  ./gradlew build --no-daemon -Pmc="$mc"
  found=0
  for jar in atlas-loader-*/build/libs/atlas-*+"$mc".jar; do
    [ -e "$jar" ] || continue
    cp "$jar" build/dist/
    found=1
  done
  if [ "$found" -eq 0 ]; then
    echo "No jar was built for Minecraft $mc" >&2
    exit 1
  fi
done

echo "=== Built jars"
ls -1 build/dist
