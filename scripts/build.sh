#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

JDK="${JDK_HOME:-/c/Users/10797/.jdks/jdk8u512-b09}"
GAME="${GAME_DIR:-D:/Steam/steamapps/common/SlayTheSpire}"
WORKSHOP="${WORKSHOP_DIR:-D:/Steam/steamapps/workshop/content/646570}"

CP="$GAME/desktop-1.0.jar;$WORKSHOP/1605060445/ModTheSpire.jar;$WORKSHOP/1605833019/BaseMod.jar"

RUN="build/runs/build-$$-$(date -u +%Y%m%d-%H%M%S)"
rm -rf "$RUN"
mkdir -p "$RUN/classes"

"$JDK/bin/javac" -encoding UTF-8 -source 1.8 -target 1.8 -cp "$CP" \
  -d "$RUN/classes" $(find src/main/java -name '*.java')

cp -r src/main/resources/. "$RUN/classes/"

mkdir -p dist
"$JDK/bin/jar" cf dist/inruncompendium.jar -C "$RUN/classes" .
echo "built dist/inruncompendium.jar"
