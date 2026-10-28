# HANDOFF: start here, then work down the list until every milestone is done

> Current authorization (2026-10-27): the user asked the assistant to build anything needed in OrbStack `~/los24` autonomously while they sleep. This supersedes older instructions below to ask them to build. Only incremental builds of `gsi_arm64 userdebug`; never clean or wipe `out/`.

You continue a long-running project. Read, in this order: `CLAUDE.md` (rules, binding), `goal.md` (the goal), `memory.md` (status, local reference projects, how to run),
`docs/PLAN.md` (the plan; the section "P7.1 GPU route: next steps" and items 8, 8b, 8c are the live work list), `docs/ARCHITECTURE.md`, `docs/PATCHES.md`, `docs/AOSP-BUILDS.md`.
Then do the milestones below **in this order**, one at a time, committing at every step, updating `docs/PLAN.md` status and `memory.md` as you go. Do not stop between
milestones; stop only for something only the user can do (an Android build in `~/los24`, see below) and say exactly what to build, then continue with other work meanwhile.

Current progress: **Milestone E is active by the user's latest instruction; media B is closed.** Real Binder/media integration is committed in `d72bb04`, and the headless hub/process directory in `8aedf5e`. E1 framework service binding now passes: generated AIDL, native Parcel/Bundle payloads, exported/normal-permission checks, null binding, 20 canceled binds, native death and automatic restart/reconnection, unbind/onDestroy, automatic hub startup, and headless service-only APKs. Native media and GPU regressions pass. **Next: E2 broadcasts**, then E3 providers/observers, E4 activities/results/started services/pending intents, and E5 notifications. Track service breadth gaps in PLAN 8c; do not mark E complete. C/D are outside the current requested scope.

## What this is
apklayer runs Android apps on a Linux desktop with real Android userspace and no emulator. The route that matters is the device-library route: the LineageOS 24 tree's
arm64 libraries run under bionic_translation on kernel binderfs; real SurfaceFlinger, servicemanager, our own HALs (gralloc, HWC, permission, virtual display sink) and
HWUI render on the host GPU; the app is a Robolectric JVM (shadows for system services) loading the real native libraries. One SurfaceFlinger virtual display per app,
shown as a Wayland window by `apklayer-shell`. UiBench runs today with GPU rendering, real input, dialogs. The older CPU-only route (x86/Rosetta, bundled Mesa, GStreamer and
WebKitGTK stand-ins) was removed (2026-10-28): everything runs on the device libraries. Hosts are not Mesa-specific (no minigbm/Mesa dependency); real refresh rate; no fake stand-ins for hardware.

## Rules
Binding rules (builds in `~/los24`, no waiting/background, cleanup, no manual testing, reuse local code, patches, commits) are in `CLAUDE.md`; do not restate them here.
- Verify on the VM with real input (`/tmp/uinput-keys`, compiled from `tests/vm/uinput-keys.c`), SurfaceFlinger dumps (`servicetool dump SurfaceFlinger`), logs and screenshots through `tests/vm/vmcapture.py` (flameshot on the UTM VM, `prlctl` on Parallels) on the Mac (look at the image).
- Android-side edits: edit `aosp/`, `scripts/sync-aosp.sh "message"`, build the named modules, install outputs from `out/target/product/generic_arm64/system/lib64/` on the VM. New modules need a reason in the commit message and `aosp/hals/README.md`.
- The user likes short honest status; they get frustrated by silence, long stalls, claims without verification. Say what is verified and what is not.
- Decisions already made (do not reopen): system services stay Robolectric shadows (no system_server) except where a process boundary exists, where real binder is used (hub, PLAN 8c); backlight and thermal belong to the host; accounts the Android way; system providers/apps ship on every instance; Bluetooth through a BlueZ bridge scoped by app usage; **CTS strictly last**; x86_64 host libandroid_runtime build deferred until after the tool is public.

## Environment
- VM: `ssh parallels@10.211.55.4` (password `1234`, use `sshpass -p 1234 ssh ...`), Ubuntu arm64 on Parallels, GNOME Wayland, virtio-gpu (virgl; no Vulkan hardware, no Bluetooth). Repo copy `~/apklayer-m1` (sync files by `tar -cf - … | ssh … tar -xf -`). Build host-side C with `ninja -C b <target>` there (the Java host jar target fails on an old unrelated error: compile single classes with javac against `~/arm64-native/aosp/host-classes:sim-classes:Robolectric_all.jar` and copy into `aosp/host-classes`).
- Run the GPU route: `XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE=$HOME/arm64-native bin/apklayer run com.android.test.uibench` (starts services via `scripts/arm64-stack.sh none`, a virtual display sink, the arm64 JVM; environment in `scripts/native-env.sh`; `APKLAYER_RUNTIME_DEBUG=1` for native logs; stack logs in `/tmp/apklayer-stack/`). Stop: `bin/apklayer stop`.
- Regression test: `tests/vm/gpu.sh` (quick smoke) or `tests/vm/gpu-window.sh` (dialog/resize). The Robolectric patches are in `runtime/patches/` (compile into `~/arm64-native/native-runtime.jar`, see `scripts/build-native-arm64.sh`); the shim is `bridge/native/runtime_shim.c`; host HAL libs `bridge/hals/*`, shell `bridge/shell/*`.
- Pitfalls learned: the property store is write-once (`ro.*`), restart the stack per test unless fixed; the host display HAL's callbacks struct is ABI-shared with the already-built composer (add setters, not fields); a `cat` of a missing file produces an empty library on the VM; `prlctl capture` is reliable now; gdb needs `sudo`; Robolectric's `@Direct` on natives returns a stub 0 (call `$$robo$$name$nativeBinding`).

## Milestones, in order (each: implement, verify on the VM automatically, commit, update PLAN/memory)
**A. GPU route fixes** (PLAN "P7.1 GPU route: next steps" items 1-7):
 1. Crash when resizing the window while a dialog is open (window disappears, JVM idle, main thread stuck in `HardwareRenderer.nSetStopped`): diagnose (thread dumps, sink log, buffer starvation) and fix; add to `tests/vm/gpu.sh` (quick smoke) or `tests/vm/gpu-window.sh` (dialog/resize).
 2. Dialog right margin out of bounds: clamp window frames to the display, re-measure, symmetric margins (`runtime/patches/.../ShadowWindowManagerGlobal`).
 3. Window mapping/focus: the sink presents an initial blank frame; drop the AppHost "ignore initial inactive" workaround.
 4. Hide the primary display's empty window (headless primary with real-refresh vsync, no visible window).
 5. Several apps at once (own virtual display/window each; focus; input routing; instance naming per PLAN P9).
 6. Text input and clipboard on the proxied display window.
 7. Cleanup that remains (list in PLAN item 7: patches as overlay with tests, `ro.*` per launch, describe step for the tree flavor, trace options, vsync requests without own window, ARCHITECTURE docs).
**B. Media** (HAL domain layout `aosp/hals/<domain>` + `bridge/hals/<domain>`): MediaCodec/MediaPlayer on the real stack, video buffers through our gralloc into SurfaceFlinger layers (YUV zero-copy), codec2 HAL over the host's decoders, audio HAL (PipeWire). Verify with real media in the corpus.
**C. Camera**: camera provider HAL over PipeWire/V4L2 into BufferQueue surfaces; verify with a camera app/preview.
**D. Other hardware**, one domain at a time (`aosp/hals/README.md`): gnss (GeoClue), sensors (IIO), vibrator/haptics and game controllers (evdev), battery/power (UPower, logind), connectivity reporting the host's real state (NetworkManager, ModemManager, honest "no SIM"), USB (udev), biometrics (fprintd), NFC (PC/SC), portals (files, screen capture), then **Bluetooth** (BlueZ bridge, shadows of `android.bluetooth.*`, scoped by corpus usage, compatibility table, developed against `btvirt`/vhci).
**E. Apps talk like on Android: the instance hub** (PLAN 8c): hub process, real `Binder`/`Parcel` JNI in app JVMs, cross-app tests in this order: bound AIDL service + death notification across two JVMs; broadcasts (manifest/dynamic/ordered/sticky/permissions); content providers with observers; cross-app `startActivity`/results/`startService`/pending intents; notifications.
**F. System apps and providers on every instance** (PLAN 8b): Settings, Contacts/Calendar, Download, Telephony, UserDictionary, Documents UI, Media providers from the tree's APKs, data per instance, over the hub.
**G. Vulkan** (PLAN P7.5): G1 native execution checkpoint implemented (original Android loader + host Vulkan HAL, APK/JNI image readback). G2 native-buffer/dmabuf import and sync-fd acquire/release, G3 AHardwareBuffer/WSI/skiavk remain unfinished; end-to-end presentation verification needs a real Vulkan GPU driver. See PLAN “Vulkan G1 checkpoint”.
**H. CTS** (PLAN P10): strictly last; the relevant app-facing modules, a tracked pass number, failures triaged into gaps (fixed) and device-behaviour tests (listed with reasons).
Deferred beyond H: the x86_64 host libandroid_runtime build and the public release (Flatpak, PLAN P8), instance multiplication for full phones (PLAN P9), SystemUI as a yardstick.

## Working loop
Pick the next unfinished milestone item, state the plan in two lines, implement the smallest change, sync Android-side edits and request the exact modules to build (batch several fixes per build), install, run on the VM, verify with real input and an image/log, commit, update `docs/PLAN.md` + `memory.md`, continue. Keep the user informed in short messages when something is verified or blocked, never silently.
