# apklayer: rules for every session (read first, also after a context reset)

Goal and plan: `goal.md`, `docs/PLAN.md`. Status and local reference projects: `memory.md`.

## NEVER (user rules, repeated complaints; re-read at the start of every turn)
1. No `sleep`, poll loops, `timeout` wrappers or `tail -f` waits. A command silent for ~3 s is killed, then inspect.
2. No background launches via `run_in_background` to dodge waiting. Start, check and close an app in ONE foreground command.
3. Always close what you start: test apps and shell on the VM (`pkill -x apklayer-shell; pkill -f '[a]pklayer.host.Main'`), Monitors and background tasks (TaskStop). Before ending a turn confirm nothing of yours still runs (Mac `ps`, VM `ps`).
4. Reuse local sources first (memory.md "Reference projects"); smallest edit; no redoing finished work.
5. Never `pkill -f` a pattern contained in your own command line.
6. **NEVER wipe or clean the user's `~/los24/out` in OrbStack** (it took very long to build): no `rm -rf out`, no `m clean`/`make clean`/`installclean`/`clobber`, no switching to another product (`lunch`/`breakfast` other than `gsi_arm64 userdebug`) in that tree, no `OUT_DIR` games that delete it, no `git clean`/`repo sync -d` in ~/los24 that could remove it. Build only incrementally, and log each build in docs/AOSP-BUILDS.md first.

6. **Never ask the user to test by hand or leave an app open for them** (user, 2026-10-26: "i don't like to do manual testings for you"). Verify with automated checks on the VM (scripted input, snapshots, logs, `-Dapklayer.perf`); what only a person can judge goes into docs/PLAN.md as "needs a human check" and is not asked for.

## Before writing or downloading anything
1. Read `memory.md` "Reference projects on this machine". Robolectric, Mac.apk, android_translation_layer
   (ATL) and the AOSP tree are all **local**; never clone or download what is there.
2. Look for real code to copy or reuse first (those projects, Mesa, AOSP). Write new code only if
   nothing fits, and say why in the commit message.
3. Smallest change that works: few edits, small files. Do not redo finished work.

## Architecture and maintenance
- One responsibility per file. Shell: `bridge/shell/*.c`; host: `runtime/host/java/apklayer/host/*`;
  NDK shims: `bridge/ndk/<lib>.c`, built by `scripts/build-native.sh`. Do not grow a file past ~600 lines; split.
- Every deviation from upstream is a thin patch marked "apklayer patch" and listed in `docs/PATCHES.md`;
  vendored code goes in `docs/THIRD_PARTY.md`.
- Update `docs/PLAN.md` status and `memory.md` with each step; commit at every step
  (trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`).

## Working style (the user's standing requests)
- Verify everything yourself on the Parallels VM with real input; the user does not test by hand.
- Run only the test groups for the code you changed (`tests/p2-host.sh`, `tests/vm/e2e.sh`, `tests/vm/native.sh`).
- **NEVER use `sleep` or poll loops (`until ...; do sleep`) in commands, and no `timeout` wrappers.** The user has complained repeatedly. Run the command and read its output, or use run_in_background and wait for the notification. Keep scripts short.
- **3-second rule (user, 2026-10-26):** a command that has not produced output after ~3 seconds is killed (TaskStop/pkill), then read what it left and decide. No waiting on blocked commands, no `tail -f | grep` waits.
- Monitors and background jobs: stop each one (TaskStop) as soon as its answer is in, and close test apps on the VM when done. Never leave `tail -f` monitors running.
- **No background launches.** `run_in_background` is not a way around the no-waiting rule. A test app is started, checked and closed (on the VM too: stopping a Mac job does not stop the remote app) inside one foreground command that ends with `pkill -x apklayer-shell; pkill -f '[a]pklayer.host.Main'`. Before ending a turn, confirm nothing of mine still runs (Mac `ps`, VM `ps`).
- No long scripts. Do not chase single failing corpus apps.
- VM: `ssh parallels@10.211.55.4` (pw 1234). After a VM restart re-register Rosetta binfmt (see memory.md).
