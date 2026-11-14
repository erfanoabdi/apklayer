# apklayer: open work

Only what is still to do. Done items are removed in the commit that finishes them.
History: `CHANGELOG.md`, `docs/history/` (full old plans). Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: device HALs only for vibrator, camera, media; never sensors or GNSS.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- Droid-ify and NewPipe: an NPE from a fragment/activity view that was not created (`ScreenFragment.getToolbar`).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).
- Fennec on x86: `free(): invalid pointer` or SIGSEGV at 0x8 with `debug.apklayer.log=1`.
- Termux: a failed exec in a forked child may still hang in `exit()` (fix untested in Termux itself).
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- FCL: no new frame after a window resize.
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR dies after 31 s to 3 min under the x86 translator (Tencent ACE). It has run 26+ min natively on the arm64 server.
- Cause on x86 unknown (CHANGELOG "ACE: what the runs of the day say"). Next: the same game on the x86 PC with the saved account; the unread ACE config files and `libanort.so`.
- The server's crash collectors are running (core files in `/var/tmp/pgr-core`, maps snapshots in `~/pgr-experiments`).
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
- Shutter time is the request's time, not the frame's `timestamp_ns` (goldfish session). All streams scaled from the largest. NDK libcamera2ndk is a stub.
- Portrait desktop monitor: a manager/`apklayer config` entry for the pipewire `mount=` option.
- Parallels' MacBook camera: first frame black. Retest Open Camera, Fossify Camera.
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
- text-input-v3 has no show/hide request. Enter labels are words, not icons.
- Untested: `input-desktop` IBus surrounding text (flameshot aborts on Parallels); WebView surrounding text has no assertion.
- OTR2 in a mobile maximized window and FlorisBoard typing.
- Lomiri greeter: only the first uinput tap registers.

## Permissions and Android rules
- Permission prompt: precise/approximate location, "only this time", background location step.
- No screen for `APP_NOTIFICATION_SETTINGS`. Channel groups stay per process.
- Shared libraries: HIDL libraries, `<uses-native-library>`, resources.
- Storage: no scoped-storage rules; `UNMOUNTABLE` not sent.
- Every instance scans every drive. Eject can fail "busy" while MediaProvider maps an image.
- Call audio: oldest `MODE_IN_COMMUNICATION` requester wins.
- Keystore: only pam_unix passwords, never locked.
- libcore: `sun.misc.Cleaner`, `fstat(fd)`, SharedMemory `PROT_EXEC`/`PROT_NONE`.
- Termux under `APKLAYER_SANDBOX=1`: app uid/gid names, `/system/bin/sh` and `getprop` work unsandboxed (tests/corpus/termux-sys.py, 2026-11-15); not run with the sandbox on. `getprop` needs `system/bin/toolbox` + link in the prebuilts' android-root (`tools/stage root` now copies it; the prebuilts repo does not have it yet).
- Direct buffer `array()` writes are lost. Only the default-route interface is a network.

## Startup and memory
- JDK 25: flatpak still names JDK 21; findJava prefers an installed 21 over 25.
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla; NewPipe ~21 s cold, ~12 s warm.
- Cold-start costs: Hyphenator `nInit`, cold `JNI_OnLoad` ~150 ms, hub boot wait of 4-8 s.
- SurfaceFlinger shader-cache priming; composer GL context ~80 MB.
- A sound server started later is used only from the next stack start.
- Owner to decide: should Lomiri suspend an unfocused apklayer window?

## Userspace binder
- Fewer hops per call (futex doorbells, no second wakeup, busy-poll). Measure SurfaceFlinger and audio.
- Halium without binderfs: host context is fixed at `/dev/binder`.
- `binder` group: `ClassNotFoundException: apklayer.host.RealBinder` in a fresh tree.

## Native bridge (x86_64)
- Diagnostics: `tools/ace/` (bpftrace on the translator and glibc, `pgr-netcut.sh`).
- ACE: the open leads are in the PGR section above and the CHANGELOG.
- No translation cache across starts.
- Telegram has no x86_64 FOSS APK to test.
- The guest JVM finds the host's `libjsig`: watch for a later SIGSEGV handler.
- Unity: `hpatchz`, no backtrace in fault notes, the app log cap misses lines.

## Runtime identity (nothing blocking)
- Modules: `dl_iterate_phdr` lists 279 of 320 (arm64); the staged graphics stack (libEGL, libGLESv*, libvulkan) stalls the app at start when loaded eagerly. libc exports `bionic_*` names: `libc.so` is the mapping itself, so a stand-in with Android's names cannot be the same file.
- x86: `HWCAP2` is 0, exe/AT_EXECFN `/system/bin/arm64/app_process64` (the translator names its guest program; needs a change in its tree), `dladdr` names, `/proc/net/unix` (EACCES for the translated guest only; readable on arm64 and for the host user; cause not found).
- Process attributes: main thread id; property count 231 of 635 (Waydroid's full list is not in `tests/identity/results`).
- Termux: `TERMUX_APP__DATA_DIR` is a host path.
- Launch leftovers: btload child `environ`, `#!/bin/sh` through the generic exec path.
- Java: `String.class.getClassLoader()`, `Object.class.getResource`, ArrayStoreException text, StackOverflow depth, main thread id 2.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with the INSTALL.md checklist.
- Run the groups sandboxed on the x86 PC (needs `sudo sh tools/host-setup/sandbox.sh` once).
- A release-candidate deb with the sandbox on for a day.
- Spare-born processes start in the spare's cwd: a `chdir` to `APKLAYER_CWD` hung `hello.input` and the system-apps OtherProcess; find out why (`run.cpp` still sets the variable).
- Sandboxed, these groups fail: `sysprov` (ANDROID_ID changes across `stop`), `hub-services` (`hub-unit.sh`), system-apps `location` (also unsandboxed).
- Shared spare, cold component hosts.
- Isolated processes run as the app uid. Design: own uid (AID_ISOLATED 99000+) in the userns map, a view kind in `sandbox-exec`, caller uid to package in the hub.

## Packaging and releases
- Click: Share/"Open with"; "Choose file…" from the manager; update check offers debs only.
- Updates outside the manager leave running instances on old programs.
- Flatpak: launcher portal dialog; `flatpak run` keeps a pipe on stdout.
- amd64 release leg, release guard's first tag run, PackageKit/pkexec install: untested.
- First command after install blocks ~75 s for runtime setup.
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.
- Sailfish: libhybris in every Android process, a Chum/OBS repository, no text input in the manager.
- Parallels has an old `apklayerd` 0.3.4 that takes over hub starts. UTM's `~/prebuilts/common` is empty.
- Server RDP: after a shell restart or a client of another size, re-run the monitor config (`tools/server/rdp-session/README.md`); nothing automates it.

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
- Script store clients in the corpus (Aurora, Telegram: HTTPS needs scripted onboarding input).
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
