# PGR (Punishing: Gray Raven) on the translated x86_64 route: status 2026-11-17

Short version: native arm64 works; the translated x86_64 route dies. The ACE anti-cheat (libanogs.so) runs a payload in its own arm64 VM. At about 80-90 s (about 29 s on an instance with ACE history) that payload enters a "net stage" that frees the same list of 64 blocks it already freed at start (a second free of the same 4 KiB blocks). Our free diagnostic refuses the frees ("no glibc chunk on entry", `BERBERIS_LOG_FREES=1`); the game freezes and dies 80-110 s later (SIGSEGV/SIGBUS). Native arm64 never takes that path.

## What is fixed (all in main)
- Thread-start trampoline in the preload (`vma_names.c`): ACE aborted the game 20-30 s after launch with it (removed).
- GL filter mapped by absolute path: `dlsym` returned NULL for GL functions the filter does not define, Unity crashed at 4-8 s on native (mapped by bare name, `cli/env.cpp`).
- Heavy-tier lobby noise: the 3D lobby is noise in two-gear mode; `lite-translate-or-interpret` draws it cleanly (3/3 vs 3/3). Per-app switch in the manager ("Lite translation") and `apklayer app <pkg> translation-mode default|lite|interpret`.

## How to reproduce the crash without any account
Fresh instances (`pgrp1..4` on the tester PC: hardlinked storage, empty PGR app-data, no login) storm 12/12 at 85-87 s even while the game sits at the first-run "Notice" dialog. Harness: `tools/ace/pgr-par.sh` (4 in parallel, early exit on the first storm line, about 2.6 runs/min). `pgrshared` (ACE state files present) storms at 27-31 s.

## What the payload does differently (the live lead)
`tools/ace/gate-diff.py`, patch 0071 (`BERBERIS_TRACE_GATE`), native side `tools/ace/pgr-gate-native.sh` (hardware execute breakpoints on `libanogs.so` +0x1f5200/+0x1f5238; uprobes/ptrace kill the game).
- One thread runs the VM; every host call goes through the gate at +0x1f5200 (callee = a libanogs-internal wrapper, x3 = VM pc).
- The streams are identical for the first 208 tokens and in the 2 s heartbeats. At the 20 s burst, native runs a full check round: reads `/proc/self/maps`, opens every mapped file and reads its first 4096 B (about 280 files), reads about 1600 `map_files` links and 123 fd links, scans `/proc` (about 400 entries, 2024 status reads), opens `/dev/random` about 2000 times (about 92k events). Translated runs about 21 opens in the same window (about 8-9k events) and skips that round. Nothing is gated by a failing syscall: every wrapper return class matches (GATE).
- With `APKLAYER_MAPS_CONSISTENT=4` (branch `pgr-mapfix`: served maps without host x86/JDK/translator lines) the VM takes the native branch at VM pc 0x141f0, then forks again 659 tokens later (VM pc 0x15b44: a loop at 0x15b8c calling libanogs +0x2d2a08 3084 times = a zip central-directory scan of base.apk, 2 x 1542 entries). Native also reads the central directory (33 reads of 4 KB); this loop is not what separates the routes.
- At 80 s translated runs the net stage (3 x select, then 12 x time/srand/rand/rmdir/__errno at VM pc 0x32760) and a third batch of 64 frees at VM pc 0x32824 = the storm. Native runs a small task at VM pc 0x33a54 instead.
- Open question: which VM-internal input selects the "skip the check round" branch. The first guest syscall after `stat /dev/virtpipe-sec` (ENOENT on both routes) is `openat /proc/self/maps` natively and `openat comm.zip` translated.

## Ruled out (each measured; baseline storm about 100%)
Instruction-set features, ID registers, HWCAP, SIGILL/signal frames (D); timing/CPU throttle (P); file and device presence (N, SERVE: every name in the served maps consistent); maps text (MAPFIX level 4: moves one fork, not the outcome); pid visibility (native in a private pid namespace survives 3/3; `APKLAYER_PIDS_VISIBLE=1` no effect); host leaks in status/dev/net/fd (R, REGRID: `APKLAYER_LEAK_VIEW=all` and every group); allocator layout (E4: the 4 KiB blocks are contiguous and untrimmed, the scudo knob only makes the same double free abort); stage-1 network bytes and the server reply (NET: byte-identical on both routes); ACE storage cycle (U2); translator gears (I1: interpret-only storms too; lite and default storm); atomics (AT: 196 checks, all gears); thread/TLS/allocator behaviour (TH: one real difference, `pthread_self()` in key destructors, patch 0062 in `pgr-th`: fixes it, does not stop the storm); `/proc` read semantics (PROC: merged; does not stop the storm); upstream Digitalis fixes (all already in the tree, coworker check); REGRID sweep of every default-off knob (maps view, host maps, MAP_NORESERVE, GL and kernel identity, timers, CNTFRQ, COUNT_MRS, cpufreq, lite, interpret-only, status-view preload, leak groups).

## Branches (local, not merged, all default-off)
`pgr-serve-maps` (APKLAYER_MAPS_CONSISTENT 1-3), `pgr-mapfix` (level 4, APKLAYER_PIDS_VISIBLE), `pgr-th` (patch 0062, renumber: main has 0062/0063 from the coworker), `pgr-e4` (0070 guest malloc knob), `pgr-r-leaks`, `pgr-peel2`, `pgr-regrid`, `pgr-par` (harness), `pgr-net-netdump`.

## Next
1. Use patch 0071 (GATE lib on prebuilts `experiments/pgr-gate/`) to find the VM branch right after the virtpipe stat on both routes and what memory it reads (ctx, FILE struct, sscanf out-parameters); on native the VM registers can only be read with the hardware breakpoints.
2. Ask the translation-layer developer about the VM's reads that depend on the translator's behaviour (time sources, `/proc/self/stat`, task counts, FILE struct fields, `st_blksize` of /proc files: RE saw ACE's FILE layer size its buffer from `st_blksize`).
3. Release 0.5.0 can ship with the known issue ("PGR on x86 dies about 1.5-3 minutes after launch; native arm64 works; Lite translation fixes the lobby noise").
