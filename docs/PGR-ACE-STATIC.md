# PGR: static analysis of ACE's libanogs.so (agent RE, 2026-11-16)

Material: libanogs.so of `com.kurogame.gplay.punishing.grayraven.en` (5 721 816 bytes, one mapping, load bias 0, text 0..0x528000), memory dumps of the
running game on the tester PC (instance pgrshared, translated, default mode; only the login screen was run, no taps) taken with
`tools/ace/re-dump.py` / `tools/ace/re-scan.py` (root reads of /proc/<pid>/mem, event driven by inotify; nothing is touched). Disassembly with capstone (aarch64).
All offsets are libanogs offsets (guest address = host address in the translator).

## 1. What was established

1. **libanogs.so is NOT unpacked in place.** The mapped text (r--p in the host maps, 0..0x529000) is byte-identical to the file, in three dumps taken at 20-45 s
   (before and during the storm). The offsets of the earlier agents (+0x1f5238, +0x1f929c, +0x2cea94) are ordinary static code of the file.
2. **The "unpacked ACE" code is a second image, loaded by ACE itself into anonymous memory**: one 5.7 MB rw-p anonymous mapping (host maps; guest rwx) a few MB BELOW
   libanogs (run 3: 0x7b4ee6487000-0x7b4ee6a01000, libanogs at 0x7b4ee8e4b000), plus smaller code-dense anonymous regions (1.1-2.3 MB each) near it. It is real
   arm64, control-flow-flattened (OLLVM style: `cmp wN, wM; b.ge ...` dispatcher chains, state constants built with mov/movk, `csel` + jump tables `ldr x8,[x22,w8,uxtw #3]; br x8`).
   It starts with `.eh_frame`-like data, so it is a library image (ELF-like, relocated by a private loader). Its page contents are NOT in the file on disk.
   Dumps (not committed, 7-12 MB): `re-scan.py` mode "storm" writes `region-<start>-<end>.bin` for every anonymous region of 1-16 MB within 64 MB of libanogs.
3. **+0x1f5200 is a generic call gate, not a free-loop**: `sub sp,0x80; stp x8,x0,[sp,0x70]; add x29,sp,0x70; ...; mov x0,#0x6520; eor x30,x0,x30; stp x29,x30,[sp,0x20]; mov x0,x2; blr x1; ...`.
   It calls `x1(x2)` (x0 := x2) and stores the caller's LR XOR 0x6520. Consequences:
   - the guest `lr` at every berberis "free() of no glibc chunk" is +0x1f5238 (return into the gate), because `x1` = libc `free` and `x2` = the pointer: the gate calls `free(x2)` directly;
   - the berberis `guest frames` are meaningless here: the gate's x29 points at [saved x8, saved x0], so the walker prints the saved `x0` (+0x1f929c = the value of the data slot `[0x535000+0x4e8]`,
     a function pointer to an AES-like block routine, loaded in the caller as `ldr x0,[x0,#0x4e8]`). `0x1f929c` is therefore NOT a return address and not a caller; ditto `+0x2cea94`
     (it IS a real return address, see 6, but of another gate user). The true caller of the gate is stored as `lr ^ 0x6520` at gate_sp+0x28 (nothing found on the dumped stacks: the stack scan for
     `(fp, ret^0x6520)` pairs matched only data pointers; the guest CPU context was not in the dumped regions).
   - the only direct call of the gate in libanogs is at +0x1ef4ac (`bl 0x1f5200`, x1 = host function from `0x1eadf0`, x2 = context); every other user reaches the gate through a register/pointer, i.e. from the
     flattened second image (no relocation or table in libanogs points at it).
4. **libanogs contains an arm64 interpreter** (the `0x1ef3xx..0x1f1xxx` area: `ubfx w10,w0,#5,#5 / #0x10,#5 / #0x16,#2` = Rn, Rm, shift decode; register file at ctx+0..0xf8, pc ctx+0x108, next-pc ctx+0xf0,
   code/import tree at `[ctx+0x190]`). `bl` handler at +0x1ef440: `imm26<<2`; the target is looked up in a binary search tree (`0x1eadb8/0x1eadf0`, key at node+0x18, value = host function at node+0x20); a hit calls
   the host function through the gate with `x2 = ctx`. So ACE can run code in its own emulator with HLE'd imports. Whether the free storm comes from this interpreter or from the native second image is NOT
   proven; with `x2 = ctx` the interpreter's HLE call would free the context, so the storm's `x2 = slab pointer` points to direct users (the second image) of the gate.
5. **libanogs uses ~260 libc imports through PLT stubs 0x5273c0..0x527bf0** (16 bytes each); names (from the dynamic relocations): free 0x527440, malloc 0x527450, calloc 0x527460, realloc 0x527a40,
   fopen 0x5274f0, fclose 0x527540, fread 0x527510, fgets 0x5277a0, fseek 0x5277b0, lseek 0x5279d0, read 0x527790, open 0x527780, __open_2 0x527660, __openat_2 0x5279c0, mmap 0x527880, munmap 0x527ae0,
   syscall 0x527760, popen 0x5276e0, readlink 0x5279f0, pthread_create 0x527860, pthread_mutex_lock 0x527b10, sscanf 0x5273f0, prctl 0x527720, ptrace 0x527840, sigaction 0x5278f0, getpid 0x527420,
   gettid 0x5275c0, clock_gettime 0x527750, nanosleep 0x5274b0, abort 0x527ad0, __system_property_get 0x527a30.
6. **`+0x2cea94` is the return address of `blr x8` at 0x2cea90**: a variadic syscall veneer (args w20,x21..x26; x8 = result of `bl 0x33874c` = the libc `syscall` pointer); the munmap/tgkill of the
   punishment go through it, i.e. libc `syscall()` (not a raw svc) in this variant.

## 2. The 4096-byte slots are a small-object pool (slab chain), found in libanogs

Same shape in nine instances (stride 0xe8: mallocs at 0x2f651c, 0x2f6604 ... 0x2f6c5c, free-loops at 0x2f71b8 ... 0x2f7f98) and ~150 more `malloc(0x1000)` call sites (0x50a2dc...0x520020):

```
2f6214  size class router:   if (size > 0x80) tail-call malloc; else lock(pool+0x288) ... class pool alloc (2f64b4)
2f64b4  pool_alloc(pool):    if (pool[8]==0 (free list)) { slab = malloc(0x1000);        // 2f6518
                                                      slab[0xff8] = pool[0]; pool[0] = slab;   // chain link at the END of the slab
                                                      pool[0x28]+=n; pool[0x38]+=n; pool[0x10]++;   // totals, per-slab cell count n = pool[0x20]
                                                      thread the cells (stride pool[0x18]) on the free list pool[8] }
2f70ec  pool_trim(pool):     for (s = pool[0]; s; s = next) { next = s[0xff8];
                               if every cell of s is free (cell+8 == 0x400 && cell+0xc == 0 for n cells) {
                                  unlink s (prev[0xff8] = next, or pool[0] = next);  free(s);       // 2f71b8
                                  pool[0x28]-=n; pool[0x38]-=n; pool[0x10]--; } }
        called from 2f6d44. pool_trim has NO lock inside; the caller takes pool+0x288 only on the allocation path (2f6244).
2f0a2xx "append record" family (0x50a2d8...): 0x1000-byte slabs of 0x20-byte records, slab[0]=previous slab, slab[8]=used, guard `used-0xfd0 < 0` (never overflows 0xff0).
```
Meaning for the storm: the 14 `free(slab)` calls at 0x1010 stride, with `lr = gate`, have the exact shape of `pool_trim` freeing consecutive empty slabs of one pool (consecutive chunks of one
arena: slabs are allocated back-to-back). Whether the freed pointers are "stale" because a first trim already freed them (double trim from two threads: no mutex in pool_trim; the
berberis diag shows "already in glibc's tcache (freed twice)" for some of them and bad headers for others) or because the pool object itself is stale/corrupt, is undecided (see 4).

## 3. What decides to trim and to crash (not reached)

The chain "trim, 5xx ms of probing, rt_sigaction x5, munmap(__errno block), tgkill" is in the second image (the gate callers), whose flow is flattened; a static reconstruction of the
condition needs the full 5.7 MB image (dumps exist, regenerate with `re-scan.py`) and a deflattener (state variable `w8` compared in a dispatcher; targets in the jump tables at 0x53b000/0x53c000-style
tables in libanogs' own flattened functions, but for the second image the tables sit inside the image). Not done within this task. What the libanogs side shows about inputs:
- libanogs reads files through its own FILE-like layer (0x3395f4: calloc(0x68) FILE, own `open` 0x338d84, `fstat` 0x339158, `malloc(st_blksize if 0x400<=st_blksize<=0x8000 else 0x1000)` as the buffer, lseek/fseek/ftell);
  `fopen`/`fgets`/`fread` PLT users are at 0x4a3074..0x4a9970 (parsers, `sscanf`), 0x4de0f0..0x4dfc60, 0x1f9454 (key file), 0x25e028 (popen + fread + sscanf 0x25e0a0).
  For a memfd-served /proc file `st_blksize` is 4096 (real procfs: 1024), so the buffer would be 4096 instead of 1024 bytes: a difference in allocation sizes only (not an overflow by itself).
- The lead's hint (ASCII maps text 8 bytes before each 4096 slot): in the stack dump of the net thread (tid of the storm, run 3) the berberis diagnostic's own maps text (it reads /proc/self/maps to classify the
  chunk) sits on the host stack part of that thread (0x7b4eef1a1010..0x11a0, lines of the real maps), so maps text exists in the arena heap around the diagnostics too; and a freed chunk reused by any
  text reader gets ASCII at ptr-8 (size field) after its previous owner freed it. "Bad header = text" is therefore consistent with a stale (already freed and reused) pointer; a heap overflow by a 4096-byte reader
  is not needed and the libanogs pool code has no overflow of the 0xff8 link or of the used-guard. Not excluded: an overflow in the second image.

## 4. Candidate inputs, ranked

1. **A second thread (or a second invocation) runs the same pool_trim/destructor over a slab chain** (timing/ordering of two ACE threads at the end of the data-collection cycle; pool_trim has no lock). Native
   (scudo) would abort or ignore too, so on native the two never overlap; in the translated process the slow end of the net thread (connect return, 6 s clock) lines up with the other thread. Plausibility: high
   (the storm is `free(slab)` x14 per thread, twice from two threads, "bad header or already in tcache"). Test: serialize ACE's threads (taskset 1 CPU moved the death to ~48 s, not removed: ordering stays).
2. **What gates the report/trim path**: the net stage itself (agent M: native never connects :10012; agent G: death = connect(:10012) + 6 s regardless of the answer). The decision to enter the stage happens before it
   and is not a syscall result; it is in the second image. Candidates left by earlier agents and not refuted by this analysis: the contents of `ano_tmp/*` (vot.dat, ano_dfh.zip, comm.dat, ano.ano3.dat, ano_ksh.dat,
   mn_cache.dat; their absence at start reproduces the cycle: runs 2-4 of this task after run 1 survived (3 of 3, 70 s) with ano_tmp populated by run 1 and a changed overrides.prop of another agent (interpret-only,
   which survives by itself), so this is NOT clean evidence), memory content ACE scans (the 5.7 MB image checks its own libs through /proc/self/maps; the translated maps are 1.5 MB vs 6.5 MB reads natively).
3. **Allocator behaviour**: glibc `free` of a chunk whose header ACE (or a reader) overwrote reports "no glibc chunk"; scudo (native) would not abort for a trimmed-twice slab only if the header check passes
   (16-byte header with checksum): scudo aborts on double free, so native does not double free; same as 1.

## 5. Cheap experiments (none run beyond the dumps; none needs the lobby)

- E1: make the double free visible and attributable. Run with `BERBERIS_LOG_FREES=1` (already in the diag) plus the dumper in storm mode (tools/ace/re-scan.py with the run log as WATCH) and add the guest `x29/x30/sp` of the
  freeing thread to the berberis free diagnostic (or read the CPU context by searching the thread's TLS pointer `tls=` in a heap dump): then `[x29-0x48] ^ 0x6520` is the real caller in the second image; its
  function (flattened) is the trim/destructor, and its callers up to the thread entry give the decision.
- E2: serialize the two trimming threads without changing anything else: run the game with `taskset -c 0` and, separately, with one translator thread at a time is not possible; instead use
  `berberis.mode=lite-translate-or-interpret` plus `perf`-free logging of tids at each refused free (the diag already prints `tid`): if the two tids always are {net thread, thread X} and the first free of X precedes the net
  thread's by < 1 ms, the race hypothesis is confirmed; then name X (`ace-comms.bt`).
- E3: dump the pool object itself: the pool structs are in libanogs' .data/.bss (rw segment 0x532000-0x575000 dumped by re-scan.py as `libanogs-*.data`): at the first refused free, read `pool[0]` (chain head),
  `pool[0x10]` (slab count), `pool[0x28]/[0x38]` for the nine pools (find them from the xrefs of `adrp x?,#0x581000+0xabc` area) and compare with the heap: if the chain head is a freed slab, the trim ran twice; if the chain is
  intact but the slabs' own `[+0xff8]` links hold text, the slabs were overwritten (overflow by a reader).

## 6. Process notes

- tester PC, pgrshared, under `flock ~/pgr-run.lock`, the game started 8 times (login screen only, `-Dapklayer.runms=60000..70000`, no input); runs 1 and 5 died with the usual storm, 2-4 survived the 70 s
  (see 4.2 for why they are not evidence). root-owned dump dirs created by the dumper (`sudo python3`) were removed, `~/pgr-re-work` is gone. No ACE file was moved or deleted (an attempt to move them aside
  as pgr-timeline.sh does was not run). Another agent changed `pgrshared/overrides.prop` (`berberis.mode.<pkg>=interpret-only`) while this task ran.
