# apklayer — Goal

**Mission:** Run Android APKs natively on Linux. No emulator, no VM, no Linux-inside-Linux.
First target app: **`tests/uibench/uibench.apk`, full UI**, rendered to a real Wayland window
and interactive (reached: M5).

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
- Thin patches only, marked "apklayer patch", logged in docs/PATCHES.md (currently none).
- Binder: a userspace driver-like component is acceptable.
- GUI target: Wayland; test in the Parallels Ubuntu VM (ssh parallels@10.211.55.4).
- AOSP builds only when absolutely necessary, locally on the OrbStack VM, with a separate
  OUT_DIR (never los24's `out/`); each one is justified in docs/AOSP-BUILDS.md. Otherwise
  los24 is a source/prebuilt reference.
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
| — | P3: compatibility corpus (30 F-Droid APKs), continuous from here | pending |
| M8 | P4: native `.so` in APKs (bionic_translation + NDK shims → Mesa/PipeWire) | pending |
| M9 | P5: platform services (WebView→WPE, media→GStreamer, portals, microG) | pending |
| M10 | P6: multiple apps (apklayerd, cross-app intents/binder-lite, bwrap) | pending |
| M11 | P7: own native runtime (one Soong host build in OrbStack → compdb → Meson for x86_64 + arm64) → native arm64 + GPU HWUI | pending |
| M12 | P8: ship (Flatpak for x86_64 + arm64, desktop integration, performance budget) | pending |

Decisions, phases, exit tests and the immediate task list: **docs/PLAN.md** (plan of record).

## Definition of done (v1)

`uibench.apk` launches under apklayer on the Wayland desktop: real View tree drawn,
visible and interactive. **Met 2026-10-25** (user tested clicks on the VM).
Next definition of done (v2): an arbitrary pure-Java APK opens via `apklayer run <apk>`.
