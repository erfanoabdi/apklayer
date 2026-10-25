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
| D24 | AOSP builds | **Allowed only when absolutely necessary, only on the OrbStack `ubuntu22-x86` VM**, with a separate `OUT_DIR=~/apklayer-aosp-out` (los24's own `out/` is never touched). Each build is listed with its justification in docs/AOSP-BUILDS.md. The only planned one is P7.1 (plus P7.3 if JNI signatures mismatch). | User rule. Soong is the only authoritative resolver of AOSP's native module graph (Skia, HWUI, minikin, ICU, HarfBuzz…); re-deriving it by hand would be thousands of lines of guesswork. |
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
bridge/shell/        apklayer-shell (C, shell.c)
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

**Exit test P4:** corpus T3 apps start and render. A simple NDK GLES sample (`hello-gl2`)
draws via Mesa in its subsurface.

### P5 — Platform services (M9)

Each item is an apklayer shadow plus a host backend, with its own scripted check:
1. **WebView → WPE WebKit** (D11): load and run JS, `evaluateJavascript`,
   `addJavascriptInterface`, `WebViewClient` callbacks, input forwarding, cookies in the app
   data dir.
2. **Media → GStreamer** (D12): MediaPlayer (local/http), AudioTrack, SoundPool, MediaCodec
   (decode to Surface → subsurface; decode to ByteBuffer), MediaExtractor/MediaMuxer basic.
3. **Portals** (D13): file chooser for SAF; OpenURI for outbound `ACTION_VIEW`/`SEND`;
   Notification → org.freedesktop.Notifications (with actions → PendingIntent); camera via
   PipeWire camera portal → Camera2 minimal; location → GeoClue portal → LocationManager.
4. **Connectivity:** ConnectivityManager/NetworkCallback reflect NetworkManager state;
   DownloadManager → a JVM HTTP client into the app's external dir.
5. **Accounts/keystore:** AndroidKeyStore backed by a per-app software keystore file
   (BouncyCastle). AccountManager is local.
6. **microG** (D14): bundled, auto-installed. GCM/FCM out of scope until accounts work.

**Exit test P5:** corpus T2 ≥ 80%. A video plays with sound, a WebView-based app logs in to a
test page, and "Open link" opens the host browser.

### P6 — Multiple apps and the system (M10)

1. **`apklayerd`** (D15): starts with the shell. Holds the package registry from the install
   cache, and resolves implicit intents across installed apps (chooser through the shell when
   ambiguous).
2. **Cross-process calls:** `startActivity` to another package spawns or reuses that app's JVM.
   `bindService`/`ContentResolver` to another package is relayed as Parcel bytes; the
   receiving JVM unmarshals with the real `Parcel`/AIDL `Stub`.
3. `BroadcastReceiver`s from the manifest: `BOOT_COMPLETED`-style system broadcasts are not
   sent; app-to-app explicit broadcasts are relayed.
4. **Sandboxing outside Flatpak:** each app JVM runs under `bwrap`, seeing only its data dir,
   storage, the deps cache and the shell socket.

**Exit test P6:** sharing text from one installed app to another works. An app using microG's
location or SafetyNet-free APIs runs.

### P7 — Own native runtime: native arm64 + GPU HWUI (M11)

1. **Soong host build, x86_64 (necessary: D24).** In OrbStack, with
   `OUT_DIR=~/apklayer-aosp-out` and `SOONG_GEN_COMPDB=1`, build only the host variant of
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
- No AOSP builds, local artifacts first, one-time fetches only through `deps/deps.lock`.
- Every phase: update goal.md (milestone status), memory.md (facts learned) and this file
  (if a decision changed, with the reason).
- Report progress to the user at every exit test, with a snapshot.

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
9. P1 exit test with uibench + two corpus T1 apps.
