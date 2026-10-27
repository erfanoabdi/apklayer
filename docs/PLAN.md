# apklayer — Plan of Record

Status at writing (2026-10-25, revised the same day: D8, D9, D17, P7): **M5 done**. uibench runs with full UI and mouse/keyboard
input in a Wayland window (docs/ARCHITECTURE.md). This document is the single plan from
here to a full Android APK opener on Linux. Every decision below is final unless new
evidence breaks it. If that happens, change this file in the same commit as the code.

---

## 1. Decisions

| # | Topic | Decision | Reason |
|---|---|---|---|
| D1 | Framework | **Robolectric `android-all-instrumented` SDK 36** (Android 16, `16-robolectric-13921718-i7`). Drop SDK 27. Run every app on SDK 36 regardless of its `targetSdk`, as real devices do. | Modern apps and AndroidX need it. Matches the los24 source reference (Android 16). From SDK 35 on, the native graphics runtime ships inside the jar. One SDK means one thing to make solid. |
| D2 | Robolectric | **Pin to the newest Robolectric release that supports SDK 36 and contains the `simulator` module.** Coordinates and sha256 recorded in `deps/deps.lock`. | A single pinned version. The Simulator replaces our JUnit harness. |
| D3 | App host | **Use Robolectric's Simulator** (`org.robolectric:simulator:4.17`, MIT) from the published jar, unmodified (it is on Maven, so no vendored copy). Add apklayer classes beside it: a Wayland `FrameListener`, an input source, and the activity stack. **No JUnit anywhere in the product.** | Copy, don't write. The Simulator is exactly "run an app outside tests". |
| D4 | DEX conversion | **Keep dex2jvm** (AGPL), unmodified. Always run it with the target android-all on its classpath. Cache the output by `sha256(apk) + android-all id + dex2jvm commit`. | It works on real apps today. Replacing it gains nothing for users. |
| D5 | License | **apklayer is AGPL-3.0-or-later.** Add `LICENSE`. | Forced by dex2jvm, and compatible with Apache-2.0 (AOSP) and MIT (Robolectric). |
| D6 | Process model | **One JVM per app.** All windows go to a single per-session native **`apklayer-shell`** (done in P1.6). App JVMs contain **no apklayer native code** until D9. | Crash isolation. App JVM arch can differ from the host's. One component owns Wayland. |
| D7 | Frame transport | Per window: a pixel file in `/dev/shm` (double-buffered, damage rect in header). Control, input and wakeups go over a **Unix domain socket** (pure-Java `SocketChannel` on the app side, no polling). | Removes busy polling and tearing. Pure Java in the app process. |
| D8 | Rendering | **Now:** CPU HWUI/Skia from the Robolectric prebuilt; present only damaged regions. **Later (P7):** apklayer's **own Meson build of AOSP's native runtime** (HWUI, Skia, minikin, androidfw, the `libandroid_runtime` JNI subset) from los24 sources, with HWUI's GPU pipelines enabled (`EglManager` + `SkiaOpenGLPipeline` on Mesa EGL/Wayland; `SkiaVulkanPipeline` on Mesa Vulkan). Its source/flag lists come from **one Soong host build in OrbStack** (see P7). | The prebuilt is AOSP's *host* HWUI variant, which compiles only `platform/host` stubs and excludes `SkiaOpenGLPipeline`, `SkiaVulkanPipeline`, `EglManager` and `VulkanManager` (frameworks/base/libs/hwui/Android.bp). GPU HWUI on Linux therefore means compiling HWUI's device sources for Linux ourselves. The same build gives native linux-arm64 (D17). User rule (2026-10-25): AOSP builds are OK **only when absolutely necessary**, run locally on the OrbStack VM. |
| D9 | Native `.so` in APKs | **ATL's `bionic_translation`**, not libhybris. It loads bionic-linked app `.so` files into a glibc process without an Android system image, builds with Meson, supports x86_64 and aarch64, and is used daily by ATL on real apps. libhybris solves the reverse problem: glibc userlands using Android *vendor HAL/GPU blobs* on Android-kernel phones (Halium: Sailfish, Ubuntu Touch, Droidian). It expects the device's Android `/system`+`/vendor` libraries and focuses on HAL wrappers (gralloc, hwcomposer, camera, sensors). We have no system image, and app NDK libs only need the stable NDK ABI. Alongside it go apklayer **NDK shims** in `bridge/ndk/`: `liblog`, `libandroid` (AAsset*, ANativeWindow*, AConfiguration*), `libEGL`/`libGLESv*` → Mesa, `libOpenSLES`/`libaaudio` → PipeWire. Load the APK's ABI that matches the **app JVM's arch**. No ISA translation. | The proven approach, already built with Meson. JNI is the same standard ABI under HotSpot. APKs almost always ship both arm64-v8a and x86_64. |
| D10 | GL/Vulkan in apps | `GLSurfaceView`, `SurfaceView` and `TextureView` get a real **EGL surface on a `wl_subsurface`** placed at the view's screen rect, rendered by **Mesa** directly. Vulkan goes the same way via the Mesa Vulkan WSI. | Games and video need real GPU output. Mesa speaks the same GLES/EGL/Vulkan as Android drivers. |
| D11 | WebView | **WPE WebKit** (`wpe-webkit-2.0` + `wpebackend-fdo` SHM export), rendered into the `WebView`'s canvas by an apklayer shadow of `android.webkit.WebView`. | WPE is built for embedding into a custom renderer, which matches our bitmap composition. WebKitGTK (ATL's choice) is a GTK widget, which doesn't. |
| D12 | Media / audio | **GStreamer** for everything: `MediaPlayer`, `MediaCodec`/`MediaExtractor` (codec bins), `AudioTrack`/`SoundPool` (appsrc → `pipewiresink`). `ExoPlayer` works through MediaCodec. | One dependency, already on the VM, covers codecs and PipeWire. |
| D13 | Host services | **xdg-desktop-portal** for everything sandbox-relevant: file chooser (SAF `ACTION_OPEN_DOCUMENT`), `ACTION_VIEW` links (OpenURI), camera, notifications, location. Clipboard via `wl_data_device` in the shell. | Works inside Flatpak (D16) and on every desktop. |
| D14 | Google services | **microG** (GmsCore + GsfProxy) as a preinstalled, bundled APK running under apklayer itself. | The only redistributable option. Apps bind to it like on a degoogled phone. |
| D15 | Cross-app | A tiny **`apklayerd`** session daemon (Java, same Robolectric stack) holds the installed-package registry. It routes implicit intents, `bindService` and `ContentResolver` calls between app JVMs over Unix sockets, carrying real **`Parcel` bytes**. No kernel binder. | User-approved "userspace binder-lite". Parcel bytes keep AIDL stubs unmodified. |
| D16 | Distribution | **Flatpak** (`org.freedesktop.Platform` 24.08, openjdk21 extension; android-all and Robolectric jars inside). A `.deb` later. | Portals and sandboxing for free, and the runtime is the same on every distro. |
| D17 | Host platforms | **Both x86_64 and arm64 Linux are first-class, fully native targets.** x86_64 works natively from P1, using Robolectric's prebuilt native runtime. Native arm64 arrives in **P7** with apklayer's own native-runtime build (D8). Rosetta/FEX are **not** a product path: the x86_64-JVM-under-Rosetta setup on the Parallels VM is only a development stopgap until P7, and is removed after. | Robolectric publishes no linux-arm64 native runtime, so arm64 needs our own build of the same AOSP native libs. That is one piece of work shared with GPU HWUI (D8). |
| D18 | Build | **Meson for everything.** C targets (shell, NDK shims) plus Java via Meson `jar()` targets (host, dex2jvm, stubs). Dependencies come from `deps/deps.lock`, fetched once by `scripts/fetch-deps.sh` into `~/.cache/apklayer/deps`. Nothing large goes in git. | User preference (Meson). One build. Reproducible, offline after the first fetch. |
| D19 | CLI | `apklayer install <apk\|apks\|xapk>`, `apklayer run <pkg\|apk>`, `apklayer list`, `apklayer uninstall <pkg>`. Install converts and caches the DEX, extracts the icon by rendering the launcher icon drawable with the framework, and writes a `.desktop` entry plus an `application/vnd.android.package-archive` MIME handler. | It behaves like a native app installer. |
| D20 | App data | Per app `~/.local/share/apklayer/apps/<pkg>/` holding `data/` (Context data, cache and files dirs, databases, shared_prefs), `apk/` and `classes.jar`. External storage is `~/.local/share/apklayer/storage/emulated/0` (Android/data scoped). | Survives restarts. Matches Android's layout. |
| D21 | Split APKs | Accept `.apks`/`.xapk`/`.apkm`. Use the base APK for resources, merge DEX from all splits, and pick ABI/density/locale config splits that match the host. | Most store downloads are splits today. |
| D22 | Fixes to framework behaviour | Only as **apklayer shadows** (`runtime/host/shadows`, Robolectric's own extension mechanism). Never edit android-all, Robolectric or dex2jvm. Every shadow is listed in docs/PATCHES.md. | This keeps the "no hand patches" principle. Shadows are how Robolectric itself adapts AOSP. |
| D24 | AOSP builds | **On the OrbStack `ubuntu22-x86` VM, in the user's LineageOS 24 build tree `~/los24` (`breakfast gsi_arm64 userdebug`, its existing `out/`; user, 2026-10-26: "you can also build libs and targets if you need")**. Each build is listed with its justification in docs/AOSP-BUILDS.md before it runs; build only what is needed. Planned: P7.1 (plus P7.3 if JNI signatures mismatch). | User rule. Soong is the only authoritative resolver of AOSP's native module graph (Skia, HWUI, minikin, ICU, HarfBuzz…); re-deriving it by hand would be thousands of lines of guesswork. |
| D23 | Verification | Every feature lands with a **scripted headless check**: the existing `-Dapklayer.script`/snapshot mechanism, run by `scripts/check.sh` on the Mac (fast) and on the VM (real Wayland). The corpus (P3) gates releases. | The user tests by hand; we must catch regressions first. |

---

## 2. Target architecture

```
                 ┌──────────────────────── apklayer-shell (native, host arch) ─────────────────────────┐
 Wayland ◄──────►│ one xdg_toplevel per app window · wl_subsurfaces for GL/video (D10) · wl_seat input │
 compositor      │ clipboard · decorations · per-window shm buffers · Unix socket server              │
                 └───────▲──────────────────────────────────────────────▲───────────────────────────┘
                         │ shm frames + socket (D7)                      │
 ┌───────────────────────┴───── app JVM (one per app, D6) ─────┐  ┌──────┴──────── apklayerd (D15) ─────┐
 │ android-all SDK 36 (unmodified) + Robolectric shadows        │  │ package registry · intent routing   │
 │ + apklayer shadows (WebView/WPE, media/GStreamer, portals)   │◄►│ bindService / ContentResolver relay │
 │ Simulator host: lifecycle, activity stack, windows, input    │  │ (Parcel bytes over Unix sockets)    │
 │ app classes (dex2jvm, cached) · APK .so via bionic_translation│  └─────────────────────────────────────┘
 └──────────────────────────────────────────────────────────────┘
```

Target repo layout:

```
bridge/shell/        apklayer-shell (C: main, client, window, decor, pointer, keyboard, registry)
bridge/ndk/          NDK shim libraries (C)                         (P4)
runtime/dex2jvm/     unchanged
runtime/host/        Java: simulator/ (vendored, MIT), apklayer/host/ (launcher, Wayland FrameListener,
                     input, activity stack, data dirs), apklayer/shadows/, apklayer/cli/, stubs/
runtime/apklayerd/   Java session daemon                            (P6)
deps/deps.lock       pinned Maven coordinates + sha256
scripts/             fetch-deps.sh, check.sh, vm-run.sh
tests/corpus/        corpus.lock (APK names, versions, sha256), expected snapshots
packaging/flatpak/   manifest                                       (P7)
```

---

## 3. Phases

Each phase ends with its **exit test**. Do not start the next phase until it passes. Commit
at every step. Update memory.md and goal.md when a phase completes.

### P1 — Product foundation: `apklayer run any.apk` (M6)

1. ✅ (done 2026-10-25) **Restructure** per the layout above. Move `tests/rob/*` → `runtime/host/`, `tests/stubs` →
   `runtime/host/stubs`, and `bridge/src/*` → `bridge/shell/`. Keep `tests/` for test assets only.
2. **deps.lock + fetch-deps.sh.** Record exactly what `~/rob` holds today. Then add the SDK 36
   android-all-instrumented jar and the pinned Robolectric release (D1, D2). This is a one-time
   fetch into `~/.cache/apklayer/deps`; the VM copy is synced from the Mac cache, never fetched twice.
3. **Meson build** (D18): a top-level `meson.build` with `subdir('bridge/shell')`,
   `subdir('runtime/dex2jvm')` and `subdir('runtime/host')`. Produces `apklayer-shell`,
   `dex2jvm.jar` and `apklayer-host.jar`.
4. **SDK 36 switch.** Point the host at SDK 36 and re-run dex2jvm against the SDK 36
   hierarchy. Delete the `Build` outer stub if conscrypt no longer needs it, and verify
   `android-stub.jar` is still required (drop it if not). Exit check: uibench passes the
   existing scripted run on SDK 36.
5. **Vendor the Simulator** (D3). Copy `simulator/src/main/java` into
   `runtime/host/simulator/`, add its LICENSE, and record the source commit in
   `runtime/host/simulator/UPSTREAM`. If it needs APIs newer than the pinned jars, bump the pin
   (D2); never edit vendored files. Write `apklayer.host.Main`, which:
   - builds the sandbox via `SandboxBuilder` (SDK 36, our classpath),
   - writes `test_config.properties` equivalents programmatically (`android_resource_apk`),
   - resolves the launcher activity with the real `PackageManager`,
   - runs the activity stack and window composition (ported from `RunUiBench`: back stack,
     `WindowManagerGlobal` windows, input → topmost window),
   - registers `WaylandFrameListener` (a socket and shm client of apklayer-shell).
   Delete `RunUiBench` and the JUnit dependency.
6. **Shell protocol v1** (D7). In `apklayer-shell`: a Unix socket at
   `$XDG_RUNTIME_DIR/apklayer/shell.sock`. Messages are length-prefixed little-endian structs:
   `HELLO(pkg,pid)`, `WINDOW_CREATE(id,w,h,title)`, `FRAME(id,buf,damage)`,
   `WINDOW_DESTROY(id)` from the app; `INPUT(id,...)`, `CONFIGURE(id,w,h,scale)` and
   `CLOSE(id)` from the shell. Two shm buffers per window, and the shell releases a buffer after
   `wl_buffer.release`. Frames are sent only when the view tree drew something
   (`ViewRootImpl` traversal / damage); there is no 16 ms loop.
7. **Data dirs** (D20). Point Context data, cache, files and database dirs and external storage
   at the per-app tree, by shadowing `ContextImpl` dir getters if Robolectric's temp-dir
   plumbing can't be configured.
8. **CLI + install cache** (D19, D4). `apklayer install/run/list/uninstall`. Conversion
   happens at install. `run` starts `apklayer-shell` if it isn't running and spawns the app JVM
   with the right flags. On the arm64 dev VM until P7: x86_64 JVM + `-XX:TieredStopAtLevel=1
   -XX:+UseSerialGC` under Rosetta (development stopgap only, D17).
9. **Desktop entries.** Render the launcher icon drawable to a 256 px PNG through the
   framework. Write the `.desktop` file and register the MIME handler.

**Exit test P1:** on the VM, `apklayer install uibench.apk && apklayer run
com.android.test.uibench` works, and so do two non-uibench pure-Java APKs from the corpus
seed list (P3.1). The headless check passes on the Mac. Closing the window exits the JVM.
Data written by the app survives a restart.

### P2 — Desktop-grade window and input (M7)

1. **Resize:** `CONFIGURE` → `RuntimeEnvironment.setQualifiers("w<dp>dp-h<dp>dp")` +
   display metrics, then relayout. A minimum size comes from the app's layout.
2. **HiDPI:** `wl_output`/fractional-scale → `densityDpi` (160 × scale). Buffers are rendered
   at device pixels.
3. **Orientation:** honor `screenOrientation`: portrait apps open in a portrait-shaped window,
   landscape apps in a landscape one. `setRequestedOrientation` resizes the window.
4. **Pointer:** the wheel and touchpad axis (`wl_pointer.axis`, `axis_value120`) become
   `ACTION_SCROLL`; for views that don't handle scroll, synthesize drag-fling. Hover becomes
   `ACTION_HOVER_*`, and the right button becomes long-press.
5. **Touch:** `wl_touch` → multi-pointer `MotionEvent`s.
6. **Keyboard:** xkbcommon in the shell maps keys to Android `KEYCODE_*` with meta state.
   Text goes through the focused `InputConnection` (commitText/setComposingText), with IME
   composition via `zwp_text_input_v3`. Hardware Back = Esc; Alt+Left = Back.
7. **Clipboard:** `wl_data_device` ⇄ `ClipboardManager` (text, URIs).
8. **Multiple windows per app:** each `WindowManagerGlobal` top-level window that is not
   attached to an activity (toasts, overlays) → its own `wl_subsurface`. Dialogs stay
   composited into the activity window.
9. **Lifecycle:** window minimized or unfocused → `onPause`/`onStop`; restored →
   `onRestart`/`onResume`. On close: the activity finishes and the process exits when the
   stack is empty.

**Exit test P2:** a corpus app with text fields accepts typing (including Unicode), scrolls
with the wheel, resizes without restarting, and renders sharp at 200% scale.

**P2 status (2026-10-26):** all nine items implemented and tested; two caveats listed below.

| Item | State |
|---|---|
| 1 Resize | ✅ in-place rebuffer of the toplevel; in-place configuration change (activities are not recreated yet); compositor resize, edge/corner drag and maximize verified on the VM |
| 2 HiDPI | ✅ headless (200% → 2x pixels, `wl_output` v2, buffer scale, pointer scaling, startup scale applied before launch); ⚠ **not verified on a real 2x output** (the VM cannot do 2x) |
| 3 Orientation | ✅ requested portrait/landscape (manifest or `setRequestedOrientation`) reshapes the window |
| 4 Pointer | ✅ wheel → `ACTION_SCROLL` (dialogs included); wheel replayed as touch drag/fling for views without scroll support; hover and right-click (= long press) plumbed |
| 5 Touch | ✅ `wl_touch` → multi-pointer stream (headless test; no touch hardware on the VM) |
| 6 Keyboard | ✅ xkb, key repeat, Unicode typing, D-pad/Tab navigation, Esc/Alt+Left/Backspace = Back; IME via `zwp_text_input_v3` (headless test; ⚠ not exercised with a real input method) |
| 7 Clipboard | ✅ text, both directions, verified on the VM with `wl-copy`/`wl-paste` |
| 8 Multiple windows | ◐ dialogs/popups/toasts are separate windows for input and are composited into the app window; separate `wl_subsurface`s only if a real need appears |
| 9 Lifecycle | ✅ inactive → `onPause`, minimized → `onStop`, active → `onResume`/restart |
| Window decoration | ✅ client-side title bar: Back, Minimize, Maximize/Restore, Close, drag-to-move, double-click maximize, edge/corner resize with resize cursors |

Tests: `tests/p2-host.sh [group]` (headless, Mac) and `tests/vm/e2e.sh [group]` (real uinput input +
`prlctl capture` screenshots on the VM). Run only the groups for the code you changed.

### P3 — Compatibility engine (runs continuously from here on)

1. **Corpus** `tests/corpus/corpus.lock`: 30 F-Droid APKs, pinned version + sha256, fetched
   once into the deps cache. Three tiers:
   - **T1, pure Java/Kotlin UI** (must pass from P3 on): e.g. Markor, Simple Calculator-class
     apps, Etar, Material Files, F-Droid client, Aegis (Java crypto), Catima, OpenTracks.
   - **T2, WebView/media** (pass after P5): AntennaPod, NewPipe, an RSS reader with WebView.
   - **T3, native code** (pass after P4): Termux, Organic Maps, KeePassDX.
2. `scripts/check.sh corpus` runs every app headless: install → run → 300 frames → snapshot →
   tap the center of the first clickable view → snapshot. A pass means no uncaught exception,
   a non-blank frame, and a second activity or state change. Results go to
   `tests/corpus/results.md` with snapshots.
3. **Fix loop:** for each failure, find the missing behaviour, then fix it in this order:
   Robolectric already has a shadow → enable or configure it; else write an apklayer shadow
   (D22); never touch upstream. Log every shadow in docs/PATCHES.md.

**Exit test P3:** ≥ 90% of T1 passes.

**P3 status (2026-10-26):** corpus fetched (`scripts/fetch-corpus.sh`, `tests/corpus/corpus.lock`, 17 T1 apps from F-Droid), harness
fixed (non-blank = >1% of pixels differ from the background). **T1: 7/17 pass (41%)**, up from 2/17 after shared fixes only:
`MacApkUnsafe` vendored, no `System.exit` redirect, app-bundled Guava relocated, `<activity-alias>` launchers, a primary external
volume for the sandbox user. Remaining failures are app-specific (user direction: do not chase individual apps): BouncyCastle
class-loader clash (amaze, aegis), `WorkManager` not initialized (fossify calendar), `Display from a non-visual Context` (osmand),
ExceptionInInitializerError (Material Files), hangs (AnkiDroid, fossify messages), and apps that end after the first frame. The fix
loop continues in the background of later phases; the 90% exit test is **not** met yet.

### P4 — Native libraries in APKs (M8)

1. Vendor ATL's `bionic_translation` (Meson subproject in `subprojects/`, from the local ATL
   checkout or its pinned upstream). Build it for the **app JVM's arch** (x86_64 on the VM:
   `gcc-x86-64-linux-gnu` cross toolchain, one-time install).
2. A `System.loadLibrary`/`Runtime.load` path through a host shadow: resolve
   `lib/<abi>/lib*.so` from the APK (extracted at install), and load it via bionic_translation's
   linker. JNI_OnLoad then runs normally under HotSpot.
3. NDK shims `bridge/ndk/` (D9), in this order: `liblog` → `libandroid` (AAssetManager over
   the APK, AConfiguration, ALooper, ANativeWindow backed by a shell subsurface) → `libEGL`/
   `libGLESv2`/`libGLESv3` (re-export Mesa, with EGL native window = our ANativeWindow) →
   `libOpenSLES`/`libaaudio` (GStreamer/PipeWire) → `libvulkan` (Mesa loader) →
   `libmediandk` (GStreamer).
4. ABI selection at install: the JVM arch's ABI → `armeabi-v7a`/`x86` are not supported →
   if the APK has no matching ABI, refuse with a clear message.

**P4 status (2026-10-26):**
- ✅ P4.1 `bionic_translation` (cloned from its upstream, not vendored) is built for x86_64 by `scripts/build-native.sh` in the OrbStack
  x86_64 VM (libunwind 1.8 from source, clang for `libstdc++_bio`, a RELR compat header for glibc 2.35); the results land in
  `build/native/x86_64/` and `vm-run.sh` ships them to the Parallels VM.
- ✅ P4.2 `System.loadLibrary/load` and `Runtime.loadLibrary/load` in app classes are rewritten at install (`DenyInstrument`) to
  `apklayer.host.NativeLoader`, which loads the `.so` with the bionic linker through the JNI helper `bridge/native/native_loader.c`
  (`libapklayer_native.so`), runs `JNI_OnLoad`, and binds the exported `Java_*` functions with `RegisterNatives`.
- ✅ P4.4 install extracts `lib/<abi>/*.so` for the app JVM's ABI (x86_64 under the Rosetta stopgap).
- ✅ Verified: **CPU-Z loads its real `libcpuid.so` and starts**; Termux and KeePassDX start (`tests/vm/native.sh [apk]`). Shared platform fixes found on the way: `MultiDex.install` no-op, in-process services (`apklayer.host.Services`), `Unsafe.getUnsafe()` (`UnsafeAccess`, `Reflect`).
- ◐ P4.3 NDK shims (`bridge/ndk/*.c`, each built as a glibc `<name>_shim.so.0` and mapped from `<name>.so` by the bionic_translation cfg.d): **liblog ✅, libandroid ✅ (assets over the APK, ALooper, AConfiguration; ANativeWindow stubs)** (KeePassDX's `libaes.so` loads with it). EGL/GLES shim ✅ (Mesa llvmpipe surfaceless bundled, window surfaces are pbuffers; ~180 MB with LLVM). **Native GL → window ✅** (2026-10-26, `tests/apps/hello-gl2`, checked by `tests/vm/native.sh build/hello-gl2.apk`: `HostSurfaces` gives each SurfaceView a real `HostSurface`; libandroid's `ANativeWindow_fromSurface` reads its id/size over JNI; libEGL's `eglSwapBuffers` reads pixels back to `$APKLAYER_GLDIR/<id>.rgba`; the host composites them into the window frame; known cost: ~1 core at 30 fps under llvmpipe+Rosetta, per-pixel Java blit to optimise). Pending: audio, vulkan, mediandk and the GLES `hello-gl2` sample. Design note for ANativeWindow/GL: the app JVM is not a Wayland client (the shell is), so GL must render off-screen (Mesa surfaceless/llvmpipe, which needs x86_64 Mesa libs bundled for the Rosetta userspace) and hand pixels to the shell as a subsurface buffer.
  `libstdc++_bio` is built without `__cxa_guard` (needs libc++'s `<atomic>`).

- Java GL (`GLSurfaceView`) finding (2026-10-26, CPU-Z `OGLActivity`): `HostSurfaces` now delivers `surfaceCreated/Changed` to SurfaceView callbacks (Robolectric's fake holder never does), so the GL thread starts, then fails in `EGLContext.getEGL()` = `Stub!`: Robolectric acquires only `javax.microedition.khronos.opengles.GL`, so the EGL10 classes come from the outer `android-stub.jar`, and the real `EGLImpl`/`GLImpl` natives live in `libandroid_runtime`, which we do not have. Fix options: real JNI for gles_jni over our Mesa shim (large), or shadows returning fake EGL objects + GL strings (small). Open; not on the P4 exit path (that is NDK GL).

**Exit test P4:** corpus T3 apps start and render. A simple NDK GLES sample (`hello-gl2`)
draws via Mesa in its subsurface.

### P5 — Platform services (M9)

Each item is an apklayer shadow plus a host backend, with its own scripted check:
1. **WebView → WPE WebKit** (D11) — ◐ first slice working (2026-10-26, `tests/vm/webview.sh`): `HostWebViews` drives a `bridge/webview/apklayer-webview.py` engine process per WebView (WebKitGTK 4.1 offscreen, stopgap for WPE, frames in the GL frame format composed over the view) from what Robolectric records: `loadUrl`, `loadData*` → load; `evaluateJavascript` → result callback; `WebViewClient.onPageFinished`. Input: clicks (on release) and the wheel reach the engine (`HostWebViews.pointer`). Also done: `addJavascriptInterface` (the page's synchronous XHR to the engine's `apklayer://bridge/...` scheme, answered by the host), `onPageStarted`, `onReceivedTitle`, `shouldOverrideUrlLoading`, every `evaluateJavascript` call in order (apklayer's own `ShadowHostWebView`, registered by `HostShadowProvider` through META-INF/services with higher priority than Robolectric's). Also: keyboard typing into the focused page element (characters, Backspace, Delete, Enter), history (`canGoBack`/`goBack`/`goForward`/`reload`, `getUrl`/`getTitle` from the engine). Pending: cookies/settings, load errors, drag/touch scroll, file upload/download, downloads; layout (Robolectric's WebView provider is a no-op proxy: the view is measured via a wrapped ViewDelegate but never gets a frame, so the engine rect is the parent's origin + measured size). Alternative noted for later: the real Android System WebView APK (needs binder, isolated services, a GPU process). Original item: load and run JS, `evaluateJavascript`,
   `addJavascriptInterface`, `WebViewClient` callbacks, input forwarding, cookies in the app
   data dir.
2. **Media → GStreamer** (D12) — ◐ MediaPlayer audio ✅ (2026-10-26, `tests/vm/media.sh`): `ShadowHostMediaPlayer` keeps Robolectric's state machine and listener timing, takes the duration from `gst-discoverer-1.0`, and drives `bridge/media/apklayer-media.py` (GStreamer playbin) for start/pause/stop/seek/volume/loop. `AudioTrack` PCM streams to the desktop audio server (`HostAudio` listens to Robolectric's write listener, one `pw-play` per track; a full pipe blocks the writer like a real track) and `SoundPool` loads, completes and plays (`ShadowHostSoundPool`, gst-play). Video to a SurfaceView ✅ (`setDisplay`/`setSurface` → the engine's appsink writes RGBA frames that `HostSurfaces` composes; `tests/vm/video.sh`; 640 px wide, software path). **`MediaCodec` decoders ✅ for VP8/VP9/H.264/AV1 video and Opus/AAC/MP3 audio** (H.264/AAC/AV1 need `gstreamer1.0-libav` and `-plugins-bad` on the VM) (2026-10-26, `tests/vm/codec.sh`: `HostCodecs` advertises them in `MediaCodecList` and plugs into Robolectric's codec simulation; each decoder is a GStreamer engine process, `bridge/media/apklayer-codec.py`; decoded RGBA frames reach the Surface when the app releases a buffer with render, PCM goes to the app's AudioTrack). Pending: HEVC, encoders, MediaExtractor/MediaMuxer, async-mode and flush edge cases. Also pending: content:// and file-descriptor data sources, video size callbacks,  SoundPool loops/stop/rate, AudioRecord, MediaCodec, MediaExtractor. Original item: MediaPlayer (local/http), AudioTrack, SoundPool, MediaCodec
   (decode to Surface → subsurface; decode to ByteBuffer), MediaExtractor/MediaMuxer basic.
3. **Portals** (D13) — ✅ so far (2026-10-26, `HostPortals`): `ACTION_VIEW` of http/https/mailto/tel/geo that no app activity handles → `xdg-open`; notifications the app posts → `notify-send` ; `startActivityForResult` now works: a child activity's `setResult` is delivered to its caller, and document pickers (OPEN_DOCUMENT, GET_CONTENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE) open a desktop file dialog (zenity; `APKLAYER_PICKER` overrides it for tests) and return `file:` URIs. Check: `APKLAYER_PICKER='echo /etc/hostname' APKLAYER_JOPTS='-Dapklayer.runms=14000 -Dapklayer.script=9000:pick:android.intent.action.OPEN_DOCUMENT' scripts/vm-run.sh` prints `picked`. Notification actions and the portal form of the chooser pending. Check: `APKLAYER_JOPTS='-Dapklayer.runms=16000 -Dapklayer.script=9000:notify:Hello+9500:view:https://example.org/' scripts/vm-run.sh` prints `notification:` and `opened`. file chooser for SAF; OpenURI for outbound `ACTION_VIEW`/`SEND`;
   Notification → org.freedesktop.Notifications (with actions → PendingIntent); camera via
   PipeWire camera portal → Camera2 minimal; location → GeoClue portal → LocationManager.
4. **Connectivity** — ✅ first slice (2026-10-26, checked in `tests/vm/media.sh`): the default network is an unmetered Wi-Fi one and registered `NetworkCallback`s get `onAvailable`/`onCapabilitiesChanged` (`ShadowHostConnectivityManager`; following NetworkManager's real state is pending); `DownloadManager` downloads over HTTP into the destination or `Download/` and broadcasts `ACTION_DOWNLOAD_COMPLETE` (`ShadowHostDownloadManager`; progress, pause/retry, notifications pending). Original item: ConnectivityManager/NetworkCallback reflect NetworkManager state;
   DownloadManager → a JVM HTTP client into the app's external dir.
5. **Accounts/keystore** — ◐ AndroidKeyStore AES/HMAC keys ✅ (2026-10-26, `HostKeyStoreProvider`: a software JCEKS store in the app's data dir behind `KeyStore.getInstance("AndroidKeyStore")` and `KeyGenerator.getInstance("AES", "AndroidKeyStore")`; checked in `tests/vm/media.sh`). `AccountManager` is Robolectric's local in-memory store (enough for apps that keep an account list; persistence across runs pending). Pending: key pairs (RSA/EC with certificates). Original item: AndroidKeyStore backed by a per-app software keystore file
   (BouncyCastle). AccountManager is local.
6. **microG** (D14): bundled, auto-installed. GCM/FCM out of scope until accounts work.

- **P5 performance follow-up (user, 2026-10-26: NewPipe video+audio play but are "very very laggy"; revisit):** likely causes to measure first: (a) `MediaCodec` decode is synchronous per input buffer over pipes (engine 300 ms pull timeout; frames copied through Java), (b) RGBA frames written to a file and re-read by the host for every frame (`HostSurfaces.blit`, per-pixel scaling in Java), (c) the app JVM runs under Rosetta with C1 only, (d) llvmpipe/Python engines. **Measured (2026-10-26, `-Dapklayer.perf=true`, NewPipe 360p H.264+AAC on the VM):** video decode 8.6 ms/frame (engine incl. scale+RGBA), AAC 0.6 ms/buffer, window compose 1.0 ms, render 1.1 ms, app JVM ~18% CPU: not CPU-bound. Prime suspect: playback pacing. `ShadowAudioTrack.getPlaybackHeadPosition` counts bytes *written*, so ExoPlayer's audio clock runs ahead of what is actually audible (pipe + pw-cat buffering) and frames look late/dropped; fix idea: head position from wall clock since play(), reported latency. Also earlier fixed: 300 ms per-input pull wait in the codec engine (46 frames: 4.1 s -> 0.2 s). Fix direction: shared-memory/dmabuf frame path straight to a Wayland subsurface (P5.2), async codec pipeline, native engines instead of Python.

- **T2 status (2026-10-26, VM smoke runs, `tests/vm/smoke.sh`):** AntennaPod renders its home screen; NewPipe browses YouTube and plays video + audio (laggy, see above); Nextcloud shows its first-run screen. ownCloud (`org.nitri.ownCloud`) is not on F-Droid under that name, so it is not pinned. Formal T2 percentage (corpus.lock + a scripted pass criterion on the VM) is still to do.

- **Revisit list (user, 2026-10-26):** (1) NewPipe video/audio lag (an audio-clock fix made no difference to the user), (2) **desktop notifications were annoying** (every app notification, including ongoing/media/progress updates, became a popup): ✅ policy done 2026-10-26 (`HostPortals.followNotifications`, `tests/vm/notify.sh`): ongoing/foreground-service notifications and channels of low importance never pop up, an unchanged notification is not shown again, a changed one replaces its popup (`notify-send --replace-id`). Still open: per-app mute, closing a popup when the app cancels it, notification actions/PendingIntents, the user's own judgement of what is still too much.

**Exit test P5:** corpus T2 ≥ 80%. A video plays with sound, a WebView-based app logs in to a
test page, and "Open link" opens the host browser.

### P6 — Multiple apps and the system (M10)

1. **First slice ✅ (2026-10-26, `tests/vm/share.sh`):** at install the app's activity intent filters are read from its manifest (`ManifestFilters`) into `info.properties`; a host that cannot handle an implicit intent (`HostApps.route`) matches it against every other installed app and starts the first match through the CLI, handing the intent (action, type, data, component, string extras) over in a file that the new process picks up instead of the launcher. Results go back to the caller ✅ (`startActivityForResult` across apps: the callee writes its `setResult` to a file named in `APKLAYER_RESULT_FILE` when the activity it was started for finishes; the caller's host delivers it, or RESULT_CANCELED if the callee ends without one; also in `tests/vm/share.sh`). A chooser for several matching apps ✅ (`HostApps.choose`: a zenity list, `APKLAYER_CHOOSER` overrides it for tests; cancel = RESULT_CANCELED). Pending: bindService/ContentResolver/broadcast relay, reusing a running instance, non-string extras. Original item: **`apklayerd`** (D15): starts with the shell. Holds the package registry from the install
   cache, and resolves implicit intents across installed apps (chooser through the shell when
   ambiguous).
2. **Cross-process calls:** `startActivity` to another package spawns or reuses that app's JVM.
   `bindService`/`ContentResolver` to another package is relayed as Parcel bytes; the
   receiving JVM unmarshals with the real `Parcel`/AIDL `Stub`.
3. `BroadcastReceiver`s from the manifest: `BOOT_COMPLETED`-style system broadcasts are not
   sent; app-to-app explicit broadcasts are relayed.
4. **`/data/data/<pkg>` for hardcoded paths** (found 2026-10-26 with Termux: renders its UI, then "files directory not found at /data/data/com.termux/files"): bind the app data dir there inside the `bwrap` mount namespace. On Ubuntu 24.04 unprivileged userns is blocked by AppArmor (`apparmor_restrict_unprivileged_userns=1`: `bwrap: setting up uid map: Permission denied`), so this needs a bwrap AppArmor profile (Flatpak ships one) or `sysctl` on the VM.
4b. **Sandboxing outside Flatpak:** each app JVM runs under `bwrap`, seeing only its data dir,
   storage, the deps cache and the shell socket.

**Exit test P6:** sharing text from one installed app to another works. An app using microG's
location or SafetyNet-free APIs runs.

### P7 — Own native runtime: native arm64 + GPU HWUI (M11)

> **P7.0 result (2026-10-26): ✅ the device `libandroid_runtime.so` LOADS on the arm64 VM** (no Rosetta, no recompilation): its
> 237-library closure (91 MB, collected from `~/los24/out/target/product/generic_arm64`) is opened by bionic_translation.
> What it took: bionic_translation built natively for arm64 (libunwind 1.8.1 from the local source, `libegl-dev`,
> `libbsd-dev`); `SO_MAX` 128 → 512 in its linker (one line, PATCHES.md); `bridge/native/android_platform.c`
> (30 Android libc/linker/property symbols: stubs and fortify wrappers); `scripts/android-stubs.py` (generates do-nothing
> stubs for the ~140 GL/EGL symbols nothing provides, from the actual gap); `libgcc_s` in the process (unwinder); the cfg.d
> mapping `libdl_android.so → libdl_bio.so.0`. Test harness: a 15-line C program (`btload`) that does what NativeLoader does.
> **P7.0c result (2026-10-26): uibench starts on the arm64 JVM with the device natives, up to the first binder call.**
> The font-init abort was not version skew (disproved: same abort with the tree's own Robolectric) but ART's calling
> convention: `@CriticalNative` methods (285 of them: fonts, paint, canvas...) are called by ART *without* `JNIEnv`/`jclass`,
> the device libraries are built that way, Robolectric's host build compiles the two parameters back in. HotSpot passes them,
> so every argument arrived shifted (`Font_Builder_addAxis` got `env` as its builder pointer). Fix, in the shim: those methods
> are registered through a thunk that drops the two arguments (`runtime_shim.c: critical_thunk`). The list comes from
> `apklayer.host.CriticalNatives` (ASM over the build's own javac output, `framework.jar*` under
> `out/soong/.intermediates/frameworks/base/framework-minus-apex/android_common/javac`, because the android-all jar has the
> annotation stripped), written into `native-runtime.jar` by `scripts/build-native-arm64.sh`. Also: device liblog goes to
> stderr through its own `__android_log_set_logger` (warnings and worse; all with `APKLAYER_RUNTIME_DEBUG`), so fatal HWUI
> messages are no longer lost; the LineageOS framework hardcodes `/system/framework/org.lineageos.platform-res.apk`
> (`ShadowHostApkAssets` redirects it to `android-root/system/framework/`, set by `-Dapklayer.platformres`); Robolectric's
> `MediaCodecInfoBuilder` does not fit this framework (`mMime`), so codec registration is skipped with a log line. Needed
> next: **a service manager** (libbinder waits for `servicemanager`: binder step above) and folding the aosp-flavor host
> build (host-classes + `META-INF/services`) into the staging script.
>
> **P7.0d result (2026-10-26): the app STARTS on the arm64 JVM with the device natives (no Rosetta), but does not render yet.**
> uibench's `MainActivity` starts, `frames=1`, exit code 134 at shutdown (an abort while the process tears down: not yet
> looked at). The libbinder wait seen before came from the RenderThread (found with the shim's `APKLAYER_DUMP_ON`/
> `APKLAYER_DUMP_ABORT` debug aid: `RenderThread::initThreadLocals` → `AChoreographer_create` → `DisplayEventReceiver` →
> ComposerService = SurfaceFlinger over binder). Fix: `bridge/ndk/libnativedisplay.c`, our own `libnativedisplay.so` mapped by
> cfg.d: Choreographer as a 60 Hz timerfd clock, weak stubs for the rest of the exports. So **no servicemanager was needed for
> this**; kernel binder works on the VM (`modprobe binder_linux`, `mount -t binder binder /dev/binderfs`) and stays for later.
> Why no pixels: the baseline (x86 Rosetta path) makes 20 frames and a real image, ours one blank frame. For SDK 36+
> Robolectric's `ShadowNativeHardwareRenderer` does not apply (`maxSdk = UPSIDE_DOWN_CAKE`), the real `HardwareRenderer` natives
> run; Google's host libhwui renders into something Robolectric can read back, the device libhwui renders into a BufferQueue
> `ANativeWindow` (gralloc, SurfaceFlinger). **Next: give libhwui an `ANativeWindow` of our own** (a CPU-readable buffer; ATL's
> `libandroid` ANativeWindow is the local reference) or draw through `HardwareBufferRenderer`/`Bitmap` canvases, then compare
> frames with the x86 path (`tests/vm` corpus T1).
>
> **P7.1 design (user, 2026-10-26): our own SurfaceFlinger replacement that composites on the GPU, with the display's real
> refresh rate; no bitmap/software stand-ins ("that was the real big deal for lags").** Why the app draws one blank frame
> today: for SDK 36+ the simulator takes pixels from the `Surface` that `HardwareRenderer` rendered into (`ShadowPixelCopy`);
> Google's host runtime has its own Surface/BufferQueue glue (a prebuilt in the tree, `robolectric_nativeruntime_native_prebuilt`,
> no sources), the device libs expect SurfaceFlinger. Pieces, in order (each testable alone, all reusing the tree's real
> libgui/libbinder through bionic_translation):
> **Refined (2026-10-26, after finding the real pieces in `out/` and the tree): run the tree's REAL SurfaceFlinger** (built:
> `system/bin/surfaceflinger`, an executable; rebuilt as a library the same way as the service manager so bionic_translation can
> run its `main`) **with apklayer's own backends underneath**: SF's `RenderEngine` composites on the GPU through the host's EGL/GLES (the VM happens to run Mesa on a
> virtio-gpu render node; nothing is Mesa-specific), transactions/BLAST/display events/screenshots (PixelCopy) are all real code. We write only what
> a desktop lacks: (a) the HWC (`android.hardware.graphics.composer3` AIDL service, all layers client-composited; the client
> target's dmabuf and the real `wl_surface.frame` pacing go to the shell), (b) gralloc (our own thin allocator/mapper over the HOST's `libgbm`; the tree's minigbm is NOT used: it has
> per-driver backends and none for NVIDIA) and (c) the launcher. Per app first (an SF + HWC per app process, each on
> its own binderfs instance), a shared one later. "apklayer-sf" = this set.
> 1. **Service manager** (`libapklayer_servicemanager`, run by `btload --main`) on binderfs; then the SF library
>    (`libapklayer_surfaceflinger`) with a stub HWC, until the app's `Surface` gets buffers.
> 2. **gralloc**: allocator + mapper over host `libgbm` (Mesa, NVIDIA via nvidia-drm, AMD, Intel alike), dmabuf modifiers negotiated, no per-GPU code; imports as `EGL_EXT_image_dma_buf_import`. No GPU/dmabuf on the host: an explicit shm + llvmpipe mode chosen at startup.
> 3. **HWC**: `apklayer-hwc` (composer3), vsync from the shell.
> **Video (user question, 2026-10-26): the same buffers carry YUV.** The allocator offers NV12/YV12/P010 (GBM formats, per-driver
> availability checked at startup, NVIDIA's GBM is the doubtful one); sampling is `EGL_EXT_image_dma_buf_import` +
> `GL_OES_EGL_image_external` (the GPU converts, no CPU copy); the HWC reports video layers as DEVICE composition so the NV12
> dmabuf goes to the Wayland compositor as a `wl_subsurface` (overlay/scanout, colour metadata kept); `apklayer-codec` writes
> decoded frames into BufferQueue buffers (zero-copy with VA-API/NVDEC/V4L2 dmabuf output, one CPU write for software
> decoders, never a readback). Fallback: GPU shader to RGBA, then the explicit CPU mode.
> **What the real system services need from a desktop (found by reading `servicemanager`, 2026-10-26; items 0.x come before 1):**
> - 0.1 **SELinux**: `Access.cpp` insists on `getcon()` and `selinux_check_access` (service_contexts). No Android policy exists on a
>   desktop; a `libselinux.so` replacement (cfg.d mapped, generated from the real library's exports) answers "allowed" and a fixed
>   context. The host's own isolation (bwrap/AppArmor, P6) is the real security boundary.
> - 0.2 **VINTF**: `isDeclared()` for the HALs (`composer3`, `allocator`, `mapper`) reads `/vendor/etc/vintf/*.xml`; there is no
>   such directory. Needs the path layer below, with manifests apklayer ships.
> - 0.3 **Path layer**: `/system`, `/vendor`, `/apex`, `/data`, `/dev/binder` as seen by the bionic libraries map to
>   `$APKLAYER_NATIVE/android-root` (bionic_translation has no remapping, `bionic_getenv("ANDROID_ROOT")` is hardwired to `/system`).
>   Candidate: wrap `open/openat/stat/access/opendir/readlink` in the libc_bio wrappers (a small, reusable patch, PATCHES.md).
> - 0.4 **Properties**: the `__system_property_*` functions in libc_bio print "not handled yet" and return nothing; services
>   (`servicemanager.ready`, `ro.hardware.*`, `debug.sf.*`) need a real store: a shared mmap'd property area written by an
>   `apklayer-init` that sets the product's properties (from `build.prop` files in out/), readable by every process.
>
> **SurfaceView (user, 2026-10-26: "if you keep SurfaceFlinger zero-copy it is fast for SurfaceView as well"): no separate shortcut
> (Mac.apk's direct-to-native-window path is not needed).** A SurfaceView/video layer is its own SF layer with its own buffer
> queue; with dmabuf buffers and DEVICE composition it reaches the compositor as a `wl_subsurface` (overlay), otherwise
> RenderEngine samples it on the GPU: zero-copy both ways. Must hold: apps/decoders render into gralloc buffers (EGLImage on the
> dmabuf), `sync_file` fences both ways, damage/release through the shell, pacing from the real frame callbacks, per-layer
> DEVICE/CLIENT from what the compositor offers. **Acceptance test for P7.1:** a GL SurfaceView plus a video layer, copies and CPU
> use measured (target: zero CPU copies of pixels).
>
> **UiBench on the GPU route (2026-10-27): the app's UI is on screen.** The tree flavor (`APKLAYER_NATIVE=~/arm64-native`, the arm64 JDK, the services-only stack `scripts/arm64-stack.sh none`) runs the real device libraries; Robolectric's windows use real SurfaceFlinger layers (relayout2, client-managed SurfaceControl, real BLASTBufferQueue), HWUI renders with Skia GL on the host GPU into our gralloc buffers through the EGL window-surface blit, SurfaceFlinger composes and our HWC shows the frame in the shell. Needs-cleanup list: runtime/patches (Robolectric shadows), the debug prints, input and window focus on the shell window, the display window versus the app window, per-launch properties, the launcher environment in bin/apklayer.
>
> **Status (2026-10-26): the whole path works on the VM**: servicemanager, allocator, composer and the real SurfaceFlinger run as arm64
> bionic libraries under btload, SF's RenderEngine (Skia/GL ES 3.0 on virgl) composes `sftest`'s layer into our gralloc framebuffer and
> the shell receives its dmabuf frames (buffers rotate, one fd set per buffer). The last blocker was a generated do-nothing stub for
> `glEGLImageTargetTexture2DOES` in btload; GL extension stubs now forward to the host driver. Still open: window surfaces for EGL
> present fences, Java side. **Step 3b done (2026-10-26)**: EGL window surfaces over an ANativeWindow (host gbm_surface + one GPU blit into the
> dequeued buffer, native fence), `gltest` renders GL ES into a BufferQueue layer through SurfaceFlinger (300 frames reach the shell) and
> follows a layer resize (new surface pair behind the same EGLSurface). Pixel check done: the composer reads a presented frame back through the driver
> (`APKLAYER_RUNTIME_DEBUG`) and (10,10) is the expected bar colour of `sftest` (480x320 layer at the top left, black elsewhere).
>
> **Measured on the VM's virtio-gpu (2026-10-26, `tests/hals/graphics-test.c`): GBM allocates ARGB/ABGR/XRGB8888, RGB565, RGBA_F16 and
> RGBA_1010102, but NOT NV12, I420, YV12 or P010**, so YUV needs a fallback: ONE linear R8/R16 allocation holding all planes back to back
> (what a real NV12 buffer is; the Android side must report one base pointer with plane offsets, so separate per-plane buffers
> do not work), imported to EGL as a multi-plane dmabuf image with per-plane offsets, with
> `eglQueryDmaBufFormatsEXT` deciding at startup whether the driver accepts it; otherwise the GPU-shader conversion to RGBA. Also
> measured: `mmap` of a virtio-gpu dmabuf is refused (EACCES), so CPU access always goes through the driver (`gbm_bo_import` +
> `gbm_bo_map`), which is coherent on every driver.
> **Measured (2026-10-26, `tests/hals/egl-probe.c`, VM virtio-gpu = virgl on the host GPU): the host EGL on `EGL_PLATFORM_GBM_KHR` over our
> graphics HAL's device gives everything the Android side needs**: EGL 1.5, a GLES3 surfaceless context, `EGL_EXT_image_dma_buf_import(_modifiers)`
> (RGBA8888 and the packed NV12/YV12 import and bind as textures), `EGL_KHR_fence_sync/wait_sync`, `EGL_ANDROID_native_fence_sync`
> (real sync_file fds), `EGL_KHR_surfaceless_context`, `EGL_KHR_no_config_context`, `EGL_EXT_buffer_age`. Missing (to be added by
> our EGL layer): `EGL_ANDROID_image_native_buffer` (ANativeWindowBuffer to dmabuf import), `EGL_IMG_context_priority`,
> `EGL_KHR_partial_update`. SurfaceFlinger's RenderEngine uses about 25 EGL calls (`eglGetDisplay/Initialize/ChooseConfig/
> CreateContext/MakeCurrent/CreatePbufferSurface/Create+DestroySyncKHR/WaitSyncKHR/DupNativeFenceFDANDROID/QueryString...`) and
> Skia's `GrAHardwareBufferUtils` for images (`eglGetNativeClientBufferANDROID`, `eglCreateImageKHR(EGL_NATIVE_BUFFER_ANDROID)`).
> **EGL layer (Android side, `aosp/hals/graphics/egl`): our own `libEGL`, mapped over Android's loader by cfg.d**, forwarding to
> the host EGL (GBM platform, the HAL's gbm device), adding the Android extensions above, and later window surfaces over
> ANativeWindow (host GBM surface or FBO, buffers attached to the BufferQueue).
> **Display path measured (2026-10-26, `tests/hals/display-test.c`, `bridge/shell/dmabuf.c`, `bridge/hals/graphics/display.c`):** the shell imports
> dmabufs through `zwp_linux_dmabuf_v1` (new messages DMABUF_FRAME with SCM_RIGHTS fds, DMABUF_RELEASE, an empty-path WINDOW_CREATE for
> dmabuf-only windows; the shell now reads with recvmsg), real GPU buffers from the graphics HAL are presented, 89 of 90 released back by the
> compositor, and vsync comes from the compositor's frame callbacks. **Finding: the VM's `wl_output` says 59.9 Hz but the frame callbacks run
> at about 111 Hz (the Mac's ProMotion display is 120 Hz and Parallels reports the mode as 60).** "Real refresh rate" must therefore come from
> the measured callback cadence, not the mode: the composer HAL will report `getDisplayVsyncPeriod` from measured frame callbacks.
> **Resizing and windows (user question, 2026-10-26: "with a custom HWC apps will run full screen as the whole display, can we still resize?").**
> Yes, in two levels. (1) **The display follows the shell window**: the HWC's size is the window's size; the shell's CONFIGURE message
> (already used for P2 resize/HiDPI) makes the HWC announce a new display configuration (or a hotplug re-announce) to SurfaceFlinger, which
> re-lays out, and apps get a configuration change like a resized emulator window. To verify: how smoothly SurfaceFlinger takes a live
> size change; rotation is the same mechanism. (2) **One shell window per Android top-level window, built as one Android display per window** (user: "can we do multi window?"; the HWC only
> sees a flat layer list without names or parents, so mapping layers to windows by rectangles would be fragile): each window is a virtual
> display with its own layer stack, the HWC announces/removes displays by hotplug, each display is one `xdg_toplevel`, and a resize is level 1
> per display. Needs: an N-display HWC (display id and shell window id are parameters), and a window-manager shadow doing what system_server
> does for surfaces (attach each app's SurfaceControl to its display's layer stack, z-order, visibility; choose the display a new activity
> launches on; Robolectric already has multi-display shadows). Windows then resize, move, minimise and close individually. Status bar, dialogs and
> popups stay inside their window. Needs the shadow layer to expose per-window size and accept resizes from the shell. Rule for the code:
> the display size and the window ids are always parameters, never constants.
> 4. **Window surfaces**: libhwui's EGL window surface on the SF's buffers (Mesa Android platform / our libEGL shim).
> 5. **Presentation**: the client target's dmabuf to the shell over SCM_RIGHTS, `zwp_linux_dmabuf_v1`, zero copy.
> 6. Same service later answers input (InputFlinger), camera, and the userspace binder (design above) replaces the kernel one.
>
> **Next (P7.0b): `JNI_OnLoad` and the JNI registration**: the library registers natives under the framework's class names,
> Robolectric's shadows call its own `org.robolectric.nativeruntime.*Natives` classes; intercept `RegisterNatives` and
> bind the same functions to the Robolectric class, then run the sandbox with this native runtime on the arm64 JVM.
>
> **Why the full device library (user, 2026-10-26): "in future if we want full support of apps we need all input, GPU, camera
> and even userspace binder too".** Google's *host* variant leaves exactly those out; the device library contains them, so this
> route grows into them without rebuilding anything. What each needs from the desktop (all later, in this order of value):
> - **Binder**: `libbinder` talks to `/dev/binder`. The VM's kernel (6.8, Ubuntu) ships `binder_linux` and `binderfs` as modules
>   (`CONFIG_ANDROID_BINDER_IPC=m`), not loaded by default: `modprobe binder_linux` + a binderfs mount gives a real binder; a
>   service manager and the few system services apps call (activity, package, window, display, input, notification, …) would
>   be apklayer's own, answering over it. Fallback with no kernel module: libbinder's RPC binder over a Unix socket
>   (`libbinder_rpc_unstable.so` is in `out/`). Ships as a requirement for Flatpak users (kernel module) or the RPC path.
> - **Binder decision (user, 2026-10-26): kernel binder (`binder_linux`) for now; later apklayer writes a userspace binder so that
>   *everyone* can run Android apps without kernel drivers** (the goal is all users, not only the dev VM).
>   Research (2026-10-26, no mature userspace *driver* exists): Android's own **RPC binder** (`RpcServer`/`RpcSession` over Unix
>   sockets/vsock; official, but `ProcessState` still opens /dev/binder, so libbinder would need a backend patch); `rsbinder`
>   (Rust library, needs kernel binderfs); Waydroid (bridges the host kernel binder); **Fuchsia Starnix's
>   `src/starnix/modules/binderfs/binder.rs`** (a Rust implementation of the driver's command protocol: the best design reference).
>   Our design: all Android code runs on bionic_translation's libc, so `open("/dev/binder")`, `ioctl(BINDER_WRITE_READ…)` and
>   `mmap` are answered by apklayer's own driver inside that layer (talking to `apklayerd` across processes), with unmodified
>   libbinder/AIDL on top; semantics (nodes, refs, transactions, thread pools, buffers, death notifications, fd passing via
>   SCM_RIGHTS) follow the kernel driver and Starnix.
> - **Input**: libinput-style `InputChannel` (socketpair, no binder needed) fed from the shell's events instead of Robolectric's
>   UiAutomation injection.
> - **GPU**: HWUI's GL pipeline over libgui `BufferQueue` (in-process) with buffers as dmabufs to the shell; Mesa on `/dev/dri`,
>   `/dev/dma_heap` present.
> - **Camera**: `CameraService` replacement behind binder, fed by PipeWire/V4L2 (`/dev/video*` exist).
>
> **Revised 2026-10-26 (user decision): no Meson/retargeting build.** Use the arm64 **bionic** (Android) libraries
> already built in `~/los24/out/target/product/generic_arm64/system/lib64` (`libandroid_runtime.so`, `libhwui.so`, …)
> and load them into the arm64 JVM with bionic_translation, as P4 does for apps' native libraries. Steps and open
> questions are in "P7.0 experiment" below; the original compdb→Meson plan (items 1-3) is kept as the fallback.

1. **Soong host build, x86_64 (necessary: D24).** In OrbStack, with
   `SOONG_GEN_COMPDB=1` (in ~/los24 after `breakfast gsi_arm64 userdebug`, using its `out/`), build only the host variant of
   `libandroid_runtime` (`host_supported` in frameworks/base/core/jni/Android.bp; the same
   module layoutlib and Robolectric ship). Outputs: the x86_64 `.so` and
   `compile_commands.json` holding the exact sources, defines and flags of every module in
   its graph. Validate: its exported JNI registrations match Robolectric's prebuilt and the
   android-all SDK 36 jar's `native` methods (generated diff, must be empty).
2. **arm64 glibc via Meson (necessary: Soong has no glibc arm64 host target).** Soong's
   cross-host options are `linux_bionic` (bionic binaries) and musl arm/arm64, and neither
   loads into a glibc JVM. `scripts/compdb2meson.py` turns the compdb into
   `runtime/native/meson.build` (same sources/defines; target triple and sysroot swapped to
   `aarch64-linux-gnu`). It is built **natively on the arm64 VM** with clang. It also
   rebuilds x86_64, so Soong is not needed again until a source bump.
3. **If step 1's JNI diff is not empty** (los24 differs from android-all build 13921718):
   build android-all from the same los24 tree too (`robolectric-host-android_all`, in
   external/robolectric). Java and natives then come from one revision. This is the only
   other allowed Soong build.
4. **Native arm64:** drop the generated `libandroid_runtime.so` into
   `native/linux/aarch64/` on the classpath (where `DefaultNativeRuntimeLoader` looks), run the
   whole stack with the arm64 JDK, and remove the Rosetta stopgap from the scripts.
5. **GPU HWUI:** compile HWUI's device pipeline sources (`EglManager`,
   `SkiaOpenGLPipeline`, `RenderThread`, `SkiaVulkanPipeline`, `VulkanManager`) against Mesa,
   with a `ReliableSurface`/`ANativeWindow` backed by a shell `wl_surface` (dmabuf). Switch
   `ThreadedRenderer` to it. The CPU path stays as a fallback.

**Exit test P7:** on the arm64 VM with no Rosetta, the corpus T1 pass rate equals x86_64.
With GPU HWUI on, a scrolling list in uibench runs at the display refresh rate with lower
CPU than the CPU path.

### P8 — Ship (M12)

1. A Flatpak manifest (D16) bundling the shell, NDK shims, bionic_translation, WPE WebKit (from
   the GNOME runtime or built), the JDK extension, deps from the lock and microG.
2. App icons and the launcher list appear in the desktop's app grid through exported `.desktop`
   files; double-clicking an `.apk` installs it.
3. Performance budget, as release criteria: cold start ≤ 4 s for a T1 app on x86_64
   (enable a JVM AppCDS archive of android-all + host), idle CPU < 2% (P1.6 damage-driven
   frames), memory ≤ 600 MB per T1 app.
4. A `.deb`, a release notes page, and docs/USER.md.

**Exit test P8:** fresh Ubuntu 24.04 machines, **x86_64 and arm64**: `flatpak install apklayer.flatpak` →
double-click `Markor.apk` → it opens and is usable on both.

---

## 4. Working rules for executing this plan

- Order is strict: P1 → P2 → P3 (then continuous) → P4 → P5 → P6 → P7 → P8.
- One logical step = one commit, with the verification output in the commit message body.
- Never edit upstream code (android-all, Robolectric, simulator, dex2jvm, bionic_translation).
  Adapt through shadows, configuration and post-processing, and log it in docs/PATCHES.md.
- AOSP builds only for what we need, in the user's ~/los24 tree and logged in docs/AOSP-BUILDS.md; local artifacts first, one-time fetches only through `deps/deps.lock`.
- Every phase: update goal.md (milestone status), memory.md (facts learned) and this file
  (if a decision changed, with the reason).
- Report progress to the user at every exit test, with a snapshot.

## 4b. User concerns tracker (never drop these)

| Concern (user) | State |
|---|---|
| Keep working toward the goal without stopping; report progress | ongoing: report at every milestone/exit test |
| Keep the md files (PLAN, memory, goal, ARCHITECTURE, README) current | done at each step; check before every commit |
| File architecture: `shell.c` / `AppHost.java` too big | ✅ 2026-10-26: shell split into 7 modules, host into AppHost/HostInput/HostWindows/ScriptRunner. Watch next: `ShellClient`, `HostWindows`; `Translator.java` (4.6k, vendored from Mac.apk) is deliberately left alone |
| Window decoration (resize, close, minimize, back button) | ✅ |
| Resize by holding the edges, resize cursor | ✅ (cursor appearance depends on the system theme; the VM theme is old) |
| D-pad menu selection and mouse-wheel scrolling (incl. Dialog List) | ✅ fixed (window focus + touch mode; dialog-aware routing) |
| Test efficiency: only run tests for what changed | ✅ group arguments on both test scripts |
| The user does not test by hand: verify on the VM before reporting | ongoing |
| See what happens in the VM (visible in the Parallels window) | ongoing: tests run in the real session |
| No sleeps / no waiting scripts; 3-second rule; no background launches; close everything started | rules in `CLAUDE.md` (NEVER section) |
| Reuse local sources first; small edits; mind maintenance and architecture | rules in `CLAUDE.md`; vendored/own code logged in THIRD_PARTY/PATCHES |
| Do not ask the user to test by hand | ✅ rule in `CLAUDE.md`; features that only a person can judge are listed here as "needs a human check" |
| Revisit: NewPipe video/audio lag | open, see P5 "Revisit list" (measurements recorded) |
| Revisit: desktop notifications annoying | policy ✅ (`tests/vm/notify.sh`); mute/cancel/actions still open |
| Run multiple apps in the sandbox | P6 slice ✅ (cross-app intents + results, `tests/vm/share.sh`); per-app bwrap sandbox blocked by Ubuntu's userns restriction |

## 5. Immediate next tasks (start here)

1. ✅ Add `LICENSE` (AGPL-3.0-or-later) (D5).
2. ✅ Restructure directories (P1.1). Make the existing script work with the new paths and verify
   uibench.
3. ✅ Write `deps/deps.lock` for the current `~/rob` contents (P1.2).
4. ✅ Fetch SDK 36 android-all + the pinned Robolectric once, then switch uibench to SDK 36 (P1.4).
   Result: Robolectric 4.17 already supports SDK 36 and publishes `simulator`, so no version bump was needed.
   The `Build` stub was dropped; `android-stub.jar` = SDK android-36.1; `--add-opens` flags are in `runtime/host/jvm.args`.
5. ✅ Simulator-based host `apklayer.host.Main` (P1.5). The published `simulator-4.17.jar` is used
   unmodified (no vendored source needed); JUnit removed from the product. Port of RunUiBench's stack, window and
   input logic (P1.5).
6. ✅ Shell protocol v1 over a Unix socket with damage-driven frames (P1.6): `apklayer-shell`
   (multi-app, zero-copy shm pools, wl_buffer.release flow), pure-Java `ShellClient`; the JNI
   presenter (libapklayer-wl, WlWindow) and presentd were removed.
7. ✅ Top-level Meson build (P1.3): `meson setup b && ninja -C b` builds apklayer-shell,
   dex2jvm.jar and apklayer-host.jar (jars via `scripts/javac-jar.sh`; the shell is optional where
   Wayland is missing). The Mac quick check (`scripts/check.sh`) uses plain javac on the same
   sources, because the Mac has no Meson.
8. ✅ Data dirs, CLI, install cache, desktop entries (P1.7–P1.9): `bin/apklayer`; data persists
   via links pre-created before the lazy Application; `Main --describe` writes info + icon;
   `.desktop` + MIME handler; config in `~/.config/apklayer/env`. Verified on the VM, including a
   menu-style launch with `gtk-launch`.
9. ◐ P1 exit test: uibench + **Vibrations Test + Open Camera pass headless on the Mac** (local
   APKs from ~/TestApps, no download). On the VM, Vibrations Test runs via `apklayer run`;
   Open Camera on the VM is still to do.
   Five more apps fail with concrete, listed causes (tests/corpus/results.md) that are P3 fix-loop
   items: manifest ContentProviders before Application.onCreate (Firebase), a `getprop` stand-in,
   `Display.getHdrCapabilities`, the MediaPlayer data source, the NewPipe startup NPE.

### P8 — Yardstick: the SystemUI APK (user, 2026-10-26: not a goal, "just something cool"; covering it means almost any app works)
Not scheduled, and it never drives a design decision. A late compatibility stress test: SystemUI uses nearly the whole platform
API, privileged window types and hidden interfaces, so running it shows the shadow layer and the stack are broad enough for
ordinary apps. **System services remain Robolectric shadows (user: "we don't need them to be real, even for SystemUI"); no
`system_server`.** If attempted: extend the shadows for `StatusBarManager`/`IStatusBarService`, status/navigation/keyguard/shade
window types, insets and cutouts, the notification-listener feed, keyguard, wallpaper, input monitors; feed them from the host.
The useful by-product is the list of platform gaps it exposes, each of which also helps ordinary apps.

### P9 — Vision: several phone instances at once (user, 2026-10-26)
Each instance is a full virtual phone made of ordinary host processes: **its own binder domain** (a separate binderfs device
from `binder-control`, later a separate userspace-binder context), its own servicemanager, SurfaceFlinger and HALs, its own
system-property area, its own `/data` (the path layer, step 0.3, maps the Android paths per instance), its own runtime directory
for the shell and vsync sockets, and one process group (cgroup/systemd scope) that can be stopped as a unit. Windows from an
instance are Wayland windows tagged with the instance (title/app-id). The same app can run in two instances with separate
accounts and storage. Isolation between instances and from the host is the P6 sandbox (bwrap/namespaces) applied per instance.
Design rule from now on: nothing in P7 may assume a single instance (no fixed `/dev/binder`, fixed socket names or global
property store; take an instance id/root as a parameter).

### P10 — Compatibility proof: the relevant CTS tests (user, 2026-10-26: "at the end of the goals add running relative CTS tests to make sure our apklayer is really compatible")
**Strictly the last thing we do (user, 2026-10-26: "i wouldn't waste time on CTS this early"): no CTS work before everything else is done.** The measure of "compatible". The tree has the sources (`~/los24/cts/tests/tests/<module>`: `view`, `widget`, `graphics`,
`text`, `content`, `os`, ... about 170 modules). Approach: (1) choose the **app-facing** modules (what ordinary apps depend on:
view, widget, graphics, text, content/resources, os basics, media, input, drawables) and leave out device-system ones (telephony,
SELinux, permissions policy, hardware certification); (2) build each module's test APK incrementally in ~/los24 (logged in
docs/AOSP-BUILDS.md); (3) run it with our own instrumentation runner (AndroidJUnitRunner inside an instance, results in a stable
format) on the shadow-based system; (4) keep a table per module: passed / failed / skipped, with each failure classified as a **gap
to fix** or **asserts real-device behaviour** (reason listed, expected-failure file under `tests/cts/`); (5) track the pass rate
per module over time, run the chosen subset in CI-style scripts on the VM. Exit test: the chosen modules reach an agreed pass
rate with every remaining failure explained. This also feeds P3: every gap found is a gap real apps can hit.

### P7.1 GPU route: next steps (written 2026-10-27, in priority order)
State: UiBench runs on the device-library route through `bin/apklayer run` (services on demand, one SurfaceFlinger virtual display per app, GPU
rendering end to end, real input through the shell's input proxy, dialogs stacked on top). Test: `tests/vm/gpu.sh` (needs the VM and the libs built from `~/los24`).

**2026-10-27 investigation:** fixed stale stack readiness before relaunch; initialized the real RenderEngine cache files and readiness property (avoids its five-second Android-init wait); display windows now map a blank shm frame before GPU output so Wayland can deliver focus/vsync. Cold launches and real clicks into Dialog List verified. Sink buffer release and stale-size frame rejection built and installed; one complete resize/Back/new-size-frame run passed, but repeated runs expose an intermittent bionic_translation mutex assertion and input focus sensitivity. A1 remains open. The dump mutex assertion was traced to missing translated pthread_mutex_timedlock and fixed; static-init/contention/reuse tests and a full dump pass. A separate EGL initialization lifetime bug remains under resize; fix in progress.

1. **✅ Fixed: crash/stall resizing with a dialog open** (verified 2026-10-27). Sink buffers return on compositor release; stale-size frames are dropped. Missing translated timed mutex locking and Android EGL initialization reference counting are fixed. Real Wayland callbacks follow the active mapped surface instead of an occluded primary. `tests/vm/gpu.sh` passes real menu clicks, compositor resize, layer survival, Back dismissal and final-size GPU frames, with before/after screenshots.
2. **✅ Fixed: dialog right margin / bounded frames** (verified 2026-10-27). A window's frame fits the display, while its buffer includes 32px shadow insets on each side. WMS placement now lives in a parent container and subtracts the insets, as `WindowState.transformFrameToSurfacePosition` does; BLAST owns the child’s destination frame and cannot overwrite the parent transform. Oversized requested frames are clamped so ViewRootImpl remeasures them. UiBench screenshots measure symmetric **16px / 16px** margins before and after resize; `gpu-check-image.py` checks them. Containers are removed when windows are removed.
3. **✅ Window mapping and focus** (verified 2026-10-27). Display windows map an initial blank frame using the shell's existing shm mechanism, then GPU dmabufs replace it. AppHost's initial-inactive exception is removed; normal pause/resume handling applies from the start. Cold launch, real input, resize, Back and margin checks pass without the workaround.
4. **✅ Headless primary** (verified 2026-10-27). The composer opens a host timing connection without WINDOW_CREATE, so only app virtual displays create Wayland windows. Refresh period is taken from wl_output's actual mode, not smoothed intervals between on-demand callbacks. Vsync uses real callbacks from the active mapped app surface. The shell reads initial output mode/scale before accepting clients. Smoke tools get their own stack-0 virtual display. Full GPU regression and 12-frame GL resize smoke pass; shell logs confirm no primary toplevel.
5. **✅ Several apps at once in the current Android instance** (verified 2026-10-27). Concurrent launchers share a flock-protected service startup and event-driven ready/death wait. Each launch has a PID-unique layer stack, an instance/launch-tagged title and its own sink/log; long package names cannot collide in the title prefix. Pointer/title hover references are cleared on window teardown. `tests/vm/multi-app.sh` launches two GPU JVMs together, locates their real windows in captures, switches focus by uinput, closes one with real Back, and maximizes/resizes the survivor. It verifies distinct layers/titles, one service stack, continued frames, lifecycle routing and cleanup. Full phone/binder/data isolation remains P9; these apps intentionally share one Android instance.
6. **✅ Text input and clipboard** (verified 2026-10-27). Proxy ids route field enable and IME events to the focused JVM. Clipboard pipes run asynchronously in the shell event loop with per-client echo suppression and immutable outgoing selections. Fixed ShellClient’s empty clipboard payload and the upstream key-character shadow’s handling of Ctrl/Alt/Meta. `tests/vm/input.sh` verifies real typing, host paste with Ctrl+V, Android copy read by wl-paste, actual IBus preedit/Chinese commit and a 20 KB transfer; restores the input source and closes all children.
7. **✅ Runtime/startup cleanup** (verified 2026-10-27). `build-runtime-overlay.sh` propagates compilation failures and atomically publishes a complete jar; forced failure preserves its hash. `build-tree-host.sh` builds all host sources in a fresh directory, adapting the actual ShadowProvider API and reusing the two pinned simulator callback classes absent from the older tree. Full fresh host/overlay compilation passes GPU dialog/resize/Back/margin checks. All ro.* properties are write-once again: system hyphen data is staged once and shared by JVMs. Property isolation/mutability tests and two-app launches pass. Tree install uses its actual framework for conversion/cache identity and its native JVM/environment for metadata/icon rendering (verified hello.input install). Trace flags honor false/0, relocation diagnostics use the existing linker trace facility, service-only startup no longer starts tail. Windowless vsync and the architecture docs are complete. A fully provisioned clean-machine installer remains release work; these checks rebuild host/overlays from scratch against the staged device artifacts.
8. **HALs before anything else after that (user, 2026-10-27): media and camera first, then the other hardware.** Host media backend started: native GStreamer retains YUV samples and exposes real decoder availability/dmabuf metadata. Real VP8 encode/decode at 64x48 and 80x60 passes pixels, PTS, EOS and sample lifetime tests. Initial Codec2 interface and native work/gralloc path now pass tests/vm/codec2.sh (three real VP8 frames, PTS/frame indices/EOS, YUV values). Fixed upstream non-DMA-heap linear mapping for the real BLOB allocator. AIDL discovery and work/buffer transfer now pass through the real Android Codec2Client in another process, including a hidden reference frame; process exit and cleanup pass. Opt-in real MediaCodec JNI now passes APK startup discovery, three decoded VP8 frames via getOutputImage (YUV/PTS), EOS and stop/release, plus HWUI frame commit and physical Back (tests/vm/native-media.sh). Actual AOSP media.player/resource_manager services run alongside Codec2. Native SurfaceView VP8 presentation also passes: real framework holder/BLAST child layers, rendered callbacks, a red-pixel screenshot check and physical Back. The YUV fallback now allocates separate R/RG plane resources; packed R8 imported but sampled incorrectly on virgl. Mapper CPU lock stages contiguous YUV, while SurfaceFlinger imports the plane dma-bufs directly. VP9 profile 0 now shares the adapter and passes native host decode at two sizes, AIDL visible/hidden references and hidden+visible superframes, app MediaCodec YUV/PTS/EOS and SurfaceView presentation. Multiple displayed pictures per input work now use SimpleC2Component.cloneAndSend and FLAG_INCOMPLETE, with EOS only on the final picture; AIDL and native app buffer/Surface tests verify duplicate timestamps and both pictures. Higher-bit-depth VP9 is not advertised. Host H.264 B-frame decoding now passes 12 coded-order inputs into 12 presentation-order YUV outputs with exact timestamps/EOS and asynchronous availability notifications. H.264 Codec2/app integration now also passes: callbacks queue the existing component handler, pending work completes by output PTS, EOS follows all 12 B-frame pictures, Java buffer output survives flush/replay, and SurfaceView renders the final frame with a red-pixel check. Native MediaExtractor callback-source MP4 parsing now passes through a separate real extractor process and original AOSP plugins, including track/CSD, keyframe seek, 12 extracted H.264 samples decoded to YUV, PTS/EOS and release. File-descriptor sources now pass nonzero-offset reads, track/sample/seek, caller ownership and immediate caller close after setDataSource; native MediaPlayer/audio remain next; other codecs/drain cases, hardware decoder dmabuf import and corpus playback remain unfinished; service is not enabled by default. Media: decoders/encoders and `MediaCodec`/`MediaPlayer`
   on the real stack (video buffers through our gralloc, YUV zero-copy into SurfaceFlinger layers, the codec2 HAL over the host's decoders), audio. Camera: the camera
   provider HAL over PipeWire/V4L2 into BufferQueue surfaces. Then gnss, sensors, vibrator and the rest of `aosp/hals/README.md`. Each follows the domain layout
   (`aosp/hals/<domain>` Android side, `bridge/hals/<domain>` host side).
   Native audio host backend verified (2026-10-27): `bridge/hals/audio` queues mapped PipeWire buffers on the owning worker and derives presentation from queued/resampler/device timing. Early process notification and full available buffer capacity avoid quantum gaps from small Android writes. S16 stereo and float mono pass actual isolated monitor PCM frequency/frame checks, delayed position, drain, pause, flush/resume, and teardown (`tests/vm/audio-host.sh`). Requires PipeWire >=1.0.4 for its monotonic clock API. Android AIDL HAL/AudioFlinger/AudioPolicy and MediaPlayer integration remain next; this does not yet replace app audio shadows.

   Native AIDL audio output verified (2026-10-27): original AOSP Module/Config/Stream/FMQ worker with own PipeWire driver, real binder port/patch/stream setup and monitor-captured stereo PCM. Presentation/drain, standby/close and pause/flush/replay (lifetime epoch 50880) pass tests/vm/audio-aidl.sh. Hardware master controls and unsupported modes return errors. Unprivileged transfer worker uses normal priority while PipeWire schedules audio. BT translates genuine mutex priority-inheritance attributes. Opt-in only; actual AudioFlinger/AudioPolicy and app native audio/MediaPlayer remain next.

   Original AudioFlinger/AudioPolicy startup verified (2026-10-27): reuse original service archives, AIDL adapter, default policy engine, effect factory with no staged effect plugins, and original volume XML. Native binder route probe returns 48k/960 frames/45ms. Standalone native activity/privacy clients return actual service absence instead of waiting for system_server. Checked fread ABI fixed for real XML parsing. AudioTrack sample playback and app JNI remain next.

   Native AudioTrack playback verified (2026-10-27): real PID/UID/Binder attribution, original AudioFlinger mixed track, stream volume setup, blocking PCM writes, marker callback, presentation timestamp and stop/flush/release. Isolated PipeWire monitor verifies both channel frequencies and all 48k tone frames (47760 nonzero including zero crossings). Actual silence frames retain the graph through presentation; completion uses the callback, no delay. tests/vm/audio-track.sh passes. Java AudioTrack/MediaPlayer JNI and attribution Parcel integration remain next.

   Scoped native attribution Parcel verified (2026-10-27): original AIDL serializer and real native Parcel, all fields/Unicode/nulls/chains, stable/distinct local Binder identity, scoped and cyclic-error release, Java Parcel reuse. tests/vm/native-audio.sh passes UI frame/physical Back, and native-media regression remains green. Native JNI gets a real pointer; general Binder/Parcel and remote proxies remain for the hub. Java AudioTrack/MediaPlayer integration is next.

   Java AudioTrack JNI verified (2026-10-27): original framework/native PCM, actual scoped attribution and PID/UID, early Application capability query, real policy volume/global session IDs, delayed head/marker/timestamp, stop/flush/release. Isolated monitor captures exact stereo440/880Hz47760 nonzero toneframes; UI commit and physical Back pass. BT monotonic condition deadline regression reproduces three old failures and passes all four API/default-clock combinations after patch12. AudioSystem original initializer runs immediately after initial capability JNI registration. Opt-in only; Java AudioService focus/player bookkeeping and MediaPlayer/general native Parcel packets remain next.

   Native media follow-up (2026-10-27): selective native payload Parcel foundation passes original JNI serialization, coexistence and ownership tests; MediaPlayer is still pending. Java Binder/FD packets and native Java interface enforcement are explicitly unsupported until Binder cache/dispatch integration. Callback timeout root cause identified in tree DeliQueue versus API36 Robolectric legacy scheduler; select AOSP's provided legacy queue mode at static initialization. Fixture confirms actual main-thread marker delivery with deli:false, PCM capture and cleanup.

   Original MediaPlayer video verified (2026-10-27): actual Java/JNI/NuPlayer/Codec2 -> SurfaceView, real FD source caller-close, async prepare, video track/duration/dimensions, pause/resume and playback pacing, native time-discontinuity Parcel timestamps, seek/replay and two completion callbacks. Native callback factory uses original payload data with normal Java recycle/finalization ownership. tests/vm/native-player.sh checks131674 red pixels, real SF layer, release and physical Back. Compressed audio/A-V integration and more source/error cases remain next; exact short-clip getCurrentPosition endpoint semantics still need investigation.


   **Bluetooth** after camera: Robolectric-style shadows of `android.bluetooth.*` over a host daemon speaking D-Bus to BlueZ (not Android's own stack: it needs
   system_server's services and an exclusive controller), scoped by what the corpus apps actually call (BLE scan, GATT client/server and advertising first; then RFCOMM/L2CAP
   sockets; audio profiles through PipeWire), with a compatibility table of every API and its status; unimplemented calls return real errors. Developed against
   BlueZ's `btvirt`/vhci virtual controllers (the VM has no Bluetooth hardware).
   **Decided with the user (2026-10-27):** display backlight and thermal are the host's business (not ours); accounts are the Android way (the account apps' own
   authenticators through `AccountManager`, no host account bridge). Connectivity reports the host's real state (NetworkManager for Wi-Fi/Ethernet, ModemManager for
   cellular if the host has a modem, otherwise "no SIM"; a clearly labelled virtual-SIM test mode is the only exception); also battery/power (UPower, logind), sensors
   (IIO), game controllers and haptics (evdev), USB (udev), biometrics (fprintd), NFC (PC/SC), file/screen-capture portals.
8b. **System apps and providers on every instance by default** (user, 2026-10-27): the tree's own ContactsProvider, CalendarProvider, DownloadProvider, SettingsProvider,
   TelephonyProvider, UserDictionaryProvider, DocumentsUI (+ContactsPicker), MediaProvider and friends (APKs are in the build's `system/priv-app`), each with its data in the
   instance's storage. They are apps like any other (converted and run by apklayer), but their consumers are other apps, so this is the cross-process case: apps reach
   `ContentProvider`s through `IContentProvider` binder calls. Plan: an instance "system JVM" hosts the providers; app JVMs reach them over real Binder (the kernel binder
   already runs on the instance; the Java `Binder`/`BinderProxy` JNI has to be registered and un-shadowed for this one path, per PLAN P6's "real binder where a
   process boundary exists"); the rest of the system services stay shadows. Start with Settings and Contacts/Calendar, then Media/Documents.
8c. **Apps talk to each other like on Android** (user, 2026-10-27: "intents, broadcasts, binder AIDLs... should work like native Android"). Decision: **an instance
   hub next to the Robolectric shadows** (option 1 of three: hub; our own thin system_server with the real framework client code (ActivityThread/ContextImpl instead of
   Robolectric's ActivityController); the tree's real system_server). Hub = one process per instance owning the package registry (what `apklayer install` records), receiver
   registrations (manifest and dynamic), the directory of running services and providers, app process lifecycle, and the pid-to-package table that gives
   `Binder.getCallingUid` and permission checks their answers (all JVMs run as one host user). Each app JVM keeps its shadows; whatever targets another package is
   forwarded to the hub. `IBinder`s cross JVMs over the real kernel binder: the real `android.os.Binder`/`BinderProxy`/`Parcel` JNI is registered and Robolectric's
   shadows for them are removed, so AIDL stubs, `linkToDeath` and Parcelables behave as on a device. The hub speaks the real AIDL interfaces where they exist
   (IContentProvider, IIntentSender, IServiceConnection...), so a later move to our own system_server (option 2) does not change the apps.
   Order of work, each step with an automated cross-app test on the VM: (1) two tiny test apps, one with a bound AIDL service, one binding to it across two JVMs, with a death
   notification; (2) broadcasts (manifest and dynamic receivers, ordered, sticky, permissions); (3) content providers (Settings first, then Contacts/Calendar) with
   observers; (4) cross-app `startActivity`/`startActivityForResult`, `startService`, pending intents; (5) notifications and the rest. Behaviour is checked against Android's
   documented semantics and, later, CTS's app-framework modules.
9. Vulkan (P7.5) after the HALs.
10. **CTS (P10) stays last**: not before media, camera and the other HALs work.

### P7.5 — Vulkan (user goal, 2026-10-26)

Android's `libvulkan.so` (the loader) is replaced, as `libEGL` is, by a layer of ours over the host's Vulkan loader (Mesa, NVIDIA,
any vendor; no driver dependency). It adds what Android's WSI expects: `VK_KHR_android_surface` (`vkCreateAndroidSurfaceKHR` over an
`ANativeWindow`) and a swapchain whose images are buffers dequeued from that window: each gralloc dmabuf is imported as a `VkImage`
(`VK_EXT_external_memory_dma_buf`, `VK_EXT_image_drm_format_modifier`), acquire is `dequeueBuffer`, present is `queueBuffer` with the
render-complete semaphore exported as a sync fd (`VK_KHR_external_semaphore_fd`). Zero copy (better than the GL window path, which
needs one GPU blit because a GBM window surface cannot render into foreign buffers); resize is the app's `OUT_OF_DATE` handling.
Also AHardwareBuffer import for Vulkan, and HWUI's Vulkan backend (`debug.hwui.renderer=skiavk`) as the target once the layer
works. First step: probe the VM's driver for the needed extensions (virtio-gpu may lack them; Venus would provide them), then the
layer, then a `vktest` tool beside `gltest`.

**Probe (2026-10-26, Parallels VM):** no hardware Vulkan: the only device is llvmpipe (CPU; `VK_EXT_external_memory_dma_buf` and
`external_memory_fd` yes, but no `image_drm_format_modifier` and no `external_semaphore_fd`), and the Venus ICD (`virtio_icd.json`) finds no GPU
(Parallels' virtio-gpu offers no Venus). The layer can be written against llvmpipe for structure (dmabuf import, dequeue/queue) but
synchronisation and the zero-copy claim need a machine with a real GPU driver (a Mesa or NVIDIA desktop); mark such steps "needs real
GPU" and verify there.

### P7 fallback and x86 counterpart (user, 2026-10-26)
**Fallback (done):** Robolectric 4.17's own native runtime for arm64 (third party, `scripts/fetch-robolectric-arm64.sh`, THIRD_PARTY.md) as a selectable flavor
of `bin/apklayer` (`APKLAYER_NATIVE` directory holding `robolectric-nativeruntime-linux-aarch64.jar`, Maven Robolectric and its android-all as in the
default flavor): uibench runs on the arm64 JVM with no Rosetta, 19 frames, same output size as the x86 baseline. It is CPU rendering (no GPU zero-copy)
and stays as the fallback while the device-library route is finished. **x86_64 counterpart (deferred: the very last thing, after the tool has been published; user, 2026-10-26):** the tree's own `libandroid_runtime` for the
Linux x86_64 host (the tree-consistent Robolectric native runtime for x86 hosts), built in ~/los24 (docs/AOSP-BUILDS.md), to replace the
Rosetta-era x86 library where the tree's framework jar is used.
