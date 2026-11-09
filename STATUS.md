# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan (done, next, open): `docs/PLAN.md`. History: `CHANGELOG.md`.

## State (2026-11-04)
- About 60 real apps draw and run, current F-Droid versions included (`tests/corpus/results.md`); Google apps through microG (sign-in, Meet accounts). Full suite on UTM (2026-11-04, after the startup/media/installer/cleanup rounds): 43 of 44 groups pass; vulkan-present cannot pass on either VM; it passes on the owner's x86_64 PC (real GPU), where the x86_64 route runs.
- Released: v0.1.0, v0.2.0, v0.2.1 (2026-11-04), v0.3.0 (2026-11-05; one Meson build, runtime 0.2.1). Since 0.1.0: Bluetooth, USB, camera, fake sensors/GNSS/vibrator (`providers.toml`), `apklayer logs|report`, the host binder context (Halium), apps' real target SDK. Meet works in a call: join, sound, remote video (owner checked, 2026-11-04).
- Halium (Volla X23, Ubuntu Touch, 2026-11-05): with `graphics = device` apps and GL games draw on the device's Mali GPU (docs/HALIUM.md); camera = device is next (PLAN (e)).
- Window style (2026-11-05): mobile (maximized, navigation bar, phone density) or desktop, `apklayer settings set window-mode`; immersive mode done, phone window check pending (docs/PLAN.md "Mobile window style").
- Per-app sandbox (2026-11-08, milestone two, still off by default: `APKLAYER_SANDBOX=1`): Android's /data view and groups, kernel caller uids, D-Bus helpers through apklayerd, a spare per app, the deb's AppArmor profile/udev rule/unit; what remains before it is the default: docs/PLAN.md "Plan: per-app sandbox".
- Background push (2026-11-05, microG 2026-11-08): installing microG Services turns the instance always on (`apklayer instance autostart <id> on|off`); with every app closed microG checks in, keeps its connection to Google (heartbeats, reconnects) and gets real FCM tokens, and a cloud message wakes the closed app (groups `push`, `push-microg`). A real message on Parallels is prepared, it needs a deb with this round (PLAN "Plan: background push" 7).
- Halium (Volla X23, Ubuntu Touch, 2026-11-05): with `graphics = device` apps and GL games draw on the device's Mali GPU; `camera = device` works (camera2 and Camera1, Open Camera live on both cameras); vibrator, GNSS (lomiri), sensors (sensorfw), audio (PulseAudio backend) and H.264 video all run on the phone; media = device (the device's hardware Codec2: H.264 decode into a SurfaceView, encode; 2026-11-09) works and is the default on Halium with graphics = device (docs/HALIUM.md "Hardware providers").
- Ubuntu Touch from the deb (2026-11-05): `apklayer setup` and the manager work without root (boot unit for binder, no pkexec); manager at Lomiri's scale; apps from the drawer draw on the GPU. Open: prebuilts branch and runtime 0.3.1 release (PLAN "Ubuntu Touch install").
- Binder (2026-11-09, for 0.4.9): apklayer's userspace binder (bridge/binderd, docs/BINDER.md) is the default, no root step; the kernel's is an option (`apklayer binder kernel`, the manager's Hardware page). Test groups run on userspace; `binder-kernel`, `host-binder-kernel` keep the kernel's.
- Next phase, in order: `docs/PLAN.md` "Next phase".

## How to work here
- A new machine: `docs/INSTALL.md` (prebuilts branch, build, first app).
- Build: `meson setup b --prefix <dir> -Dprebuilts=<prebuilts> && ninja -C b && meson install -C b --skip-subprojects [--only-changed]` builds and stages everything (bionic_translation as a patched subproject, pinned jars, native runtime, host classes, framework jars instrumented once; `-Dgfxstream=` for ES 1); the tree's `bin/apklayer` uses that install. Staging from the tree: `tools/stage`.
- Upstream edits are patch files only (`patches/`, applied by `patches/apply`; index in `docs/PATCHES.md`). Android-side modules: edit `aosp/`, `tools/sync-aosp.sh`,
  incremental builds in `~/los24` (`docs/AOSP-BUILDS.md`), then install the output on both VMs (take a module's library from `out/soong/.intermediates/vendor/apklayer/...`: `m <module>` does not refresh `system/lib64`) and run `tools/gen/tree-files-lock.py`.
- Tests: one `tests/run [-j N] [group...]` per VM tree at a time (a lock refuses a second; put groups in one run with -j) (groups in `tests/groups.toml`; each its own instance; it pushes the tree, then `ninja` and `meson install` into `<repo copy>/inst`; `-Dprebuilts` from `APKLAYER_PREBUILTS` in `APKLY_VM_ENV`).
  Second VM: `APKLY_VM_CONF=parallels`; named instances: `APKLY_SLOTS="a b"` (space-separated). Corpus: `CORPUS_INSTANCE=<id> python3 tests/corpus/run.py <app>`.
- Screenshots: `apklayer [--instance ID] screenshot <package> <out.png>` (the app's own SurfaceFlinger picture). Input into one instance's window:
  `instance.inject` (tests/lib/instance.py); kernel keys (`instance.keys`) go to the shared desktop's focus and are only for input-desktop.
- CLI (`cli/`): `--version | reconvert <pkg>|--all-stale | launcher show|hide | install [--system] | run | list | uninstall | stop [<package>] | hub | wait hub | screenshot | info | clear-data | permissions [grant|revoke] | instances |
  instance create|remove|autostart <id> [on|off] | prop [set|unset] | status | ime enable|disable | settings [list]|set KEY VALUE|unset KEY | gpus | gpu set|unset | daemon | home-access <pkg> on|off | logs [<pkg>] | report <pkg> | providers [set <domain> <backend> [k=v...]|unset <domain>]`. State and logs: `~/.local/state/apklayer/<instance>/`.
- Hardware plugins (out of tree, `apklayer-dev`): `docs/PLUGINS.md`; example in `examples/`.
- Manager UI: `manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b`.

## Test VMs
- **UTM** (default): host and password in `tests/vm/local.conf` (untracked; `local.conf.example`). Ubuntu 26.04 arm64, GNOME Wayland, Venus Vulkan 1.4, virgl
  ES 3.0, 10 CPUs, 30 GB. Repo copy `~/apklayer`, runtime `~/arm64-native`, libraries `~/android-arm64`, bionic_translation `~/bt-arm`. After a reboot:
  `sudo tools/host-setup/binder.sh [instance...]` (only for the kernel-binder groups). `/tmp` is tmpfs: no big files there.
- **Parallels**: `ssh parallels@10.211.55.4` (key auth, passwordless sudo), Ubuntu 24.04 arm64, glibc 2.39 (rebuild glibc parts there; Android libraries copy as
  they are), 12 CPUs. Runtime `~/apklayer-rt` (env `~/.config/apklayer/env`), repo copy `~/apklayer`. Its default instance holds the owner's apps.
- Neither VM can present Vulkan; a real PC is needed for that and for x86_64.

## References on this machine
Mac.apk, Robolectric, android_translation_layer (all in `~/GitHub`), Husk (GPL, read-only reference, never copied). LineageOS tree `~/los24` in OrbStack
`ubuntu22-x86` (`orb -m ubuntu22-x86 bash -c ...`).

## Facts that bite
- `ro.*` properties are write-once: every stack start gets a fresh store.
- Process lookup: Android processes carry their process name in `/proc/<pid>/cmdline`; find an instance's processes by `APKLAYER_INSTANCE` in `/proc/<pid>/environ`
  or the recorded `run.*` pids, never by name. Never `pkill -f` a pattern in your own command line. Do not attach `jcmd` to app JVMs (it ended Fennec).
- After an edited bionic_translation patch: ninja and install (the reconfigure resets the subproject when the edited series no longer matches; a new patch is picked up by ninja itself).
- Files deleted in git stay on the VMs after a push: remove them there by name. macOS tar: `COPYFILE_DISABLE=1`, `tar -c -f - --no-xattrs`.
- dex2jvm needs `android-all` on its own classpath; its output needs `@DoNotInstrument` (`DenyInstrument`). gcc 15 makes implicit declarations errors.
