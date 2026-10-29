# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan: `docs/PLAN.md`. History: `CHANGELOG.md`.

## 2026-10-29 night summary
- Fennec starts, loads pages, content processes run; page area still white (WebRender gets no transaction): see docs/PLAN.md item 1 and the tools in tests/vm (fennec-view.sh, egl-trace.bt).
- tests/apps/hello-system + tests/vm/system-apps.sh: 10 of 11 system-service checks pass (media open). Hub groups rerun: pass.
- Conversations starts; Device Info HW and Puzzles are licence/anti-piracy gated; Joplin: JS idle, white window (the exit abort was a red herring, fixed anyway, bt step 17).
- x86_64: docs/X86_64.md, scripts/stage-x86_64.sh (libs, root, bt, shards); natives compile for x86_64. The x86_64 build of vendor/apklayer modules was still running in ~/los24 (log ~/apklayer-x86-build.log in OrbStack); when done rerun scripts/stage-x86_64.sh, then `m libeffectconfig libcodec2_hidl_plugin_stub` for x86_64. Afterwards check arm64 with a no-op `m libapklayer_egl`.
- Not started: real keystore2/KeyMint, Google apps retest, OpenTTD, Unciv/Lemuroid/Rocks black windows.

## NEXT TASKS (2026-10-28 night: items 1-7 of the previous list were worked through; what is left is below)
0. **Done this morning, not in the list below**: DocumentsUI works (volumes: Internal storage = primary, Home, Linux root per instance; opening a file in another app with its grant; `tests/vm/files.sh`, `tests/vm/view.sh`). Open from it: which volumes ordinary apps reach, storage permission rules, the real MediaProvider (`docs/PLAN.md` "Storage volumes").
1. **Corpus** (`tests/corpus/results.md`: 14 of 20 apps reach a steady GPU picture, up from 11): fix the remaining causes listed in `docs/PLAN.md` (Nextcloud, Telegram, WhatsApp, OTR2 under the WebView route, WireGuard, drminfo); add one app per engine (Unity, UE4, Godot, cocos2d-x, Flutter) when APKs are available.
2. **Settings provider**: the tree's SettingsProvider starts but `Settings.*` still use Robolectric's in-memory fakes (`-Dapklayer.realsettings=true` switches them off; the provider then fails in its settings migration). UserDictionaryProvider inserts return null. Calendar/Download providers untested.
3. **Camera** is not implemented (no camera HAL; apps see no camera). Also not done: attestation/KeyInfo for the Keystore, `GeoClue` accuracy (needs an agent), `tests/vm/texture-view.sh` (black on the UTM VM), `tests/vm/audio-aidl.sh` (left channel check).
4. **Before release**: COMMERCIAL.md terms, CLA, NOTICE files, per-app sandbox, x86_64 Android libraries (needs approval for a second `out/`), Flatpak.
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
- The VM's bionic_translation build (`~/bt-arm/bionic_translation/b`): `scripts/patch-bionic-translation.sh` first; the linker (`libdl_bio`) build needs `~/local/lib/libunwind.so` (symlink to the system one); copy the result over `~/arm64-native/libdl_bio*` (a stale copy there made WebView fail with an unknown IRELATIVE relocation).
- `scripts/install-shim.sh` needs the untracked `bridge/native/android_stubs.c` (copy `~/bt-arm/android_stubs.c`); gcc 15 makes implicit declarations errors.

- Compatibility pass (2026-10-28): results per app in `tests/corpus/results.md` (tools: `tests/vm/appcheck.sh`, `tests/vm/appshot.py`, `tests/corpus/fdroid.py` for hash-verified F-Droid downloads). About 25 apps draw their UI; open: AnkiDroid (crash behind ACRA), Puzzles (anti-piracy redirect), Open Sudoku (database), SDL games with assets (Rocks 'n' Diamonds, TIC-80 black), WireGuard (Go TLS), Unciv black, NewPipe/Sensors content, camera apps. Meet reaches its welcome screen; its accounts and the Google sign-in wait for the microG Device Attestation / real keystore (planned, not started).
- VM: `~/arm64-native/libc_bio.so(.0)` must be the newest `~/bt-arm/bionic_translation/b/libc_bio.so.0` (has the Android `__fread_chk` argument order, the patched libc-chk.c); the older copy made OpenTTD abort with glibc "buffer overflow detected". Backups: `~/libc_bio.so*.before-fread`.
