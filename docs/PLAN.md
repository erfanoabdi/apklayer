# apklayer: open work

Only what is still to do. History: `CHANGELOG.md`; earlier plans with their decisions and narratives: `docs/history/` (the last full plan is
`docs/history/PLAN-2026-11-10.md`). Binding rules: `CLAUDE.md`. Drop an item here in the commit that finishes it.

## Ground rules for open work
- Distribution: GitHub releases only (debs, the Sailfish RPM, the Ubuntu Touch click, Flatpak bundles); no Flathub or OpenStore submission.
- No per-app hacks: generic fixes only; the corpus (`tests/corpus/`) measures compatibility with current app versions. CTS strictly last.
- Android's own interfaces (AIDL/HIDL HALs, framework classes) are the only interface for hardware; no apklayer-specific front.
- Halium: device HALs only for vibrator, camera, media; never the device's sensors or GNSS HAL (single-client: go through sensorfw/GeoClue).
- No cached app processes: idle component hosts end; always-on only for push/background, not as a speed trick.
- An Android IME stays optional; no IME switcher. No Recents in the mobile navigation bar.

## Bugs (open)
- PGR (Punishing: Gray Raven, Unity): its first-run Notice dialog (KuroSDK) was laid out for 1024x768 beside an 814x456 window, its accept button below the window. Current runs on the NVIDIA PC (2026-11-11) give every PGR window the real size (3440x1440, after 8e089188's display density) and no Process.killProcess; the dialog itself shows only on a first run, which needs a fresh install (clearing the tester's data would lose the account and the 43 GB download): check on a fresh instance.
- Ubuntu Touch spread (per-launch shell, 2026-11-10, CHANGELOG): run groups input, multi-app, mobile on Parallels for the shell/CLI change;
  a drawer launch that finds its app already running (relaunch) starts and ends a shell of its own for nothing.
- Native bridge: pthread_sigqueue with a guest pthread record is not mapped.
- Fennec 158 on x86 translation (CHANGELOG part 9): fixed so far: Build.CPU_ABI (part 7) and the Gecko main thread's `pthread_join` on a stale thread record (binary_translation 0029, needs the new libberberis_arm64 9786502a in the prebuilts). Now the page is fetched (GET /page) and its inline script runs in the content process, but its `fetch('/rendered')` never reaches the parent: the parent's main thread sleeps in a guest futex wait (FUTEX_WAIT_BITSET word 6, no later wake) after ~15 s while its IPC I/O thread receives the child's PHttpChannel constructor (3012 bytes); a lost wake-up of the parent main thread's event queue. gecko-page.py on x86 still fails (no beacon); ARM passes. Next: which guest condvar/mutex that is and why its signal does not wake it (kemu atomics, futex wake path of libapklayer-preload).
- FCL: after a window resize no new frame reached the shell (window looked frozen); not diagnosed.
- An app whose first MediaStore use is MediaStore.Images.Media crashes the JVM (SIGSEGV in ConstantPool::klass_at_impl via Robolectric's ShadowMap).
- An installer run once hung after its self update waiting on `apklayer.stack.ready` with no stack (not reproduced).
- Phone with the deb: after 4-5 NewPipe launches `HubStartup.connect` blocked in `pingBinder` (swap full); restarting the instance cleared it.
- volumes-sandbox "catchup present internal file" failed once in five (scan sent, not indexed in 30 s).
- Unciv is black on UTM (Mesa 26, virgl and zink; draws on Parallels): an apitrace capture replayed elsewhere to find the call.

## Graphics
- Present fences signal at the hand-over to the shell, not the real present (needs a kernel fence the composer can signal; then wp_presentation).
- Swap interval 0: lower near-empty frame rate (waits for a free gbm buffer); an attach that waited drops that buffer's release fence (EglAttach.cpp).
- Host-ES1 path (Gles1.cpp) untested: needs NVIDIA or a Mesa with -Dgles1=enabled.
- Vulkan presentation untested on AMD (NVIDIA passes vulkan-present since a3e931cd); the probe takes any GPU, not the instance's.
- android.hardware.vulkan.level declared 0; Play feature list lacks multitouch, sensor.*, opengles.aep, vulkan.* where the host has them.
- GPU per instance: build and test the Vulkan device filter (DeviceSelect.h); copy fallback when a buffer import fails; the VA plugin's device choice; LINEAR on Intel.
- OTR2 on the PC at 2448x1374 ~30 fps (bound by GPU or its translated thread); Swappy's other inputs untested; the swap bpftrace trace does not load on the PC.
- Windows: an app on a smaller second output still sees the largest; output hotplug reaches the app only at its next configure; no Back button under
  server-side decorations; KDE untested; a real-session scale switch check.
- Mobile style on Lipstick: the pointer on the navigation-bar strip goes to the app. Landscape keeps the bar at the bottom (Lomiri rotates now: Android puts it at the side).
- Lomiri: a fixed-orientation app is turned (composed rotated, docs/HALIUM.md "Rotation on Ubuntu Touch"); the keyboard gets no inset while
  turned. Rerun camera-reload on the X23
  (hello.camera's Main is now turned). Sailfish (Lipstick) keeps the letterbox; its rotation unchecked.

## Camera
- Sensor timestamps; all streams scaled from the largest; NDK libcamera2ndk is a stub (and would miss a lazy cameraserver on first call).
- A portrait desktop monitor gives rotate-and-crop like a turned phone: needs a per-camera "mounted on the display" setting or the monitor's transform.
- First YUV frame of Parallels' MacBook camera reached hello.camera black; iPhone camera in Open Camera; Fossify Camera retest.
- Halium: preview rotated in the phone's landscape window; `getCallingUid` in host callbacks; rear-camera still; present fences on the phone.

## Input / IME
- text-input-v3: no surrounding text and no show/hide request (a closed touch-desktop keyboard returns only on the next focus change); desktop touch session unchecked.
- A WebView field sends no surrounding text; Enter labels are English words, not keyboard icons; the Maliit slide-in sends ~10 CONFIGUREs.
- IME target and FLAG_ALT_FOCUSABLE_IM windows: recheck (likely fixed by 5f5dc6ff).
- On Lomiri's greeter only the first tap of a uinput device registers.
- OTR2: a portrait/landscape game in a mobile maximized window untested; FlorisBoard typing untested.

## Permissions and Android rules
- Permission prompt: location precise/approximate and "only this time", background location as its own step.
- Roles: kept across restarts and shown in the manager; a role's qualification check (RoleController's required components).
- APP_NOTIFICATION_SETTINGS has no screen; notification channel groups stay per process.
- Window-system gaps: getWindowInsets, attachWindowContextToDisplayArea.
- Shared libraries: other packages' ApplicationInfo lacks sharedLibraryFiles/Infos; HIDL libraries for old system apps; `<uses-native-library>` linking;
  installed apps' libraries give classes but not resources.
- Storage: no per-app scoped-storage rules (apps reach other apps' Android/data without the sandbox); getExternalFilesDirs has no secondary volume;
  MEDIA_CHECKING/BAD_REMOVAL/UNMOUNTABLE not sent; every instance scans every drive; changes over the inotify budget seen only at next hub start or mount.
- Desktop eject can fail "busy" while MediaProvider has a scanned image mapped (needs a udisks2 unmount hook or MediaProvider dropping its mappings).
- Call audio: of several MODE_IN_COMMUNICATION requesters the oldest counts (no per-app playback tracking; needs PlayerBase reports).
- Keystore: only pam_unix passwords (a PAM conversation helper for others); no LockedHint -> never locked (ScreenSaver fallback); a cancelled prompt
  leaves the hub dialog; isDeviceLocked ignores whether a credential is set; UNLOCKED_DEVICE_REQUIRED untested; gatekeeper libraries not in prebuilts/lock/x86_64.
- libcore: `sun.misc.Cleaner` (repoint to a host class when an app needs it); fstat(fd) stays Robolectric's; SharedMemory PROT_EXEC/PROT_NONE.
- Termux: under the sandbox unchecked; getpwuid/getgrgid names for app ids; /system/bin/sh and getprop missing from android-root; the /data/data mapping is textual.
- Calls: writes through `array()` on an app's direct buffer are lost (needs pinned arrays); only the default-route interface is listed as a network.

## Startup and memory
- The spare key churns with launches from terminals with different environments (allowlist, or the hub deriving the environment per launch).
- Hyphenator nInit off-CPU on cold starts; cold JNI_OnLoad ~150 ms; a SIGKILLed waiter leaves its JVM until stop; spare-born processes have cwd = appsDir.
- An app's first hub call waits for the hub boot (4-8 s under load): fewer classes before READY.
- A sandboxed instance cannot write its CDS archive; "Preload Warning: Verification failed" lines in the dumping app's log.
- Native side: SurfaceFlinger's shader-cache priming; the composer's GL context (~80 MB once an app drew) and SurfaceFlinger's Mali block; libraries services never use.
- Audio without a sound server: a server started later is used only from the next stack start; audio-late-install failed once per host during a CDS write.
- Owner to decide: whether Lomiri should suspend an unfocused apklayer window.

## Userspace binder
- Fewer hops per call (futex doorbells, reply without a second replier wakeup, a busy-poll window); measure SurfaceFlinger per frame and audio.
- A Halium device without binderfs (host context fixed at /dev/binder): isHalium() needs binderfs today.

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
  Done (2026-11-13), built but not run on PGR (the owner: no PGR runs while they sleep):
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

## Sandbox (before it can be the default)
- The NVIDIA tester's box with docs/INSTALL.md Troubleshooting's checklist (cause unconfirmed: nvidia-modprobe under no-new-privileges, glvnd vendor choice).
- The groups sandboxed on the x86 PC (needs the owner's `sudo sh tools/host-setup/sandbox.sh ...` once).
- A release-candidate deb with the sandbox on for a day of normal use.
- A spare shared between apps; component hosts start cold; broker lacks chmod/utimes/symlink/link/xattrs, renameat2 flags, mkstemp/scandir opens.
- Isolated processes run as the app uid; an on-demand daemon keeps its first client's environment; drive access is all or nothing per app.

## Click, Flatpak, packaging, releases
- Click: Share/"Open with" (content-hub hook and a second app); "Choose file…" from the click's manager unchecked; the manager's update check offers debs only.
- An update outside the manager (apt, dpkg, click) leaves running instances on the old programs (shell, stack, hub) until they all stop or the next
  login; the manager's Update restarts them (2026-11-10, not yet built or run: no VM was reachable).
- Flatpak: the launcher portal dialog unchecked (Ubuntu 24.04's icon validation fails under AppArmor; needs Ubuntu 26.04 or Fedora);
  the `flatpak run` that starts apklayerd keeps a pipe on stdout until `apklayer stop`.
- Releases: the amd64 leg untested; the first command after install blocks ~75 s for runtime setup (the manager's first run must show it);
  the release guard's first tag run untested; deb install through PackageKit/pkexec untested; `setup` should say when the release's runtime differs.
- System-app reinstall on runtime change not checked on a real deb upgrade or a Halium phone.
- Sailfish: libhybris mapped into every Android process; a Chum/OBS repository for zypper updates; the manager (2026-11-10) has no text input
  (Lipstick has no text-input protocol) and its "Open" from an app's page was not checked to start the app.

## Manager
- Apps installed before 2026-11-04 need a reinstall for permissions; a grant reaches the app at its next start; signature grants approximated.
- ui.sh has no permission step; Hardware page: no live GNSS/camera test, screenshot mode presses only controls in view, each refresh loads every backend.
- Binder devices of a removed kernel-binder instance stay until reboot.

## Hardware
- Providers: pick the best backend by probe() when a domain is unset; fronts do not check the plugin ABI themselves.
- Vibrator: compose/PWLE effects and haptic constants. GNSS: ParcelFileDescriptor.close leaves the fd open in the app; x86_64 libapklayer_gnss.
- USB: attach while no hub runs; interface-name filters; one chooser for several matching apps; node access under the sandbox.
- Bluetooth: a secure connection to an unbonded device pairs through the system's agent; setPin also confirms a comparison; Sailfish apps cannot
  register an agent; BlueZ reaches only recently seen devices; several clients on one server socket untried on hardware; PendingIntent scans;
  an unclosed server socket keeps its record until the app ends.
- Push: a real WorkManager app doing one-time work; a constraint stopping a running job not exercised; resume after host suspend; microG 0.3.x.

## Tests and tooling
- one-build plan leftover: the deb's per-user bundle setup is still `tools/release/setup-runtime.sh` (ship the instrumented framework jar in the bundle?).
- Store clients scripted in the corpus: an Aurora download and split install, F-Droid update and uninstall.
- webview-input failed once under 4-way load; native-media's AVC round trip fails ~1 in 2 on Parallels; audio-silent fails on the x86 PC.
- x86 PC lead data home has no system apps (`bin/apklayer install --system <prebuilts>/x86_64/system-apps/*.apk`); prebuilts must carry `x86_64/system-apps/` and `common/gfxstream`.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.

## Planned work
- **Per-app memory and start time** (low priority): 1. JDK 25 AOT cache (2026-11-14, CHANGELOG), what is left: the first launch on 25 is the training run and ends after ~3 s of the first activity (a headless training at install would keep the user's first launch); no smaps_rollup PSS or Calculator/NewPipe numbers yet; the packages still name JDK 21 (debian/control, rpm spec, flatpak yml); 2. per-JVM limits where starts do not slow;
  Robolectric's instrumentation, the JDK patches, bionic_translation and every package's JDK; 2. (done 2026-11-14 on Parallels: heap free ratios + two C1 threads, NewPipe app JVM -21% PSS, CHANGELOG; open: measure on the phones with and without `APKLAYER_JVM_MEM=default`, apps with a live heap above ~500 MB);
  3. only if needed, a shared zygote-like JVM. Today: ~1 GB PSS per app JVM on the Volla, NewPipe ~21 s cold / ~12 s warm.
- **One build instead of tools/***: finish with `tools/stage` rewritten small in Python and the remaining `tools/release/*` folded into Meson.

## Needs real hardware or a person
- NVIDIA/AMD: Vulkan presentation, host ES1. Fennec page text on UTM is virgl's (report to virglrenderer).
- Halium: host binder contexts against real vendor HALs; `media = device` as Halium default (owner, X23 only checked); NewPipe's network on the phone.
- Sailfish phone: geoclue1 fix with location on; Lipstick's switcher with two apklayer apps; a finger on the full-screen host dialog.
- Ubuntu Touch phone: the manager on the real screen, the navigation bar under Lomiri's panel, a drawer tap, the shell's android_wlegl window,
  Lomiri maximizing under its top bar, the bar's look; turning the phone by hand (checked only with a fake accelerometer, tools/halium/ut-rotate.sh).
- Bluetooth: the PIN dialog against a legacy (HC-05-style) device. USB: a real serial adapter with a serial app.
- Push: a real Telegram/WhatsApp message with microG in an always-on instance on Parallels; the unit starting the stack at login.
- A person: window raise of a relaunched app; the text magnifier; portal file chooser and pkexec in the manager; Google sign-in in Meet/microG;
  playback quality by ear and eye; audio under load; a Material You app's colours; the IME window's look; the manager's installed paths.
- Fullscreen (2026-11-10, CHANGELOG): NewPipe's player fullscreen with a playing video on the x86 PC (GNOME) and the Ubuntu Touch X23
  (Lomiri: does set_fullscreen hide its panel; a landscape request in a maximized mobile window, with the UT rotation work); Robolectric
  lets any new window's default insets (a dialog, a popup) show the bars again (Android: the focused window controls them); no
  ACTION_CONFIGURATION_CHANGED broadcast on a resize or turn.
- Apps: a real Godot 4 game; JW Library cover thumbnails; a NewPipe download.

## Parked (owner's decision)
- NFC, biometrics, game controllers, ModemManager telephony, screen capture portal; backlight and thermal are the host's.
- Bluetooth advertising/peripheral, A2DP/HFP, LE audio, OPP; USB accessory mode, tethering, detaching host drivers.
- Several phone instances as a product; a JRE provisioned by setup for the Ubuntu Touch deb (the click bundles its JDK).

## Runtime identity (open, rerun of 2026-11-14)

Goal: what an app sees matches Android (Java, native inspection, ART internals). Do not equate maps labels with ART compatibility. Comparison: `docs/runtime-identity-matrix.md` (first run 29 match, 13 partial, 73 mismatch; rerun on main fecb75f5: 57 / 29 / 28; raw key equality ARM64 207 and x86 212 of 464, was 142 and 169). Probes `tests/apps/hello-identity`, tools and results `tests/identity/`, group `identity-matrix`. Open, most real-app impact first:
1. Host paths in native views: the app's own files in maps/fd links (`/home/.../apps/<pkg>/lib/*.so`, base.apk) and the loader (`findLibrary`, nativeLibraryDirectories, APK element) need a `/data/app/<pkg>-x/` path-layer mapping; `/proc/self/exe` opened as a file is still the JDK `java` ELF; `/proc/<pid>/environ` of a process that publishes no guest environment (JVM, forked helper) shows host variables (QT_* now stripped in cli/env.cpp nativeEnv; WAYLAND_DISPLAY, XDG_RUNTIME_DIR and the rest stay: the JVM needs them).
2. `dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs")` still resolves on ARM64 (answer is right); JNIEnv functions resolve to libart.so in `dladdr` on native ARM64 (done 2026-11-14, bridge/native/libart_jni.c; open: x86 translation (Berberis' own JNI table), and `*env` itself, the JVM's table, names libopenjdkjvm.so).
3. Process attributes: x86 AT_PLATFORM null and HWCAP2 0 (translator, `runtime/arm64/init_kernel_args.cc`: needs a stack string and a HWCAP2 matching the emulated features); TMPDIR unset (Android sets it in ActivityThread); main thread is not the pid (the JDK launcher's thread; known limit); cpuinfo is the host's on ARM64; raw-syscall filter does not cover stat owner ids, getdents64, prctl, sysinfo, x86, or kernels without seccomp.
4. Java loaders and threads: `String.class`/framework `getClassLoader()` (Android: BootClassLoader); `Object.class.getResource` gives jrt:; libcore overlay classes (Daemons, VMClassLoader, Transformers, sun.misc.Cleaner) absent; main thread id is not 2; `Thread.getAllStackTraces` lacks some ART daemons.
5. Exception texts: ArrayStoreException, `ArrayList.get` IndexOutOfBounds and other libcore texts; StackOverflowError depth (21-47k, ART 65k); Proxy class name, MethodHandle class.
6. Hidden-API: the framework's hiddenapi-flags database as the policy; `setHiddenApiExemptions` stays visible because HiddenApiBypass (Termux) cannot walk ART structures here.
7. TLS/HTTPS: NewPipe receives HTTPS responses (2026-11-14); recheck Droid-ify, F-Droid, Aurora, Telegram with `HostProviders` (SunEC serves XDH); NewPipe 0.x shows a blank Trending page because its extractor cannot parse today's YouTube JSON (app side, `ParsingException: Could not parse JSON`).
8. Properties: native `dalvik.vm.*` beyond the pixel5-extra.prop defaults; `Debug.getRuntimeStats` returns [].
9. Environment/launch: Termux's TERMUX_APP__DATA_DIR is a host path (ApplicationInfo.dataDir is Robolectric's temp dir); `/proc/<pid>/environ` of a btload-launched program is empty for its child; Termux punctuation keys type nothing; guest `system`/`popen`/`posix_spawn` leftovers (`#!/bin/sh` through the generic exec path, relative PATH after `addchdir`, `POSIX_SPAWN_CLOEXEC_DEFAULT` race, vfork-child allocations leak, a toybox child's /proc/self/status EBADF in hello-environment); direct JNI calls into host System, Runtime.exec/ProcessBuilder through JNI.
10. Fennec under x86 translation: starts, loads the page, but no beacon: the parent main thread blocks in a sync IPC send (xid) whose reply never comes (CHANGELOG Fennec parts 9-11); `free(): invalid pointer` / SIGSEGV at 0x8 with `debug.apklayer.log=1` is separate and open.
11. ART structures (largest, last; only hook frameworks, HiddenApiBypass in Termux/Aurora and anti-cheat need them; the corpus has no direct ART probing): libart.so ELF with .dynsym/.symtab and stubs at its maps path: done on native ARM64 2026-11-14; x86 translation done 2026-11-14 (own PC tree): the guest file is an aarch64 build (tools/lib/build-libart-arm64.sh, the tree's clang; lock row), loaded in the guest at bridge init (native_bridge.c): maps line, file, symbols, code==memory 18/18; open on x86: JNI-table `dladdr` naming (Berberis' guest table, needs a Berberis-side hook); Executable.artMethod and Class/Field/Thread ART fields over a synthetic 32-byte ArtMethod/16-byte ArtField table (jmethodID == artMethod); String `count`; identityHashCode in 28 bits; non-moving large arrays (collector-backed nonmoving allocation or an ART engine; SerialGC today, JNI critical access copies). Done 2026-11-14: `Executable.artMethod` over a synthetic table (matrix row 8). Open: jmethodID translation in the JNI table; `Class.accessFlags/dexCache/ifTable/objectSize`, `Thread.nativePeer` (the JDK's `eetop` is the peer; no Class constructor to hook, add by JdkMembers + fill at class load); `Field.accessFlags` (JDK's Reflection filter hides every field of `Field`/`Method`: patch `Reflection.fieldFilterMap`); String `count` and `shadow$_klass_` are impossible on HotSpot (String is `value`/`coder`, object header is not a field; faking them by reflection would not move any Unsafe offset); identityHashCode: HotSpot's hash is 31 bits and `Object.hashCode`/`System.identityHashCode` are intrinsics (only wrapping at the callers would mask it); non-moving arrays: HotSpot SerialGC moves objects, humongous pinning exists only in G1 (`-XX:+UseG1GC` region pinning, not tried).
12. Termux: bootstraps and the shell starts (2026-11-14, UTM: the path layer hid /dev/pts/<n> from the app's own forked child, fixed); a typed `echo a.b-c/d:e` prints correctly (text route; the hardware punctuation KeyEvents of HostInput are not separately shown); on x86 (PC, 2026-11-14, libberberis_arm64 b30c6c27) the earlier fork SIGSEGV in a libgallium atfork handler no longer occurs: bootstrap, shell, forked children and a typed `echo a.b-c/d:e` work (nothing found to fix; closed); a failed exec in a forked child hangs in exit() joining a libgallium thread that does not exist after fork (use of exit instead of _exit by the app; not fixed).
