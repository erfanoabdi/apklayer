# apklayer: rules for every session (read first, also after a context reset)

Goal and plan: `goal.md`, `docs/PLAN.md`. Status and local reference projects: `STATUS.md`. **A new session starts from `STATUS.md`** (state and the next task).
This file is the single home of the binding rules; other docs point here instead of repeating them.

## Builds in `~/los24` (OrbStack VM `ubuntu22-x86`)
Current authorization (2026-10-27, user): build anything needed in `~/los24` autonomously. This replaces the earlier rule (2026-10-26) to only edit and ask the user to trigger builds.
- Only incremental builds of `gsi_arm64 userdebug`: `cd ~/los24 && source build/envsetup.sh && breakfast gsi_arm64 userdebug && m <target>`.
- Also allowed (user, 2026-11-04): incremental builds of `gsi_x86_64 userdebug` in the same tree (`breakfast gsi_x86_64 userdebug && m <target>`; its outputs are `out/target/product/generic_x86_64`, beside the arm64 ones). One build at a time (`flock -n ~/los24-build.lock`); the arm64 product stays the default.
- **NEVER wipe or clean `~/los24/out`** (it took very long to build): no `rm -rf out`, `m clean`/`make clean`/`installclean`/`clobber`, no other product than these two (`lunch`/`breakfast` of anything else), no `OUT_DIR` games, no `git clean`/`repo sync -d` that could remove it.
- Log each build in `CHANGELOG.md` ("Build log") first; how-to in `docs/AOSP-BUILDS.md`. Edit `aosp/` here and run `tools/sync-aosp.sh "message"` (mirrors into `~/los24/vendor/apklayer`). Install outputs on the VM (`~/android-arm64/lib64`).
- The owner's x86 PC (`ssh erfan@192.168.41.58`, passwordless sudo): building and installing packages there is allowed (user, 2026-11-04); it is the owner's desktop: work under `~/apklayer-x86/`, close what you start.
- If the authorization is withdrawn: edit and sync only, ask the user to build the named modules, take outputs from `out/` read-only.

## Running and testing (user rules, repeated complaints)
1. **No waiting.** No `sleep`, poll loops, `timeout` wrappers, `tail -f` waits. A command silent for ~3 s is killed (TaskStop/pkill), then read what it left and decide.
2. **No background launches** via `run_in_background` to dodge waiting. Start, check and close a test app in ONE foreground command ending with that instance's `apklayer [--instance ID] stop` (it ends only that instance's processes and shell). Never kill by process name (`pkill -x apklayer-shell`, `pkill -f '[a]pklayer.host.Main'`): other instances run in parallel; use `tests/vm/kill-instance.sh <id> <pattern>`.
3. **Close everything you start**: test apps and shell on the VM, Monitors and background tasks (TaskStop). Before ending a turn confirm nothing of yours still runs (Mac `ps`, VM `ps`).
4. Never `pkill -f` a pattern contained in your own command line (use `[x]pattern`).
5. **Never ask the user to test by hand or leave an app open for them.** Verify with automated checks on the VM (scripted input, snapshots, logs, `-Dapklayer.perf`). What only a person can judge goes into docs/PLAN.md as "needs a human check" and is not asked for.
6. Run only the test groups for the code you changed: `tests/run <group>...` (groups in `tests/groups.toml`: `gpu` quick smoke, `gpu-window`, `otr2`, `vulkan`, the hub groups `providers services activities pendingintents notifications broadcasts binder hub-services webview`, media `codec2 native-*`, audio `audio-*`, system `files view sysprov system-apps keystore devices portal properties`). Each group runs in its own apklayer instance, several at a time. No long scripts.
7. VM: host and password live in `tests/vm/local.conf` (untracked; `local.conf.example`), read by `tests/vm/config.sh` / `config.py`; the repo copy on the VM is `~/apklayer`. Use `ssh utm@192.168.64.7` with that password (the VM user has passwordless sudo, `/etc/sudoers.d/90-apklayer-tests`; the tests use `sudo -n`).

## Cleaning up after yourself (owner, 2026-11-14: trees piled up until the VMs and the PC were full)
Whoever creates something on a test host removes it before finishing, in the same task:
- **Trees and data** you made under `~/apklayer-x86/<name>`, `~/apklayer-<name>`, `~/<name>-click`, `/tmp`, a phone's home (`~/apklayer-<name>`, `/userdata/apklayer/<name>-agent`): delete them at the end, also when the task failed or was stopped. Name them after the task, so the owner can tell what they are. Never leave one "for reruns".
- **Processes**: stop your instances (`apklayer --instance <id> stop`) and anything you started; check `ps` on the host before you report.
- **Copies**: APK, runtime and bundle copies you pushed to a host, lock files (`~/apklayer-vm-*.lock`), build containers/volumes and scratch files on the Mac.
- **Never delete** what you did not create: the owner's FCL data (`~/apklayer-fc` on Parallels, `~/apklayer-x86/proc` on the PC) and the `apks` folders stay until the owner says; shared trees (`~/apklayer-st`, `~/apklayer-x86/{lead,main}`) belong to the lead.
- Say in your report what you deleted and what you left, with its size and why.
- Disk is a shared limit: check `df -h /` before you copy a tree; at 90% free space first (your own leftovers), then stop and ask.

## Before writing or downloading anything
1. Read `STATUS.md` "Reference projects on this machine". Robolectric, Mac.apk, android_translation_layer (ATL) and the AOSP tree are all **local**; never clone or download what is there.
2. Look for real code to copy or reuse first (those projects, Mesa, AOSP). Write new code only if nothing fits, and say why in the commit message.
3. Smallest change that works: few edits, small files. Do not redo finished work.

## Architecture and maintenance
- One responsibility per file. Shell: `bridge/shell/*.c`; host: `runtime/host/java/apklayer/host/*`; the device-library stubs `bridge/ndk/*.c` (meson, `bridge/ndk/meson.build`); build: Meson end to end (`meson setup b -Dprebuilts=... && ninja -C b && meson install -C b`); staging from the LineageOS tree: `tools/stage`. Do not grow a file past ~600 lines; split.
- Every deviation from upstream is a thin patch marked "apklayer patch" and listed in `docs/PATCHES.md`; vendored code goes in `docs/THIRD_PARTY.md`.
- Keep `STATUS.md` short (current state only); dated entries go to `CHANGELOG.md`, open work only to `docs/PLAN.md` (done items leave it; the full old plan is `docs/history/PLAN-2026-11-10.md`); commit at every step.
- Use they/them for people whose pronouns are unknown.
