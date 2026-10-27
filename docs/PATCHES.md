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

## Robolectric shadows for the device-library (GPU) route — `runtime/patches/org/robolectric/shadows/`
Copies of the tree's shadows (external/robolectric) with thin changes marked "apklayer patch", compiled into `native-runtime.jar` ahead of
`Robolectric_all.jar` by `scripts/build-native-arm64.sh`. They make an app's window a real SurfaceFlinger layer instead of a simulated surface.
- `ShadowSurfaceControl`: no fake `nativeCreate`; the JNI binding (`$$robo$$nativeCreate$nativeBinding`) runs against SurfaceFlinger, without
  the Java metadata Parcel (no native peer here) and without `NOT_ADD_TO_ROOT` (no window manager parents the layer). `initializeNativeObject`
  keeps a layer ViewRootImpl already made (Android 16 client-managed surfaces) and builds one only if the out SurfaceControl is empty.
- `ShadowBlastBufferQueue`: empty (the real libgui BLASTBufferQueue runs); `nativeSetApplyToken` is a no-op (needs the real `android.os.Binder` JNI).
- `ShadowWindowManagerGlobal`: the window session also answers `relayout2`/`relayoutAsync2` (Android 16 `useClientSurface`); the window's layer is placed
  at its frame, in the app's layer stack (`-Dapklayer.layerstack`), stacked by window type then order added (the window manager's share for the compositor);
  `-Dapklayer.tracewindows` prints each window's attrs, requested size and frame.
- `ShadowDisplayEventReceiver`: `nativeInit` with the five-argument signature this tree's framework has while reporting SDK 36.
- `org/robolectric/nativeruntime/DefaultNativeRuntimeLoader`: Linux aarch64 accepted; SurfaceControl, SurfaceSession and BLASTBufferQueue natives registered.

## The runtime shim (`bridge/native/runtime_shim.c`) in the JVM
Loads `APKLAYER_HOST_LIBS` (the host graphics HAL) globally; installs the path layer once libc_bio is loaded; hooks libutils thread creation
(`androidSetCreateThreadFunc`) so binder-pool and callback threads attach to the JVM (what `AndroidRuntime::startReg` does on a device); adapts
`@CriticalNative` natives with a thunk that moves the 7th to 12th arguments; logs the classes it registered under `APKLAYER_RUNTIME_DEBUG`.
`bridge/native/properties.c`: `ro.hyphen.data.dir` may be replaced (the natives extract the hyphenation data to a fresh directory every start).
`bridge/ndk/libnativedisplay.c`: `ADisplay_*` for HWUI's DeviceInfo (one internal display, sRGB 8888).

## Shell: input proxy (`bridge/shell`)
`MSG_ATTACH_INPUT`: an app takes over the input, state and size of the window with a given title (the display's window, owned by the composer or the
virtual display sink). `W_TARGET`/`W_ID` route those messages; `window_attach_input`, `window_drop_proxy`.

## Display startup and sink ownership (2026-10-27)
- `bin/apklayer`: clear stale stack-ready marker before spawning replacement services.
- `scripts/arm64-stack.sh`: create the actual RenderEngine cache files and set Android init’s cache-ready property.
- `bridge/shell/window.c`: reuse the decoration memfd/shm pattern to map an initial blank display frame, so focus and real compositor vsync do not depend on the first Android frame. The buffer is freed on compositor release or window destruction.
- `aosp/hals/graphics/vdisplay/VirtualDisplay.cpp`: return acquired buffers when Wayland releases them; drop old-size queued frames after configure.

## bionic_translation timed mutex locking (2026-10-27)
`scripts/patch-bionic-translation.sh` adds `bionic_pthread_mutex_timedlock` by reusing the existing lock wrappers’ lazy initialization and native-pointer unwrap. Without it the loader falls back to glibc’s timed lock, which overwrites the pointer inside an Android mutex. Android `Mutex::timedLock` is used by SurfaceFlinger dumps and buffer-queue paths. `tests/vm/pthread-timedlock.c` verifies static initialization, contention timeout and reuse against the actual wrapper library. Both native build scripts apply this patch.

## EGL display lifetime (2026-10-27)
`aosp/hals/graphics/egl/Egl.cpp` implements Android EGL’s initialization reference count (from `egl_display_t::initialize/terminate`) around the single host GBM display. `libgui/SyncFeatures.cpp` explicitly relies on it: its temporary initialize/terminate pair must not terminate RenderEngine’s display. The existing `gltest` borrows a display with a live context, then renders and resizes; 12 frames verified on the VM.

## Shared compositor vsync (2026-10-27)
`bridge/shell/vsync.c` retains the existing Wayland frame callback method but serves pending clients from the active mapped window. A covered primary surface is not a clock: the compositor may withhold its callbacks indefinitely. Requests survive source changes, map/configure changes and client teardown; clients such as the native Choreographer need not own a separate toplevel. No timer or synthetic refresh is used.

## Window container placement (2026-10-27)
`runtime/patches/.../WindowSurface.java` supplies the window container that WMS normally parents a client-managed BLAST layer under. Position and stacking belong to that parent, because BLAST sets the child’s destination frame on every buffer. Position subtracts `LayoutParams.surfaceInsets`, following AOSP `WindowState.transformFrameToSurfacePosition`; removal destroys the container. `ShadowWindowManagerGlobal` clamps requested frames to the display so ViewRootImpl remeasures oversized windows. UiBench screenshot checks measure 16px margins on each side before/after resize.

## Headless primary (2026-10-27)
The composer uses `apklayer_display_open_headless`: HELLO/output-refresh/vsync messages with no WINDOW_CREATE and no retained presentation buffers. Callback struct ABI is unchanged. App virtual displays are the only toplevels. `onRefreshStatic` derives the period from the real output mode; variable gaps between requested frame callbacks do not change display refresh. Smoke tools get a stack-0 virtual display in `arm64-stack.sh`.

## Concurrent app windows (2026-10-27)
`bin/apklayer` serializes service startup with flock and uses `scripts/wait-ready.py` (inotify + pidfd) instead of readiness polling. Per-launch PID ids replace truncated package hashes for layer stacks; display names include launch and instance before the package, and logs are distinct per sink. `pointer_forget_window` clears title hover before teardown so a queued leave cannot redraw a destroyed surface. Two GPU JVMs, focus, independent close and survivor resize are covered by the multi-app VM regression.

## Proxied IME and clipboard (2026-10-27)
Shell text-input enable resolves proxy ids and keeps per-window state; compositor enter/leave controls the active field and W_TARGET/W_ID route preedit and commit. Clipboard pipes join the existing poll loop, keeping source text snapshots and echo caches per client; this avoids deadlocking when two JVMs share the shell. ShellClient fills its ByteBuffer before sending clipboard text. The tree ShadowKeyCharacterMap overlay reuses upstream code and suppresses characters under Ctrl/Alt/Meta, matching AOSP modifier matching so Android shortcuts reach EditText. Real Wayland/IBus/uinput coverage lives in tests/vm/input.sh.

Runtime overlay packaging uses scripts/build-runtime-overlay.sh: compile every Java patch against the staged tree runtime, preserve or stage the native shim, and atomically publish after success. Compiler failures leave the working jar intact; build-native-arm64 no longer swallows javac errors.
