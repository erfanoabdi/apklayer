# apklayer — Goal

**Mission:** Run Android APKs natively on Linux. No emulator, no VM, no Linux-inside-Linux.
First target app: **`tests/uibench/uibench.apk`, full UI**, rendered to a real Wayland window
and interactive (reached: M5).

## Long-term vision (user, 2026-10-26)

1. **Real Android underneath, no emulator.** Android's own userspace (framework, native libraries, SurfaceFlinger, binder,
   system services) runs on the Linux desktop; the hardware layer is apklayer's: HALs for graphics, camera, GNSS, vibrator,
   sensors and more, backed by the host's standard interfaces (GBM/EGL/Wayland, PipeWire/V4L2, GeoClue, evdev, IIO) and never
   tied to one GPU vendor. Rendering is GPU zero-copy (dmabuf end to end), paced by the real display's refresh rate.
2. **Whole phones as instances.** An instance is a complete virtual phone: its own service manager, SurfaceFlinger, HALs,
   system properties, storage and accounts, running as ordinary processes (no VM, no container image). Several instances run
   side by side and are isolated from each other and from the host, so the same app can run twice, each copy with its own data
   and login. A single-app instance (today's `apklayer run <apk>`) is the small case; a full phone is the large one.
3. **SystemUI as a yardstick, not a goal (user, 2026-10-26).** Running the SystemUI APK is not something we build toward; it is
   something it would be cool to be able to do, and a proof of breadth: SystemUI touches nearly every part of the platform API,
   so if it runs, almost any ordinary app will. It is used as a late stress test of compatibility, never to decide a design.
   System services stay **Robolectric shadows** (user: "we don't need them to be real, even for SystemUI").

4. **Proof of compatibility: the relevant CTS tests (user, 2026-10-26).** At the end, apklayer is measured against Android's own
   Compatibility Test Suite, the app-facing modules (views, widgets, graphics, text, content, os, media, and the like), run with the
   shadow-based system, so "compatible" is a number from the real tests and not a claim. Failures are triaged into real gaps
   (fixed) and tests that assert a real device's system behaviour (listed with the reason).

## Method (user directive, 2026-10-25)

**The Robolectric method.** Do NOT build on Mac.apk's hand-patched framework snapshot,
because manually patched framework files are unmaintainable across Android version updates.
Instead:

1. **Unmodified AOSP compiled to JVM**: `org.robolectric:android-all-instrumented`.
2. **Robolectric shadows + sandbox** (MIT) for the parts that need host bindings, plus
   Robolectric's NATIVE graphics runtime (real HWUI/Skia).
3. **dex2jvm** (`runtime/dex2jvm`, from Mac.apk, AGPL) converts app DEX → JVM classes,
   against the android-all class hierarchy; app classes stamped `@DoNotInstrument`.
4. **apklayer's own host layer**: `bridge/shell/` (Wayland presenter + input). Driver: `runtime/host/`.
5. Earlier stacks (hand-grown seed, Mac.apk `java_mac`) live in `archive/`, reference only.

## Hard rules (user)

- Never stop until the goal is reached; report actively, but do not pause.
- Copy from AOSP/Robolectric rather than write new code; write less.
- Thin patches only, marked "apklayer patch", logged in docs/PATCHES.md.
- Session rules (reuse local sources first, small edits, architecture, testing style) live in `CLAUDE.md`; keep it in sync with this list.
- Binder: a userspace driver-like component is acceptable.
- GUI target: Wayland; test in the Parallels Ubuntu VM (ssh parallels@10.211.55.4).
- AOSP: the user built LineageOS 24 for apklayer in OrbStack (`~/los24`, `breakfast gsi_arm64 userdebug`,
  its own `out/`). Build the libs/targets we need there (log each in docs/AOSP-BUILDS.md); do not rebuild
  what `out/` already has. Otherwise los24 is the source/prebuilt reference.
- Prefer local artifacts over network downloads.
- Git: commit at every step.

## Milestones

| # | Milestone | Status |
|---|---|---|
| M0 | Sources & tooling recon (Mac.apk, ATL, los24, VM) | done |
| M1 | AOSP code runs on a stock JVM (message loop proven) | done (archived seed) |
| M2 | Real app Activity constructs against a JVM framework | done (archived java_mac era) |
| M3 | Robolectric pipeline end-to-end on the VM | done: real PackageParser via AGP `test_config.properties`, app theme resolves |
| M4 | uibench full UI on the Wayland window | done: NATIVE graphics (real HWUI/Skia), ~43 fps |
| M5 | Interactive: pointer/keyboard, activity back stack, dialogs | done: clicks, drag, Esc/Backspace = Back, dialogs composited |
| M6 | P1: product foundation: `apklayer run any.apk` on SDK 36, Simulator host (no JUnit), shell protocol, CLI, install cache | **in progress**: SDK 36 ✅, deps lock ✅, Simulator host ✅, shell protocol ✅, Meson ✅, CLI + install + persistent data + menu entries ✅; exit test: 2 extra apps pass headless (Mac), VM run pending |
| M7 | P2: desktop-grade window and input (resize, HiDPI, wheel, touch, IME, clipboard) | **done** (2026-10-26): see docs/PLAN.md P2 status; open caveats: 2x output and a real IME not exercised |
| — | P3: compatibility corpus (30 F-Droid APKs), continuous from here | **in progress**: 17 T1 apps pinned, 7/17 pass; exit test (90% T1) not met, fix loop continues in the background |
| M8 | P4: native `.so` in APKs (bionic_translation + NDK shims → Mesa/PipeWire) | **in progress**: CPU-Z loads its real native library (bionic linker + JNI loader, x86_64 under Rosetta); NDK shims log ✅, android ✅, EGL/GLES ✅ (bundled Mesa llvmpipe); pixel delivery to the shell, audio, vulkan pending |
| M9 | P5: platform services (WebView→WPE, media→GStreamer, portals, microG) | pending |
| M10 | P6: multiple apps (apklayerd, cross-app intents/binder-lite, bwrap) | pending |
| M11 | P7: device-library route: the tree's arm64 Android libraries on bionic_translation (no Rosetta), own SurfaceFlinger stack with GPU zero-copy rendering (PLAN P7.1) | **in progress**: native runtime loads, app starts on arm64 (P7.0); servicemanager runs on kernel binder (P7.1); SurfaceFlinger, gralloc and HWC next |
| M12 | P8 (ship): Flatpak for x86_64 + arm64, desktop integration, performance budget | pending |
| M13 | Vision phases (PLAN P8: SystemUI as a yardstick; P9: several phone instances at once) | not scheduled |
| M14 | P10: relevant CTS tests run and tracked (the compatibility proof) | not scheduled |

Decisions, phases, exit tests and the immediate task list: **docs/PLAN.md** (plan of record).

## Definition of done (v1)

`uibench.apk` launches under apklayer on the Wayland desktop: real View tree drawn,
visible and interactive. **Met 2026-10-25** (user tested clicks on the VM).
Next definition of done (v2): an arbitrary pure-Java APK opens via `apklayer run <apk>`.
