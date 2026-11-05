<p align="center"><img src="data/apklayer.svg" width="180" alt="apklayer logo: an Android robot in layered glass"></p>

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
Writing a hardware plugin (sensors, GNSS, vibrator or camera backend, built out of tree against `apklayer-dev`): `docs/PLUGINS.md`.
For development, the Android side comes from a LineageOS tree built once; `CHANGELOG.md` logs the incremental builds, rules in `docs/AOSP-BUILDS.md`.
```sh
meson setup b --prefix ~/apklayer-install -Dprebuilts=<prebuilts checkout>   # bionic_translation (subproject, patched), pinned jars: fetched
ninja -C b                                   # tool, shell, converter, host HALs, native runtime, host classes, framework jars (instrumented once)
meson install -C b --skip-subprojects        # the runtime into the prefix (bin/apklayer uses it)
sudo tools/host-setup/binder.sh              # kernel binderfs
bin/apklayer install app.apk | run <package|app.apk> | list | uninstall <package> | screenshot <package> <out.png> | stop
bin/apklayer instances | instance create|remove <id> | prop [set KEY VALUE | unset KEY] | info | clear-data | permissions [grant|revoke] | stop <package> | status
bin/apklayer gpus | gpu set <id>|unset         # the host's GPUs; the instance runs on the chosen one (default: the desktop compositor's)
```

**apklayer-manager** (`manager/`): a Material 3 window over the CLI for phones and desktops: apps (install, open, stop, clear data, uninstall,
info), instances, per-instance Android property overrides, what the instance sees of the device. Built on its own (prebuilt Slint, no Rust):
```sh
manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b && manager/b/apklayer-manager
```

## Logs and bug reports
Every launch of an app writes its log, from the desktop's app grid as well as from a terminal: the app's output, its `android.util.Log` lines and native logs.
- `apklayer logs <package>`: the log of the app's last launch (`--previous`: the launch before). `apklayer logs` lists all log files of the instance (`~/.local/state/apklayer/<instance>/`).
- `APKLAYER_LOGCAT=1 apklayer run <package>`: the same log live in the terminal, Android log included.
- `apklayer report <package>`: one archive (`apklayer-report-<package>.tar.gz`) with the apklayer version, your distribution, desktop and GPU, the app's info and permissions, its last two launch logs and the instance's service logs.

To report a bug: open the app again, reproduce the problem, run `apklayer report <package>` and attach the archive to a [new issue](https://github.com/erfanoabdi/apklayer/issues/new/choose) together with the app's link and version and the steps to reproduce. Look through the archive first: logs can contain account names, URLs and file names.

## Layout
`cli/` the tool · `runtime/` app host, dex2jvm, Robolectric overlays · `bridge/` JNI shim, shell, host HALs · `aosp/` Android-side modules · Meson builds everything (`meson.build`); `tools/` staging from the tree (`tools/stage`), generators, host setup ·
`tests/` VM tests, sample apps · `examples/` an out-of-tree plugin · `docs/` plan, architecture, patches, licenses · `STATUS.md`, `goal.md`, `CLAUDE.md` (agent rules).

## License
AGPL-3.0-or-later for everyone (`LICENSE`, `NOTICE`); commercial licenses are available from Navotpala Tech (Bliss Co-Labs), info@navotpala.tech (`COMMERCIAL.md`). Third-party parts keep their licenses (`docs/THIRD_PARTY.md`).
