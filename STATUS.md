# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan: `docs/PLAN.md`. History: `CHANGELOG.md`.

## NEXT TASK (proposed, waiting for the user's OK)
**Build the compatibility corpus on the current route.** Everything else is done, blocked or deferred, and "works" is only anecdotal today.
1. A small APK set in `tests/corpus/`: UiBench, OTR2, one app per game engine (Unity, UE4, Godot, cocos2d-x, Flutter), a few F-Droid apps, WebView and media samples.
2. A runner (`tests/corpus/run.sh`): install, launch on the test VM, wait for the first frame (property or log marker, no timers), screenshot, record pass/fail in `tests/corpus/results.md`.
3. Triage each failure into a gap to fix; fix the most common first.
Alternatives, if the user prefers: ship path (x86_64 Android build, Flatpak: needs approval for a second `out/`), audio HAL stream failure on the UTM VM, one hardware domain (camera/sensors/GNSS).

## State (2026-10-28)
- Works: UiBench (GPU, input, dialogs, resize), WebView, TextureView/SurfaceView, native games (OTR2 renders), media and audio through the real services, the instance hub
  (services, broadcasts, providers, activities, notifications, pending intents). Verified on the UTM VM this session: gpu smoke, native-surface, native-media, codec2, binder,
  services, broadcasts, providers; activities, notifications, pending intents and hub-services are being re-run after the C++ tool change.
- Known failing, not from this work: native-player (audio HAL stream open), texture-view (black on the UTM VM), webview (no provider on the VM), `hub-services.sh auto`.
- CLI: C++ tool in `cli/` (`bin/apklayer` execs it): `install | run | list | uninstall | stop | hub | component-host | wait hub | env`; test helpers `stack <lib>` and `exec <lib>`.
  State and logs: `~/.local/state/apklayer/<instance>/`. Apps: `~/.local/share/apklayer/apps/<pkg>`.
- Build: `meson setup b && ninja -C b` (g++), `scripts/build-tree-host.sh ~/arm64-native`, `scripts/fetch-deps.sh`. Run on the VM:
  `XDG_RUNTIME_DIR=/run/user/1000 ~/apklayer/bin/apklayer run <package>`.

## Test VM (UTM)
Host and password: `tests/vm/local.conf` (untracked; `local.conf.example`). Ubuntu 26.04 arm64, GNOME Wayland, Venus Vulkan 1.4, virgl ES 3.0 (apklayer claims 3.2). Passwordless sudo.
Repo copy `~/apklayer`, runtime `~/arm64-native`, libs `~/android-arm64`. After a reboot run `scripts/vm-binder-setup.sh`. `/tmp` is tmpfs: never put big files there.
Screenshots: `tests/vm/vmcapture.py`. Input: `tests/vm/uinput-keys.c`.

## References on this machine
Mac.apk, Robolectric, android_translation_layer (all in `~/GitHub`), Husk (GPL, read-only reference, not to copy). LineageOS tree `~/los24` in OrbStack `ubuntu22-x86`
(use `orb -m ubuntu22-x86 bash -c ...`); build rules `docs/AOSP-BUILDS.md`.

## Facts that bite
- dex2jvm needs `android-all` on its own classpath; its output needs `@DoNotInstrument` (`DenyInstrument`).
- `ro.*` properties are write-once: every stack start gets a fresh store.
- `pkill -f` can kill your own shell: use `pkill -x` or `[x]pattern`. macOS tar: `COPYFILE_DISABLE=1`.
- `scripts/install-shim.sh` needs the untracked `bridge/native/android_stubs.c` (copy `~/bt-arm/android_stubs.c`); gcc 15 makes implicit declarations errors.
