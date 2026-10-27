# Third-party code and artifacts

## In this tree

| Path | Origin | License |
|---|---|---|
| `runtime/dex2jvm/` (`com.macapk.dex`) | Mac.apk (kksimp), DEX→JVM converter, copied verbatim | AGPL-3.0 |
| `tests/uibench/uibench.apk` | AOSP UiBench test app | Apache-2.0 |
| `archive/**` (removed; git tag `archive-last`) | AOSP (Apache-2.0) and Mac.apk (AGPL-3.0) code from earlier milestones; not built | as marked in each origin |

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

## License audit for a dual license (2026-10-28, not legal advice)

Question: can apklayer be AGPL-3.0-or-later for everyone plus a commercial license (PLAN D5), distributable as deb/rpm/AUR/Flatpak/AppImage?
A commercial license can only cover code the licensor owns or that is permissively licensed. Findings, from file headers, local clones and the GitHub API; "unverified" marks what was not read in full.

| Component | License | Verdict for dual licensing |
|---|---|---|
| apklayer's own code (`bridge/`, `runtime/host`, `runtime/patches`, `aosp/`, `scripts/`, `tests/`) | AGPL-3.0-or-later (NOTICE: "apklayer contributors"). git: 309 commits by Erfan Abdi, 24 by an `apklayer <agent@apklayer.local>` identity; no CLA | OK if the copyright holder is one person/entity. To decide: authorship of the agent commits (a lawyer's question); a CLA before accepting outside contributions |
| `runtime/dex2jvm/` except `HostDefaults.java` | the vendored files are kksimp/dex2jvm at commit `4c666fd` (public history); upstream LICENSE is **Apache-2.0** (LICENSE/NOTICE not read in full). Upstream has 3 newer commits (2026-10-19/22) | Permissive, OK. Row above in "In this tree" still says AGPL: relabel after updating the copy |
| `runtime/dex2jvm/com.macapk.dex/HostDefaults.java` | "Mac.apk's own" file; Mac.apk is AGPL-3.0 with a commercial license and a CLA owned by its maintainer | **Needs the Mac.apk owner's permission, or replace** (it is a small defaults file) |
| `runtime/host/java/com/macapk/loader/MacApkUnsafe.java` | copied verbatim from Mac.apk, AGPL-3.0 | **Same: permission or rewrite.** Check whether upstream dex2jvm ships a library-mode replacement (its `library` option) |
| Any other file copied from Mac.apk | grep found none beyond the two above (references in docs/tests only) | OK |
| bionic_translation (built beside this repo, patched by `scripts/patch-bionic-translation.sh`) | SPDX in sources: `MIT AND Apache-2.0` (one identifier found by grep; the repo has no LICENSE file; unverified for every file) | Permissive, OK. Keep the patch script as patches, not vendored copies |
| android_translation_layer (ATL) | GPL-3.0 | Reference only; grep finds no copied code (one comment mentions a TLS constant "as on ATL"). Keep it that way: copying any ATL code would force GPL on that file |
| Robolectric 4.17, shadows in `runtime/patches/org/robolectric/*` | MIT | OK. These files are modified copies of MIT code: keep the MIT notice for them (headers not checked, unverified) |
| AOSP / LineageOS tree libraries and HAL code (Apache-2.0; bionic BSD; libc++ Apache with LLVM exception), `aosp/hals/**` derived from AOSP | Apache-2.0 / BSD | OK; ship the NOTICE files of the shipped libraries (not yet collected) |
| libvpx parts (`VpxHeaders.h`, `LICENSE.libvpx`) | BSD-3-Clause | OK |
| Mesa, PipeWire | MIT | OK |
| GStreamer (media bridge) | LGPL-2.1+ | OK if dynamically linked/used as a separate process; do not statically link |
| JDK 21 | GPLv2 + Classpath Exception | OK for running apps on it; bundling (Flatpak/AppImage) needs its legal notices |
| Chromium WebView APK | BSD-style plus many third-party notices | OK; include the APK's own notices if redistributed (unverified) |
| deps.lock (guava, ASM, icu4j, snakeyaml, jspecify, Conscrypt, BouncyCastle, androidx.test, Android SDK `android.jar`) | Apache-2.0 / BSD-3 / MIT / ICU / Android SDK terms (`android.jar` stubs are under the SDK license, not Apache: check before bundling) | OK except the SDK jar question |
| Android apps users run (UiBench is AOSP, corpus F-Droid APKs, Modern Combat 3, WhatsApp) | their own | Not part of apklayer. Do not redistribute corpus or game APKs/data with the repo; `tests/corpus/shots` logs are only logs |

Blocking items before a dual license: (1) Mac.apk owner's permission, or replacement, for `HostDefaults.java` and `MacApkUnsafe.java`; (2) a CLA/ownership decision, including the agent-written commits; (3) the Android SDK `android.jar` terms if it is ever bundled.
Not blocking but open: collect NOTICE/legal files for everything shipped (AOSP libraries, JDK, WebView) before the first package.
