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
4. **apklayer's own host layer**: `bridge/` (Wayland presenter + input). Driver: `tests/rob`.
5. Earlier stacks (hand-grown seed, Mac.apk `java_mac`) live in `archive/`, reference only.

## Hard rules (user)

- Never stop until the goal is reached; report actively, but do not pause.
- Copy from AOSP/Robolectric rather than write new code; write less.
- Thin patches only, marked "apklayer patch", logged in docs/PATCHES.md (currently none).
- Binder: a userspace driver-like component is acceptable.
- GUI target: Wayland; test in the Parallels Ubuntu VM (ssh parallels@10.211.55.4).
- Do NOT build AOSP (anywhere); los24 is a source/prebuilt reference only.
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
| M6 | Any pure-Java APK via `apklayer run app.apk` (Robolectric Simulator, no JUnit) | **next** |
| M7 | Modern framework (SDK 35/36 android-all), resize/HiDPI, persistent app data | pending |
| M8 | Native `.so` in APKs (bionic-on-glibc loader) | pending |
| M9 | Platform services (WebView, media, portals, notifications), binder-lite | pending |

Details and options for M6+: **docs/ROADMAP.md**.

## Definition of done (v1)

`uibench.apk` launches under apklayer on the Wayland desktop: real View tree drawn,
visible and interactive. **Met 2026-10-25** (user tested clicks on the VM).
Next definition of done (v2): an arbitrary pure-Java APK opens via `apklayer run <apk>`.
