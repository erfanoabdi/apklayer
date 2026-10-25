#!/bin/sh
# javac-jar: compile Java sources in one javac call and package them (used by Meson).
# Usage: javac-jar.sh <out.jar> <classes-dir> <classpath|-> <main-class|-> <sources...>
set -e
out="$1" classes="$2" cp="$3" main="$4"
shift 4
rm -rf "$classes" && mkdir -p "$classes"
if [ "$cp" = "-" ]; then javac -nowarn -encoding UTF-8 -d "$classes" "$@"
else javac -nowarn -encoding UTF-8 -cp "$cp" -d "$classes" "$@"; fi
if [ "$main" = "-" ]; then jar --create --file "$out" -C "$classes" .
else jar --create --file "$out" --main-class "$main" -C "$classes" .; fi
