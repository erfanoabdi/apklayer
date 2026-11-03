#!/usr/bin/env bash
# The host JDK with apklayer's java.base classes built in: `scripts/build-host-jdk.sh <native>` writes <native>/jdk (a jlink image of the system JDK whose
# java.base carries <native>/jdk-patch, see apklayer.host.JdkPatch) with its default CDS archive. The CLI runs app and hub JVMs on it instead of
# `--patch-module java.base=...`, which switches class data sharing off (the JDK's own archive and an app's archive alike).
set -euo pipefail
native="$(realpath "$1")"
[ -d "$native/jdk-patch" ] || { echo "no $native/jdk-patch (scripts/build-tree-host.sh first)"; exit 1; }
java_home="${JAVA_HOME:-$(dirname "$(dirname "$(realpath "$(command -v java)")")")}"
[ -f "$java_home/jmods/java.base.jmod" ] || { echo "no $java_home/jmods (install the full JDK package, e.g. openjdk-21-jdk)"; exit 1; }
stamp="$( (cd "$native/jdk-patch" && find . -type f | sort | xargs sha256sum; "$java_home/bin/java" -version 2>&1) | sha256sum | cut -d' ' -f1)"
[ "$(cat "$native/jdk/apklayer-stamp" 2>/dev/null)" != "$stamp" ] || { echo "$native/jdk is up to date"; exit 0; }
work="$(mktemp -d "$native/.jdk.XXXXXX")"
trap 'rm -rf "$work"' EXIT
"$java_home/bin/jmod" extract --dir "$work/base" "$java_home/jmods/java.base.jmod"
cp -R "$native/jdk-patch/." "$work/base/classes/"
version="$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.version = //p')"
platform="$("$java_home/bin/jmod" describe "$java_home/jmods/java.base.jmod" | sed -n 's/^platform //p')"
mkdir "$work/mods"
# the module's own sections, as in the JDK's jmod; the hashes of the other modules are left out (they are only checked when present)
"$java_home/bin/jmod" create --class-path "$work/base/classes" --cmds "$work/base/bin" --config "$work/base/conf" --header-files "$work/base/include" \
    --legal-notices "$work/base/legal" --libs "$work/base/lib" --module-version "$version" --target-platform "$platform" "$work/mods/java.base.jmod"
modules="$(ls "$java_home/jmods" | sed 's/\.jmod$//' | paste -sd, -)"
"$java_home/bin/jlink" --module-path "$work/mods:$java_home/jmods" --add-modules "$modules" --output "$work/jdk" --generate-cds-archive
echo "$stamp" > "$work/jdk/apklayer-stamp"
rm -rf "$native/jdk"
mv "$work/jdk" "$native/jdk"
echo "Published $native/jdk"
