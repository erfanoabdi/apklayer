# Patches over upstream code

**Current pipeline: zero patches** to AOSP (`android-all`), Robolectric or dex2jvm. All three
are consumed exactly as published or copied.

How apklayer adapts upstream without patching it:

| Need | How (no patch) |
|---|---|
| App resources/manifest in Robolectric | The APK is passed as the manifest (`new AndroidManifest(..., apk)`), exactly as Robolectric's simulator `AppLoader` does |
| dex2jvm output vs Robolectric instrumentation | Post-process with `DenyInstrument` (adds `@DoNotInstrument`); the converter itself is untouched |
| dex2jvm class hierarchy | Run it with `android-all` on its classpath |
| JDK module access on SDK 36 | `runtime/host/jvm.args` (Robolectric's own `--add-opens` list) |
| Android API classes on the outer classpath | SDK `android-36.1/android.jar` (as AGP's mockable jar) |
| Graphics mode, looper, SQLite | `FixedConfiguration` as in the simulator's `AppLoader` (NATIVE graphics/SQLite, PAUSED looper) |
| Activity stacking (Robolectric records `startActivity`, doesn't launch) | Host back stack in `AppHost`, emulating `ActivityThread`: pause previous → start new → stop previous and set its decor INVISIBLE only if the new window is opaque; restore on finish |
| Persistent app data (Robolectric uses throwaway temp dirs) | `LazyApplication` ON + data dirs pre-created as links into `apps/<pkg>/data` (see ARCHITECTURE.md "App data") |
| An activity declared in the manifest but missing from the dex | Logged and ignored (the app stays on its screen); Android would crash the app. uibench's `InflatingEmojiListActivity` is such a case |
| A decor that never records its first draw when its activity adds a dialog in `onCreate` | `AppHost` invalidates each newly launched decor once |

Rule: if a patch ever becomes unavoidable, keep it thin, mark it inline `apklayer patch`,
and log it here. The M1 seed's patch log is in `archive/seed-m1/docs/PATCHES.md`.

| `HostDefaults.REDIRECT_EXIT = null` (dex2jvm) | An app's `System.exit` really exits: apklayer runs one app per JVM, so Mac.apk's `MacApkProcessExit` (which fakes ActivityManager in a shared JVM) is not used |
| `MacApkUnsafe.warn` (vendored from Mac.apk) | Mac.apk's `NativeLog.w` (JNI logcat) replaced by a stderr line; no other change |
| App-bundled library relocation (`DenyInstrument`, ASM `ClassRemapper`) | An app that bundles its own Guava gets it moved under `apklayer.relocated.` at install, so it never mixes with the host's Guava 33 in the sandbox class loader. Not done for BouncyCastle (providers are registered by class-name strings) |
