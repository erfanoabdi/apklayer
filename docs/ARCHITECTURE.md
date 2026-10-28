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
 Instance services, host processes (the stack daemon of `cli/stack.cpp`): servicemanager, SurfaceFlinger, gralloc, HWC, audio, media, vdisplay sink
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

## Windows, timing, media, hub (short)
- Each app renders into BLAST window layers of one SurfaceFlinger virtual display; the sink (`libapklayer_vdisplay`) turns it into a shell toplevel. The primary display is a headless
  timing anchor with the real refresh rate and vsync from the shell. Text input and clipboard belong to the focused proxied window; the shell reads and writes selections asynchronously.
- Media: Codec2 and the extractor run as separate AOSP services with host GStreamer decoders behind them; YUV reaches SurfaceFlinger as dmabufs. Audio goes through the audio HAL to PipeWire.
- Instance hub: a headless JVM (`apklayer hub`) owns the package/process directory; app JVMs carry real Binder/Parcel between each other. It reuses the framework's APK parser, launches
  service-only apps through `component-host`, and implements bound and started services, broadcasts, providers, activities, notifications and pending intents. Tests: `tests/vm/*.sh`.
- Every piece is meant to be per instance (instance root for data, properties, binder device, sockets); several instances at once are deferred (`docs/PLAN.md`).
