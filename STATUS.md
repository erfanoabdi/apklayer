# apklayer: status (short; history is in CHANGELOG.md, decisions and open work in docs/PLAN.md, binding rules in CLAUDE.md)

## What it is
Run Android APKs natively on Linux (no emulator, no VM): Android's own arm64 libraries from a LineageOS 24 `gsi_arm64 userdebug` build run under
bionic_translation on kernel binderfs; the framework is unmodified AOSP `android-all` on the JVM with Robolectric shadows; apklayer's HALs and
`apklayer-shell` (Wayland windows) sit underneath. Workspace `/Users/erfanabdi/GitHub/apklayer`. Never use the old name "apkly".

## Current state (2026-10-28)
- Works: UiBench with GPU rendering, input, dialogs, resize; WebView (Chromium provider APK, needs `scripts/webview-setup.sh`); TextureView/SurfaceView; native games (OTR2
  renders); media codecs and audio through the real services; the instance hub (bound/started services, broadcasts, providers, activities, notifications, pending intents).
- The CLI is the C++ tool in `cli/` (`bin/apklayer` execs it): `install | run | list | uninstall | stop | hub | component-host | wait hub | env`, test helpers
  `stack <lib> [args]` and `exec <lib> [args]`. Readiness is a property of the instance (`apklayer.stack.ready`, `apklayer.hub.ready`), waited on with `aprop wait`.
  Logs and markers: `~/.local/state/apklayer/<instance>/` (`APKLAYER_LOGS` overrides). Apps: `~/.local/share/apklayer/apps/<pkg>`.
- Build: `meson setup b && ninja -C b` (needs g++), `scripts/build-tree-host.sh ~/arm64-native`, `scripts/fetch-deps.sh`. Run on the VM:
  `XDG_RUNTIME_DIR=/run/user/1000 ~/apklayer/bin/apklayer run <package>`; `servicetool list|check|wait|dump NAME` via `apklayer exec libapklayer_servicetool.so ...`.
- Open: multi-instance (P9) deferred by the user; Vulkan presentation is opt-in and blocked on the UTM host stack; audio HAL's first output stream fails on the UTM VM
  (native-player); TextureView renders black on the UTM VM; `hub-services.sh auto` ("oneway native callback"); WebView needs the provider set up on the VM; the x86_64 build
  of the Android libraries; sandbox before a public release; COMMERCIAL.md terms, CLA, NOTICE files; replacing the ninja-replay build scripts.

## Test VM (UTM on this Mac)
- Host and password: `tests/vm/local.conf` (untracked, see `local.conf.example`); the VM user has passwordless sudo. Ubuntu 26.04 arm64, 6 CPUs, 9 GB, GNOME Wayland
  (`wayland-0`, `XDG_RUNTIME_DIR=/run/user/1000`), kernel 7.0. GPU: Venus Vulkan 1.4 on the Apple M5 Max, virgl ES 3.0 (apklayer claims 3.2 through the driver's version override).
- `binder_linux` ignores `devices=` once loaded: `scripts/vm-binder-setup.sh` creates the nodes with the binder-control ioctl (run after a reboot).
- `/tmp` is tmpfs: Robolectric leaves ~120 MB per aborted JVM; clean `/tmp/robolectric-nativeruntime*`, never put big APKs there.
- Screenshots: `tests/vm/vmcapture.py` (flameshot in the guest). Real input: `tests/vm/uinput-keys.c`. Layout on the VM: `~/apklayer` (synced repo, build in `b/`),
  `~/arm64-native`, `~/android-arm64`, `~/bt-arm`, `~/.cache/apklayer/deps`.

## Reference projects on this machine (look here before writing or downloading)
- `/Users/erfanabdi/GitHub/Mac.apk` (kksimp/Mac.apk, AGPL), `/Users/erfanabdi/GitHub/robolectric` (MIT), `/Users/erfanabdi/GitHub/android_translation_layer` (ATL; bionic-on-glibc loading),
  `/Users/erfanabdi/GitHub/Husk` (GPL-2.0, iOS translation layer: read-only reference, its engine test tools are a model for our compatibility corpus; do not copy code).
- LineageOS 24 tree: `~/los24` in the OrbStack VM `ubuntu22-x86` (`/Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24`; use `orb -m ubuntu22-x86 bash -c ...`, listings over the
  mount are slow). Build rules: `docs/AOSP-BUILDS.md`.

## Facts that still bite
- dex2jvm reads the class hierarchy from its own classpath (run it with `android-all`, away from any Android SDK); its output needs `@DoNotInstrument` (`DenyInstrument`).
- SDK 36 (`android-all-instrumented-16-robolectric-13921718-i7`) needs the `runtime/host/jvm.args` `--add-opens`; `android-stub.jar` is the SDK 36.1 `android.jar`.
- The property store is write-once for `ro.*`: a stack gets a fresh store per start. Creating a service lets the main looper run other work: hub starts wait while it is created.
- `pkill -f <pattern>` can kill the shell that runs it (use `pkill -x` or `[x]pattern`); macOS `._*` files: `COPYFILE_DISABLE=1 tar`.
- `scripts/install-shim.sh` needs the generated, untracked `bridge/native/android_stubs.c` (copy `~/bt-arm/android_stubs.c`); gcc 15 makes implicit declarations errors.
