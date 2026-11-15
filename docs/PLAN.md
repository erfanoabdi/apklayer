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
- GApps first sign-in: Play Store (52.4.41) started Play services' add-account redirect twice, 20 ms apart (hub.log `com.android.vending starts ...UnpackingRedirectActivity` twice, owner's default instance 2026-11-15 19:00); the second flow added the account, the first one's WrapperControlledActivity stayed open after it. Same start: Play Store showed its LocaleChangedModeActivity (desktop locale nl-NL), a possible second trigger. The locale trigger is fixed (CHANGELOG 2026-11-15, host settings before READY); the double add-account start is not. The Play services exit-137 loop is fixed (CHANGELOG 2026-11-15, six framework gaps at module init); a new uncaught exception in one of its intent operations would bring it back: its crash handler ends the process. Fix merged 2026-11-16 (HubAccounts: an authenticator session answers its client once), untested: on the next sign-in hub.log must show one `UnpackingRedirectActivity` start; if two, trace with `-Dapklayer.tracestart=true`.
- GApps sign-in: after local account creation on GMS 26.44 / Android 17, setup reports “There was a problem communicating with Google servers”; screenshot-time logs include a failed DroidGuard response fetch. Cronet Dynamite also failed to load `ImplVersion`; path-based DEX loader support is patched and covered by a regression check, but end-to-end sign-in is still unverified. The token exchange over the Java Cronet fallback failed with `System error` (body read into a direct buffer's array copy): fixed at converter ABI 11 (CHANGELOG 2026-11-15); the next run added the account and then deadlocked in `Services.bind` ("Getting account ready"): fixed (same date); then the account was stored with Google's data but Play Store could not see it (signer lineage): fixed (same date); then Play Store's downloads were refused by its internal-only grpc-binder check (oneway sender uid): fixed for the userspace binder (same date; the kernel binder still reads such a caller as nobody); the install then failed on the session's dex metadata file (.dm): fixed (same date); needs a human check: a sign-in in `gwd` (owner's credentials); then the remaining server error, if any, from its log. Owner reports Waydroid Android 13 works without Integrity (GMS version unknown).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR dies 114-161 s after launch on the repaired tester PC (x86_64 translated; 11 of 11 runs, any preload, translator, runtime or translation mode), after ACE connects to its servers and the invalid-free storm starts; not the `/proc/self/root` probe (none in 5 logged runs). PGR on the AWS arm64 server (native) is stable: 2.5 h or more in the lobby with libapklayer_egl 652ac421; its three hs_err files and the ~13 GB cores in /var/tmp/pgr-core are exit-time faults at `systemctl stop`, not gameplay crashes. Next for the tester PC: reinstall PGR's APK set there (an agent's `apklayer install` erased its lib/ and arm64 split; the full set is on AWS), run with fresh app data (ACE state in files/ano_tmp), look at what ACE gets for /proc/self/fd, /proc/self/map_files, /proc/<pid>, /dev/virtpipe-sec; restore `berberis.mode.<pgr>=lite-translate-or-interpret` (takes effect after a whole-instance `apklayer stop`).
- New libraries not run on the tester PC/NVIDIA or the phones: x86_64 `libapklayer_egl` (sRGB fix) and the linked-GL query fix (`gl-formats` passes on the Intel PC).
- Home scene on NVIDIA: block noise on 3D models. Recheck with `apklayer prop set berberis.mode.com.kurogame.gplay.punishing.grayraven.en lite-translate-or-interpret` (and unset for the reference); the ASTC fallback in `cli/env.cpp` is a separate lever.

## Graphics
- Multi-GPU: tested on the MacBookPro16,1 (Intel UHD 630 + AMD Pro 5500M, the desktop's KWin on the AMD one) and with a nested KWin on the Intel one (`APKLAYER_COMPOSITOR_GPU`, a virtual KWin names no device); needs a person or hardware: a real KWin session on the iGPU (`KWIN_DRM_DEVICES`), NVIDIA + Intel (Optimus), two dGPUs. A different GPU per app is not built (one stack, one GPU per instance: use two instances).
- KDE, needs a human eye: edge/corner drag resize, Alt+F8, snap by dragging, the look of KDE's title bar around the app, a real second monitor, fractional scale on the real display, `fullscreen-key` (F11) on a private session.
- Lipstick: a mouse over the navigation-bar strip (pointer.c `in_remote_strip`) is the bar's since 2026-11-15; needs a human check (no pointer device on the phone to try it).

## Camera
- Fossify Camera on Parallels is unchecked (no APK locally); Open Camera works on the MacBook camera.
- Halium camera: preview rotation needs a human check (preview and still agree on both phones; whether the picture is upright was not judged). `camera`/`media = device` work (libhidl 0005, `tools/halium/hidl-list.sh`). NewPipe playback with `media = device` is blocked by YouTube extraction ("Could not parse website") on the phones' network, not by the stack. The X23 `hello.camera` large still is cut by the landscape-to-portrait activity recreation (test-app race).

## Input / IME
- KDE navigation bar (the mobile bar under server-side decorations, `navbar auto|on|off`): pixel-check the light scheme.
- Wheel and stylus: written from the protocols and Android docs, never run on real hardware (touchpad, tablet); the stylus slider (AXIS_WHEEL) and tool wheel (AXIS_SCROLL) are delivered through the test input only (AXIS choice is ours: Android has no stylus-specific ones).
- Non-Latin input: RTL cursor, selection and bidi in an EditText (needs a test app and a human look); layout hotkeys on a real compositor (the xkb group reaches the app as its input method subtype, tested with the injected keymap); `getUnicodeChar()` of a non-Latin key is the shell's character with no meta variants (no per-layout .kcm: dead keys, Shift/AltGr levels through `getUnicodeChar(meta)`).
- text-input-v3 has no show/hide request and no enter-key hint (labels are the keyboard's own): hide = disable until show/new field and MULTILINE only without an action are sent, never run against a real compositor (GNOME/KWin/Phosh OSK); a desktop input method (fcitx) stops composing while hidden. Fn keys (F1-F12) have no Android keycode in `keysym_to_android`.
- OTR2 in a mobile maximized window and FlorisBoard typing.

## Permissions and Android rules
- HIDL (`docs/HIDL.md`): needs a phone: the stack in a host-HAL instance (camera/media/graphics = device) starts the manager and writes the marker, forwarded names still work, an own service there. Rerun `tests/run hidl host-binder` on the x86 PC with the new libraries.
- Storage: `UNMOUNTABLE` is decided not to be sent (udisks sends no failed-mount event and an unknown file system has no UUID to key a volume by; a drive nobody can mount stays unlisted, as a drive the desktop does not mount). Scoped storage follows symlinks only in a process that made one itself (or its children) and cwd-relative paths only when the cwd is under the instance's Android/ directory; a link made by an earlier process and `openat(dirfd, ...)` into another package's directory are not seen (needs a fd-based check).
- Call audio: native-stream route needs a real AAudio/OpenSL test and per-client playback configs (today the audio policy's `isStreamActive`/`isSourceActive` is instance-wide). Not doable from apklayer's side: the stock libaudiopolicyservice has no per-uid/per-client activity query (the per-client configs live in AudioService's PlaybackActivityMonitor, which apklayer does not run); it needs a patch in frameworks/av's policy service (an `isActiveForUid`-like call) built in los24.
- A PulseAudio-only sound server started later is used only from the next stack start (PipeWire is picked up per stream); needs an audio HAL hot-swap: the HAL's Stream calls the one backend library the stack was started with (APKLAYER_HOST_LIBS); a swap means dlopen of the other `libapklayer-audio-*.so` from the HAL process and a per-stream function table (Stream.cpp, ~12 call sites), to be built and checked in los24.

## Sandbox (before it can be the default)
- NVIDIA sandbox: the broker and `nvidia-modprobe` at 0660 and for missing nodes run only on a real Debian-style NVIDIA box (code read and the node-to-arguments table tested with a Debian node list; nodes are 0666 on the tester PC, no `nvidia-modprobe`).
- A release-candidate deb with the sandbox on for a day.
- SettingsProvider throws "Ssaid settings not accessible" for an isolated process (android:isolatedProcess, uid 99000) of an app whose SSAID already exists: the lookup is keyed by the caller's uid, the insert by the app's, so it regenerates the same value and `insertSettingLocked` returns false (frameworks/base/packages/SettingsProvider, `getSsaidSettingLocked`/`generateSsaidLocked`: key the lookup by `callingPkg.applicationInfo.uid`; an apklayer patch of the settings APK). Group `sandbox` alone failed every second run in a slot before `tests/vm/sandbox.sh` deleted the table first.

## Packaging and releases
- Sailfish RPM: the `jni_guest_surface.c` change now logs `[runtime] jni_guest_surface active: ...` once per app JVM; needs an RPM of this tree installed on the Quintus and one app run to see it there.
- File associations (`docs/FILE-ASSOCIATIONS.md`, group `open-files`): needs a person: a real double-click of .apk/.apks/.xapk/.apkm in GNOME Files and Dolphin, and OpenVPN importing an .ovpn from the file manager. Open: `apklayer open` under the sandbox is tested only for a fresh launch, not the running app's `onNewIntent`; the install preview resolves plain-string labels and PNG icons only (no adaptive/vector icon, no sparse/16-bit arsc types: package name fallback); Flatpak (mime package needs an `xdg-data/mime:create` override and the host's `update-mime-database`), Sailfish and Ubuntu Touch variants; the manager's multi-file install queue.
- Click: Share/"Open with"; "Choose file…" from the manager; the click update install (`installClick`) is untested off a phone.
- Flatpak: launcher portal dialog.
- Release: the first real tag run, the GitHub runners (the fetch steps, `actions/checkout` of the `prebuilts-v<version>` tag, upload/download of `~` file names) are unrun here; the guard, SHA256SUMS rename and `releases-json.py` ran locally with a fake tag (2026-11-16); PackageKit/pkexec install untested; the Flatpak `openjdk25` extension and 26.08 runtime stay unverified without `flatpak-builder`.
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.

## Hardware
- NFC (`docs/NFC.md`, group `nfc`, mock tags): the PC/SC source against a real reader (UID, Type 4 NDEF read, direct transmit) is unrun; a neard backend (D-Bus, PN533/ST21NFC without PC/SC); the NFC permission check, `ACTION_ADAPTER_STATE_CHANGED`, `ignore()`/tag removal callbacks, a chooser for several matching apps; HCE and a phone's own NFC HAL are not planned.
- Sailfish phone: needs a person for the Maliit-hide check, a finger on the keypad and the lock code; Maliit's sideways keyboard in a landscape app; the manager's own text field has no keyboard under Lipstick (Slint has no Maliit client); sensorfw rotation by physically turning the phone; Bluetooth, scoped storage not run.
- Sailfish location: needs `/var/lib/location/location.conf` enabled and connman's gps `Powered=true` (off by default); a real fix outdoors unchecked; sensorfw stops sensors with the screen off (the app reads zeros): documented, not changed.
- VPN for apps (host-wide, `docs/VPN.md`, group `vpn`): built through NetworkManager (user-owned TUN, `apklayer vpn-guard`, `/1` default route, pkexec helper fallback); open: drive WireGuard and OpenVPN for Android to an established TUN (WireGuard needs a tunnel made in its UI, OpenVPN a profile and a server); a person's check of the pkexec dialog on a real desktop and the notification's Disconnect button; the pkexec path end to end through the hub; the per-app sandbox; Sailfish via connman; several instances with VPNs (they share the `/1` routes); no kill switch, `setMtu` ignored on the NM path.
- USB: the sandbox path (`tests/vm/usb.py` with `USB_SANDBOX=1`, group `usb-sandbox`, optional) is unrun: the UTM VM lacks `tools/host-setup/sandbox.sh` (machine-wide AppArmor change); the hub opens the node and hands the descriptor over (the same path as without the sandbox, which `usb` runs).
- Bluetooth: pairing paths on real devices; Sailfish: `ensure_agent` (bridge/portal/bluez.py) leaves `agent` set when RegisterAgent fails, so only the first bond reports `pairing without our agent` (no functional harm); our agent is not the default agent, so a pairing a remote device starts reaches the system's agent only; real BlueZ's choice between two client profiles of one UUID is unchecked (the mock hands each registration one connection).
- Push: no `hello-work` test app: androidx.work is not on this machine (no downloads); WorkManager itself runs (Nextcloud 3.27 debug: 15 jobs through the hub, periodic ones at 15 min); a test app needs a work-runtime AAR from the owner; constraints stopping a running job, periodic runs and resume after suspend untested; microG 0.3.x.

## Tests and tooling

## Needs hardware or a person
- Tester PC: run `kde-window.py` once at 150% on its KWin (the prebuilts there are current; the new deb is built, not installed).
- Needs the Quintus lock code (not in `local.conf`): re-run `phone-test.sh sfos` groups with the current RPM.
- Halium: vendor HALs, `media = device`, NewPipe network, the new native changes on the phones.
- Sailfish: geoclue fix, Lipstick switcher with two apps, a finger on the host dialog.
- Ubuntu Touch: manager on screen, navigation bar under the panel, drawer tap, shell window, maximize, turning by hand.
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Parked (owner's decision)
- Biometrics, ModemManager telephony, screen capture portal.
- Bluetooth advertising/peripheral, A2DP/HFP, LE audio, OPP; USB accessory, tethering.
- Several phone instances as a product; a JRE for the Ubuntu Touch deb.
- Confirm `sandbox` (runms 60000) and `window-geometry` under two parallel groups on a real 4-CPU host (only simulated with busy loops).
- Game controllers (`docs/GAMEPAD.md`, group `gamepad`, tested with a uinput pad only): needs a real pad: udev permissions (uaccess/`input` group and the retry when they change), a real DS4/DualSense/Xbox pad over USB and Bluetooth (axis layout, rumble motors), Halium phones; limits: events go only to the focused window's app, no gyro/touchpad/LED/battery, one vibrator id per pad.
