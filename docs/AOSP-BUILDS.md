# AOSP builds log

Rule (user, 2026-10-25): AOSP builds only when **absolutely necessary**, locally on the
OrbStack `ubuntu22-x86` VM, with `OUT_DIR=~/apklayer-aosp-out`. Never touch los24's own `out/`.
Every build gets an entry here **before** it runs.

| Date | Target(s) | Why it is necessary | Command | Result |
|---|---|---|---|---|
| 2026-10-26 (started) | host `libandroid_runtime` (x86_64, glibc) + `SOONG_GEN_COMPDB=1` | Only authoritative source of the native module graph (Skia/HWUI/minikin/ICU…) needed for our x86_64 + arm64 Meson build and GPU HWUI; Robolectric ships no arm64 or GPU variant | `OUT_DIR=~/apklayer-aosp-out SOONG_GEN_COMPDB=1 m out/soong/.intermediates/.../libandroid_runtime/linux_glibc_x86_64_shared/libandroid_runtime.so` after `lunch aosp_x86_64-trunk_staging-eng` in ~/los24 (not the device variants) | running, see below |
| (contingent, PLAN P7.3) | `robolectric-host-android_all` | Only if P7.1's JNI signatures differ from android-all build 13921718 | — | — |
