# apklayer: rules for every session (read first, also after a context reset)

Goal and plan: `goal.md`, `docs/PLAN.md`. Status and local reference projects: `memory.md`. **A new session starts from `HANDOFF.md`** (ordered milestones to the end).
This file is the single home of the binding rules; other docs point here instead of repeating them.

## Builds in `~/los24` (OrbStack VM `ubuntu22-x86`)
Current authorization (2026-10-27, user): build anything needed in `~/los24` autonomously. This replaces the earlier rule (2026-10-26) to only edit and ask the user to trigger builds.
- Only incremental builds of `gsi_arm64 userdebug`: `cd ~/los24 && source build/envsetup.sh && breakfast gsi_arm64 userdebug && m <target>`.
- **NEVER wipe or clean `~/los24/out`** (it took very long to build): no `rm -rf out`, `m clean`/`make clean`/`installclean`/`clobber`, no other product (`lunch`/`breakfast` of anything else), no `OUT_DIR` games, no `git clean`/`repo sync -d` that could remove it.
- Log each build in `docs/AOSP-BUILDS.md` first. Edit `aosp/` here and run `scripts/sync-aosp.sh "message"` (mirrors into `~/los24/vendor/apklayer`). Install outputs on the VM (`~/android-arm64/lib64`).
- If the authorization is withdrawn: edit and sync only, ask the user to build the named modules, take outputs from `out/` read-only.

## Running and testing (user rules, repeated complaints)
1. **No waiting.** No `sleep`, poll loops, `timeout` wrappers, `tail -f` waits. A command silent for ~3 s is killed (TaskStop/pkill), then read what it left and decide.
2. **No background launches** via `run_in_background` to dodge waiting. Start, check and close a test app in ONE foreground command ending with `pkill -x apklayer-shell; pkill -f '[a]pklayer.host.Main'` (or `bin/apklayer stop` + `pkill -9 -x tail`).
3. **Close everything you start**: test apps and shell on the VM, Monitors and background tasks (TaskStop). Before ending a turn confirm nothing of yours still runs (Mac `ps`, VM `ps`).
4. Never `pkill -f` a pattern contained in your own command line (use `[x]pattern`).
5. **Never ask the user to test by hand or leave an app open for them.** Verify with automated checks on the VM (scripted input, snapshots, logs, `-Dapklayer.perf`). What only a person can judge goes into docs/PLAN.md as "needs a human check" and is not asked for.
6. Run only the test groups for the code you changed (`tests/p2-host.sh`, `tests/vm/e2e.sh`, `tests/vm/native.sh`, `tests/vm/gpu.sh`). No long scripts; do not chase single failing corpus apps.
7. VM: `ssh utm@192.168.64.7` (pw 1234; the old Parallels VM was parallels@10.211.55.4). After a VM restart re-register Rosetta binfmt (see memory.md).

## Before writing or downloading anything
1. Read `memory.md` "Reference projects on this machine". Robolectric, Mac.apk, android_translation_layer (ATL) and the AOSP tree are all **local**; never clone or download what is there.
2. Look for real code to copy or reuse first (those projects, Mesa, AOSP). Write new code only if nothing fits, and say why in the commit message.
3. Smallest change that works: few edits, small files. Do not redo finished work.

## Architecture and maintenance
- One responsibility per file. Shell: `bridge/shell/*.c`; host: `runtime/host/java/apklayer/host/*`; NDK shims: `bridge/ndk/<lib>.c`, built by `scripts/build-native.sh`. Do not grow a file past ~600 lines; split.
- Every deviation from upstream is a thin patch marked "apklayer patch" and listed in `docs/PATCHES.md`; vendored code goes in `docs/THIRD_PARTY.md`.
- Update `docs/PLAN.md` status and `memory.md` with each step; commit at every step.
- Use they/them for people whose pronouns are unknown.
