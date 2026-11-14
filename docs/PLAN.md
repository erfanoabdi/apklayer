# apklayer: open work

Only what is still to do, one short entry each. History and narratives: `CHANGELOG.md`; the full old plans (every detail dropped from here):
`docs/history/PLAN-2026-11-14.md`, `docs/history/PLAN-2026-11-10.md`. ART simulation: `docs/ART-DEFERRED.md` (closed). Binding rules: `CLAUDE.md`.
Drop an item here in the commit that finishes it.

## Ground rules for open work
- Distribution: GitHub releases only (debs, the Sailfish RPM, the Ubuntu Touch click, Flatpak bundles); no Flathub or OpenStore submission.
- No per-app hacks: generic fixes only; the corpus (`tests/corpus/`) measures compatibility with current app versions. CTS strictly last.
- Android's own interfaces (AIDL/HIDL HALs, framework classes) are the only interface for hardware; no apklayer-specific front.
- Halium: device HALs only for vibrator, camera, media; never the device's sensors or GNSS HAL (single-client: go through sensorfw/GeoClue).
- No cached app processes: idle component hosts end; always-on only for push/background, not as a speed trick.
- An Android IME stays optional; no IME switcher. No Recents in the mobile navigation bar.

## Bugs (open)
- Unciv is black on UTM (Mesa 26 virgl/zink; draws on Parallels): GLES 3.2 context, no GL/shader error. Theory: virgl claims 32 fragment samplers, shaders above 16 kill the context (`tests/vm/gles-sampler-limit.c`); a cap to 16 in libapklayer_gles stayed black, unverified that Unciv read it. Next: log the cap's hit, else apitrace.
- volumes-sandbox "catchup present internal file" failed once in five. Suspects: the scan broadcast (`HubMediaWatch.send`) reaches MediaProvider before it is ready, or MediaProvider's first scan overlaps the single-file scan.
- Ubuntu Touch: a drawer launch of an already running app starts and ends a shell for nothing. Fix (needs the phone): in cli/main.cpp skip `startLomiriAppShell` when `windowLaunch` finds a running window JVM; the raise needs the app's own `shell-<pid>.sock` from the run.<pid> record.
- FCL: after a window resize no new frame reached the shell (not diagnosed).
- Phone with the deb: after 4-5 NewPipe launches `HubStartup.connect` blocked in `pingBinder` (swap full); restarting the instance cleared it.
- Fennec on x86: with `debug.apklayer.log=1` (or a profile without the test's onboarding prefs) the parent sometimes aborts: `free(): invalid pointer` or SIGSEGV at 0x8 (Breakpad re-raise).
- Termux: a failed exec in a forked child hung in `exit()` (libgallium atexit thread join); an atfork `on_exit` hook was added, untested.
- NewPipe 0.x: Trending is blank because its extractor cannot parse today's YouTube JSON (app side).

## Graphics
- Present fences signal at the hand-over to the shell, not the real present (needs a kernel fence the composer can signal; then wp_presentation).
- Swap interval 0: lower near-empty frame rate (waits for a free gbm buffer); an attach that waited drops that buffer's release fence (EglAttach.cpp).
- Host-ES1 path (Gles1.cpp) untested: needs NVIDIA or a Mesa with -Dgles1=enabled.
- Vulkan presentation untested on AMD; the probe takes any GPU, not the instance's. `android.hardware.vulkan.level` declared 0; the Play feature list lacks multitouch, sensor.*, opengles.aep, vulkan.*.
- GPU per instance: build and test the Vulkan device filter (DeviceSelect.h); copy fallback when a buffer import fails; the VA plugin's device choice; LINEAR on Intel.
- OTR2 on the PC at 2448x1374 ~30 fps (GPU or its translated thread); Swappy's other inputs untested; the swap bpftrace trace does not load on the PC.
- Windows: an app on a smaller second output sees the largest; output hotplug reaches the app only at its next configure; no Back button under server-side decorations; KDE untested.
- Mobile style on Lipstick: the pointer on the navigation-bar strip goes to the app. Landscape keeps the bar at the bottom (Android: at the side).
- Lomiri: a fixed-orientation app is turned (docs/HALIUM.md); the keyboard gets no inset while turned. Rerun camera-reload on the X23. Sailfish keeps the letterbox; its rotation unchecked.
- Software-GPU hosts (the AWS server, llvmpipe over vkms): works since the `EGL_ANDROID_native_fence_sync` check (EglWindow.cpp); weston's VNC output stalls without a client.

## Camera
- Sensor timestamps; all streams scaled from the largest; NDK libcamera2ndk is a stub (and would miss a lazy cameraserver on first call).
- A portrait desktop monitor gives rotate-and-crop like a turned phone: needs a per-camera "mounted on the display" setting.
- Parallels' MacBook camera first YUV frame black; iPhone camera in Open Camera; Fossify Camera retest.
- Halium: preview rotated in the phone's landscape window; `getCallingUid` in host callbacks; rear-camera still; present fences on the phone.

## Input / IME
- text-input-v3: no surrounding text and no show/hide request (a closed touch-desktop keyboard returns only on the next focus change); desktop touch session unchecked.
- A WebView field sends no surrounding text; Enter labels are English words, not keyboard icons; the Maliit slide-in sends ~10 CONFIGUREs.
- IME target and FLAG_ALT_FOCUSABLE_IM windows: recheck. On Lomiri's greeter only the first tap of a uinput device registers.
- OTR2: a portrait/landscape game in a mobile maximized window untested; FlorisBoard typing untested.

## Permissions and Android rules
- Permission prompt: location precise/approximate and "only this time", background location as its own step.
- Roles: kept across restarts and shown in the manager; a role's qualification check. APP_NOTIFICATION_SETTINGS has no screen; channel groups stay per process.
- Window-system gaps: getWindowInsets, attachWindowContextToDisplayArea.
- Shared libraries: other packages' ApplicationInfo lacks sharedLibraryFiles/Infos; HIDL libraries for old system apps; `<uses-native-library>` linking; installed apps' libraries give classes but not resources.
- Storage: no per-app scoped-storage rules; getExternalFilesDirs has no secondary volume; MEDIA_CHECKING/BAD_REMOVAL/UNMOUNTABLE not sent; every instance scans every drive; changes over the inotify budget seen only at next hub start or mount. Desktop eject can fail "busy" while MediaProvider has an image mapped.
- Call audio: of several MODE_IN_COMMUNICATION requesters the oldest counts (needs PlayerBase reports).
- Keystore: only pam_unix passwords; no LockedHint -> never locked; a cancelled prompt leaves the hub dialog; isDeviceLocked ignores whether a credential is set; UNLOCKED_DEVICE_REQUIRED untested; gatekeeper libraries not in prebuilts/lock/x86_64.
- libcore: `sun.misc.Cleaner`; fstat(fd) stays Robolectric's; SharedMemory PROT_EXEC/PROT_NONE.
- Termux: under the sandbox unchecked; getpwuid/getgrgid names for app ids; /system/bin/sh and getprop missing from android-root; the /data/data mapping is textual.
- Calls: writes through `array()` on an app's direct buffer are lost (needs pinned arrays); only the default-route interface is listed as a network.

## Startup and memory
- JDK 25 + AOT cache (2026-11-14): works, ~8% faster cold start; open: the first launch on 25 is the training run and ends after ~3 s (a headless training at install would keep the user's first launch); no PSS numbers; the packages still name JDK 21 (debian/control, rpm spec, flatpak yml); the 5 "Verification failed" shadow classes at dump time (also on 21: the dump's verifier cannot see framework classes).
- Per-JVM heap limits (2026-11-14, Parallels: NewPipe -21% PSS): measure on the phones with and without `APKLAYER_JVM_MEM=default`; apps with a live heap above ~500 MB. Today ~1 GB PSS per app JVM on the Volla, NewPipe ~21 s cold / ~12 s warm; a shared zygote-like JVM only if needed.
- The spare key churns with launches from terminals with different environments. Hyphenator nInit off-CPU on cold starts; cold JNI_OnLoad ~150 ms; a SIGKILLed waiter leaves its JVM until stop; spare-born processes have cwd = appsDir.
- An app's first hub call waits for the hub boot (4-8 s under load). A sandboxed instance cannot write its CDS archive.
- Native side: SurfaceFlinger's shader-cache priming; the composer's GL context (~80 MB once an app drew); libraries services never use.
- Audio without a sound server: a server started later is used only from the next stack start.
- Owner to decide: whether Lomiri should suspend an unfocused apklayer window.

## Userspace binder
- Fewer hops per call (futex doorbells, reply without a second replier wakeup, a busy-poll window); measure SurfaceFlinger per frame and audio.
- A Halium device without binderfs (host context fixed at /dev/binder): isHalium() needs binderfs today.

## Native bridge (x86_64)
(The translator is Digitalis, a fork of AOSP's Berberis that keeps its names; docs/X86_64.md.) Diagnostics: `tools/ace/` (bpftrace on the translator and glibc: `sudo tools/ace/trace.sh <script.bt>`; `pgr-netcut.sh`).
- PGR / Tencent ACE (CHANGELOG "ACE: what the runs of the day say", 2026-11-12..14): with the same files PGR lives 31 s to ~3 min, then crashes on purpose: ACE's teardown thread (a /proc/<pid>/task scan, one page made executable, handlers for 6/11/4/5/7/3/15, the page made non-executable, 20 double frees at libanogs +0x2f4d34) then a wild jump (pc = sp = a small number) delivered as SIGSEGV by the translator as upstream Berberis and Google's libndk_translation do. What decides ACE to take that path is unknown (its config tss_ano.dat/tp2cfg.dat partly decoded, libanort.so unread, no server reply read). Ruled out: maps/map_files names, offsets, HWCAP/cpuinfo, emulator files, proc/etc/mount views, sandbox, Pixel 5 profile, tier selection, other-process visibility (hidepid filter built and installed), cmdline. The PGR run on the server (headless, software GL) is the current test.
  Next: 5-10 runs per setup to see whether the 0.4.18 graphics proxies matter (34/44 s with them, 87 s and a main menu with 0.4.17's); then the unread config files and libanort.so; the guest `/proc/self/status` ids (0015); per-thread maps spellings (`/proc/<tid>/maps`) are not emulated. Not shippable: hiding /home (PGR then died at 34 s).
- 0006 refuses valid chunks at a retired glibc heap's end (the 16-byte fencepost; Endfield's and PGR's libil2cpp frees): accept the fencepost pair so the reports go to real bad frees.
- PGR's home scene on NVIDIA: block noise on the 3D character and some UI tiles (title screen clean); not ASTC, not gralloc buffers; gone lite-translated (0007/0008 installed 2026-11-12) but the home scene was not rechecked.
- Fennec: gecko-page passes on the PC since 2026-11-14 (0029, 0030); ARM passes; page text on UTM is virgl's.
- No translation cache across starts; Telegram (no x86_64 FOSS APK to test); the manager's "translated" label.
- The guest JVM finds the host's libjsig through RTLD_DEFAULT: watch for a third library's later SIGSEGV handler.
- Unity: `hpatchz`; no backtrace in the fault note; the app log cap misses Log lines.

## Runtime identity (matrix of 2026-11-14: 54 match / 38 partial / 22 mismatch of 117; `docs/runtime-identity-matrix.md`, probes `tests/apps/hello-identity`, results `tests/identity/`)
Remaining, by what an app or anti-cheat can detect (effort in brackets). Nothing here is blocking.
- `/proc/self/map_files` readlinks still show a host path first (proc view) [small]; the x86 matrix file is not re-measured.
- File existence: `/home` and `/tmp` stay visible (host app dirs below them: `createDirectories` stalls on ENOENT; needs op-aware hiding); Android paths still absent: `/system/bin/app_process32`, `/apex/com.android.art/javalib/core-oj.jar`, `/system/framework/framework.jar`, `/data/dalvik-cache`, `/dev/socket/zygote`, `/vendor/lib64` [medium].
- maps: anon names (`scudo:*`, `libc_malloc`, `stack_and_tls:*`) via `prctl(PR_SET_VMA)` on our own allocations [small]; boot.art/oat/vdex, core-oj.jar, dalvik-main space cannot be backed by HotSpot.
- Modules: `dl_iterate_phdr` 270 of 320 (missing: boot oat files, ~10 ART-apex libraries, ~25 /system/lib64 libraries without a staged file); libc exports are `bionic_*` names (symbol resolvers find 1 of 8); `dlopen("libc.so")` handle answers `JNI_GetCreatedJavaVMs`; `getauxval(AT_BASE)` names the apex libdl; x86 guest libc is a host path [medium].
- x86: HWCAP2 stays 0 (Berberis reports RNDR/I8MM absent); exe/AT_EXECFN `/system/bin/arm64/app_process64`; `dladdr` names `/system/lib64/arm64/libc.so`; JNI-table `dladdr` (Berberis' table); `/proc/net/unix` EACCES vs readable [medium].
- Process attributes: main thread id 14 not 2 and not the pid (JDK launcher; known limit); `loginuid`, `wchan`, `Umask` 0002, `Cpus_allowed_list`, cpuinfo implementer on ARM64 (host's), no `ANDROID_SOCKET_zygote`, property count 219 vs 635 (`ro.zygote`, `ro.product.board`, `init.svc.adbd`, `sys.usb.state`, `ro.build.type=user`, `dalvik.vm.heapgrowthlimit`), thread names lack `RenderThread` [small each].
- Environment/launch: Termux's `TERMUX_APP__DATA_DIR` is a host path (ApplicationInfo.dataDir is Robolectric's temp dir); `/proc/<pid>/environ` of a btload-launched program is empty for its child; guest `system`/`popen`/`posix_spawn` leftovers (`#!/bin/sh` through the generic exec path); direct JNI calls into host System, Runtime.exec through JNI.
- Apps converted before converter ABI 9 get only a printed `apklayer reconvert <pkg>` line (no automatic reconvert).

## Sandbox (before it can be the default)
- The NVIDIA tester's box with docs/INSTALL.md Troubleshooting's checklist (cause unconfirmed: nvidia-modprobe under no-new-privileges, glvnd vendor choice).
- The groups sandboxed on the x86 PC (needs the owner's `sudo sh tools/host-setup/sandbox.sh ...` once). A release-candidate deb with the sandbox on for a day of normal use.
- A spare shared between apps; component hosts start cold; broker lacks chmod/utimes/symlink/link/xattrs, renameat2 flags, mkstemp/scandir opens.
- Isolated processes run as the app uid; an on-demand daemon keeps its first client's environment; drive access is all or nothing per app.

## Click, Flatpak, packaging, releases
- Release: `tools/release/versions` needs `runtime=0.4.19` (the prebuilts changed after 0.4.18) when a release is tagged.
- Click: Share/"Open with" (content-hub hook and a second app); "Choose file…" from the click's manager unchecked; the manager's update check offers debs only.
- An update outside the manager (apt, dpkg, click) leaves running instances on the old programs until they all stop or the next login (the manager's Update restarts them; not run on a real device).
- Flatpak: the launcher portal dialog unchecked (Ubuntu 24.04's icon validation fails under AppArmor); the `flatpak run` that starts apklayerd keeps a pipe on stdout until `apklayer stop`.
- Releases: the amd64 leg untested; the first command after install blocks ~75 s for runtime setup (the manager's first run must show it); the release guard's first tag run untested; deb install through PackageKit/pkexec untested; `setup` should say when the release's runtime differs.
- System-app reinstall on runtime change not checked on a real deb upgrade or a Halium phone.
- Sailfish: libhybris mapped into every Android process; a Chum/OBS repository for zypper updates; the manager has no text input (Lipstick has no text-input protocol) and its "Open" from an app's page was not checked.
- The Parallels VM has an old `apklayerd` 0.3.4 deb that takes over hub/spare starts unless `XDG_RUNTIME_DIR` is private; `~/prebuilts/common` on UTM was emptied (refill from the Mac checkout).

## Manager
- Apps installed before 2026-11-04 need a reinstall for permissions; a grant reaches the app at its next start; signature grants approximated.
- ui.sh has no permission step; Hardware page: no live GNSS/camera test, screenshot mode presses only controls in view, each refresh loads every backend.
- Settings page (2026-11-14, `apklayer config`): per-app settings force a cold start (the env differs from the spare's).
- Binder devices of a removed kernel-binder instance stay until reboot.

## Hardware
- Providers: pick the best backend by probe() when a domain is unset; fronts do not check the plugin ABI themselves.
- Vibrator: compose/PWLE effects and haptic constants. GNSS: ParcelFileDescriptor.close leaves the fd open in the app; x86_64 libapklayer_gnss.
- USB: attach while no hub runs; interface-name filters; one chooser for several matching apps; node access under the sandbox.
- Bluetooth: a secure connection to an unbonded device pairs through the system's agent; setPin also confirms a comparison; Sailfish apps cannot register an agent; BlueZ reaches only recently seen devices; several clients on one server socket untried on hardware; PendingIntent scans; an unclosed server socket keeps its record until the app ends.
- Push: a real WorkManager app doing one-time work; a constraint stopping a running job not exercised; resume after host suspend; microG 0.3.x.

## Tests and tooling
- one-build plan leftover: `tools/stage` rewritten small in Python and the remaining `tools/release/*` folded into Meson (the deb's per-user bundle setup is still `tools/release/setup-runtime.sh`).
- Store clients scripted in the corpus: an Aurora download and split install, F-Droid update and uninstall; HTTPS recheck of Droid-ify, F-Droid, Aurora, Telegram after the Conscrypt fixes.
- webview-input failed once under 4-way load; native-media's AVC round trip fails ~1 in 2 on Parallels; audio-silent fails on the x86 PC.
- x86 PC lead data home has no system apps (`bin/apklayer install --system <prebuilts>/x86_64/system-apps/*.apk`); prebuilts must carry `x86_64/system-apps/` and `common/gfxstream`.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.
- Test-tree gotchas: `meson install --only-changed` leaves stale classes in `inst/.../jdk-patch` (delete the old class after removing a patched one); meson clones `subprojects/bionic_translation` from GitLab on a fresh tree (copy a local checkout instead of downloading).

## Needs real hardware or a person
- NVIDIA/AMD: Vulkan presentation, host ES1.
- Halium: host binder contexts against real vendor HALs; `media = device` as Halium default (X23 only checked); NewPipe's network on the phone; today's native changes (path layer, maps, uname/env, libart.so) untested on the phones.
- Sailfish phone: geoclue1 fix with location on; Lipstick's switcher with two apklayer apps; a finger on the full-screen host dialog.
- Ubuntu Touch phone: the manager on the real screen, the navigation bar under Lomiri's panel, a drawer tap, the shell's android_wlegl window, Lomiri maximizing under its top bar; turning the phone by hand.
- Bluetooth: the PIN dialog against a legacy (HC-05-style) device. USB: a real serial adapter with a serial app.
- Push: a real Telegram/WhatsApp message with microG in an always-on instance on Parallels; the unit starting the stack at login.
- A person: window raise of a relaunched app; the text magnifier; portal file chooser and pkexec in the manager; Google sign-in in Meet/microG; playback quality; audio under load; a Material You app's colours; the IME window's look.
- Fullscreen: NewPipe's player fullscreen with a playing video on the x86 PC (GNOME) and the X23 (Lomiri: does set_fullscreen hide its panel); Robolectric lets any new window's default insets show the bars again; no ACTION_CONFIGURATION_CHANGED broadcast on a resize or turn.
- Apps: a real Godot 4 game; JW Library cover thumbnails; a NewPipe download.
- PGR on the AWS server (headless, software GL): past the Notice dialog the anti-cheat verdict and the registration webview (blank white page, being investigated 2026-11-14).

## Parked (owner's decision)
- NFC, biometrics, game controllers, ModemManager telephony, screen capture portal; backlight and thermal are the host's.
- Bluetooth advertising/peripheral, A2DP/HFP, LE audio, OPP; USB accessory mode, tethering, detaching host drivers.
- Several phone instances as a product; a JRE provisioned by setup for the Ubuntu Touch deb (the click bundles its JDK).
