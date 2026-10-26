# Patches over upstream code

**No patches to AOSP (`android-all`) or Robolectric.** dex2jvm and the converter output get the thin adaptations in the table at the end. All three are otherwise consumed exactly as published or copied.

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
| `MultiDex.install(Context)` → no-op (`DenyInstrument`) | The app's multi-dex files are merged into one jar at conversion; MultiDex's reflection on `BaseDexClassLoader.pathList` fails in the sandbox class loader |
| `Unsafe.getUnsafe()` and reflective `Method.invoke` → `UnsafeAccess` / `Reflect` (`DenyInstrument`) | ART lets any app call `Unsafe.getUnsafe()`; HotSpot throws `SecurityException("Unsafe")` for non-boot callers. Only that one reflective call is special-cased |
| App `startService/stopService/bindService/unbindService` → `Services` (`DenyInstrument`) | Robolectric only fakes `bindService` with a preconfigured binder. The host runs the app's own services in-process (ServiceController) and delivers the real `IBinder` |
| `ShadowHostWebView` (runtime/host, registered by `HostShadowProvider` via META-INF/services, priority 1000) | A subclass of Robolectric's `ShadowWebView` that also queues every `evaluateJavascript` call (Robolectric keeps only the last). No change to Robolectric itself |
| `ShadowHostMediaPlayer`, `ShadowHostSoundPool` (same registration) | Subclasses of Robolectric's shadows that keep its state machine and callbacks and add real playback through engine processes (bridge/media). `HostAudio` is a plain listener on Robolectric's public `ShadowAudioTrack` write hook |
| Manifest ContentProviders are created before the first activity (`AppHost.installProviders`) | Android creates them (highest initOrder first) before `Application.onCreate`; Robolectric only creates them lazily. androidx.startup (WorkManager, emoji2, lifecycle-process) initialises through them. AntennaPod and the fossify apps failed with "WorkManager is not initialized" without it |
| The package `android` (with a placeholder signing certificate) and the app's own signing info are registered (`AppHost.installSystemPackage`); `ShadowHostPackageManager.hasSigningCertificate` | Real devices have both; androidx.media and NewPipe's media browser check callers against them |
| Runtime permission requests are granted at once (`HostPortals.requestPermissions`) | No permission prompt exists on the desktop yet; the result goes to `onRequestPermissionsResult` as on Android |
| `ShadowHostConnectivityManager`, `ShadowHostDownloadManager` (same registration) | Subclasses of Robolectric's shadows: network callbacks fire for the desktop's connection, and enqueued downloads run |
| `HostKeyStoreProvider` (a JCA provider added at app start) | Robolectric has no "AndroidKeyStore"; a software store in the app's data directory stands in. Not hardware-backed |
| Task rules for starting activities (`LaunchRules`, executed in `AppHost.launch`) | Robolectric records `startActivity` and the host used to always create a new activity. Launch modes singleTop/singleTask/singleInstance and the CLEAR_TOP / SINGLE_TOP / CLEAR_TASK flags now reuse (`onNewIntent`) or clear the existing instances like ActivityTaskManager. Nextcloud's login and first-run screens looped forever without it |
| Cross-app intents (`HostApps`, `ManifestFilters`) | The sandbox only knows its own package; an intent no local activity handles is matched against other installed apps' recorded filters and started in their own process. Stand-in for apklayerd's registry until P6 proper |
| bionic_translation linker `SO_MAX` 128 → 512 (`sed` in the build scripts; the dependency is cloned beside the repo, not vendored) | Android's `libandroid_runtime.so` closure is 237 libraries; the linker's fixed soinfo pool of 128 made every later `dlopen` fail with "too many libraries" |
| `bridge/native/android_platform.c`, `scripts/android-stubs.py` | Symbols Android's own libraries import that bionic_translation's libc lacks: real wrappers for fortify/`__b64_ntop`/`memset_explicit`, stubs for the property service, linker namespaces and malloc debugging; generated do-nothing stubs for GL/EGL entry points the JVM-hosted runtime never calls |
| LineageOS tree: `frameworks/native/cmds/servicemanager/Android.bp` + `frameworks/native/services/surfaceflinger/Android.bp` (appended `libapklayer_servicemanager`, `libapklayer_surfaceflinger`: the binaries' sources as shared libraries exporting `main`; plus an empty `services/surfaceflinger/include/` Soong wants) | the tree's real servicemanager and SurfaceFlinger must be loaded by bionic_translation, which loads libraries, not executables. Revert: `git -C ~/los24/frameworks/native checkout cmds/servicemanager/Android.bp services/surfaceflinger/Android.bp` and `rmdir services/surfaceflinger/include` (PLAN P7.1, docs/AOSP-BUILDS.md) |


## Robolectric `DefaultNativeRuntimeLoader` (arm64 Linux)
`runtime/patches/org/robolectric/nativeruntime/DefaultNativeRuntimeLoader.java`: the tree's loader, one line changed (marked "apklayer patch"): `isSupported()` also accepts Linux aarch64, because the shim and the device libraries are arm64. `scripts/build-native-arm64.sh` compiles it into `native-runtime.jar` (it comes first on the classpath) and puts the shim under `native/linux/aarch64/` as well as `x86_64/` (the android-all jars need both copies too).
