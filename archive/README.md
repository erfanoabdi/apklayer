# archive/ — superseded work (reference only)

Nothing here is built or used by the current pipeline. Do not extend it or import from it.
Kept for reference and for git history. The decisions that led here are in `HISTORY.md`.

| Path | What it was | Superseded by |
|---|---|---|
| `m0-recon/` | M0 recon notes and the original plan (real ART + framework.jar + HWUI over EGL) | Robolectric method (docs/ARCHITECTURE.md) |
| `seed-m1/` | Hand-grown AOSP seed (Base64, Looper/Handler/MessageQueue on a bare JVM), its smoke test, patch log and link-gap notes | Mac.apk snapshot, then Robolectric android-all |
| `java_mac-era/runtime/java_mac/` | Mac.apk's hand-patched AOSP framework snapshot (3k files) | `android-all-instrumented` (unmodified AOSP, maintained by Robolectric) |
| `java_mac-era/runtime/prebuilt/framework-res.apk` | Platform resources for the java_mac `Boot` path | resources inside android-all |
| `java_mac-era/bridge/` | `nativecocoa.c`/`nativeos.c`/`nativelog.c` (Mac.apk's 71-native host seam ported to Wayland) and `window.c` (M2a first-window demo) | `bridge/src/present.c` + `presentd.c` |
| `java_mac-era/tests/` | `Launch` (Boot.run driver), `AppRunner`, `LoadProbe`, `FrameSmoke` | `tests/rob` driver |
| `java_mac-era/docs/` | Runner recon for Mac.apk's Boot | — |
| `unused-stubs/` | Outer-classpath stubs (Activity/Application/Context/ReactPackage), found to be unnecessary | `android-stub.jar` (only `tests/stubs/android/os/Build.java` remains) |

Why the pivot: the user rejected building on Mac.apk's hand-patched framework because
"manually patched files … won't be good to replicate by the android version updates".
