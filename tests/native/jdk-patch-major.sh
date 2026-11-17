#!/bin/sh
# jdk-patch-major.sh <host-classes cp>: a jdk-patch cut by each installed JDK starts only that JDK (the mismatch findJava avoids); exit 1 when the match fails
set -u
cp="$1" rc=0 t="$(mktemp -d)"; trap 'rm -rf "$t"' EXIT
for j in /usr/lib/jvm/*/bin/java; do
    [ -x "$j" ] || continue
    rm -rf "$t/p"; "$j" -cp "$cp" apklayer.host.JdkPatch "$t/p" >/dev/null 2>&1 || continue
    m="$(cat "$t/p/apklayer-jdk")"
    for k in /usr/lib/jvm/*/bin/java; do
        [ -x "$k" ] || continue
        km="$("$k" -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.specification.version = //p')"
        ok=0; "$k" --patch-module "java.base=$t/p" -version >/dev/null 2>&1 && ok=1
        [ "$km" = "$m" ] && [ $ok = 0 ] && { echo "FAIL: $k does not start with its own major's patch"; rc=1; }
        [ "$km" != "$m" ] && [ $ok = 1 ] && echo "note: $k ($km) runs the $m patch"
    done
done
exit $rc
