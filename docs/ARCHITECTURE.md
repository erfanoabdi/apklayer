# apklayer — Architecture (Robolectric method)

## One sentence
Run the **unmodified AOSP framework compiled for the JVM** (Robolectric's `android-all`), load
the APK's DEX as JVM classes (dex2jvm), drive the real Activity lifecycle, and connect the
result to the Linux desktop through a thin host layer (Wayland today).

## Pipeline

```
 app.apk ─┬─ classes*.dex ──dex2jvm──> app classes (.jar) ──DenyInstrument──> @DoNotInstrument
          │                 (hierarchy from android-all)
          └─ manifest + resources.arsc ──AOSP PackageParser / AssetManager (APK passed as the manifest)

 App JVM, one per app (x86_64 under Rosetta on the arm64 VM until PLAN P7; native on x86_64)
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ Robolectric sandbox: android-all-instrumented 16 (SDK 36) + shadows 4.17     │
 │ NATIVE graphics: libandroid_runtime (in android-all) = real HWUI/Skia        │
 │                                                                              │
 │ apklayer.host (Simulator method, no JUnit): Main → SandboxBuilder → AppHost  │
 │   activity back stack ← startActivity()/finish() (recorded by Robolectric)   │
 │   event loop: Looper + paused Choreographer; real draw traversals            │
 │   on draw: ScreenUpdateHandler → takeScreenshot (all windows) → int[]        │
 │   shell INPUT → MotionEvent/KeyEvent → UiAutomation.injectInputEvent         │
 │   ShellClient (pure Java): Unix socket + 2-buffer shm file per window        │
 └───────────────┬──────────────────────────────────────────────▲───────────────┘
   FRAME(buf, damage) │ $XDG_RUNTIME_DIR/apklayer/shell.sock     │ INPUT / RELEASE / CLOSE
 ┌───────────────▼──────────────────────────────────────────────┴───────────────┐
 │ apklayer-shell (native, host arch, one per session, many apps):              │
 │   xdg_toplevel per window; the app's shm file is the wl_shm pool (zero copy) │
 │   wl_seat pointer/keyboard → focused window's app                            │
 └──────────────────────────────────────────────────────────────────────────────┘
                                   Wayland compositor
```

## Components

| Path | Role |
|---|---|
| `runtime/dex2jvm/` | DEX → JVM bytecode (upstream kksimp/dex2jvm, Apache-2.0). Reads class hierarchy from its own classpath, so it must be run with the target `android-all` on it. |
| `runtime/host/java/apklayer/tools/DenyInstrument.java` | Stamps a runtime-visible `@DoNotInstrument` on the app classes. dex2jvm's `<init>` lowering conflicts with Robolectric's constructor renaming. |
| `runtime/host/java/apklayer/host/Main.java` | Entry point: builds the sandbox with Robolectric's simulator `SandboxBuilder` (no JUnit) and runs `AppHost` in it. |
| `runtime/host/java/apklayer/host/AppHost.java` | App setup as in the simulator's `AppLoader` (manifest = the APK), the simulator's event loop, launcher resolution, back stack (`Robolectric.setupActivity`-style launches; previous activity stopped and hidden unless the new one is translucent, as `ActivityThread` does), resize/DPI (`hostResize`). |
| `runtime/host/java/apklayer/host/HostInput.java` | What the shell sends (pointer, xkb keys, wheel, configure) → Android events. Keys go through the topmost window's `ViewRootImpl` (focus navigation, touch mode); Backspace = Back outside text fields. |
| `runtime/host/java/apklayer/host/HostWindows.java` | Which window/view gets input: topmost window by type (dialogs above activity windows), hit-testing, wheel routing, debug dumps. |
| `runtime/host/java/apklayer/host/ScriptRunner.java` | Verification steps (`-Dapklayer.script`): tap, key, keydown/keyup, wheel, configure, back. |
| `runtime/host/java/apklayer/host/ShellFrameListener.java` | `FrameListener` for Robolectric's `ScreenUpdateHandler`: receives the composed screen (`UiAutomation.takeScreenshot`) only when something drew, and presents it. |
| `runtime/host/java/apklayer/host/ShellClient.java` | Pure-Java client of apklayer-shell: Unix socket, the window's two-buffer shm file, damage rect vs. the shown frame, buffer release tracking (latest frame wins). |
| `runtime/host/jvm.args` | JDK `--add-opens` flags Robolectric needs on modern SDKs (copied from Robolectric's own test configuration). |
| `bridge/ndk/*.c` | NDK shim libraries (P4.3), each a glibc `<name>_shim.so.0` mapped from `<name>.so` by the bionic_translation cfg.d. So far: `liblog`. |
| `runtime/host/java/apklayer/host/Services.java` | Runs the app's own services in-process (`startService/bindService/...` routed here by `DenyInstrument`). |
| `runtime/host/java/apklayer/host/UnsafeAccess.java`, `Reflect.java` | `Unsafe.getUnsafe()` for app code (also via reflection), as on ART. |
| `bridge/native/native_loader.c` | `libapklayer_native.so`: JNI helper that maps an app's bionic-linked `.so` with bionic_translation's linker (`bionic_dlopen`), runs `JNI_OnLoad` and binds natives (P4). Built by `scripts/build-native.sh`. |
| `runtime/host/java/apklayer/host/NativeLoader.java` | Receives the app's `System.loadLibrary/load` (rewritten by `DenyInstrument`), loads through the JNI helper, binds `Java_*` exports by name (ELF `.dynsym` parse + JNI demangling + `RegisterNatives`). |
| `bridge/shell/` | `apklayer-shell`: the session's Wayland side for all apps. `shell.h` (protocol doc, shared state), `main.c` (event loop), `client.c` (app sockets), `window.c` (xdg toplevels, zero-copy buffers, in-place rebuffer), `decor.c` (client-side title bar: Back/Minimize/Maximize/Close), `pointer.c` (title bar clicks, edge resize, wheel, cursors), `keyboard.c` (xkb → Android keys, repeat), `registry.c` (globals, seat, output scale). |
| `bin/apklayer` | CLI (PLAN D19). `install`: dex2jvm (cached by APK sha256 + android-all + dex2jvm commit) → DenyInstrument → `Main --describe` (package, label, launcher, version, launcher icon drawn by the framework) → `~/.local/share/apklayer/apps/<pkg>/` + `.desktop` entry (StartupWMClass = app_id) + `.apk` MIME handler. `run`: starts the shell if needed, runs the host with persistent data. Config: `~/.config/apklayer/env`. |
| `scripts/vm-run.sh` | Syncs to the VM, builds with Meson, restarts the shell and runs `bin/apklayer run <apk>`. |
| `scripts/check.sh` | Headless scripted run with snapshots (fast check on the Mac). |

## Shell protocol v1 (Unix socket, little-endian `{u32 type, u32 len, payload}`)

| Dir | Type | Payload |
|---|---|---|
| app → shell | 1 HELLO | `i32 pid`, utf8 package |
| app → shell | 2 WINDOW_CREATE | `i32 id, w, h`, utf8 `"<shm path>\0<title>"` (file = 2 × w·h ARGB buffers) |
| app → shell | 3 FRAME | `i32 id, buf, x, y, w, h` (damage rectangle) |
| app → shell | 4 WINDOW_DESTROY | `i32 id` |
| shell → app | 101 INPUT | `i32 id, type, a, b`: type 0/1/2 = touch DOWN/UP/MOVE at (a, b); 100 = key (evdev a, pressed b) |
| shell → app | 103 CLOSE | `i32 id` (window closed by the user) |
| shell → app | 104 RELEASE | `i32 id, buf` (compositor released that buffer) |

## App data (PLAN D20)

Robolectric creates `<pkg>-dataDir`, `userDataDir` and `deviceDataDir` in its per-run temp
directory when the Application is created, and deletes them at exit. The host runs with
`LazyApplication` ON and pre-creates those three entries as links into
`apps/<pkg>/data/{app,user,device}` (`-Dapklayer.data`, `-Dapklayer.package`) before the
Application exists. Robolectric keeps an existing entry and its exit cleanup does not follow
links, so SharedPreferences, databases, files and caches persist across launches.

## Why these choices
- **Unmodified framework.** Android version bumps mean switching the `android-all` jar, not
  re-applying patches. There are no hand patches to AOSP or Robolectric (see docs/PATCHES.md).
- **NATIVE graphics.** LEGACY mode is a recording fake: it draws no text and doesn't compose
  `translate`. NATIVE mode runs AOSP's own HWUI/Skia.
- **Separate shell process.** Crash isolation, one component owning Wayland for every app,
  and no native code inside app JVMs, so an app JVM's architecture can differ from the host's.
- **Rosetta (development stopgap until PLAN P7).** Robolectric publishes its native runtime
  for linux-x86_64, mac and windows only. The arm64 VM therefore runs the app JVM as x86_64
  under Rosetta (C1 JIT + Serial GC, because C2 and G1 crash there; Robolectric's temp-dir
  cleanup at exit occasionally segfaults too). P7 builds the runtime for native arm64.

## Known limits
See docs/PLAN.md: the plan of record, with the target architecture (apklayer-shell + one JVM
per app + apklayerd) and phases P1–P7.

## Android side and host side (device-library route, PLAN P7)

The route that runs Android's own userspace (framework natives, libgui, libhwui, SurfaceFlinger, servicemanager) on a Linux
desktop splits every hardware-facing service in two:

```
 app / SystemUI / framework (Java, HotSpot)
        |  binder (kernel binderfs now, userspace binder later)
 Android side, built in LineageOS by Soong, runs on bionic_translation:
   aosp/hals/<domain>/    AIDL HAL frontends (composer3, allocator, mapper, camera, gnss, vibrator, sensors, ...)
   aosp/tools/            development tools
        |  plain C API  (bridge/hals/<domain>/include/apklayer/<domain>.h)
 Host side, built by Meson, glibc:
   bridge/hals/<domain>/  GBM, EGL, Wayland dmabuf, PipeWire, V4L2, GeoClue, iio, evdev force feedback ...
   bridge/ndk/            replacement libraries mapped over device libraries (libnativedisplay, libselinux, ...)
   bridge/shell/          the Wayland client (windows, input, vsync)
```

The same C API shape as libhybris's compat layers, in the opposite direction: there the host calls an Android HAL, here an
Android HAL calls the host. Rules: the Android side stays thin (translate, never decide); no GPU/vendor specifics in the Android
side; buffers cross as dmabuf fds and fences as sync_file fds; one directory per domain on both sides, named as AOSP names it.
`aosp/` is mirrored into `~/los24/vendor/apklayer` (its own git history, one commit per sync) by `scripts/sync-aosp.sh`.

Yardstick (user, 2026-10-26): the SystemUI APK is not a goal but a proof of breadth; the system services stay Robolectric
shadows (no system_server). Real code is used for graphics, binder and hardware.

Multiple instances (user, 2026-10-26): every piece above is per instance, parameterised by an instance root (data, properties,
binder device, sockets). One instance = one full phone (or a single app). See PLAN P9.


## GPU windows and timing (device-library route, 2026-10-27)
Each app JVM renders into real BLAST window layers under WMS-style containers. `WindowSurface` positions the parent using frame minus surfaceInsets and stacks it by window type; BLAST keeps ownership of the child buffer geometry. One app virtual display composes its layer stack into gralloc buffers. `libapklayer_vdisplay` consumes those buffers, presents dmabufs through the host display HAL, and returns acquired buffers on Wayland release. Old-size queued frames are dropped after resize.

The composer’s primary is a headless timing anchor. It connects to the shell for real output mode/refresh and vsync but creates no window. Only app sinks create toplevels; an initial blank shm frame maps each one before Android rendering. `vsync.c` routes outstanding timing requests through a mapped active surface and rewires them when the source changes. Timestamps come from Wayland frame callbacks; no timer synthesizes refresh. Native clients can request timing without owning another window. Input/state/configure messages for a display window go to its JVM through the shell’s input proxy.

Concurrent launches in one Android instance share a serialized startup. Each app launch uses its launcher PID for its layer stack and prefixes its window title with the launch and instance before truncating the package suffix to the shell title limit. Input proxies remain per client/window. Ending one JVM only stops its sink; instance services stay until `apklayer stop`. Separate full-phone binder/data domains are still the later P9 work.

Text-input state belongs to the proxied display window. Only the window entered by the compositor enables its field; IME events return through the proxy to its JVM. Clipboard selections are read and written asynchronously in the shell poll loop, so an app can consume another JVM’s selection without blocking the shared Wayland client. Selection payloads own immutable text snapshots and echo suppression is per JVM.

The tree launcher uses the selected framework for both dex conversion and cache identity. Install-time icon/metadata rendering and app execution share the arm64 native JVM setup and Android root. Read-only properties belong to the service instance; app JVMs use the same staged system hyphenation data and never replace its path. Host classes and runtime overlays build in fresh staging directories with compilation errors propagated before publishing.

The media integration begins with an opt-in Codec2 AIDL service. Android’s existing SimpleC2Component and ComponentStore frontend handle lifecycle/configuration and binder transport; the host GStreamer backend retains raw YUV samples. The software path copies YUV into mapper-described flexible-YUV gralloc buffers, which cross binder back to the real Codec2Client. Compressed input uses AOSP’s existing gralloc-backed BLOB allocator, selected through the platform pool mask; it needs no DMA-heap device. This path is verified for visible and hidden VP8 frames. Opt-in app MediaCodec uses the real framework Java implementation and libmedia_jni, registered under the existing native-runtime JNI adapter. A small entrypoint starts the existing AOSP MediaPlayerService/ResourceManagerService. The loader finishes deferred MediaCodec/List initialization before Application.onCreate; tests verify discovery, getOutputImage YUV, timestamps/EOS and stop/release. Android uses Linux memfd for shared memory. Hardware sample import, video layer presentation and app MediaPlayer/audio HAL integration are still being built.

Native SurfaceView uses framework Java lifecycle and its own real BLAST queue/SurfaceFlinger child layer. Software decoder YUV is copied into gralloc; GPU composition samples YUV dma-bufs directly. If GBM cannot allocate native YUV, each plane has a correctly typed R/RG resource. Android mapper lock requires a single base pointer, so this fallback stages contiguous YUV for CPU access and publishes writes to the separate planes at unlock. Its CPU layout offsets are distinct from the physical per-fd EGL offsets. A single tall R8 allocation is unsuitable on virgl: import success did not imply correct YUV sampling. Native decoder dma-buf import remains separate unfinished work.

For reordered codecs, the host decoder can notify sample/EOS/error availability from streaming threads. Notifications only schedule frontend work; clients pull and map on their own work thread. Closing the backend disables notifications and joins its pipeline. The H.264 adapter queues a drain event on SimpleC2Component’s existing handler, then completes pending work by output PTS. The EOS input can produce a cloned picture before its original work returns final empty EOS. Host, real AIDL and Java buffer/Surface tests verify B-frame order and delayed EOS; Java flush/replay also passes.

Native MediaExtractor runs original media JNI and parses container bytes in a separate AOSP MediaExtractorService process, with original MP4/WebM plugins. A MediaDataSource callback returns source bytes over binder to that service; track metadata, CSD, sample reads and seeking feed the existing native decoder. Absolute plugin paths resolve through the instance Android-root overlay, whose plugin links point to staged tree libraries. Callback-source MP4 extraction/decode and descriptor-source offset/sample/seek/ownership are verified. Descriptor JNI adapts actual Android/HotSpot FileDescriptor layouts without changing native FileSource semantics. MediaPlayer integration remains subsequent work.


## Instance hub and cross-app service binding

The device-library route can enable `-Dapklayer.realbinder=true -Dapklayer.instancehub=true`. App JVMs keep their system-service shadows while original Binder/BinderProxy/Parcel JNI carries cross-process objects. The headless `apklayer hub` JVM owns the installed-package/process directory and bound-service records. A lifetime lock prevents duplicate hubs; app startup launches it on demand and waits for actual service-manager publication. Kernel Binder identities and endpoint death drive process records. All JVMs currently share the host UID; this does not provide full-phone isolation.

The hub reuses the framework APK parser to resolve declared services and check enabled/exported state and normal declared permissions. It launches service-only APKs through `component-host`, which runs their Application and main looper without a virtual-display window. App endpoints accept lifecycle commands only from the hub. Existing Robolectric ServiceController machinery creates and destroys services on their app main threads. The client uses Android's original LoadedApk ServiceDispatcher and IServiceConnection, including native Binder death monitors, callback threading and forgotten-connection suppression. Serialized outbound commands preserve bind/unbind ordering. Bound processes restart while auto-create connections remain.

Verified coverage includes generated AIDL, Unicode/Bundle payloads, normal permission checks, null binding, cancellation, abrupt death/reconnection and unbind/onDestroy. Multiple bindings per connection, filter-specific rebind and signature/runtime grant integration remain service gaps. Broadcasts, providers/observers, activities/results, started services, pending intents and notifications are the remaining Milestone E work. The feature remains opt-in while that integration proceeds.
