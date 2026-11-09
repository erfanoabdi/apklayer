#!/bin/sh
# jdk-image.sh <java> <native dir>: the runtime's own JDK image, for class data sharing (docs/PLAN.md "app start speed on phones"). HotSpot turns CDS
# off for a patched java.base (--patch-module), so <native>/jdk is a jlink image of the JDK's modules whose java.base has the jdk-patch classes built
# in (a rebuilt java.base jmod) and the default CDS archive; and the class path's one directory becomes aosp/sim-classes.jar (a dynamic dump exits
# the VM on a non-empty directory in the class path). The CLI (cli/env.cpp) takes the image while its stamp is the JDK's lib/modules time, else the JDK
# with --patch-module. Run by meson install and tools/setup-runtime.sh; a JDK without jlink or jmods (a JRE) gets nothing, and nothing fails.
set -eu
java="$(realpath "$(command -v "$1")")" native="$(realpath "$2")"
home="$(dirname "$(dirname "$java")")" img="$native/jdk" patch="$native/jdk-patch" sim="$native/aosp/sim-classes"
if [ -d "$sim" ] && { [ ! -f "$sim.jar" ] || [ -n "$(find -L "$sim" -newer "$sim.jar" | head -1)" ]; }; then
    "$home/bin/jar" cf "$sim.jar.new" -C "$sim" . && mv -f "$sim.jar.new" "$sim.jar"
fi
[ ! -f "$home/apklayer-stamp" ] || exit 0  # <java> is such an image already (the click's own JDK, tools/release/click.sh)
[ -x "$home/bin/jlink" ] && [ -f "$home/jmods/java.base.jmod" ] && [ -d "$patch" ] || { echo "apklayer: $home has no jlink and jmods: apps run without class data sharing" >&2; exit 0; }
stamp="$(stat -c %Y "$home/lib/modules")"
[ "$(cat "$img/apklayer-stamp" 2>/dev/null)" != "$stamp" ] || [ -n "$(find -L "$patch" -newer "$img/apklayer-stamp" | head -1)" ] || exit 0
tmp="$(mktemp -d "$native/.jdk-image.XXXXXX")" && trap 'rm -rf "$tmp"' EXIT
"$home/bin/jmod" extract --dir "$tmp/x" "$home/jmods/java.base.jmod"
cp -r "$patch/." "$tmp/x/classes/"
set --  # the jmod's sections as they were (module-info keeps its hashes of the other modules)
for s in libs:lib cmds:bin config:conf header-files:include legal-notices:legal man-pages:man; do [ -d "$tmp/x/${s#*:}" ] && set -- "$@" "--${s%%:*}" "$tmp/x/${s#*:}"; done
mkdir "$tmp/m"
describe="$("$home/bin/jmod" describe "$home/jmods/java.base.jmod")"
"$home/bin/jmod" create --class-path "$tmp/x/classes" "$@" --module-version "$(echo "$describe" | sed -n '1s/^java.base@//p')" \
    --target-platform "$(echo "$describe" | sed -n 's/^platform //p')" "$tmp/m/java.base.jmod"
# every module the JDK has, so the image runs what the JDK runs; the patched java.base first on the module path
"$home/bin/jlink" --module-path "$tmp/m:$home/jmods" --add-modules "$("$java" --list-modules | sed 's/@.*//' | paste -sd, -)" \
    --generate-cds-archive --output "$tmp/jdk" 2>&1 | grep -v "incubator modules" >&2 || true
"$tmp/jdk/bin/java" -Xshare:on -version >/dev/null 2>&1 || { echo "apklayer: the JDK image did not start with its CDS archive; apps run without" >&2; exit 0; }
echo "$stamp" > "$tmp/jdk/apklayer-stamp"
rm -rf "$img.old" && { [ ! -e "$img" ] || mv "$img" "$img.old"; } && mv "$tmp/jdk" "$img" && rm -rf "$img.old"  # running JVMs keep their open files
