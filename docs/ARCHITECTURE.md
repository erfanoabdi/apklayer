# apklayer — Architecture

## One sentence
Run the Android framework on the JVM (unmodified AOSP `android-all` + Robolectric shadows), load the APK's DEX as JVM classes (dex2jvm), and
underneath run **Android's own native libraries and services** on the host (bionic_translation, kernel binder, SurfaceFlinger, apklayer's HALs), so
apps and games see real Android, shown as Wayland windows. Details of that route follow in "Android side and host side".

## Pipeline

```
 app.apk ─┬─ classes*.dex ──dex2jvm──> app classes (.jar) ──DenyInstrument──> @DoNotInstrument
          │                 (hierarchy from android-all)
          └─ manifest + resources.arsc ──AOSP PackageParser / native AssetManager

 App JVM, one per app: Robolectric sandbox (android-all 16, SDK 36) + apklayer shadows
   apklayer.host: Main → AppHost (lifecycle, back stack, host input, windows), the hub client
   Android's native libraries (libandroid_runtime, HWUI, media, binder) loaded by bionic_translation; the app's own .so too
        │ kernel binder (binderfs)
 Instance services, host processes (scripts/arm64-stack.sh): servicemanager, SurfaceFlinger, gralloc, HWC, audio, media, vdisplay sink
        │ dmabuf frames, real vsync
 apklayer-shell (one per session): one xdg_toplevel per virtual display, pointer/keyboard/touch/clipboard/IME → the focused app
        │
   Wayland compositor
```

## Components

| Path | Role |
|---|---|
| `runtime/dex2jvm/` | DEX → JVM bytecode (upstream kksimp/dex2jvm, Apache-2.0, plus our `HostDefaults.java`). Reads the class hierarchy from its own classpath, so it runs with the target `android-all` on it. |
| `runtime/host/java/apklayer/tools/DenyInstrument.java` | Stamps `@DoNotInstrument` on the app classes (dex2jvm's `<init>` lowering conflicts with Robolectric's constructor renaming) and rewrites a few app calls (native loading, services). |
| `runtime/host/java/apklayer/host/` | `Main` builds the sandbox, `AppHost` runs the app (setup as in Robolectric's simulator `AppLoader`), `HostInput`/`HostWindows`/`ScriptRunner` input and verification steps, `ShellClient` the shell connection, `Native*`/`ShadowHost*` the native shadows, `Hub*` the instance hub, `HostShadowProvider` registers the shadows. |
| `runtime/patches/` | Robolectric overlay classes (thin, marked "apklayer patch", listed in docs/PATCHES.md). |
| `bridge/native/` | The JNI shim that registers Android's natives, system properties, the path layer, TLS and binder glue, `NativeLoader` support for apps' `.so`. |
| `bridge/hals/` | Host side of the graphics, audio and media HALs (GBM/EGL, PipeWire, GStreamer). |
| `bridge/shell/` | `apklayer-shell`: the session's Wayland side (protocol documented in `shell.h`). |
| `aosp/` | Android-side modules built in the LineageOS tree: HALs (gralloc, composer, mapper, EGL, Vulkan, audio, media), services, tools. |
| `runtime/host/jvm.args` | JDK `--add-opens` flags Robolectric needs on modern SDKs. |
| `bin/apklayer` | CLI. `install`: dex2jvm (cached by APK sha256 + android-all + dex2jvm commit) → DenyInstrument → `Main --describe`. `run`: starts the services on demand and the app JVM. |

## App data (PLAN D20)

Robolectric creates `<pkg>-dataDir`, `userDataDir` and `deviceDataDir` in its per-run temp
directory when the Application is created, and deletes them at exit. The host runs with
`LazyApplication` ON and pre-creates those three entries as links into
`apps/<pkg>/data/{app,user,device}` (`-Dapklayer.data`, `-Dapklayer.package`) before the
Application exists. Robolectric keeps an existing entry and its exit cleanup does not follow
links, so SharedPreferences, databases, files and caches persist across launches.

## Why these choices
- **Unmodified framework.** Android version bumps mean switching the `android-all` jar, not re-applying patches. Deviations from upstream are thin
  and listed in docs/PATCHES.md.
- **Android's own native libraries.** Real HWUI/Skia, SurfaceFlinger, the media stack and binder behave like a device, including for native games;
  the hardware layer is apklayer's, backed by the host's standard interfaces and never tied to one GPU vendor.
- **Separate shell process.** One component owns Wayland for every app, with crash isolation from the app JVMs.
- **Arm64 only for now.** The device libraries come from an arm64 LineageOS build; an x86_64 build is the step toward a universal runtime.

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
