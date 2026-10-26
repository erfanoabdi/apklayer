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
| 2026-10-26 | host `libandroid_runtime` (x86_64 glibc variant only) + compdb (`SOONG_GEN_COMPDB=1`) | Only authoritative source of the native module graph (Skia/HWUI/minikin/ICU…) for our x86_64 + arm64 Meson build and GPU HWUI (PLAN P7.1); the host variant is not in the user's `out/` yet (only the device build is) | in ~/los24, existing `out/`: `source build/envsetup.sh && breakfast gsi_arm64 userdebug && SOONG_GEN_COMPDB=1 m out/soong/.intermediates/frameworks/base/core/jni/libandroid_runtime/linux_glibc_x86_64_shared/libandroid_runtime.so` (log `~/apklayer-aosp-build.log`) | started |
| (contingent, PLAN P7.3) | `robolectric-host-android_all` | Only if P7.1's JNI signatures differ from android-all build 13921718 | — | — |
