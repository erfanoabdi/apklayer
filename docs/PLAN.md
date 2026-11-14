# apklayer: open work

Only what is still to do. Done items are removed in the commit that finishes them.
History: `CHANGELOG.md`, `docs/history/`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: device HALs only for vibrator, camera, media; never sensors or GNSS.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- volumes and volumes-sandbox fail in a fresh tree (UTM, Parallels) at "volume listed" and "mediastore volume": MediaStore never learns the volume (not the sandbox, not the providers). The 1-in-5 catchup race is not reproduced.
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- FCL: no new frame after a window resize.
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.
- Fennec on x86: `free(): invalid pointer` or SIGSEGV at 0x8 when `debug.apklayer.log=1`.
- Termux: a failed exec in a forked child may still hang in `exit()` (fix untested).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).
- Droid-ify: NPE in `ScreenFragment.getToolbar` (fragment view not created; same family as NewPipe's recreate NPE).

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR dies after 31 s to 3 min under the x86 translator. Cause unknown (CHANGELOG "ACE: what the runs of the day say").
- Next: more runs per setup, the unread config files, `libanort.so`.
- Home scene on NVIDIA: block noise on 3D models. Recheck with the lite-translate setting.

## Graphics
- Present fences signal at hand-over, not at the real present.
- Swap interval 0: low frame rate; a waiting attach drops a release fence.
- Host ES1 path untested (needs NVIDIA or Mesa with `-Dgles1=enabled`).
- Vulkan presentation untested on AMD.
- Play feature list lacks sensors (no host provider yet).
- GPU per instance: Vulkan device filter, copy fallback, VA plugin device, LINEAR on Intel.
- OTR2 on the PC runs ~30 fps at 2448x1374.
- Windows: smaller second output, output hotplug, no Back button under server-side decorations, KDE.
- Lipstick: the pointer on the navigation-bar strip goes to the app.
- Lomiri: no keyboard inset while the app is turned. Rerun camera-reload on the X23.
- Sailfish keeps the letterbox; its rotation is unchecked.

## Camera
- Sensor timestamps: the shutter time is the request's CLOCK_MONOTONIC time, not the frame's `timestamp_ns` (goldfish session change). All streams scaled from the largest. NDK libcamera2ndk is a stub.
- Portrait desktop monitor: `mount=[NAME:]DEGREES` exists on the pipewire backend (`providers set camera pipewire mount=90`); a manager/`apklayer config` entry and the right degrees per monitor direction are open.
- Parallels' MacBook camera: first frame black. Retest Open Camera, Fossify Camera.
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
- text-input-v3: no surrounding text and no show/hide request (a closed touch-desktop keyboard returns only on the next focus change); desktop touch session unchecked.
- A WebView field sends no surrounding text; Enter labels are English words, not keyboard icons; the Maliit slide-in sends ~10 CONFIGUREs.
- On Lomiri's greeter only the first tap of a uinput device registers.
- OTR2: a portrait/landscape game in a mobile maximized window untested; FlorisBoard typing untested.
- text-input-v3: no show/hide request in the protocol (surrounding text is sent, untested on a compositor with an IME).
- WebView fields send no surrounding text. Enter labels are words, not icons.
- Maliit slide-in sends ~10 CONFIGUREs.
- Recheck IME target and `FLAG_ALT_FOCUSABLE_IM` windows.
- Lomiri greeter: only the first uinput tap registers.
- OTR2 in a mobile maximized window and FlorisBoard typing are untested.

## Permissions and Android rules
- Permission prompt: precise/approximate location, "only this time", background location step.
- Roles: kept across restarts, shown in the manager, qualification check.
- No screen for `APP_NOTIFICATION_SETTINGS`. Channel groups stay per process.
- `getWindowInsets`, `attachWindowContextToDisplayArea`.
- Shared libraries: `sharedLibraryFiles/Infos`, HIDL libraries, `<uses-native-library>`, resources.
- Storage: no scoped-storage rules; BAD_REMOVAL/UNMOUNTABLE not sent. getExternalFilesDirs' secondary dir and MEDIA_CHECKING are in but unverified: the `volumes` group's "volume listed" fails on UTM even without them.
- Every instance scans every drive. Eject can fail "busy" while MediaProvider maps an image.
- Call audio: oldest `MODE_IN_COMMUNICATION` requester wins.
- Keystore: only pam_unix passwords, never locked, a cancelled prompt leaves its dialog.
- libcore: `sun.misc.Cleaner`, `fstat(fd)`, SharedMemory `PROT_EXEC`/`PROT_NONE`.
- Termux: under the sandbox, `getpwuid` names, no `/system/bin/sh` or `getprop`.
- Direct buffer `array()` writes are lost. Only the default-route interface is a network.

## Startup and memory
- JDK 25: the packages still name JDK 21. The first launch is the AOT training run.
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla; NewPipe ~21 s cold, ~12 s warm.
- Cold-start costs: Hyphenator `nInit`, cold `JNI_OnLoad` ~150 ms, hub boot wait of 4-8 s.
- A sandboxed instance cannot write its CDS archive.
- SurfaceFlinger shader-cache priming; composer GL context ~80 MB.
- A sound server started later is used only from the next stack start.
- Owner to decide: should Lomiri suspend an unfocused apklayer window?

## Userspace binder
- Fewer hops per call (futex doorbells, no second wakeup, busy-poll). Measure SurfaceFlinger and audio.
- Halium without binderfs: host context is fixed at `/dev/binder`.

## Native bridge (x86_64)
(The translator is Digitalis, a fork of AOSP's Berberis that keeps its names: libberberis_arm64, BERBERIS_*, berberis.mode.*; docs/X86_64.md.)
- Diagnostics for this (tools/ace/): bpftrace on the translator and glibc through `sudo tools/ace/trace.sh <script.bt> [out]`: ace-self-signal
  (signals to itself), ace-files (raw file syscalls, counted), ace-names (every raw file syscall, in order), ace-proxy (glibc calls of guest
  threads, with guest callers), guest-ids (the ids the guest's getuid ... get); pgr-netcut.sh cuts one app's network after N s (nftables).
- PGR/ACE, state of 2026-11-14 (CHANGELOG "ACE: what the runs of the day say"): with the same files PGR lives 31 s to about 3 min (hs_err or a core without one); the end is ACE's own wild jump (pc = sp = a small number such as 0x1342, after one page is made executable, seven handlers are set and the page made non-executable), delivered as SIGSEGV by the translator exactly as upstream Berberis and Google's libndk_translation do (compared in code, MprotectForGuest and the NoExec entry are the same); in the run without hs_err the translator's own copy of the signal frame onto that sp faulted. What decides ACE to take that path is still unknown: its flag table (dis_crsight, random_trap, ...) comes from config, the config files tss_ano.dat/tp2cfg.dat are partly decoded, libanort.so is unread, and no server reply was read. Tried without effect: the environment views (proc, maps, mounts, /dev/pts, /etc, data and code paths, properties), the sandbox, the Pixel 5 profile, the translator's tier selection. Not shippable and not done: hiding /home (PGR then died at 34 s twice). Next: 5-10 runs per setup to tell whether the graphics proxies of the 0.4.18 bundle matter (34 s and 44 s with them, 87 s and a main menu with 0.4.17's), then the unread config files and libanort.so.
- Tencent ACE (`libanogs.so`) turns on PGR and Endfield under the bridge (2026-11-12, NVIDIA PC, 0006 logging guest callers):
  PGR's real invalid frees all come from `libanogs.so +0x2f4d34` (the same pointers freed twice, headers that are pointers or random words),
  ~7 s later the process sends itself SIGSEGV (SI_TKILL) and the JVM aborts (or glibc's `malloc_consolidate(): invalid chunk size`);
  Endfield's UnityGfxDeviceW sends itself SIGTRAP through a guest syscall ~15 s in, before its first frame (core in coredumpctl). Next: run
  both with binary_translation 0011 (`BERBERIS_LOG_SELF_SIGNAL=1`: the guest pc and frame chain of each signal to itself) and find what ACE reads before it turns (the bridge's libraries in
  /proc/self/maps, auxv/cpuinfo, emulator properties, TracerPid). No per-app workaround: the environment it checks must look like a device's.
  2026-11-12 (bpftrace on the NVIDIA PC): no guest svc sends PGR's fatal signal (it fired for Endfield's SIGTRAP), and one PGR run ended
  instead with a wild read (SEGV_MAPERR at 0x83, guest registers in libanogs +0x3594dc/+0x1117cc): ACE crashes on purpose after it has
  decided, so the kill site does not name the check. 0012 (the guest's maps name no host mapping) did not change it either, and
  0011 logged no guest svc kill. bpftrace on the host side: the fatal SIGSEGV is a re-raise (rt_tgsigqueueinfo through glibc's
  syscall(), then tgkill), so PGR crashes, it is not killed. An earlier BERBERIS_TRACING run (~/apklayer-fix/t/pgr-trace.txt on the PC)
  shows the crash: a thread makes one page executable, installs handlers for 6/11/4/5/7/3/15, clears the page's exec, then jumps to
  0x1069; its SIGSEGV handler (0x...63ba) is misaligned, so berberis takes the default action. Suspects: a stale or wrong translation
  of that runtime-built page; the guest reading back a host handler through sigaction; berberis's "guest call didn't restore sp"
  (16 bytes, 3 per run). Traced run 2026-11-12 17:37 (BERBERIS_TRACING=1, 134 s): the end was a read of 0x2ab000000 (SEGV_MAPERR)
  from libanogs.so +0x2f67a8 on thread 920888, handler in the guest libc (+0xa4ad8); before it that thread re-read /proc/self/maps
  over and over and made 174 raw faccessat calls (paths not traced). Next: the paths and answers (tools/ace/ace-files.bt, root).
  Its file checks (bpftrace on RunGuestSyscall, 17:46 run): the emulator probes (qemu_pipe, qemud, genyd, virtpipe-sec, VMOS/x8
  sandboxes) all answer ENOENT as on a device; it opened /proc/<own pid>/maps (served like /proc/self/maps: same inode) and then
  x86_64 host libraries, /dev/nvidiactl and jdk/bin/java by name, which 0012 keeps out of the guest's maps (checked in a traced run:
  only JDK modules/classes.jsa, locale-archive, libc.mo, gconv cache still named), so those names come from elsewhere (open).
  Per-thread map spellings (/proc/<tid>/maps, /proc/self/task/<tid>/maps, /proc/thread-self/maps) are other inodes: not emulated.
  Runs with 0012+0013: traced (BERBERIS_TRACING=1) still alive at the title screen after 7.5 min (with BERBERIS_SHOW_HOST_MAPS=1
  the traced run crashed at 134 s); untraced runs end at 90-100 s with 0012 too (one outlier at ~5 min), also after 0012 was
  extended (host directories, JVM data, per-thread maps paths): the maps are not ACE's trigger; the long traced run is the
  tracing's slowness (ACE's checks are timed). ACE got the host library names from /proc/self/map_files (lists it, reads all
  6001 links); 0014 filters that view too, and PGR still ends the same way. Ruled out so far: map offsets (0009), HWCAP/cpuinfo
  (0010), host names in maps and map_files (0012, 0014), emulator files (all ENOENT). Left: the misaligned guest SIGSEGV handler,
  "guest call didn't restore sp", ACE's runtime-built code page (stale translation), timing.
  Traced run 18:48 (0014 installed): ended in glibc's `malloc_consolidate(): invalid chunk size` (a fourth ending; no guest
  signal handler involved). Every run ends with the same sequence on a fresh ACE thread: /proc/<pid>/task scan (73 faccessat), one
  page made executable, handlers for 6/11/4/5/7/3/15, the page made non-executable, the 20-slot teardown frees (libanogs +0x2f4d34),
  then a crash: ACE's shutdown path. The decision is taken before that thread starts, elsewhere (possibly by its server after a
  report, ~90 s in). The sp warning is a one-call thread (attached, warning, detached), twice per run.
  Network tests (nftables drop of the app scope's cgroup, tools/ace/pgr-netcut.sh, 2026-11-12): cut at 45 s: stuck at the Kuro
  login, closed at ~124 s without teardown; cut at 65 s (after the automatic login): teardown and crash at 121 s; cut from the start:
  loading spinner, Unity never starts, alive without teardown at 5.5 min. ACE's checks start with the game's Unity code, so the
  no-network runs do not show whether the verdict is the server's; the 65 s run shows the shutdown does not need the network after
  65 s (decided locally, or a verdict received before the cut).
  Proxy-libc trace (bpftrace on glibc, tools/ace/ace-proxy.bt): ACE read its own /proc/<pid>/cmdline from JNI_OnLoad and got the JVM's
  command line (the name was set after Application.onCreate); fixed (nameProcessEarly), PGR still ends. Its /data/user/10/<host>/...
  is a fixed probe (user 10: the first secondary user / work profile, where clone apps live), not a uid of ours.
  Proxy trace again (20:13, with nameProcessEarly and 0015): ACE now builds its paths with the package (files/shell_cache/vm/comm.zip,
  Android/data/<pkg>/files/enable.log): the JVM-path probes are gone; PGR still ends (20 frees). New: /proc is mounted without hidepid, so
  the game sees every desktop process: ACE (libanogs +0x338de4/+0x338ed0, libanort +0x12d6f8) stat'ed /proc, lstat'ed foreign pids and read
  the cmdline of Firefox content processes, apklayer's gms component host and others, and libanort read the instance hub's /proc/<pid>/status.
  Android mounts /proc with hidepid=invisible: an app sees only its own uid's processes. Candidate: hide other processes from the game's
  view of /proc (a getdents filter and ENOENT for /proc/<pid> of a process that is not the app's), covering raw syscalls (translator) and
  the proxy libc's calls (path layer), in app processes only (the hub needs the real /proc).
  Built (2026-11-13), not yet run on PGR (the owner: no PGR runs while they sleep):
  - path layer (bridge/native/paths.c `apklayer_proc_pid_hidden`/`apklayer_proc_hidden_at`, paths_preload.c): in an app process (own_data set) a /proc/<pid> outside
    the app's own cgroup scope is ENOENT under any spelling (`//`, `/./`, `/..`, `/proc/self/root/proc/<pid>`, relative to an open /proc fd or a cwd of /proc);
    readdir skips it; `APKLAYER_PROC_VIEW=host` turns it off. Checked with a probe and tests/native/path-layer.c (own tests, a plain Linux user session).
  - binary_translation 0016 (getdents64 on an open /proc), 0017 (/proc/self/status Uid:/Gid:), 0018 (/proc/net, /sys/class/net EACCES as untrusted_app;
    /proc/self/attr/current = u:r:untrusted_app:s0:c<..>): built on the Mac (CHANGELOG Build log), NOT installed on the PC. The translator's guest faccessat
    reaches the path layer through glibc's syscall() (checked in its imports); newfstatat and openat through FstatatForGuest/openat (imported by name); the
    guest's openat2/faccessat2 (439, 437) call syscall() directly with the guest path: also through the preload.
  - Limits, by design: this is a filter, not a PID namespace: the app sees every process of its own cgroup scope (its JVMs, helpers), the pids are the host's,
    kill(pid, 0) and /proc/sys/kernel/ns_last_pid-style probes still tell that other pids exist (EPERM vs ESRCH); the sandbox applies the same filter (it
    shares the host's PID namespace; an uid-based Android view would need a PID namespace per app and host pids elsewhere).
  - Installed on the NVIDIA PC and hello-arm64 passes (2026-11-13: `proc view ok`, `uid ok`; fails with the old libraries). Still to run: the same under
    APKLAYER_SANDBOX=1 (needs the sudo sandbox.sh once), then PGR with tools/ace/ace-proxy.bt to see whether ACE's /proc probes are gone and whether it still ends.
- binary_translation 0015: /proc/self/status still shows the real (desktop user's) ids to translated code.
- PGR's invalid frees (2026-11-13, 0026 + BERBERIS_LOG_FREES=1): 17 in the whole run, all double frees by ACE's teardown thread of pool slots another thread freed earlier; refused correctly, not an allocator bug. Fixing them means ACE not taking its shutdown path (open above).
- 0006 refuses valid chunks at a retired glibc heap's end (next chunk = the 16-byte fencepost; Endfield's and PGR's libil2cpp frees, headers
  0x25/0x45/0x85/0x175/0xb5 just below a 64 MB boundary): accept the fencepost pair (size 16 | PREV_INUSE, then 0 | PREV_INUSE) so the 20
  reports go to real bad frees. Those chunks are leaked meanwhile.
- PGR's home scene on NVIDIA: the 3D character and some UI tiles (Missions) are block noise (correct texel blocks mixed with blocks of
  random saturated pixels, the same places every run); the title screen is clean. Not the gralloc buffers: every buffer is tiled and the
  CPU-drawn window round-trips exactly (checked with a logging HAL, 2026-11-11), and with the CPU-drawn buffers kept LINEAR (no tiled <->
  linear GPU copy at all) the noise is the same; the title screen's capture through the same readback is clean. Unity's own GL textures
  (compressed? sRGB in Linear color space?) were the suspects. With ASTC hidden (APKLAYER_GL_HIDE) the scene is still black at 80 s and the
  black area keeps sparse single-pixel red/green/blue clusters, so not ASTC: next, a post-processing pass (NaN/Inf in a half-float target,
  sRGB render targets) - capture one frame's GL calls; PGR's ~2 min abort makes long runs impossible. Also ruled out (2026-11-11, one run
  each with APKLAYER_GL_HIDE): multisampled_render_to_texture, float/half-float/packed-float color buffers; Vulkan instead of GLES (PGR
  stays on GLES). Leading idea: the stray guest frees that pass 0006 put array memory into glibc's tcache (16 bytes written into it,
  then handed out again): corrupted 16-byte ASTC blocks are exactly blocks of random pixels. Test with the stricter 0006 (Build log).
  The noise is gone lite-translated: libberberis_arm64 with 0007/0008 is installed on the NVIDIA PC (2026-11-12) and PGR's
  berberis.mode.<package> is lite-translate-or-interpret; the home scene is not checked yet (the runs end at the title screen).
- No translation cache across starts; Telegram (no x86_64 FOSS APK to test); the manager's "translated" label.
- The guest JVM finds the host's libjsig through RTLD_DEFAULT: watch for a third library's later SIGSEGV handler.
- Unity: `hpatchz`; no backtrace in the fault note; app log cap misses Log lines (Unity's own graphics API lines never reach the app log).
- No translation cache across starts.
- Telegram has no x86_64 FOSS APK to test.
- The guest JVM finds the host's `libjsig`: watch for a later SIGSEGV handler.
- Unity: `hpatchz`, no backtrace in fault notes, the app log cap misses lines.

## Runtime identity (nothing blocking)
- Hidden: `/home` and `/tmp` stay visible (host app dirs below them).
- Modules: `dl_iterate_phdr` lists 270 of 320; libc exports `bionic_*` names.
- x86: `HWCAP2` is 0, exe name `/system/bin/arm64/app_process64`, `dladdr` names, `/proc/net/unix`.
- Process attributes: main thread id, property count 231 of 635 (Waydroid's full list is not in `tests/identity/results`: only its 37 probed keys).
- Termux: `TERMUX_APP__DATA_DIR` is a host path.
- Launch leftovers: btload child `environ`, `#!/bin/sh` through the generic exec path.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with the INSTALL.md checklist.
- Run the groups sandboxed on the x86 PC (needs `sudo sh tools/host-setup/sandbox.sh` once).
- A release-candidate deb with the sandbox on for a day.
- Spare-born processes start in the spare's cwd: a chdir to APKLAYER_CWD (spare_target.c, 1f8a8527) kept hello.input from coming up and hung the system-apps OtherProcess, so it was removed; find out why before putting it back (run.cpp still sets APKLAYER_CWD).
- Shared spare, cold component hosts, broker gaps (utimes, xattrs, renameat2 flags, mkstemp, scandir).
- Isolated processes run as the app uid; drive access is all or nothing.

## Packaging and releases
- Click: Share/"Open with"; "Choose file…" from the manager; update check offers debs only.
- Updates outside the manager leave running instances on old programs.
- Flatpak: launcher portal dialog; `flatpak run` keeps a pipe on stdout.
- amd64 release leg, release guard's first tag run, PackageKit/pkexec install: untested.
- First command after install blocks ~75 s for runtime setup.
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.
- Sailfish: libhybris in every Android process, a Chum/OBS repository, no text input in the manager.
- Parallels has an old `apklayerd` 0.3.4 that takes over hub starts. UTM's `~/prebuilts/common` is empty.

## Manager
- Apps installed before 2026-11-04 need a reinstall for permissions.
- No permission step in `ui.sh`. Hardware page: no live GNSS/camera test.
- Binder devices of a removed instance stay until reboot.

## Hardware
- Vibrator effects. GNSS fd leak, x86_64 `libapklayer_gnss`.
- USB: attach with no hub, interface filters, one chooser for several apps, sandbox node access.
- Bluetooth: pairing paths, Sailfish agent, several clients per server socket, PendingIntent scans.
- Push: a real WorkManager app, constraints, resume after suspend, microG 0.3.x.

## Tests and tooling
- Finish `tools/stage` in Python and fold `tools/release/*` into Meson.
- Script store clients in the corpus (Aurora, Telegram: HTTPS needs scripted onboarding input to test).
- Flaky: webview-input under load; native-media AVC (~1 in 2 on Parallels); audio-silent on the x86 PC.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.
- Fresh test trees: meson downloads `bionic_translation` (use a local copy).

## Planned work
- **Per-app memory and start time** (low priority): 1. JDK 25 AOT cache (2026-11-14, CHANGELOG), what is left: the first launch on 25 is the training run and ends after ~3 s of the first activity (a headless training at install would keep the user's first launch); no smaps_rollup PSS or Calculator/NewPipe numbers yet; the packages still name JDK 21 (debian/control, rpm spec, flatpak yml); 2. per-JVM limits where starts do not slow;
  Robolectric's instrumentation, the JDK patches, bionic_translation and every package's JDK; open for 2 (heap free ratios and two C1 threads are in, CHANGELOG 2026-11-14): measure on the phones with and without `APKLAYER_JVM_MEM=default`, apps with a live heap above ~500 MB);
  3. only if needed, a shared zygote-like JVM. Today: ~1 GB PSS per app JVM on the Volla, NewPipe ~21 s cold / ~12 s warm.
- **One build instead of tools/***: finish with `tools/stage` rewritten small in Python and the remaining `tools/release/*` folded into Meson.
## Needs hardware or a person
- NVIDIA/AMD: Vulkan presentation, host ES1.
- Halium: vendor HALs, `media = device`, NewPipe network, the new native changes on the phones.
- Sailfish: geoclue fix, Lipstick switcher with two apps, a finger on the host dialog.
- Ubuntu Touch: manager on screen, navigation bar under the panel, drawer tap, shell window, maximize, turning by hand.
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Parked (owner's decision)
- NFC, biometrics, game controllers, ModemManager telephony, screen capture portal.
- Bluetooth advertising/peripheral, A2DP/HFP, LE audio, OPP; USB accessory, tethering.
- Several phone instances as a product; a JRE for the Ubuntu Touch deb.

## Runtime identity (open, rerun of 2026-11-14)

Goal: what an app sees matches Android (Java, native inspection, ART internals). Do not equate maps labels with ART compatibility. Comparison: `docs/runtime-identity-matrix.md` (first run 29 match, 13 partial, 73 mismatch; rerun on main fecb75f5: 57 / 29 / 28; raw key equality ARM64 207 and x86 212 of 464, was 142 and 169). Probes `tests/apps/hello-identity`, tools and results `tests/identity/`, group `identity-matrix`. Open, most real-app impact first:
1. Host paths in native views: the app's own files in maps/fd links (`/home/.../apps/<pkg>/lib/*.so`, base.apk) and the loader (`findLibrary`, nativeLibraryDirectories, APK element) need a `/data/app/<pkg>-x/` path-layer mapping; `/proc/self/exe` opened as a file is still the JDK `java` ELF; `/proc/<pid>/environ` of a process that publishes no guest environment (JVM, forked helper) shows host variables (QT_* now stripped in cli/env.cpp nativeEnv; WAYLAND_DISPLAY, XDG_RUNTIME_DIR and the rest stay: the JVM needs them).
2. `dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs")` still resolves on ARM64 (answer is right); JNIEnv functions name libart.so in `dladdr` on native ARM64 only (bridge/native/libart_jni.c): open on x86 translation (Berberis' own JNI table), and `*env` itself, the JVM's table, names libopenjdkjvm.so.
3. Process attributes: x86 AT_PLATFORM null and HWCAP2 0 (translator, `frameworks/libs/binary_translation/runtime/arm64/init_kernel_args.cc`: needs a stack string and a HWCAP2 matching the emulated features); TMPDIR unset (Android sets it in ActivityThread); main thread is not the pid (the JDK launcher's thread; known limit); cpuinfo is the host's on ARM64; raw-syscall filter does not cover stat owner ids, getdents64, prctl, sysinfo, x86, or kernels without seccomp.
4. Java loaders and threads: `String.class`/framework `getClassLoader()` (Android: BootClassLoader); `Object.class.getResource` gives jrt:; libcore overlay classes (Daemons, VMClassLoader, Transformers, sun.misc.Cleaner) absent; main thread id is not 2; `Thread.getAllStackTraces` lacks some ART daemons.
5. Exception texts: ArrayStoreException, `ArrayList.get` IndexOutOfBounds and other libcore texts; StackOverflowError depth (21-47k, ART 65k); Proxy class name, MethodHandle class.
6. Hidden-API: the framework's hiddenapi-flags database as the policy; `setHiddenApiExemptions` stays visible because HiddenApiBypass (Termux) cannot walk ART structures here.
7. TLS/HTTPS: NewPipe receives HTTPS responses (2026-11-14); recheck Droid-ify, F-Droid, Aurora, Telegram with `HostProviders` (SunEC serves XDH); NewPipe 0.x shows a blank Trending page because its extractor cannot parse today's YouTube JSON (app side, `ParsingException: Could not parse JSON`).
8. Properties: native `dalvik.vm.*` beyond the pixel5-extra.prop defaults; `Debug.getRuntimeStats` returns [].
9. Environment/launch: the environment replacement (2026-11-13) is a checkpoint, not yet release-ready (CHANGELOG); Termux's TERMUX_APP__DATA_DIR is a host path (ApplicationInfo.dataDir is Robolectric's temp dir); `/proc/<pid>/environ` of a btload-launched program is empty for its child; guest `system`/`popen`/`posix_spawn` leftovers (`#!/bin/sh` through the generic exec path, relative PATH after `addchdir`, `POSIX_SPAWN_CLOEXEC_DEFAULT` race, vfork-child allocations leak, a toybox child's /proc/self/status EBADF in hello-environment); direct JNI calls into host System, Runtime.exec/ProcessBuilder through JNI.
10. Fennec under x86 translation: starts, loads the page, but no beacon: the parent main thread blocks in a sync IPC send (xid) whose reply never comes (CHANGELOG Fennec parts 9-11); `free(): invalid pointer` / SIGSEGV at 0x8 with `debug.apklayer.log=1` is separate and open.
11. ART structures (largest, last; only hook frameworks, HiddenApiBypass in Termux/Aurora and anti-cheat need them; the corpus has no direct ART probing). In: libart.so ELF with .dynsym/.symtab and stubs at its maps path on native ARM64 and x86 translation (the guest file is an aarch64 build, tools/lib/build-libart-arm64.sh), and `Executable.artMethod` over a synthetic table (matrix row 8; CHANGELOG 2026-11-14). Open: JNI-table `dladdr` naming on x86 (Berberis' guest table, needs a Berberis-side hook); jmethodID translation in the JNI table; Class/Field/Thread ART fields over a synthetic 32-byte ArtMethod/16-byte ArtField table (jmethodID == artMethod): `Class.accessFlags/dexCache/ifTable/objectSize`, `Thread.nativePeer` (the JDK's `eetop` is the peer; no Class constructor to hook, add by JdkMembers + fill at class load), `Field.accessFlags` (JDK's Reflection filter hides every field of `Field`/`Method`: patch `Reflection.fieldFilterMap`); identityHashCode in 28 bits (HotSpot's hash is 31 bits and `Object.hashCode`/`System.identityHashCode` are intrinsics: only wrapping at the callers would mask it); non-moving large arrays (HotSpot SerialGC moves objects, humongous pinning exists only in G1: `-XX:+UseG1GC` region pinning, not tried; JNI critical access copies today). Impossible on HotSpot: String `count` and `shadow$_klass_` (String is `value`/`coder`, the object header is not a field; faking them by reflection would not move any Unsafe offset).
12. Termux (bootstrap, shell, forked children and typed text work on UTM and x86 translation, CHANGELOG 2026-11-14): a failed exec in a forked child hangs in exit() joining a libgallium thread that does not exist after fork (the app uses exit instead of _exit; not fixed); the hardware punctuation KeyEvents of HostInput are not checked separately (text route only).
