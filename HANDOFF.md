# HANDOFF: start here, then work down the list until every milestone is done

> Current authorization (2026-10-27): the user asked the assistant to build anything needed in OrbStack `~/los24` autonomously while they sleep. This supersedes older instructions below to ask them to build. Only incremental builds of `gsi_arm64 userdebug`; never clean or wipe `out/`.

You continue a long-running project. Read, in this order: `CLAUDE.md` (rules, binding), `goal.md` (the goal), `memory.md` (status, local reference projects, how to run),
`docs/PLAN.md` (the plan; the section "P7.1 GPU route: next steps" and items 8, 8b, 8c are the live work list), `docs/ARCHITECTURE.md`, `docs/PATCHES.md`, `docs/AOSP-BUILDS.md`.
Then do the milestones below **in this order**, one at a time, committing at every step, updating `docs/PLAN.md` status and `memory.md` as you go. Do not stop between
milestones; stop only for something only the user can do (an Android build in `~/los24`, see below) and say exactly what to build, then continue with other work meanwhile.

Current progress (2026-10-27): A1–A7 are verified and committed. B media now has real native JNI/Codec2/AIDL and SurfaceView YUV presentation for VP8, VP9 profile 0 (including hidden/multi-picture work), and H.264 with B-frame reorder. Native buffer/Surface pixels, timestamps/EOS, flush/replay and physical Back cleanup pass. Next: other codecs and native MediaPlayer/extractor/audio; see memory for the required real attribution Parcel bridge. Hardware decoder dmabuf import remains unverified on this VM.

## What this is
apklayer runs Android apps on a Linux desktop with real Android userspace and no emulator. The route that matters is the device-library route: the LineageOS 24 tree's
arm64 libraries run under bionic_translation on kernel binderfs; real SurfaceFlinger, servicemanager, our own HALs (gralloc, HWC, permission, virtual display sink) and
HWUI render on the host GPU; the app is a Robolectric JVM (shadows for system services) loading the real native libraries. One SurfaceFlinger virtual display per app,
shown as a Wayland window by `apklayer-shell`. UiBench runs today with GPU rendering, real input, dialogs. The older x86/Rosetta CPU route and a third-party arm64
Robolectric jar are kept as fallbacks. Hosts are not Mesa-specific (no minigbm/Mesa dependency); real refresh rate; no fake stand-ins for hardware.

## Hard rules (the user's, repeated complaints: obey them)
- **Never build in `~/los24`** (OrbStack VM `ubuntu22-x86`, tree `~/los24`, read it via `/Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24`). Never touch/clean its `out/`.
  Edit `aosp/` here, run `scripts/sync-aosp.sh "message"` (mirrors into `~/los24/vendor/apklayer` and commits there), then **ask the user to build the named modules** and wait. Take outputs
  from `out/target/product/generic_arm64/system/lib64/` read-only and install them on the VM (`~/android-arm64/lib64`). Log each requested build in `docs/AOSP-BUILDS.md`.
  New modules need a reason in the commit message and `aosp/hals/README.md` (reuse local code first: `memory.md` "Reference projects").
- **3-second rule:** no `sleep`/poll loops/`timeout` wrappers in your own commands, no `run_in_background` to dodge waiting, nothing left running: end every test with `bin/apklayer stop`
  (+`pkill -9 -x tail`) and check `ps` on the VM. Never `pkill -f` a pattern contained in your own command line (use `[x]pattern`). Never leave a stalled command.
- The user does not test by hand and does not want to be asked to. Verify on the VM with real input (`/tmp/uinput-keys`, compiled from `tests/vm/uinput-keys.c`), SurfaceFlinger
  dumps (`servicetool dump SurfaceFlinger`), logs and `prlctl capture "Ubuntu 24.04 (with Rosetta)" --file x.png` on the Mac (look at the image).
- Smallest change, reuse local code, no redoing finished work. One responsibility per file, no file past ~600 lines. Every deviation from upstream is a thin "apklayer patch" listed in `docs/PATCHES.md`.
- Commit every step. Trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Use they/them for people whose pronouns you do not know.
- The user likes short honest status; they get frustrated by silence, by long stalls, by claims without verification. Say what is verified and what is not.
- Decisions already made (do not reopen): system services stay Robolectric shadows (no system_server) except where a process boundary exists, where real binder is used (hub, PLAN 8c); backlight and
  thermal belong to the host; accounts the Android way; system providers/apps ship on every instance; Bluetooth through a BlueZ bridge scoped by app usage; **CTS strictly last**; x86_64 host libandroid_runtime build deferred until after the tool is public.

## Environment
- VM: `ssh parallels@10.211.55.4` (password `1234`, use `sshpass -p 1234 ssh ...`), Ubuntu arm64 on Parallels, GNOME Wayland, virtio-gpu (virgl; no Vulkan hardware, no Bluetooth). Repo copy `~/apklayer-m1` (sync files by `tar -cf - … | ssh … tar -xf -`). Build host-side C with `ninja -C b <target>` there (the Java host jar target fails on an old unrelated error: compile single classes with javac against `~/arm64-native/aosp/host-classes:sim-classes:Robolectric_all.jar` and copy into `aosp/host-classes`).
- Run the GPU route: `XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE=$HOME/arm64-native bin/apklayer run com.android.test.uibench` (starts services via `scripts/arm64-stack.sh none`, a virtual display sink, the arm64 JVM; environment in `scripts/native-env.sh`; `APKLAYER_RUNTIME_DEBUG=1` for native logs; stack logs in `/tmp/apklayer-stack/`). Stop: `bin/apklayer stop`.
- Regression test: `tests/vm/gpu.sh`. The Robolectric patches are in `runtime/patches/` (compile into `~/arm64-native/native-runtime.jar`, see `scripts/build-native-arm64.sh`); the shim is `bridge/native/runtime_shim.c`; host HAL libs `bridge/hals/*`, shell `bridge/shell/*`.
- Pitfalls learned: the property store is write-once (`ro.*`), restart the stack per test unless fixed; the host display HAL's callbacks struct is ABI-shared with the already-built composer (add setters, not fields); a `cat` of a missing file produces an empty library on the VM; `prlctl capture` is reliable now; gdb needs `sudo`; Robolectric's `@Direct` on natives returns a stub 0 (call `$$robo$$name$nativeBinding`).

## Milestones, in order (each: implement, verify on the VM automatically, commit, update PLAN/memory)
**A. GPU route fixes** (PLAN "P7.1 GPU route: next steps" items 1-7):
 1. Crash when resizing the window while a dialog is open (window disappears, JVM idle, main thread stuck in `HardwareRenderer.nSetStopped`): diagnose (thread dumps, sink log, buffer starvation) and fix; add to `tests/vm/gpu.sh`.
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
**G. Vulkan** (PLAN P7.5): loader layer over the host's Vulkan, dmabuf swapchain; needs a machine with a real GPU driver for the sync parts.
**H. CTS** (PLAN P10): strictly last; the relevant app-facing modules, a tracked pass number, failures triaged into gaps (fixed) and device-behaviour tests (listed with reasons).
Deferred beyond H: the x86_64 host libandroid_runtime build and the public release (Flatpak, PLAN P8), instance multiplication for full phones (PLAN P9), SystemUI as a yardstick.

## Working loop
Pick the next unfinished milestone item, state the plan in two lines, implement the smallest change, sync Android-side edits and request the exact modules to build (batch several fixes per build), install, run on the VM, verify with real input and an image/log, commit, update `docs/PLAN.md` + `memory.md`, continue. Keep the user informed in short messages when something is verified or blocked, never silently.
