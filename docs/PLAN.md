# apklayer: open work

Only what is still to do; done items are deleted. Only the lead edits this file. History: `CHANGELOG.md`, `docs/history/`. Parked: `docs/NOTPLAN.md`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak).
- No per-app hacks; `tests/corpus/` measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: sensors via sensorfw, location via GeoClue (Sailfish) or Lomiri's location service; vibrator, camera, media use the device HALs.
- Replace an installed library by temp file + `mv`/`install`/`rsync`, never `cp`/`scp` onto a library a running JVM has mapped (it faults at exit); `tests/vm/exit-clean.py` shows both.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- ANGLE GLES (Google Android 17 x86_64/x86 build, Vulkan on): Android Files starts and exits, and the NVIDIA RTX 5060 Ti's `SurfaceView.lockCanvas()` read/write path passes an isolated three-frame test. Host Linux ANGLE imports DMA-BUFs, samples non-renderable NVIDIA images into an RGBA framebuffer, and uploads RGB565/ABGR pixels through GLES. PGR/Azur Lane logs show the compositor's native host EGL rejecting LINEAR AB24 buffers; ANGLE-mode SurfaceFlinger now selects Linux ANGLE through the apklayer EGL shim, and NVIDIA CPU-mapped windows retain tiled allocation. Build `libapklayer_egl` for x86_64/arm64 and retest when a serialized session is safe. AIDA64's log shows `vkGetFenceStatus` returning `VK_ERROR_OUT_OF_HOST_MEMORY`, followed by device loss; ANGLE patch 0003 disables present-fence maintenance for NVIDIA Android WSI. 3DMark's virtual display is black; its sink also reports the six-acquired-buffer limit and sync-transaction timeouts, so check both composition and BufferQueue progress after the EGL change. Rebuild Android ANGLE libraries and Noble deb; retest PGR, AIDA64, Azur Lane and 3DMark. Still verify GLES 3.2 with a dedicated probe. `GraphicsEnv: No GPU service` is nonfatal.
- Play Store install failed intermittently with `INSTALL_FAILED_INVALID_APK: no base APK` (HubInstaller.staged); cause unknown; the error now names the session's files: read it at the next failure.
- Play services replies of exactly 8388608 bytes (`Large reply transaction`): which call, and whether its caller copes, is unknown; read hub.log at the next one.
- YouTube 21.45: check the signed-in feed and a video with the ApklayerBuffers fix; signed out, the EU consent page looked wider than the window.
- Endfield on NVIDIA (no ASTC/ETC2 in the Vulkan driver): black window (`vk result -3`); needs ASTC emulation in the Vulkan path (RGBA8 or BC7 backing, decode at copy time).
- Play services `:persistent` exits with 1 now and then ("Instance hub disconnected"); no System.exit caller seen: run with `APKLAYER_EXIT_TRACE=1 APKLAYER_JOPTS=-Dapklayer.exittrace=true`.
- GApps sign-in: after local account creation on GMS 26.44 / Android 17, setup reports “There was a problem communicating with Google servers”; screenshot-time logs include a failed DroidGuard response fetch. Cronet Dynamite also failed to load `ImplVersion`: path-based DEX loader support is patched (regression check); the DelegateLastClassLoader overlay was missing from the framework jars (runtime/meson.build, fixed 2026-11-16, see CHANGELOG), so its NullPointerException ended Play services' process (exit 137) before check-in could finish; with it Play services checks in on the VM (CHECKIN_COMPLETE, no uncaught exception). The Dynamite modules themselves (`app_chimera/m/*/DynamiteLoader.apk`) are downloaded by Play services after check-in, so the `ImplVersion` and DroidGuard `Couldn't fetch response` errors can only be read again in a real sign-in run (`-Dapklayer.traceuncaught=true` names a crashing exception); end-to-end sign-in is still unverified. The token exchange over the Java Cronet fallback failed with `System error` (body read into a direct buffer's array copy): fixed at converter ABI 11 (CHANGELOG 2026-11-15); the next run added the account and then deadlocked in `Services.bind` ("Getting account ready"): fixed (same date); then the account was stored with Google's data but Play Store could not see it (signer lineage): fixed (same date); then Play Store's downloads were refused by its internal-only grpc-binder check (oneway sender uid): fixed for the userspace binder (same date; the kernel binder still reads such a caller as nobody); the install then failed on the session's dex metadata file (.dm): fixed (same date); needs a human check: a sign-in in `gwd` (owner's credentials); then the remaining server error, if any, from its log. Owner reports Waydroid Android 13 works without Integrity (GMS version unknown).
- Play Store installs (deb 0.4.19+gitf97511de, owner's default instance 2026-11-15 20:14-20:17): three sessions for com.gryphline.endfield.gp failed with `INSTALL_FAILED_INVALID_APK: no base APK` (HubInstaller.staged found no non-split APK in the session; its files were deleted with the failure, so unseen); a later one (20:22, 5 APKs) installed. Intermittent, cause not found. HubInstaller's error now names the files the session held and their sizes: read it at the next failure.
- Play services (`com.google.android.gms`) sends a reply of exactly 8388608 bytes (`Large reply transaction`, code 46): on the userspace binder that ended the process (exit 134) until the fix of 2026-11-15 (CHANGELOG); the caller now gets BR_FAILED_REPLY as on the kernel (no receive area holds 8 MB there either). Which call answers 8 MB, and whether its caller copes, is unknown: read hub.log for `Large reply transaction of 8388608` and the caller's exception at the next occurrence.
- YouTube 21.45: the signed-in home's empty feed (2026-11-15) and the signed-out playback error had one likely cause, fixed 2026-11-16 (ApklayerBuffers.array, CHANGELOG): check the feed and a video in the owner's signed-in instance with that build. Signed out, YouTube's EU consent page looked wider than the window (its "Inloggen" button cut off): check its viewport once a tap can reach it.
- Arknights: Endfield on NVIDIA (no ASTC, no ETC2 in the Vulkan driver): Unity creates a 1920x1080 `VK_FORMAT_ASTC_4x4_UNORM_BLOCK` image anyway and failed (`vk result -3`), the window stayed black. ASTC/ETC2 emulation in `vulkan.apklayer` (gfxstream's, CHANGELOG 2026-11-16) passes its host test on NVIDIA; next: build `vulkan.apklayer` for gsi_x86_64 (and arm64) in `~/los24` (the clang -Werror build of the vendored files is unchecked), install it on this PC in an own runtime copy, run Endfield in an own instance and read its log for `vk result`; then run group `vulkan-textures` on UTM and the x86 PC. Behind it, Tencent ACE ended Endfield ~60 s in before (CHANGELOG 2026-11-12).
- Play services' `:persistent` process exits with 1 now and then (owner's default instance 2026-11-15 22:00; once in a fresh instance's first boot 2026-11-16), starts and binds sent to it meanwhile fail with "Instance hub disconnected". No Java System.exit caller seen; run with `APKLAYER_EXIT_TRACE=1 APKLAYER_JOPTS=-Dapklayer.exittrace=true` and read its component log at the next one.
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).

## Input / IME
- Wheel and stylus (touchpad, tablet) never ran on real hardware; the stylus slider (AXIS_WHEEL) and tool wheel (AXIS_SCROLL) go through the test input only.
- Non-Latin input: RTL cursor, selection, bidi in an EditText (needs a test app and a human look); layout hotkeys on a real compositor; `getUnicodeChar()` of a non-Latin key has no meta variants.
- text-input-v3: no show/hide request or enter-key hint, never run against a real compositor (GNOME/KWin/Phosh OSK); fcitx stops composing while hidden. F1-F12 have no Android keycode in `keysym_to_android`.

## Permissions and Android rules
- Storage: scoped storage follows symlinks only in the process that made one (or its children) and cwd-relative paths only under the instance's Android/; earlier links and `openat(dirfd, ...)` into another package's directory are not seen (needs a fd-based check).
- Call audio: the hub polls the audio policy each second while two communication requesters compete; a native client carries uid 1000, so another process of the same app does not count; run `audio-uid` and `audio-backend-swap` on the x86 PC.

## Sandbox (before it can be the default)
- NVIDIA sandbox: broker and `nvidia-modprobe` (0660, missing nodes) run only on a real Debian-style NVIDIA box.
- A release-candidate deb with the sandbox on for a day.

## Packaging and releases
- File associations (`docs/FILE-ASSOCIATIONS.md`, group `open-files`): needs a person: double-click of .apk/.apks/.xapk/.apkm in GNOME Files and Dolphin, OpenVPN importing an .ovpn. Open: `apklayer open` under the sandbox for a running app's `onNewIntent`; adaptive/vector icons in the install preview; Flatpak, Sailfish, Ubuntu Touch variants; the multi-file install queue.
- Flatpak: launcher portal dialog.
- JDK 25 on the phones: Quintus runs it 1-2 s slower than 21 + CDS, X23 untested: A/B 21 vs 25 on both phones, then pick the default; the click job needs `openjdk-25-jdk-headless`.
- Release: PackageKit/pkexec install of the deb untested; the Flatpak `openjdk25` extension and 26.08 runtime are only built in CI.
- System-app reinstall on runtime change: unchecked on a phone.

## Hardware
- NFC (`docs/NFC.md`, group `nfc`): PC/SC against a real reader unrun; neard backend; NFC permission check, `ACTION_ADAPTER_STATE_CHANGED`, tag-removal callbacks, a chooser for several apps. HCE and a phone's NFC HAL are not planned.
- Sailfish phone: Maliit's sideways keyboard in landscape; the manager's text field has no keyboard under Lipstick; Bluetooth and scoped storage not run.
- Sailfish location: needs `/var/lib/location/location.conf` enabled and connman gps `Powered=true`; no real fix outdoors yet; sensorfw stops sensors with the screen off (zeros).
- VPN for apps (`docs/VPN.md`, group `vpn`): OpenVPN's data channel (`libovpnexec` sits in `ppoll`, never reads the TUN; try `APKLAYER_RAW_SYSCALL_VIEW=off`); Disconnect button and pkexec dialog on a real desktop; per-app sandbox; Sailfish via connman; instances share `/1` routes; no kill switch; the libc SIGSYS handler corrupts Go threads (only `fast_syscall` numbers are safe; make it libc-free); needs an OpenVPN server without OAuth2 and the owner's WireGuard `wesley.conf`.
- USB: sandbox path (`usb-sandbox`) unrun: the UTM VM lacks `tools/host-setup/sandbox.sh`.
- Bluetooth: pairing on real devices; Sailfish `ensure_agent` leaves `agent` set when RegisterAgent fails (harmless); our agent is not the default one; BlueZ's choice between two profiles of one UUID unchecked.
- Push: no `hello-work` test app (needs a work-runtime AAR from the owner); job constraints, periodic runs and resume after suspend untested; microG 0.3.x.

## Needs hardware or a person
- Quintus: re-run `phone-test.sh sfos` groups with the current RPM (`tools/halium/unlock.sh sfos`).
- Halium: vendor HALs, `media = device`, NewPipe network; wheel/stylus, text-input and the layout subtype cannot run under Lomiri: Sailfish/KDE check only.
- Sailfish: a finger on the host dialog (Open Camera with CAMERA revoked was denied at once, no prompt seen); a geoclue fix outdoors.
- Ubuntu Touch: turning by hand (the fake accelerometer did not reach sensorfwd); the manager's Install dialog (path field fills half the screen); the update banner is half a screen tall while it runs (`manager/ui/setup.slint` `UpdateBanner`); `HubIncoming/<n>` of a replaced Share stays.
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Notifications on the phones
- Lomiri (X23) drops every org.freedesktop.Notifications notification within ~30 ms: find why (notifyplugin `pruneExpired`/QML, a setting, a clock/zone problem) or deliver through the click Postal route (`com.lomiri.Postal`); until then no popup, tap, action or dismiss can be checked there.
- Lipstick: swipe-away delete intent and action buttons (`x-nemo-remote-action-*`) are not driven by phone-test; group/summary stacking and a foreground-service entry need a human look at the events view.
- The phones carry old host classes until the next click/RPM: rerun `phone-test.sh sfos notify` and `ut notify` after installing 0.5.x.
