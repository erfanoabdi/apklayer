#!/bin/sh
# runtime-bundle.sh <prebuilts dir> <cpu> <out dir>: the Android runtime bundle of one CPU (arm64, x86_64) from a checkout of the prebuilts branch,
# as <out>/apklayer-runtime-<cpu>-<version>.tar.zst and its .sha256 (docs/RELEASES.md). The version is `runtime` of tools/release/versions.
# Files sit at the top of the tarball: unpack with `tar --zstd -xf <bundle> -C ~/.local/share/apklayer/runtime/<version>`; the CLI does the rest.
set -eu
[ $# -eq 3 ] || { sed -n '2,4p' "$0" >&2; exit 2; }
repo="$(cd "$(dirname "$0")/../.." && pwd)"
P="$(cd "$1" && pwd)" cpu="$2" out="$3"
version="$(sed -n 's/^runtime=//p' "$repo/tools/release/versions")"
[ -d "$P/$cpu/android-libs/lib64" ] || { echo "runtime-bundle: $P has no $cpu/android-libs/lib64" >&2; exit 1; }
[ -z "$(find "$P" -name '*.part000' | head -1)" ] || sh "$P/join-parts.sh" "$P"  # files GitHub made us split
main="$(sed -n '1s/^prebuilts for apklayer main \([0-9a-f]*\).*/\1/p' "$P/MANIFEST")"
name="apklayer-runtime-$cpu-$version"
mkdir -p "$out"
stage="$(mktemp -d "$out/.stage.XXXXXX")"
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/aosp/android-all"
# hardlinks where the file system allows: tar reads each file once. A prebuilts checkout assembled from links (android-root -> another checkout)
# is copied through them: the bundle holds the files, never a link out of itself (links inside android-root stay as they are)
put() { s="$(realpath "$1")" && cp -al "$s" "$2" 2>/dev/null || cp -a "$s" "$2"; }
put "$P/$cpu/android-libs/lib64" "$stage/android-libs"
put "$P/$cpu/android-root" "$stage/android-root"
[ ! -d "$P/$cpu/system-apps" ] || put "$P/$cpu/system-apps" "$stage/system-apps"
put "$P/common/sim-classes" "$stage/aosp/sim-classes"
put "$P/common/Robolectric_all.jar" "$stage/aosp/Robolectric_all.jar"
put "$P/common/android-all-16-robolectric-13921718.jar" "$stage/aosp/android-all/android-all-16-robolectric-13921718.jar"
put "$P/common/android-all-16-robolectric-13921718.jar" "$stage/aosp/android-all/android-all-current-robolectric-r0.jar"  # the SDK provider asks for both names
put "$P/common/android-stub.jar" "$stage/android-stub.jar"
for d in android-root/system android-libs aosp/sim-classes; do [ -d "$stage/$d" ] && [ ! -L "$stage/$d" ] || { echo "runtime-bundle: $d is missing or a link" >&2; exit 1; }; done
[ ! -d "$stage/system-apps" ] || [ -n "$(ls "$stage/system-apps")" ] || { echo "runtime-bundle: system-apps is empty" >&2; exit 1; }
contents="$(cd "$stage" && ls | sed 's/.*/"&"/' | paste -sd, -)"
printf '{"version": "%s", "cpu": "%s", "main_commit": "%s", "contents": [%s, "runtime.json"]}\n' "$version" "$cpu" "$main" "$contents" > "$stage/runtime.json"
tar -C "$stage" --owner=0 --group=0 --numeric-owner --sort=name -cf - . | zstd -q -T0 -12 -o "$out/$name.tar.zst" -f
(cd "$out" && sha256sum "$name.tar.zst" > "$name.tar.zst.sha256")
echo "runtime-bundle: $out/$name.tar.zst ($(du -h "$out/$name.tar.zst" | cut -f1)), main $main"
