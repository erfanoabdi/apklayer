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
- Ubuntu Touch spread (fixed 2026-11-10, CHANGELOG): check on the X23 with the next click that swiping a card away closes only that app and the
  shell stays up (qtmir's close timeout); and that `apklayer run` from a terminal (no lomiri-app-launch) leaves no card: qtmir makes such an
  app "externally managed" and its launcher item stayed after the app ended (seen once with the test shell).
- Toasts queue per process, not across the instance's apps (NotificationManagerService has one queue).
- A restarted launcher gets no saved instance state (HubActivities.restartIfAlone shortcut; FCL after the game quits).
- Native bridge: pthread_sigqueue with a guest pthread record is not mapped.
- The boot broadcast may wake every app with a boot receiver at once (each a JVM; 14 receivers on Parallels): check, it is ordered since 2026-11-08.
- NVIDIA tester's "double free or corruption (out)" (0.4.10): recheck on NVIDIA with the current Berberis libraries.
- FCL: after a window resize no new frame reached the shell (window looked frozen); not diagnosed.
- An app whose first MediaStore use is MediaStore.Images.Media crashes the JVM (SIGSEGV in ConstantPool::klass_at_impl via Robolectric's ShadowMap).
- `--spare` JVM still running after `apklayer stop` (4 in ~120 stops, not reproduced since): candidate fix, a second records pass after the hub.
- An installer run once hung after its self update waiting on `apklayer.stack.ready` with no stack (not reproduced).
- Phone with the deb: after 4-5 NewPipe launches `HubStartup.connect` blocked in `pingBinder` (swap full); restarting the instance cleared it.
- volumes-sandbox "catchup present internal file" failed once in five (scan sent, not indexed in 30 s).
- Unciv is black on UTM (Mesa 26, virgl and zink; draws on Parallels): an apitrace capture replayed elsewhere to find the call.

## Graphics
- Present fences signal at the hand-over to the shell, not the real present (needs a kernel fence the composer can signal; then wp_presentation).
- Swap interval 0: lower near-empty frame rate (waits for a free gbm buffer); an attach that waited drops that buffer's release fence (EglAttach.cpp).
- Host-ES1 path (Gles1.cpp) untested: needs NVIDIA or a Mesa with -Dgles1=enabled.
- Vulkan presentation untested on NVIDIA and AMD; Unity games with Vulkan on the PC unchecked; the probe takes any GPU, not the instance's.
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
- No translation cache across starts; Telegram (no x86_64 FOSS APK to test); the manager's "translated" label.
- The guest JVM finds the host's libjsig through RTLD_DEFAULT: watch for a third library's later SIGSEGV handler.
- Unity: Tencent ACE past the first screens, `hpatchz`; Gray Raven "Failed to load Il2CPP"; no backtrace in the fault note; app log cap misses Log lines.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with docs/INSTALL.md Troubleshooting's checklist (cause unconfirmed: nvidia-modprobe under no-new-privileges, glvnd vendor choice).
- The groups sandboxed on the x86 PC (needs the owner's `sudo sh tools/host-setup/sandbox.sh ...` once).
- A release-candidate deb with the sandbox on for a day of normal use.
- A spare shared between apps; component hosts start cold; broker lacks chmod/utimes/symlink/link/xattrs, renameat2 flags, mkstemp/scandir opens.
- Isolated processes run as the app uid; an on-demand daemon keeps its first client's environment; drive access is all or nothing per app.

## Click, Flatpak, packaging, releases
- Click: Share/"Open with" (content-hub hook and a second app); "Choose file…" from the click's manager unchecked; the manager's update check offers debs only.
- A running always-on hub keeps the old version until its next start (deb and click).
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
- **Per-app memory and start time** (low priority): 1. JDK 25 with the AOT cache (Leyden) for framework and app classes, checked against
  Robolectric's instrumentation, the JDK patches, bionic_translation and every package's JDK; 2. per-JVM limits where starts do not slow;
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
