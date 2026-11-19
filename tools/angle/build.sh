#!/bin/sh
# build.sh <work dir> <out dir> (locally: tools/angle/build.sh ~/.cache/apklayer/angle-build out/angle): the ANGLE libraries of runtime/angle (README.md there) from Google's source at patches/angle/REVISION with
# patches/angle/*.patch, the way .github/workflows/angle.yml builds them: depot_tools and an ANGLE checkout with the Android dependencies in
# <work dir> (about 20 GB; reused when it is there), then three GN builds (Android x86_64 and x86 for the guests' EGL/GLES, Linux x86_64 for
# the host graphics HAL and the compositor) into <out dir>/{vendor/lib64/egl,vendor/lib/egl,host} and <out dir>/SHA256SUMS, the layout of
# runtime/angle. Needs git, python3, curl and a few GB of RAM per compile job; network.
set -eu
work=$(mkdir -p "$1" && cd "$1" && pwd)
out=$(mkdir -p "$2" && cd "$2" && pwd)
here=$(cd "$(dirname "$0")/../.." && pwd)
rev=$(cat "$here/patches/angle/REVISION")

[ -d "$work/depot_tools" ] || git clone -q --depth=1 https://chromium.googlesource.com/chromium/tools/depot_tools.git "$work/depot_tools"
export PATH="$work/depot_tools:$PATH" DEPOT_TOOLS_UPDATE=0 DEPOT_TOOLS_METRICS=0

src="$work/angle"
if [ ! -d "$src/.git" ]; then
    git init -q "$src"
    git -C "$src" remote add origin https://chromium.googlesource.com/angle/angle.git
fi
git -C "$src" fetch -q --depth=1 origin "$rev"
git -C "$src" checkout -q --force FETCH_HEAD
cat > "$src/.gclient" <<EOF
solutions = [{"name": ".", "url": "https://chromium.googlesource.com/angle/angle.git", "deps_file": "DEPS", "custom_vars": {}}]
target_os = ['android']
EOF
(cd "$src" && gclient sync --no-history --shallow -D --force --reset -j8)
for p in "$here"/patches/angle/[0-9]*.patch; do
    git -C "$src" apply --whitespace=nowarn "$p"
    echo "applied $(basename "$p")"
done

build() {  # <out name> <targets> <gn args>
    name=$1 targets=$2
    shift 2
    gn gen "$src/out/$name" --root="$src" --args="$*"
    autoninja -C "$src/out/$name" $targets
}
common='is_debug=false is_component_build=false angle_enable_vulkan=true angle_libs_suffix="_angle"'
android='angle_build_tests=false android_ndk_api_level=37'
build Android64 "libEGL_angle libGLESv2_angle libGLESv1_CM_angle" "$common $android target_os=\"android\" target_cpu=\"x64\" angle_enable_context_mutex=false"
build Android32 "libEGL_angle libGLESv2_angle libGLESv1_CM_angle" "$common $android target_os=\"android\" target_cpu=\"x86\""
build LinuxHost "libEGL_angle libGLESv2_angle" "$common target_os=\"linux\" target_cpu=\"x64\" angle_use_custom_libvulkan=false angle_enable_gl=false angle_build_tests=false"

mkdir -p "$out/vendor/lib64/egl" "$out/vendor/lib/egl" "$out/host"
for lib in libEGL_angle libGLESv2_angle libGLESv1_CM_angle; do
    cp "$src/out/Android64/$lib.so" "$out/vendor/lib64/egl/"
    cp "$src/out/Android32/$lib.so" "$out/vendor/lib/egl/"
done
cp "$src/out/LinuxHost/libEGL_angle.so" "$src/out/LinuxHost/libGLESv2_angle.so" "$out/host/"
# each file where Meson (meson.build) and the CLI (cli/env.cpp) look for it, of its ABI: lib64 and host x86-64, lib i386; the Android ones
# linked against bionic's libc.so, the host ones against glibc's libc.so.6
check() {  # <file> <machine> <libc>
    [ -s "$1" ] || { echo "build.sh: $1 missing" >&2; exit 1; }
    readelf -h "$1" | grep -q "Machine:.*$2" || { echo "build.sh: $1 is not $2" >&2; exit 1; }
    readelf -d "$1" | grep -q "Shared library: \[$3\]" || { echo "build.sh: $1 does not link $3" >&2; exit 1; }
}
for lib in libEGL_angle libGLESv2_angle libGLESv1_CM_angle; do
    check "$out/vendor/lib64/egl/$lib.so" "X86-64" libc.so
    check "$out/vendor/lib/egl/$lib.so" "80386" libc.so
done
for lib in libEGL_angle libGLESv2_angle; do check "$out/host/$lib.so" "X86-64" libc.so.6; done
(cd "$out" && find vendor host -name '*.so' | sort | xargs sha256sum > SHA256SUMS)
echo "ANGLE $rev with $(ls "$here"/patches/angle/[0-9]*.patch | wc -l) patches:"
cat "$out/SHA256SUMS"
