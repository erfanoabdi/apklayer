# apklayer

Run Android APKs natively on Linux: real Android userspace, no emulator, no VM, no Linux-inside-Linux.

apklayer runs the apps on **Android's own native libraries** (the device build of LineageOS: SurfaceFlinger, HWUI, libandroid_runtime, the media
stack, binder), loaded on the host by bionic_translation, with apklayer's own hardware layer underneath (graphics, audio, media, GPU) backed by
the host's standard interfaces (GBM/EGL/Wayland, PipeWire, GStreamer). The framework itself runs on the JVM as unmodified AOSP (`android-all`)
with Robolectric shadows for the system services.

```
app.apk ──dex2jvm──> JVM classes ┐
        └─ manifest/resources ───┤ (AOSP PackageParser, native resources)
                                 ▼
  android-all (unmodified AOSP) + Robolectric shadows        host: lifecycle, windows, input   (runtime/host)
  + Android's native libraries on bionic_translation         apps' own .so run here too
        │ binder (kernel binderfs)
        ▼
  Android services as host processes: servicemanager, SurfaceFlinger, gralloc, HWC, audio, media   (aosp/, bridge/hals)
        │ dmabuf, real vsync
        ▼
  apklayer-shell ──> Wayland windows (one virtual display per app)                                  (bridge/shell)
```

## Status

- **Host:** Linux on arm64 (needs the staged arm64 Android libraries, see below). An x86_64 build of the Android libraries is the next step toward
  a universal runtime.
- **Works:** UiBench with GPU rendering, input, dialogs and resize; WebView (Chromium provider APK); TextureView/SurfaceView; native games
  (Off The Road 2 renders); media codecs and audio through the real Android services; bound services and the first broadcast cases across apps
  through the instance hub (the rest of that milestone is open).
- **Opt-in / in progress:** Vulkan presentation (`APKLAYER_VULKAN_PRESENT=1`, needs a host driver that can use imported dmabufs), system
  apps and providers on every instance, camera, the other hardware domains. See `docs/PLAN.md`.

## Build and run

The Android side comes from a LineageOS tree built once (`gsi_arm64 userdebug`); `docs/AOSP-BUILDS.md` lists the incremental builds and
`aosp/hals/README.md` the modules. The staged native runtime (`~/arm64-native`, `~/android-arm64`) is produced by
`scripts/collect-android-libs.sh`, `scripts/collect-android-root.sh` and `scripts/build-native-arm64.sh`.

```sh
scripts/fetch-deps.sh                              # pinned Java deps (deps/deps.lock) -> ~/.cache/apklayer/deps
meson setup b && ninja -C b                        # apklayer-shell, the dex2jvm converter, the host HAL libraries
scripts/build-tree-host.sh ~/arm64-native          # apklayer's host classes against the staged Robolectric
sudo scripts/vm-binder-setup.sh                    # kernel binderfs (binder_linux module)

apklayer install app.apk                       # convert once (cached), menu entry + icon, .apk file handler
bin/apklayer run <package|app.apk>                 # starts the instance's Android services on demand
bin/apklayer list | uninstall <package> | stop
```

Apps live in `~/.local/share/apklayer/apps/<package>/`. Settings for menu launches go in `~/.config/apklayer/env`.

## Layout

| Path | What |
|---|---|
| `goal.md` / `memory.md` / `HANDOFF.md` / `CLAUDE.md` | mission, status and facts, ordered work, the rules for contributors and agents |
| `docs/PLAN.md` | plan of record: decisions, phases, open work |
| `docs/ARCHITECTURE.md`, `docs/PATCHES.md`, `docs/THIRD_PARTY.md` | how it works, deviations from upstream, licenses |
| `runtime/dex2jvm/` | DEX → JVM converter (kksimp/dex2jvm, Apache-2.0, plus our `HostDefaults.java`) |
| `runtime/host/` | the app host (`apklayer.host`), the hub, native shadows; `runtime/patches/` Robolectric overlays |
| `bridge/native/` | the JNI shim that registers Android's natives, properties, paths, binder glue |
| `bridge/hals/` | host side of the graphics, audio and media HALs |
| `bridge/shell/` | `apklayer-shell`: Wayland windows for the virtual displays, input, clipboard |
| `aosp/` | Android-side modules built in the LineageOS tree (HALs, services, tools) |
| `cli/` | the `apklayer` tool (C++): install, run, stack daemon, environment |
| `scripts/` | staging, builds, the build and staging scripts, patches to the tree |
| `tests/` | `tests/vm/` runs on a test VM or device with real input and screenshots; `tests/apps/` sample APKs; `tests/uibench/` |

## License

AGPL-3.0-or-later for everyone (`LICENSE`, `NOTICE`); a paid commercial license is available for companies that do not want the AGPL terms
(`COMMERCIAL.md`). Third-party parts keep their own licenses (dex2jvm Apache-2.0, AOSP Apache-2.0, Robolectric MIT); see `docs/THIRD_PARTY.md`.
