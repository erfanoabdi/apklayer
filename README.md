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

**Works:** over 40 real apps draw and run (current F-Droid versions of Thunderbird, Element, Organic Maps, OsmAnd, VLC, mpv, FluffyChat, Telegram, NewPipe, Firefox (Fennec) and more: `tests/corpus/results.md`): GPU rendering, WebView, SurfaceView/TextureView, native games, media and audio (record and play), the system's own providers (settings, media, contacts, calendar, downloads, documents), keystore2, app installs from stores, apps talking to each other through the instance hub, several instances side by side.
**Host:** arm64 Linux (needs the staged Android libraries); x86_64 is staged but untested. **In progress:** Vulkan presentation (needs a real GPU), startup time. Status: `STATUS.md`, plan: `docs/PLAN.md`.

## Build and run
**Installing on a new machine: `docs/INSTALL.md`** (step by step, with the Android parts from the `prebuilts` branch; no LineageOS tree needed).
For development, the Android side comes from a LineageOS tree built once; `CHANGELOG.md` logs the incremental builds, rules in `docs/AOSP-BUILDS.md`.
```sh
tools/fetch-deps.sh                          # pinned Java deps -> ~/.cache/apklayer/deps
tools/build bt ~/bt-arm/bionic_translation   # bionic_translation, patched (patches/apply), for this machine
meson setup b && ninja -C b                  # tool, shell, converter, host HALs, native runtime (JNI shim, btload, preload, NDK stubs)
tools/build host ~/arm64-native              # host classes against the staged Robolectric
tools/build native ~/arm64-native [shards]   # stage the native runtime: ninja's outputs, native-runtime.jar, library mapping
sudo tools/host-setup/binder.sh              # kernel binderfs
bin/apklayer install app.apk | run <package|app.apk> | list | uninstall <package> | screenshot <package> <out.png> | stop
bin/apklayer instances | instance create|remove <id> | prop [set KEY VALUE | unset KEY] | info | clear-data | permissions [grant|revoke] | stop <package> | status
```

**apklayer-manager** (`manager/`): a Material 3 window over the CLI for phones and desktops: apps (install, open, stop, clear data, uninstall,
info), instances, per-instance Android property overrides, what the instance sees of the device. Built on its own (prebuilt Slint, no Rust):
```sh
manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b && manager/b/apklayer-manager
```

## Layout
`cli/` the tool · `runtime/` app host, dex2jvm, Robolectric overlays · `bridge/` JNI shim, shell, host HALs · `aosp/` Android-side modules · `tools/` builds (`tools/build`), staging from the tree (`tools/stage`), generators, host setup ·
`tests/` VM tests, sample apps · `docs/` plan, architecture, patches, licenses · `STATUS.md`, `goal.md`, `CLAUDE.md` (agent rules).

## License
AGPL-3.0-or-later for everyone (`LICENSE`, `NOTICE`); a paid commercial license is available (`COMMERCIAL.md`). Third-party parts keep their licenses (`docs/THIRD_PARTY.md`).
