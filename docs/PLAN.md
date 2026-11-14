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
- volumes-sandbox "catchup present internal file" fails 1 in 5 runs.
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- FCL: no new frame after a window resize.
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.
- Fennec on x86: `free(): invalid pointer` or SIGSEGV at 0x8 when `debug.apklayer.log=1`.
- Termux: a failed exec in a forked child may still hang in `exit()` (fix untested).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR dies after 31 s to 3 min under the x86 translator. Cause unknown (CHANGELOG "ACE: what the runs of the day say").
- Next: more runs per setup, the unread config files, `libanort.so`.
- Home scene on NVIDIA: block noise on 3D models. Recheck with the lite-translate setting.

## Graphics
- Present fences signal at hand-over, not at the real present.
- Swap interval 0: low frame rate; a waiting attach drops a release fence.
- Host ES1 path untested (needs NVIDIA or Mesa with `-Dgles1=enabled`).
- Vulkan presentation untested on AMD. `vulkan.level` is declared 0.
- Play feature list lacks sensors (no host provider yet), `opengles.aep` and `vulkan.level` 1 (both need a probe of the device).
- GPU per instance: Vulkan device filter, copy fallback, VA plugin device, LINEAR on Intel.
- OTR2 on the PC runs ~30 fps at 2448x1374.
- Windows: smaller second output, output hotplug, no Back button under server-side decorations, KDE.
- Lipstick: the pointer on the navigation-bar strip goes to the app.
- Lomiri: no keyboard inset while the app is turned. Rerun camera-reload on the X23.
- Sailfish keeps the letterbox; its rotation is unchecked.
- weston's VNC output stalls with no client connected.

## Camera
- Sensor timestamps: the shutter time is the request's CLOCK_MONOTONIC time, not the frame's `timestamp_ns` (goldfish session change). All streams scaled from the largest. NDK libcamera2ndk is a stub.
- Portrait desktop monitor: `mount=[NAME:]DEGREES` exists on the pipewire backend (`providers set camera pipewire mount=90`); a manager/`apklayer config` entry and the right degrees per monitor direction are open.
- Parallels' MacBook camera: first frame black. Retest Open Camera, Fossify Camera.
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
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
- Storage: no scoped-storage rules, no secondary external dir, volume states not sent.
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
- No translation cache across starts.
- Telegram has no x86_64 FOSS APK to test.
- The guest JVM finds the host's `libjsig`: watch for a later SIGSEGV handler.
- Unity: `hpatchz`, no backtrace in fault notes, the app log cap misses lines.

## Runtime identity (nothing blocking)
- `/proc/self/map_files` still shows a host path first.
- Hidden: `/home` and `/tmp` stay visible (host app dirs below them).
- Missing paths: `app_process32`, `core-oj.jar`, `framework.jar`, `/data/dalvik-cache`, `/dev/socket/zygote`, `/vendor/lib64`.
- maps: anon names (`scudo`, `libc_malloc`, `stack_and_tls`) via `prctl(PR_SET_VMA)`.
- Modules: `dl_iterate_phdr` lists 270 of 320; libc exports `bionic_*` names.
- x86: `HWCAP2` is 0, exe name `/system/bin/arm64/app_process64`, `dladdr` names, `/proc/net/unix`.
- Process attributes: main thread id, `ANDROID_SOCKET_zygote`, property count 219 of 635.
- Termux: `TERMUX_APP__DATA_DIR` is a host path.
- Launch leftovers: btload child `environ`, `#!/bin/sh` through the generic exec path.
- Apps converted before converter ABI 9: no automatic reconvert.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with the INSTALL.md checklist.
- Run the groups sandboxed on the x86 PC (needs `sudo sh tools/host-setup/sandbox.sh` once).
- A release-candidate deb with the sandbox on for a day.
- Shared spare, cold component hosts, broker gaps (chmod, utimes, symlink, xattrs, renameat2, mkstemp, scandir).
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
- Script store clients in the corpus (Aurora, F-Droid). Recheck HTTPS in Droid-ify, F-Droid, Aurora, Telegram.
- Flaky: webview-input under load; native-media AVC (~1 in 2 on Parallels); audio-silent on the x86 PC.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.
- Fresh test trees: meson downloads `bionic_translation` (use a local copy).

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
