# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan: `docs/PLAN.md`. History: `CHANGELOG.md`.

## NEXT TASKS (in order; item 1 is proposed, the user confirms)
1. **Fix the intermittent app hang** in `HardwareRenderer.nSetStopped` when a window goes inactive (another app's window appears). It makes the multi-app hub tests flaky and would hit real use.
2. **Compatibility corpus** (`tests/corpus/`): UiBench, OTR2, one app per engine (Unity, UE4, Godot, cocos2d-x, Flutter), F-Droid apps, WebView and media samples; a runner that installs, launches, waits for the first frame (marker, no timers), screenshots and records pass/fail in `tests/corpus/results.md`; triage failures into gaps.
3. **Android Keystore** (many apps need it): `KeyStore`/`KeyGenParameterSpec` backed by a host key store, with a test app.
4. **The failures we already know**: audio HAL stream open on the UTM VM, TextureView black on the UTM VM, `hub-services.sh auto` ("oneway native callback"), WebView setup on the VM.
5. **System apps and providers on every instance** (Settings, Contacts, Calendar, Download, DocumentsUI, MediaProvider).
6. **Hardware domains, last**: camera, GNSS, sensors, vibrator, battery, connectivity, USB, biometrics, NFC, portals, Bluetooth.
7. **Before release**: COMMERCIAL.md terms, CLA, NOTICE files, per-app sandbox, x86_64 Android libraries (needs approval for a second `out/`), Flatpak.
Everything parked is listed in `docs/PLAN.md`.

## State (2026-10-28)
- Works: UiBench (GPU, input, dialogs, resize), WebView, TextureView/SurfaceView, native games (OTR2 renders), media and audio through the real services, the instance hub
  (services, broadcasts, providers, activities, notifications, pending intents). Verified on the UTM VM this session: gpu smoke, native-surface, native-media, codec2, binder,
  services, broadcasts, providers; activities, notifications and pending intents pass phase by phase but flake when run back to back (the hang in item 1).
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
