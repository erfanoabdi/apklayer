PGR VMREAD tools (all read-only on the game; tester needs APKLAYER_PTRACER=1 for /proc/<pid>/{mem,pagemap,task/*/syscall}).
 snap.py / htab.py / sampler.py   VM memory (0x44000 anon rw-p region = VM address space, pc = offset), handler table (pc>>2 -> libanogs handler), ctx register sampler
 probe.py + run-aws-v.sh           native AWS perturbation probes (PROBE=scan|maps|idx:a:b|asr:a:b|asl:ranges|absent|present ...) at t=14 s; bisect.sh, ddmin.py drive them
 respmap.py                        per-region residency (smaps Rss + pagemap present counts, no page content) at given seconds
 tsamp.py                          1 Hz per-thread comm/state/utime/syscall/wchan
 Finding: reading ABSENT (never touched) anon pages of the native game at t=14 s (>= ~50 pages) => storm at ~23 s (free(): invalid size); reading PRESENT pages or only maps: no storm.
