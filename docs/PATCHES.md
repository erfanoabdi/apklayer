# Patches over upstream code

**Current pipeline: zero patches** to AOSP (`android-all`), Robolectric or dex2jvm. All three
are consumed exactly as published or copied.

How apklayer adapts upstream without patching it:

| Need | How (no patch) |
|---|---|
| App resources/manifest in Robolectric | AGP's documented `com/android/tools/test_config.properties` (`android_resource_apk=`) on the classpath |
| dex2jvm output vs Robolectric instrumentation | Post-process with `DenyInstrument` (adds `@DoNotInstrument`); the converter itself is untouched |
| dex2jvm class hierarchy | Run it with `android-all` on its classpath |
| conscrypt reading `Build.SDK_INT` outside the sandbox | Outer-classpath `runtime/host/stubs/android/os/Build.java` (constants only) |
| Graphics mode | Robolectric's own `-Drobolectric.graphicsMode` |

Rule: if a patch ever becomes unavoidable, keep it thin, mark it inline `apklayer patch`,
and log it here. The M1 seed's patch log is in `archive/seed-m1/docs/PATCHES.md`.
