# AOSP builds log

Rule (user, 2026-10-26, replaces the 2026-10-25 one): the user built LineageOS 24 in the OrbStack
`ubuntu22-x86` VM for apklayer: tree `~/los24`, `breakfast gsi_arm64 userdebug`, existing `out/`
(115 GB, device build in `out/target/product/generic_arm64`). Build the libs and targets we need in
that tree, using that `out/` (incremental), and log each build here before it runs. **Never wipe or clean `out/`**
(user, 2026-10-26: it takes a very long time to rebuild; no `rm -rf out`, `m clean`, `installclean`, no other
product than `gsi_arm64 userdebug`):
`cd ~/los24 && source build/envsetup.sh && breakfast gsi_arm64 userdebug && m <target>`.
Every build gets an entry here **before** it runs.

| Date | Target(s) | Why it is necessary | Command | Result |
|---|---|---|---|---|
| 2026-10-26 | host `libandroid_runtime` (x86_64 glibc) + compdb | (for a Meson/retargeting route to an arm64 glibc library) | `SOONG_GEN_COMPDB=1 m out/soong/.intermediates/frameworks/base/core/jni/libandroid_runtime/linux_glibc_x86_64_shared/libandroid_runtime.so` in ~/los24 | **aborted by the user's decision during Soong/kati analysis, nothing was built**: use the arm64 bionic libraries already in `out/target/product/generic_arm64/system/lib64` through bionic_translation instead (PLAN P7). `out/` untouched (117 GB) |
| (contingent, PLAN P7.3) | `robolectric-host-android_all` | Only if P7.1's JNI signatures differ from android-all build 13921718 | — | — |
| 2026-10-26 | `robolectric-host-android_all` (external/robolectric) | P7.3: the device `libandroid_runtime.so` from ~/los24 needs `TypedValue.setFields(IIIIIIZ)V` (AssetManager JNI), which the prebuilt android-all 16-robolectric-13921718 lacks: Java and native must come from the same tree | in ~/los24, existing `out/`: `source build/envsetup.sh && breakfast gsi_arm64 userdebug && m robolectric-host-android_all` (log `~/apklayer-aosp-build.log`) | started |
| 2026-10-26 | `Robolectric_all` (external/robolectric) | P7: the Maven Robolectric 4.17 shadows are skewed against the newer framework/natives of ~/los24 (font init crash, Lineage-specific resources); the tree's own `external/robolectric` snapshot is the consistent set | in ~/los24, existing `out/`: `source build/envsetup.sh && breakfast gsi_arm64 userdebug && m Robolectric_all` (log `~/apklayer-aosp-build2.log`) | started |
| 2026-10-26 | `libapklayer_servicemanager` (new module appended to `frameworks/native/cmds/servicemanager/Android.bp`, a 10-line `cc_library_shared` of the same sources incl. `main.cpp`) | PLAN P7.1: libbinder needs a context manager; the tree's `servicemanager` is an executable, and bionic_translation loads libraries (our launcher calls its `main`). Revert with `git -C ~/los24/frameworks/native checkout cmds/servicemanager/Android.bp` | in ~/los24, existing `out/`: `source build/envsetup.sh && breakfast gsi_arm64 userdebug && m libapklayer_servicemanager` | started |
| 2026-10-26 | `libapklayer_surfaceflinger` (new `cc_library_shared` appended to `frameworks/native/services/surfaceflinger/Android.bp`: the `surfaceflinger` binary's defaults and sources, so its `main` is exported) | PLAN P7.1: run the tree's real SurfaceFlinger on bionic_translation with apklayer's own HWC/gralloc. Revert with `git -C ~/los24/frameworks/native checkout services/surfaceflinger/Android.bp` | in ~/los24, existing `out/`: `source build/envsetup.sh && breakfast gsi_arm64 userdebug && m libapklayer_surfaceflinger` (after the service-manager build) | queued |

