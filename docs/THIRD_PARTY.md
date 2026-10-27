# Third-party code and artifacts

## In this tree

| Path | Origin | License |
|---|---|---|
| `runtime/dex2jvm/` (`com.macapk.dex`) | Mac.apk (kksimp), DEX→JVM converter, copied verbatim | AGPL-3.0 |
| `tests/uibench/uibench.apk` | AOSP UiBench test app | Apache-2.0 |
| `archive/**` | AOSP (Apache-2.0) and Mac.apk (AGPL-3.0) code from earlier milestones; not built | as marked in each origin |

apklayer itself is AGPL-3.0-or-later (PLAN D5), compatible with all of the above.

## Consumed at runtime (not vendored; pinned in deps/deps.lock, cached in ~/.cache/apklayer/deps)

| Artifact | Origin | License |
|---|---|---|
| `android-all-instrumented-16-robolectric-13921718-i7.jar` (SDK 36) | Robolectric build of unmodified AOSP | Apache-2.0 |
| Robolectric 4.17 (`robolectric`, `shadows-framework`, `sandbox`, `resources`, `nativeruntime`, `simulator`, …) | robolectric.org | MIT |
| `nativeruntime-dist-compat-1.0.19` (HWUI/Skia/SQLite natives) | Robolectric prebuilt from AOSP sources | Apache-2.0 (AOSP) |
| `android-stub.jar` (SDK platform android-36.1 `android.jar`) | Android SDK platform | Apache-2.0 |
| JDK 21 linux-x86 (`~/jdk21-x86`) | LOCAL copy of los24 `prebuilts/jdk/jdk21/linux-x86` | GPLv2 + Classpath Exception |

- `runtime/host/java/com/macapk/loader/MacApkUnsafe.java`: copied verbatim from Mac.apk (AGPL-3.0). The converter redirects an app's `sun.misc.Unsafe` calls to it (ART-layout reads answered safely on HotSpot).

- `bionic_translation` (https://gitlab.com/android_translation_layer/bionic_translation): a build-time dependency, cloned beside this repo and built by `scripts/build-native.sh`; not vendored. Provides the bionic linker/libc shims (derived from android2gnulinux).

- `bridge/ndk/*.c`: apklayer's own small glibc shims for NDK libraries (liblog, libandroid, libEGL). Checked against ATL's `src/libandroid` (needs libutils / is stubs) and AOSP liblog (needs logd): not reusable, so kept small.
- Mesa (llvmpipe, EGL, GLES) and LLVM 15 from the build VM's Ubuntu x86_64 userspace, bundled into `build/native/x86_64` by `scripts/build-native.sh` (MIT / Apache-2.0 with LLVM exception). Not committed.
| `aosp/hals/graphics/mapper/Mapper.cpp` | structure and the metadata-provider pattern follow `external/minigbm/cros_gralloc/mapper_stablec/Mapper.cpp` (Apache-2.0, The Android Open Source Project / ChromiumOS); the buffer model and all implementations are our own |

## Third-party binary used as a fallback (not vendored; fetched by scripts/fetch-robolectric-arm64.sh, pinned by sha256)

| What | Origin | Notes |
|---|---|---|
| `robolectric-nativeruntime-linux-aarch64-4.17-16-robolectric-13921718-2.jar` (sha256 `bc04263c...6ed85`, 6.7 MB): Robolectric's native runtime `libandroid_runtime.so` for Linux arm64 plus a small `NativeRuntimeLoader` (`robolectricarm64.LinuxArm64NativeRuntimeLoader`) | github.com/LavishSonbhurra/robolectric-native-arm64, release `v4.17-16-robolectric-13921718-2` (the user pointed to it, 2026-10-26); built by its author from AOSP `android-16.0.0_r1` (build-info: clang 21.1.8, commit `bba2e238`); licences: Apache-2.0 / MIT, notices in the release | A binary built by an individual: integrity is pinned by hash, authenticity is not verifiable. Inspected: the loader only reflects into Robolectric's `DefaultNativeRuntimeLoader` and extracts the library from its own jar (no network, no process execution). Used only as the fallback flavor of `bin/apklayer` (`$APKLAYER_NATIVE/robolectric-nativeruntime-linux-aarch64.jar`); the default arm64 route is the tree-built device libraries (PLAN P7). Verified 2026-10-26: uibench runs on the arm64 JVM without Rosetta, 19 frames, snapshot identical in size to the x86 baseline |


The tree-host build reuses unchanged `FrameListener.class` and `ScreenUpdateHandler.class` from the pinned MIT simulator-4.17.jar; the tree simulator predates that split. It stages only these classes alongside the host, with the older tree simulator providing sandbox construction.

- `aosp/hals/media/codec2/VpxHeaders.h`: minimal VP9 uncompressed-header and superframe-index checks adapted from the local AOSP `external/libvpx/vp9/decoder/{vp9_decodeframe.c,vp9_decoder.c}`; libvpx, BSD-3-Clause (local `external/libvpx/LICENSE` and `PATENTS`). No decoder code vendored.

- `bridge/hals/audio/audio.c`: own PipeWire adapter, checked against the installed PipeWire 1.0.5 stream/thread-loop headers and [official playback tutorial](https://docs.pipewire.org/page_tutorial4.html). Links system libpipewire (MIT); no third-party implementation vendored. Local AOSP audio worker/driver interfaces will be reused for the Android HAL; ATL has no suitable PipeWire backend.
