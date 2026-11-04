# apklayer: status

Read first: `CLAUDE.md` (rules), `goal.md`, this file. Plan (done, next, open): `docs/PLAN.md`. History: `CHANGELOG.md`.

## State (2026-11-04)
- About 60 real apps draw and run, current F-Droid versions included (`tests/corpus/results.md`); Google apps through microG (sign-in, Meet accounts). Full suite on UTM (2026-11-04, after the startup/media/installer/cleanup rounds): 43 of 44 groups pass; vulkan-present cannot pass on either VM; it passes on the owner's x86_64 PC (real GPU), where the x86_64 route runs.
- v0.1.0 released 2026-11-04 (tag v0.1.0, prebuilts-v0.1.0). Meet works in a call: join, sound, remote video (owner checked, 2026-11-04).
- Next phase, in order: `docs/PLAN.md` "Next phase".

## How to work here
- A new machine: `docs/INSTALL.md` (prebuilts branch, build, first app).
- Build: `meson setup b && ninja -C b`; `tools/build bt|host|native|overlay|instrument|cfg|gles1 <native>`; `tools/fetch-deps.sh`; staging from the tree: `tools/stage`.
- Upstream edits are patch files only (`patches/`, applied by `patches/apply`; index in `docs/PATCHES.md`). Android-side modules: edit `aosp/`, `tools/sync-aosp.sh`,
  incremental builds in `~/los24` (`docs/AOSP-BUILDS.md`), then install the output on both VMs (take a module's library from `out/soong/.intermediates/vendor/apklayer/...`: `m <module>` does not refresh `system/lib64`) and run `tools/gen/tree-files-lock.py`.
- Tests: one `tests/run [-j N] [group...]` per VM tree at a time (a lock refuses a second; put groups in one run with -j) (groups in `tests/groups.toml`; each its own instance; it pushes the tree and rebuilds the runtime when sources changed).
  Second VM: `APKLY_VM_CONF=parallels`; named instances: `APKLY_SLOTS="a b"` (space-separated). Corpus: `CORPUS_INSTANCE=<id> python3 tests/corpus/run.py <app>`.
- Screenshots: `apklayer [--instance ID] screenshot <package> <out.png>` (the app's own SurfaceFlinger picture). Input into one instance's window:
  `instance.inject` (tests/lib/instance.py); kernel keys (`instance.keys`) go to the shared desktop's focus and are only for input-desktop.
- CLI (`cli/`): `--version | reconvert <pkg>|--all-stale | launcher show|hide | install [--system] | run | list | uninstall | stop [<package>] | hub | wait hub | screenshot | info | clear-data | permissions [grant|revoke] | instances |
  instance create|remove | prop [set|unset] | status | ime enable|disable | settings [list]|set KEY VALUE|unset KEY | gpus | gpu set|unset | daemon | home-access <pkg> on|off | logs [<pkg>] | report <pkg>`. State and logs: `~/.local/state/apklayer/<instance>/`.
- Manager UI: `manager/fetch-slint.sh && meson setup manager/b manager && ninja -C manager/b`.

## Test VMs
- **UTM** (default): host and password in `tests/vm/local.conf` (untracked; `local.conf.example`). Ubuntu 26.04 arm64, GNOME Wayland, Venus Vulkan 1.4, virgl
  ES 3.0, 10 CPUs, 30 GB. Repo copy `~/apklayer`, runtime `~/arm64-native`, libraries `~/android-arm64`, bionic_translation `~/bt-arm`. After a reboot:
  `sudo tools/host-setup/binder.sh [instance...]`. `/tmp` is tmpfs: no big files there.
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
- The native dir's copies of `libdl_bio`/`libc_bio` win over `APKLAYER_BT`: after a bionic_translation patch run `tools/build native <native>` (tests/run does it).
- Files deleted in git stay on the VMs after a push: remove them there by name. macOS tar: `COPYFILE_DISABLE=1`, `tar -c -f - --no-xattrs`.
- dex2jvm needs `android-all` on its own classpath; its output needs `@DoNotInstrument` (`DenyInstrument`). gcc 15 makes implicit declarations errors.
