#!/bin/sh
# sailfish-slint.sh <dest>: Slint's C++ library and slint-compiler, the version manager/fetch-slint.sh pins, built from source in the Sailfish OS
# build root (tools/release/sailfish-rpm.sh --in-root): Slint's prebuilt compiler needs glibc 2.35, Sailfish has 2.30. Rust is the standalone
# toolchain (Sailfish's own rust, 1.75, is older than Slint's 1.92); the sources are rpm/sources' (in /cache/sources), the crates those
# Slint's Cargo.lock pins (--locked), downloaded once to /cache/cargo. Features: std, the software renderer, experimental, testing (the
# manager's slint-testing.h, as in the prebuilt): no winit (it needs
# xdg_wm_base, which Lipstick lacks); the manager draws through its own wl_shell platform (manager/src/wlshell.cpp, -Dwlshell=true).
# <dest> gets fetch-slint.sh's layout (cpp/include/slint, cpp/lib/libslint_cpp.so, bin/slint-compiler, material/) and is reused once built.
set -eu
dest="$1" V=1.18.1 M=1.1.0 R=1.92.0
[ -e "$dest/.built-$V" ] && exit 0
rust=/root/rust work=/root/slint-build
for c in rustc cargo rust-std; do
    mkdir -p "$work/$c" && tar xJf "/cache/sources/$c-$R-aarch64-unknown-linux-gnu.tar.xz" -C "$work/$c" --strip-components=1
    sh "$work/$c/install.sh" --prefix="$rust" --disable-ldconfig >/dev/null
done
export PATH="$rust/bin:$PATH" CARGO_HOME=/cache/cargo CARGO_NET_RETRY=10 CARGO_HTTP_TIMEOUT=300 CARGO_HTTP_MULTIPLEXING=false CARGO_TARGET_DIR="$work/target"
tar xzf "/cache/sources/slint-v$V.tar.gz" -C "$work"
cd "$work/slint-$V"
SLINT_GENERATED_INCLUDE_DIR="$work/gen" cargo build --locked --release -p slint-cpp --no-default-features --features std,renderer-software,experimental,testing
cargo build --locked --release -p slint-compiler --no-default-features --features cpp,renderer-software,image-default-formats  # C++ output only
rm -rf "$dest" && mkdir -p "$dest/cpp/include/slint" "$dest/cpp/lib" "$dest/bin"
cp -r api/cpp/include/. "$work/gen/." "$dest/cpp/include/slint/"
# apklayer patch (docs/PATCHES.md): gcc 10's C++20 views must be default-constructible (before P2325), and the header's fallback view is not
sed -i 's|^\( *\)owning_view(SharedVector<cbindgen_private::IntRect> &&rectangles)$|\1owning_view() = default;\n&|' "$dest/cpp/include/slint/slint-platform.h"
grep -q 'owning_view() = default;' "$dest/cpp/include/slint/slint-platform.h"
install -m755 "$work/target/release/libslint_cpp.so" "$dest/cpp/lib/"
install -m755 "$work/target/release/slint-compiler" "$dest/bin/"
strip "$dest/cpp/lib/libslint_cpp.so" "$dest/bin/slint-compiler"
python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "/cache/sources/material-$M.zip" "$dest"
mv "$dest/material-$M" "$dest/material"
rm -rf "$work"
touch "$dest/.built-$V"
