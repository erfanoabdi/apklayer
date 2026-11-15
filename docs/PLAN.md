# apklayer: open work

Only what is still to do. Only the lead edits this file (agents report PLAN changes in their reports); done items are deleted when their branch is merged.
History: `CHANGELOG.md`, `docs/history/` (full old plans). Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: device HALs only for vibrator, camera, media; never sensors or GNSS.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- x86 PC: `system-apps` case `downloads` and `input` `ime-insets` fail (also unsandboxed).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- FCL: no new frame after a window resize (Vulkan game window). Not reproduced on Parallels (`tests/run resize-frame` passes); needs the game window on the x86 PC (`RESIZE_PKG` picks the app); suspect `bridge/shell/vsync.c`'s single shared frame callback.
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR (Tencent ACE) died at 105-144 s on half the runs: cause found, ACE's `/proc/self/root` virtual-app probe (fixed in `bridge/native/paths.c`; 31.7 min idle soak on the tester PC). Open: ship the rebuilt `libapklayer-preload.so` in the deb/bundle; run meson `path-layer` and `tests/run files`; play PGR into a gameplay scene on the tester PC (needs a test-input shell or an unlocked desktop).
- Translator differences still open vs real arm64 (`tests/apps/hello-acediff`, `known-diffs.txt`): default NaN sign and FPCR.DN, fmax/fmin/fmls NaN operands, fused FRECPS/FRSQRTS, a guest signal with no guest handler ends in HotSpot's crash handler (SIGABRT). None explains ACE.
- Home scene on NVIDIA: block noise on 3D models. Recheck with the lite-translate setting.

## Graphics
- Present fences signal at hand-over, not the real present: that needs an unsignaled kernel fence, and syncobj export and TRANSFER of an unsubmitted point fail (PC, Intel, 2026-11-15); remaining options are vgem or blocking `presentDisplay` until the frame callback (stalls SurfaceFlinger's HWC thread).
- Host ES1 path untested (needs NVIDIA or Mesa with `-Dgles1=enabled`).
- Vulkan presentation untested on AMD.
- OTR2 on the PC runs ~30 fps at 2448x1374.
- Windows: KDE untested; `window-geometry` and `decoration` groups need uibench and sway.
- Lipstick: the pointer on the navigation-bar strip goes to the app.
- Lomiri: no keyboard inset while the app is turned. Rerun camera-reload on the X23.
- Sailfish keeps the letterbox; its rotation is unchecked.

## Camera
- NDK camera: the first `getCameraIdList` can be empty for ~200 ms after cameraserver's lazy start (check the Java path too).
- Portrait desktop monitor: the right camera mount degrees per monitor direction are unknown (no way to detect it).
- Parallels' MacBook camera: first frame black. Retest Open Camera, Fossify Camera.
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
- text-input-v3 has no show/hide request and no enter-key hint (labels are the keyboard's own).
- OTR2 in a mobile maximized window and FlorisBoard typing.
- Lomiri greeter: only the first uinput tap registers.

## Permissions and Android rules
- Shared libraries: HIDL libraries need HwBinder/HwParcel JNI in app processes, an instance hwservicemanager for unlisted HALs and `android.hidl.*-java` libraries in the Android root; a native library's path in the app's linker namespace (`<uses-native-library>` itself works).
- Storage: scoped storage hides other packages' `Android/data` and `Android/obb` only for the `/storage/emulated/0`, `/sdcard` and `/data/media/0` spellings; `UNMOUNTABLE` not sent (udisks sends no failed-mount event and an unknown file system has no UUID to key a volume by).
- Call audio: the 6 s new-requester grace and reports from native AAudio/OpenSL streams (the policy's active tracks).
- Direct buffer `array()` writes are lost (HotSpot cannot pin an app's `allocateDirect` buffer; `ApklayerBuffers` returns a copy).

## Startup and memory
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla; NewPipe ~21 s cold, ~12 s warm.
- Hub ready with 146 packages is 2.1 s, ~0.5 s above the 5-package floor (`onServiceStart` 0.36 s). A CLI run outside the hub that changes a package's declared permissions in place is not seen until the apps directory changes.
- `tests/lib/instance.wait_file` races with app-log rotation (exists, then read_text).
- Cold start: `Hyphenator.nInit` (~45 ms) and the eager `JNI_OnLoad` libraries (4 ms; the 144 ms is `libandroid_runtime`) need no work. Remaining: `Application.onCreate` with ~20 packages (~2 s). A key generated with user authentication before the keystore enrollment finishes could miss the secure user id (not covered by a test).
- SurfaceFlinger shader-cache priming; composer GL context ~80 MB.
- A sound server started later is used only from the next stack start.
- Owner to decide: should Lomiri suspend an unfocused apklayer window?

## Native bridge (x86_64)
- Diagnostics: `tools/ace/` (bpftrace on the translator and glibc, `pgr-netcut.sh`); translator notes in `docs/X86_64.md`.
- Fennec on x86: a guest `libmegazord` double free is quarantined (not a crash). A crash seen once at libxul+0x3e88716 in another PC tree did not reproduce on main (6 of 6 pass).
- Translator: ops on a stale joined `pthread_t` should answer ESRCH; a guest fault without a guest handler still goes to HotSpot's handler.
- No translation cache across starts.
- Telegram has no x86_64 FOSS APK to test.
- The guest JVM finds the host's `libjsig`: watch for a later SIGSEGV handler.
- Unity: `hpatchz`.

## Runtime identity (nothing blocking)
- Path-layer leftovers: `/data/local/tmp` stays writable for apps (Android: EACCES; mode 0111 broke `rm -rf` of an instance); `/proc/self/cwd/..` from the data dir shows the host parent; stat owner vs `/proc/self/status` Uid is mapped by bionic's libc and `raw_syscall.c`, not in glibc-level calls.
- Modules: `dl_iterate_phdr` lists 285 of 320 (Waydroid's full module list is not in the repo; waydroid.txt is cut at 16 KB). Not loaded: libEGL/libGLESv*/libvulkan (libEGL stalled the app), ~30 libs not staged (libheif, libion, libbufferhub, libRS, HAL/aidl libs, libcgrouprc), `libselinux` is on the eager list but absent (cause unknown); ART libs and boot*.oat cannot exist. libc exports `bionic_*` names.
- x86: `HWCAP2` is 0, exe/AT_EXECFN `/system/bin/arm64/app_process64` (the translator names its guest program; needs a change in its tree), `dladdr` names, `/proc/net/unix` (EACCES for the translated guest only; readable on arm64 and for the host user; cause not found).
- Process attributes: main thread id; the property count is 421 (Waydroid's full list is not in the repo; some values are plausible, not verified).
- Java: `String.class.getClassLoader()`, `Object.class.getResource`, ArrayStoreException text, StackOverflow depth, main thread id 2.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with the INSTALL.md checklist (the broker now runs `nvidia-modprobe` for missing nodes; only the argument mapping is tested).
- UTM VM: restore `~/prebuilts` (arm64 and common, joined) so the shared tree's `inst` symlinks resolve; each tree running the sandbox group needs `sudo sh tools/host-setup/sandbox.sh b/cli/apklayer` once.
- A release-candidate deb with the sandbox on for a day.
- Shared spare, cold component hosts.
- Isolated processes (uid 99000+) hold no permissions and see only the hub; not covered: isolated processes of system-uid apps, `externalService`, `useAppZygote`.

## Packaging and releases
- Click: Share/"Open with"; "Choose file…" from the manager; the click update install (`installClick`) is untested off a phone.
- Flatpak: launcher portal dialog.
- Flatpak: the `org.freedesktop.Sdk.Extension.openjdk25` extension and 26.08 runtime are unverified (manifest now names JDK 25).
- amd64 release leg, release guard's first tag run, PackageKit/pkexec install: untested.
- First command after install blocks ~75 s for runtime setup on a phone (10 s on Parallels with the parallel setup; the phone is not re-measured).
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.
- Sailfish: libhybris in every Android process, a Chum/OBS repository, no text input in the manager.
- Parallels has an old `apklayerd` 0.3.4 that takes over hub starts. UTM's `~/prebuilts/common` is empty.
- Server RDP: after a shell restart or a client of another size, re-run the monitor config (`tools/server/rdp-session/README.md`); nothing automates it.

## Manager
- Apps installed before 2026-11-04 need a reinstall for permissions.
- `manager/tests/ui.sh` permission step is written but never run. Hardware page: no live GNSS/camera test.
- Binder devices of a removed instance stay until reboot.

## Hardware
- Vibrator: `View.performHapticFeedback(int)` is swallowed by Robolectric's `ShadowView` (haptic constants only reach the HAL for the hidden `Vibrator.performHapticFeedback`); ramp/PWLE segments of app effects are skipped in the hub's vibrator_manager.
- iio-sensor-proxy gives only derived values: no accelerometer or compass type. On Parallels a source-tree CLI needs `--no-daemon` plus `APKLAYER_NATIVE`/`APKLAYER_BT`/`APKLAYER_BINDER=userspace` (the old 0.3.4 daemon takes over).
- USB: one chooser for several apps; sandbox node access.
- Bluetooth: pairing paths, Sailfish agent, truly simultaneous connects to one server socket (the mock keeps one client profile per UUID).
- Push: a real WorkManager app, constraints, resume after suspend, microG 0.3.x.

## Tests and tooling
- F-Droid update and uninstall flows are not scripted in the corpus (Aurora and Telegram scripts exist).
- Flaky, not reproduced: webview-input, native-media AVC on Parallels, audio-silent on the x86 PC (watch for a recurrence with its log).
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.

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
