# apklayer: open work

Only what is still to do. Only the lead edits this file (agents report PLAN changes in their reports); done items are deleted when their branch is merged.
History: `CHANGELOG.md`, `docs/history/` (full old plans). Large tasks parked: `docs/NOTPLAN.md`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: sensors come through sensorfw, location through GeoClue (Sailfish) or Lomiri's location service; vibrator, camera and media use the device HALs.
- Replace an installed library by writing a temp file and `mv`/`install`/`rsync` (rename), never `cp`/`scp` onto a library a running JVM has mapped: the truncate discards its relocated pages and the process faults at exit (hs_err, 4 GB cores). `tests/vm/exit-clean.py` (`EXIT_CLEAN_REPLACE=cp|mv`) shows both.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- GApps first sign-in: Play Store (52.4.41) started Play services' add-account redirect twice, 20 ms apart (hub.log `com.android.vending starts ...UnpackingRedirectActivity` twice, owner's default instance 2026-11-15 19:00); the second flow added the account, the first one's WrapperControlledActivity stayed open after it. Same start: Play Store showed its LocaleChangedModeActivity (desktop locale nl-NL), a possible second trigger. The locale trigger is fixed (CHANGELOG 2026-11-15, host settings before READY); the double add-account start is not. The Play services exit-137 loop is fixed (CHANGELOG 2026-11-15, six framework gaps at module init); a new uncaught exception in one of its intent operations would bring it back: its crash handler ends the process.
- GApps sign-in: after local account creation on GMS 26.44 / Android 17, setup reports “There was a problem communicating with Google servers”; screenshot-time logs include a failed DroidGuard response fetch. Cronet Dynamite also failed to load `ImplVersion`; path-based DEX loader support is patched and covered by a regression check, but end-to-end sign-in is still unverified. The token exchange over the Java Cronet fallback failed with `System error` (body read into a direct buffer's array copy): fixed at converter ABI 11 (CHANGELOG 2026-11-15); the next run added the account and then deadlocked in `Services.bind` ("Getting account ready"): fixed (same date); then the account was stored with Google's data but Play Store could not see it (signer lineage): fixed (same date); then Play Store's downloads were refused by its internal-only grpc-binder check (oneway sender uid): fixed for the userspace binder (same date; the kernel binder still reads such a caller as nobody); the install then failed on the session's dex metadata file (.dm): fixed (same date); needs a human check: a sign-in in `gwd` (owner's credentials); then the remaining server error, if any, from its log. Owner reports Waydroid Android 13 works without Integrity (GMS version unknown).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR dies 114-161 s after launch on the repaired tester PC (x86_64 translated; 11 of 11 runs, any preload, translator, runtime or translation mode), after ACE connects to its servers and the invalid-free storm starts; not the `/proc/self/root` probe (none in 5 logged runs). PGR on the AWS arm64 server (native) is stable: 2.5 h or more in the lobby with libapklayer_egl 652ac421; its three hs_err files and the ~13 GB cores in /var/tmp/pgr-core are exit-time faults at `systemctl stop`, not gameplay crashes. Next for the tester PC: reinstall PGR's APK set there (an agent's `apklayer install` erased its lib/ and arm64 split; the full set is on AWS), run with fresh app data (ACE state in files/ano_tmp), look at what ACE gets for /proc/self/fd, /proc/self/map_files, /proc/<pid>, /dev/virtpipe-sec; restore `berberis.mode.<pgr>=lite-translate-or-interpret` (takes effect after a whole-instance `apklayer stop`).
- New libraries not run on the tester PC/NVIDIA or the phones: x86_64 `libapklayer_egl` (sRGB fix) and the linked-GL query fix (`gl-formats` passes on the Intel PC).
- Home scene on NVIDIA: block noise on 3D models. Recheck with `apklayer prop set berberis.mode.com.kurogame.gplay.punishing.grayraven.en lite-translate-or-interpret` (and unset for the reference); the ASTC fallback in `cli/env.cpp` is a separate lever.

## Graphics
- Vulkan presentation untested on AMD.
- KDE, needs a human eye: edge/corner drag resize, Alt+F8, snap by dragging, the look of KDE's title bar around the app, a real second monitor, fractional scale on the real display, `fullscreen-key` (F11) on a private session.
- Lipstick: a mouse over the navigation-bar strip (pointer.c `in_remote_strip`) is the bar's since 2026-11-15; needs a human check (no pointer device on the phone to try it).
- Sailfish keeps the letterbox; its rotation is unchecked.

## Camera
- Fossify Camera on Parallels is unchecked (no APK locally); Open Camera works on the MacBook camera. The `camera` group prints a harmless stray FAIL line from a test-app race (hello.camera recreates its activity).
- Halium camera: preview rotation needs a human check (preview and still agree on both phones; whether the picture is upright was not judged). `camera`/`media = device` work (libhidl 0005, `tools/halium/hidl-list.sh`). NewPipe playback with `media = device` is blocked by YouTube extraction ("Could not parse website") on the phones' network, not by the stack. The X23 `hello.camera` large still is cut by the landscape-to-portrait activity recreation (test-app race).

## Input / IME
- KDE navigation bar (the mobile bar under server-side decorations, `navbar auto|on|off`): pixel-check the light scheme; `haptic_feedback_enabled` is not modelled by the hub's vibrator.
- Wheel and stylus: written from the protocols and Android docs, never run on real hardware (touchpad, tablet); stylus slider and tool wheel are not delivered; AXIS_TILT/ORIENTATION follow a remembered InputReader formula, not a checked source.
- Non-Latin input: RTL cursor, selection and bidi in an EditText (needs a test app and a human look); layout hotkeys on a real compositor (the xkb group reaches the app as its input method subtype, tested with the injected keymap); `KeyEvent.getUnicodeChar()` for non-Latin keys in apps that read key events.
- text-input-v3 has no show/hide request and no enter-key hint (labels are the keyboard's own).
- OTR2 in a mobile maximized window and FlorisBoard typing.

## Permissions and Android rules
- HIDL (`docs/HIDL.md`): needs a phone: the stack in a host-HAL instance (camera/media/graphics = device) starts the manager and writes the marker, forwarded names still work, an own service there. Rerun `tests/run hidl host-binder` on the x86 PC with the new libraries.
- Storage: `UNMOUNTABLE` not sent (udisks sends no failed-mount event and an unknown file system has no UUID to key a volume by); scoped storage does not see symlinks an app makes itself or `/proc/self/cwd`-relative opens.
- Call audio: native-stream route needs a real AAudio/OpenSL test and per-client playback configs (today the audio policy's `isStreamActive`/`isSourceActive` is instance-wide).

## Startup and memory
- Measured on the X23: JVM max heap 1.39 GB (25% of RAM; the API says 256 MB), 700 MB live heap fine, instance PSS 605 MB with NewPipe idle, composer 24 MB, SurfaceFlinger 37 MB; first `apklayer setup` 63 s. After a runtime change `setup` says Installed but 9 apps stay marked stale (converter ABI) until launched or converted from the manager banner.
- Quintus idle PSS: app JVM ~394 MB, hub ~249 MB, three spares ~235 MB each; composer 22 MB, SurfaceFlinger 38 MB (no ~80 MB GL context seen).
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla (UTM: idle PSS hub JVM 480-500 MB, spare JVM 280 MB; JVM flags already tuned); NewPipe ~21 s cold, ~12 s warm.
- Composer GL context ~80 MB: measure on a phone while an app draws.
- A PulseAudio-only sound server started later is used only from the next stack start (PipeWire is picked up per stream); needs an audio HAL hot-swap.

## Native bridge (x86_64)
- Diagnostics: `tools/ace/` (bpftrace on the translator and glibc, `pgr-netcut.sh`); translator notes in `docs/X86_64.md`.

## Sandbox (before it can be the default)
- NVIDIA sandbox: the broker and `nvidia-modprobe` at 0660 and for missing nodes need a Debian-style box (nodes are 0666 on the tester PC, no `nvidia-modprobe`).
- Each tree running the sandbox group needs `sudo sh tools/host-setup/sandbox.sh b/cli/apklayer` once.
- A release-candidate deb with the sandbox on for a day.

## Packaging and releases
- Owner to decide: `dh_installsystemduser` enables `apklayerd` for every user at login (`/etc/systemd/user/default.target.wants/apklayerd.service` after `dpkg -i`) although `debian/rules` and `docs/INSTALL.md` say it starts on demand: keep it, or `override_dh_installsystemduser: dh_installsystemduser --no-enable`.
- Sailfish RPM 0.4.19 on the Quintus does not turn `media = device` on by default (fixed in main since, needs a new RPM and runtime); the same new RPM brings the Lipstick rotation and Maliit fixes.
- Sailfish RPM from current main is installed and runs apps on the Quintus; open: no log line shows the `jni_guest_surface.c` change is active; the RPM and deb jobs need one real package build to confirm `tools/check-runtime-links.sh` runs clean in them.
- File associations (`docs/FILE-ASSOCIATIONS.md`, group `open-files`): needs a person: a real double-click of .apk/.apks/.xapk/.apkm in GNOME Files and Dolphin, and OpenVPN importing an .ovpn from the file manager. Open: the manager install dialog has no name/icon/permissions preview before Install; `apklayer open` under the sandbox; Flatpak (mime package needs an `xdg-data/mime:create` override and the host's `update-mime-database`), Sailfish and Ubuntu Touch variants; the manager's multi-file install queue.
- Click: Share/"Open with"; "Choose file…" from the manager; the click update install (`installClick`) is untested off a phone.
- Flatpak: launcher portal dialog.
- Release: the first real tag run (`release.yml` fetch steps and tag-to-version handling), the Sailfish RPM job and the GitHub runners are unrun; the release guard (`tools/release/check-runtime.py`) was checked locally only; PackageKit/pkexec install untested. The debs built here lack the PulseAudio backend (`libpulse-dev` missing on both hosts); the Flatpak `openjdk25` extension and 26.08 runtime stay unverified without `flatpak-builder`.
- First command after install blocks ~75 s for runtime setup on a phone (10 s on Parallels with the parallel setup; the phone is not re-measured).
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.

## Manager

## Hardware
- Sailfish phone: needs a person for the Maliit-hide check, a finger on the keypad and the lock code; Maliit's sideways keyboard in a landscape app; the manager's own text field has no keyboard under Lipstick (Slint has no Maliit client); sensorfw rotation by physically turning the phone; Bluetooth, scoped storage not run.
- Sailfish location: needs `/var/lib/location/location.conf` enabled and connman's gps `Powered=true` (off by default); a real fix outdoors unchecked; sensorfw stops sensors with the screen off (the app reads zeros): documented, not changed.
- VPN for apps (host-wide, `docs/VPN.md`, group `vpn`): built through NetworkManager (user-owned TUN, `apklayer vpn-guard`, `/1` default route, pkexec helper fallback); open: drive WireGuard and OpenVPN for Android to an established TUN (WireGuard needs a tunnel made in its UI, OpenVPN a profile and a server); a person's check of the pkexec dialog on a real desktop and the notification's Disconnect button; the pkexec path end to end through the hub; the per-app sandbox; Sailfish via connman; several instances with VPNs (they share the `/1` routes); no kill switch, `setMtu` ignored on the NM path.
- Vibrator: `composePwle` on HALs with PWLE capability instead of the 20 ms step fallback.
- iio-sensor-proxy gives only derived values: no accelerometer or compass type. On Parallels a source-tree CLI needs `--no-daemon` plus `APKLAYER_NATIVE`/`APKLAYER_BT`/`APKLAYER_BINDER=userspace` (the old 0.3.4 daemon takes over).
- USB: one chooser for several apps; sandbox node access.
- Bluetooth: pairing paths, Sailfish agent, truly simultaneous connects to one server socket (the mock keeps one client profile per UUID).
- Push: a real WorkManager app, constraints, resume after suspend, microG 0.3.x.

## Tests and tooling
- Click: "Choose file…" picker lists the Content Hub peers but picking a file end to end, and the manager's Update button, are not driven.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.

## Needs hardware or a person
- Needs the tester PC login (it takes a password; my key is refused; host `wesley-a520mhdv`): refresh its stale `~/apklayer-build/prebuilts` by rsync from the Mac; run `kde-window.py` once at 150% on its KWin.
- Needs the Quintus lock code (not in `local.conf`): re-run `phone-test.sh sfos` groups with the current RPM.
- AMD: Vulkan presentation and host ES1 untested.
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

- Sailfish has no bash (busybox): phone tests must be POSIX sh or Python.
- Share / Open with on the X23 is not driven through the UI by `phone-test.sh`.
- Turned-window keyboard inset needs a framework patch (new android-all): Android treats the IME as a bottom inset (reason in CHANGELOG).- Confirm `sandbox` (runms 60000) and `window-geometry` under two parallel groups on a real 4-CPU host (only simulated with busy loops).

- `kde-window` cannot run on the UTM VM (no KWin; `tests/lib/kwin.py` journalctl cursor parse fails): run it on the KDE PC to cover the SSD first-window size.
- `fullscreen` and `spare-race` failed once each in the lead's shared tree and pass alone and together in a fresh tree (CHANGELOG 2026-11-16): if either fails again, look at that tree's leftover state first (stale files, foreign-owned sandbox data), then the load.
