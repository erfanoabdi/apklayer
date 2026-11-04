#!/bin/sh
# package.sh build <work> | install <work> <destdir>: the deb's build and install steps (debian/rules), offline. docs/INSTALL.md steps 5-6 and 9,
# then the installed layout (docs/RELEASES.md). Inputs, fetched beforehand by tools/release/build-deb.sh:
#   APKLAYER_PREBUILTS  a joined checkout of the prebuilts branch      APKLAYER_BT_SRC  bionic_translation at patches/bionic_translation/REVISION
#   APKLAYER_DEPS       tools/fetch-deps.sh's output                    APKLAYER_SLINT   manager/fetch-slint.sh's output
#   APKLAYER_VERSION    the package version (apklayer --version)
set -eu
repo="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo"
step="$1" W="$(mkdir -p "$2" && cd "$2" && pwd)"
case "$(uname -m)" in aarch64) cpu=arm64;; x86_64) cpu=x86_64;; *) echo "package.sh: no prebuilts for $(uname -m)" >&2; exit 1;; esac
P="${APKLAYER_PREBUILTS:?}" N="$W/native" bt="$W/bionic_translation/b"
# the flags the tree is built and tested with (meson's), not dpkg-buildflags' (LTO, Ubuntu's -Bsymbolic-functions): the path layer and
# bionic_translation interpose symbols across libraries
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS OBJCFLAGS OBJCXXFLAGS FFLAGS FCFLAGS
export APKLAYER_BUILD="$W/b" APKLAYER_BT="$bt" APKLAYER_ANDROID_LIBS="$P/$cpu/android-libs/lib64" APKLAYER_DEPS="${APKLAYER_DEPS:?}" APKLAYER_NO_INSTRUMENT=1

build() {
    rm -rf "$W/bionic_translation" && cp -a "${APKLAYER_BT_SRC:?}" "$W/bionic_translation"
    tools/build bt "$W/bionic_translation"
    rm -rf "$W/b" "$W/m"
    meson setup "$W/b" -Dprefix=/usr -Dbionic_translation="$bt" -Dandroid_libs="$APKLAYER_ANDROID_LIBS" -Dpackage_version="${APKLAYER_VERSION:?}"
    ninja -C "$W/b"
    rm -rf "$N" && mkdir -p "$N/aosp/android-all"  # the staged runtime (docs/INSTALL.md step 6); the package takes its half
    cp -a "$P/$cpu/android-root" "$N/"
    cp "$P/common/Robolectric_all.jar" "$N/aosp/"
    cp -r "$P/common/sim-classes" "$P/common/robolectric-src" "$N/aosp/"
    cp "$P/common/android-all-16-robolectric-13921718.jar" "$N/aosp/android-all/"
    cp "$P/common/android-all-16-robolectric-13921718.jar" "$N/aosp/android-all/android-all-current-robolectric-r0.jar"
    tools/build host "$N"
    tools/build native "$N" "$P/common/javac-shards"
    meson setup "$W/m" manager -Dprefix=/usr -Dlibdir=lib/apklayer -Dslint="${APKLAYER_SLINT:?}"
    ninja -C "$W/m" apklayer-manager
}

install_tree() {
    D="$(mkdir -p "$1" && cd "$1" && pwd)" L="$1/usr/lib/apklayer" S="$1/usr/share/apklayer"
    # /usr/lib/apklayer mirrors a build dir (cli/layout.cpp finds it from the CLI's own path)
    for f in cli/apklayer bridge/shell/apklayer-shell runtime/dex2jvm/dex2jvm.jar; do install -D "$W/b/$f" "$L/$f"; done
    mkdir -p "$L/bionic_translation/b" "$L/native/aosp" "$D/usr/bin" "$S/runtime/host" "$S/bridge/native" "$S/tools" "$D/usr/lib/modules-load.d" "$D/usr/share/applications"
    find "$bt" -maxdepth 1 -name 'lib*_bio.so*' ! -type d -exec cp -a {} "$L/bionic_translation/b/" \;  # the libraries, not their object dirs
    cp -r "$W/bionic_translation/cfg.d" "$L/bionic_translation/"
    # the package's half of the native dir (tools/build runtime links it into the user's runtime bundle)
    cp -a "$N/bin" "$N/android-system-stubs" "$N/native-runtime.jar" "$N/jdk-patch" "$N"/lib*.so* "$L/native/"
    cp -a "$N/aosp/host-classes" "$L/native/aosp/"
    mkdir -p "$L/native/libs" && cp "$APKLAYER_DEPS"/libs/asm-*.jar "$L/native/libs/"  # what an install runs (apklayer.tools.DenyInstrument: ASM)
    # the share dir is the CLI's root: what it and the runtime read from the tree
    cp tools/release/versions "$S/versions"
    cp -r tools/build tools/sysapps.list tools/host-setup "$S/tools/"
    cp runtime/host/jvm.args "$S/runtime/host/"
    cp -r bridge/native/apklayer.prop bridge/native/bionic_translation.cfg bridge/native/android-root bridge/portal "$S/bridge/native/" && mv "$S/bridge/native/portal" "$S/bridge/"
    find aosp/hals -name '*.xml' -exec sh -c 'for f; do install -D -m 644 "$f" "$0/$f"; done' "$S" {} +
    mkdir -p "$L/bridge" && ln -sfn ../../../share/apklayer/bridge/portal "$L/bridge/portal"  # the runtime looks for its helpers above the CLI (HostFilePortal)
    ln -sfn ../lib/apklayer/cli/apklayer "$D/usr/bin/apklayer"
    DESTDIR="$D" meson install -C "$W/m" --no-rebuild >/dev/null
    echo binder_linux > "$D/usr/lib/modules-load.d/apklayer.conf"
    cp debian/apklayer-manager.desktop "$D/usr/share/applications/"
}

case "$step" in
    build) build;;
    install) install_tree "$3";;
    *) sed -n '2,6p' "$0" >&2; exit 2;;
esac
