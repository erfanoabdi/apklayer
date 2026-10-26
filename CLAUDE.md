# apklayer: rules for every session (read first, also after a context reset)

Goal and plan: `goal.md`, `docs/PLAN.md`. Status and local reference projects: `memory.md`.

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
- No long scripts, no needless sleeps, no `timeout` wrappers that background a call. Do not chase single failing corpus apps.
- VM: `ssh parallels@10.211.55.4` (pw 1234). After a VM restart re-register Rosetta binfmt (see memory.md).
