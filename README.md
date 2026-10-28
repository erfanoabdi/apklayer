# apklayer

Run Android APKs natively on Linux: real Android userspace, no emulator, no VM.

apklayer runs apps on **Android's own native libraries** (a LineageOS 24 `gsi_arm64` build: SurfaceFlinger, HWUI, libandroid_runtime, media, binder), loaded on the host by
bionic_translation, with apklayer's own hardware layer underneath (graphics, audio, media, GPU) on the host's standard interfaces (GBM/EGL/Wayland, PipeWire, GStreamer).
The framework runs on the JVM as unmodified AOSP (`android-all`) with Robolectric shadows.

```
app.apk ──dex2jvm──> JVM classes ─> android-all + Robolectric shadows, Android's native libraries   (runtime/host, bridge/native)
        │ binder (kernel binderfs)
        ▼
Android services as host processes: servicemanager, SurfaceFlinger, gralloc, HWC, audio, media     (aosp/, bridge/hals)
        │ dmabuf, real vsync
        ▼
apklayer-shell ──> one Wayland window per app                                                       (bridge/shell)
```

**Works:** UiBench with GPU rendering and input, WebView, SurfaceView/TextureView, native games (Off The Road 2), media and audio, apps talking to each other through the instance hub.
**Host:** arm64 Linux (needs the staged Android libraries); x86_64 is the next step. **In progress:** Vulkan presentation (opt-in), more hardware domains. Status: `STATUS.md`.

## Build and run
The Android side comes from a LineageOS tree built once; `CHANGELOG.md` logs the incremental builds, rules in `docs/AOSP-BUILDS.md`.
```sh
scripts/fetch-deps.sh                        # pinned Java deps -> ~/.cache/apklayer/deps
meson setup b && ninja -C b                  # apklayer tool, shell, converter, host HAL libraries
scripts/build-tree-host.sh ~/arm64-native    # host classes against the staged Robolectric
sudo scripts/vm-binder-setup.sh              # kernel binderfs
bin/apklayer install app.apk | run <package|app.apk> | list | uninstall <package> | stop
```

## Layout
`cli/` the tool · `runtime/` app host, dex2jvm, Robolectric overlays · `bridge/` JNI shim, shell, host HALs · `aosp/` Android-side modules · `scripts/` staging and builds ·
`tests/` VM tests, sample apps · `docs/` plan, architecture, patches, licenses · `STATUS.md`, `goal.md`, `CLAUDE.md` (agent rules).

## License
AGPL-3.0-or-later for everyone (`LICENSE`, `NOTICE`); a paid commercial license is available (`COMMERCIAL.md`). Third-party parts keep their licenses (`docs/THIRD_PARTY.md`).
