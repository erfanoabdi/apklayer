# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan: `docs/PLAN.md`. History: `CHANGELOG.md`.

## State (2026-11-04)
- Runs on the device-library route with per-app class loaders and linker namespaces, the real MediaProvider/SettingsProvider/keystore2/AccountManager-through-the-hub,
  the instance hub (services, broadcasts, providers, activities, notifications, pending intents, accounts, caller identity). Instances run side by side (own binder
  devices, /data, /storage, app data, shell). Processes carry their Android process name (Process.setArgV0); services run in their declared process.
- GL: our EGL keeps Android's per-context hook tables; ES1 through gfxstream's GLES_CM translator when the host has none. Windows follow the compositor's
  fractional scale (wp_fractional_scale_v1, wp_viewporter) and its bounds. Media: Fennec (WebM, MP4, YouTube) and NewPipe play video with sound.
- Tests: `tests/run [-j N] [group...]` (tests/groups.toml), every group in its own instance, on UTM (default) or Parallels (`APKLY_VM_CONF=parallels`).
  Known failing on these VM hosts: vulkan-present (no Vulkan presentation on either VM: needs a real PC), input-desktop (needs the shared desktop to itself).
  Corpus: `python3 tests/corpus/run.py <app>`; results in tests/corpus/results.md.
- Manager UI: `manager/` (Slint, Material 3): `manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b`.
- Upstream edits are patch files only: patches/bionic_translation (0001-0030), patches/robolectric (0001-0015), patches/aosp (frameworks/native 0001-0005, av,
  base, libnativehelper, hardware/interfaces), applied by `patches/apply`. Build: meson + `tools/` (`tools/build host|native|overlay|instrument|cfg|gles1|bt`).
- CLI: C++ tool in `cli/`: `install | run | list | uninstall | stop | hub | component-host | wait hub | env | screenshot | info | clear-data | instances |
  instance create|remove | prop [set|unset] | status | ime enable|disable`; state and logs in `~/.local/state/apklayer/<instance>/`.
- Build: `meson setup b && ninja -C b`, `tools/build bt|host|native <native>`, `tools/fetch-deps.sh`. Run on the VM: `XDG_RUNTIME_DIR=/run/user/1000 ~/apklayer/bin/apklayer run <package>`.

## NEXT TASKS
Open work is listed in `docs/PLAN.md` (sections from "After the 2026-11-03 round" on). In order:
1. Compatibility with current app versions (corpus from the F-Droid index, both VMs) — in progress 2026-11-04.
2. audio-aidl's left channel; fennec-media on a fresh profile (onboarding); m.youtube.com consent dialog.
3. Startup: sandbox class define/init (~0.7 s), cold starts wait ~2 s for the hub; classes preloaded in the spare component host.
4. Before release: x86_64 PC run, Vulkan on a real GPU, Flatpak, COMMERCIAL.md terms, CLA, per-app sandbox (kernel-attested uids).
5. Last priority (owner): camera HAL.

## Test VM (UTM)
Host and password: `tests/vm/local.conf` (untracked; `local.conf.example`). Ubuntu 26.04 arm64, GNOME Wayland, Venus Vulkan 1.4, virgl ES 3.0 (apklayer claims 3.2). Passwordless sudo.
Repo copy `~/apklayer`, runtime `~/arm64-native`, libs `~/android-arm64`. After a reboot run `sudo tools/host-setup/binder.sh [instance...]`. Parallel stacks: each named instance gets its own binder devices (`/dev/binderfs/binder-<id>`, mapped by `bridge/native/paths.c`); run with `APKLAYER_INSTANCE=<id>` plus a private `XDG_RUNTIME_DIR`/`XDG_STATE_HOME`/`XDG_DATA_HOME` and an absolute `WAYLAND_DISPLAY=/run/user/1000/wayland-0`. `/tmp` is tmpfs: never put big files there.
Second VM (Parallels, Ubuntu 24.04 arm64, 12 CPUs, key auth, passwordless sudo): `APKLY_VM_CONF=parallels tests/run <group>` (tests/vm/parallels.conf, see local.conf.example); runtime in `~/apklayer-rt` (env file `~/apklayer-rt/env`, also `~/.config/apklayer/env`) beside the old 2026-10-27 dirs, glibc parts rebuilt there. Its flameshot desktop capture aborts/hangs: use `apklayer screenshot`.
Screenshots: `apklayer [--instance ID] screenshot <package> <out.png>` or `tests/vm/vmcapture.py OUT PKG ENV...` (per app window, from SurfaceFlinger); the desktop capture (`tests/vm/vmcapture.py`) is the fallback. Input: `tests/vm/uinput-keys.c`.

## References on this machine
Mac.apk, Robolectric, android_translation_layer (all in `~/GitHub`), Husk (GPL, read-only reference, not to copy). LineageOS tree `~/los24` in OrbStack `ubuntu22-x86`
(use `orb -m ubuntu22-x86 bash -c ...`); build rules `docs/AOSP-BUILDS.md`.

## Facts that bite
- dex2jvm needs `android-all` on its own classpath; its output needs `@DoNotInstrument` (`DenyInstrument`).
- `ro.*` properties are write-once: every stack start gets a fresh store.
- `pkill -f` can kill your own shell: use `pkill -x` or `[x]pattern`. macOS tar: `COPYFILE_DISABLE=1`.
- The VM's bionic_translation build (`~/bt-arm/bionic_translation/b`): `patches/apply bionic_translation ~/bt-arm/bionic_translation` first; the linker (`libdl_bio`) build needs `~/local/lib/libunwind.so` (symlink to the system one); copy the result over `~/arm64-native/libdl_bio*` (a stale copy there made WebView fail with an unknown IRELATIVE relocation).
- gcc 15 makes implicit declarations errors (bridge/native/android_stubs.c is checked in now).

- Compatibility pass (2026-10-28): results per app in `tests/corpus/results.md` (tools: `tools/debug/appcheck.sh`, `apklayer screenshot`, `tests/corpus/fdroid.py` for hash-verified F-Droid downloads). About 25 apps draw their UI; open: AnkiDroid (crash behind ACRA), Puzzles (anti-piracy redirect), Open Sudoku (database), SDL games with assets (Rocks 'n' Diamonds, TIC-80 black), WireGuard (Go TLS), Unciv black, NewPipe/Sensors content, camera apps. Meet reaches its welcome screen; its accounts and the Google sign-in wait for the microG Device Attestation / real keystore (planned, not started).
- VM: `~/arm64-native/libc_bio.so(.0)` must be the newest `~/bt-arm/bionic_translation/b/libc_bio.so.0` (has the Android `__fread_chk` argument order, the patched libc-chk.c); the older copy made OpenTTD abort with glibc "buffer overflow detected". Backups: `~/libc_bio.so*.before-fread`.

- Fennec: page background now renders (user.js layout.frame_rate=60 = software vsync). Open: page text (h1) not yet visible in the screenshot; investigate fonts/second paint.
