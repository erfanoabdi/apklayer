#!/bin/sh
# libart.so (bridge/native/libart_*.c) as the aarch64 ELF an x86_64 host's guest (arm64 app under the native bridge) loads at /apex/com.android.art/lib64/libart.so:
# the tree's own clang for aarch64-linux-android, bionic's headers, linked against the arm64 libc stub (NEEDED libc.so). Run inside the OrbStack machine.
# usage: build-libart-arm64.sh <tree, ~/los24> <bridge/native dir> <output file>
set -eu
tree=$1 src=$2 out=$3
cd "$tree/prebuilts/clang/host/linux-x86" && cd clang-r596125
clang=$PWD/bin/clang inc=$PWD/lib/clang/22/include
libc=$tree/out/target/product/generic_x86_64/system/lib64/arm64
mkdir -p "$(dirname "$out")"
cd "$src" && "$clang" --target=aarch64-linux-android35 -fPIC -O2 -shared -nostdlib -nostdinc -fuse-ld=lld -Wl,-z,noexecstack -Wl,--build-id=none -Wl,--hash-style=gnu -Wl,-soname,libart.so \
    -isystem "$inc" -isystem "$tree/bionic/libc/include" -isystem "$tree/bionic/libc/kernel/uapi" -isystem "$tree/bionic/libc/kernel/uapi/asm-arm64" -isystem "$tree/bionic/libc/kernel/android/uapi" \
    libart_jni.c libart_symbols.c -o "$out" -L"$libc" -lc
