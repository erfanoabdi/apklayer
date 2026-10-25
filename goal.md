# apklayer — Goal

**Mission:** Run Android APKs natively on Linux — no emulator, no VM, no
Linux-inside-Linux. Target test app: **`tests/uibench/uibench.apk`, full UI**
(rendered to a real Wayland window).

## Method (user directive, 2026-10-25)

**The Robolectric method.** Do NOT build on Mac.apk's hand-patched framework
snapshot — manually patched framework files are unmaintainable across Android
version updates. Instead:

1. **Unmodified AOSP compiled to JVM** — `org.robolectric:android-all-instrumented`
   (MIT tooling, real framework code, no hand patches). Android version updates
   stay replicable because nothing is hand-edited.
2. **Robolectric shadows + sandbox** (MIT) for the parts that need host bindings.
3. **dex2jvm** (`runtime/dex2jvm`, from Mac.apk, AGPL) converts app DEX -> JVM classes.
   App classes are stamped `@DoNotInstrument` (their ctor lowering collides with
   Robolectric's `<init>` renaming).
4. **apklayer's own host layer**: `bridge/src/jni/present.c` -> `libapklayer-wl.so`
   (Wayland xdg_toplevel + wl_shm). Frames: real View tree -> Canvas -> Bitmap ->
   `int[]` -> Wayland. Driver: `tests/rob` (RunUiBench + HostWindow).
5. The old Mac.apk stack (`runtime/java_mac`, Boot driver, NativeCocoa bridge) is
   **reference only** — retired as a runtime base.

## Hard rules (user)

- Never stop until the goal (full uibench UI); report, but do not pause.
- Copy from AOSP/Robolectric rather than write new code; write less.
- Thin patches only, marked "apklayer patch", logged in docs/PATCHES.md.
- Binder: a userspace driver-like component is acceptable.
- GUI target: Wayland; test in the Parallels Ubuntu VM (ssh parallels@10.211.55.4).
- Do NOT build AOSP in OrbStack; los24 is a source reference only.
- Git: commit at every step.

## Milestones

| # | Milestone | Status |
|---|---|---|
| M0 | Sources & tooling recon (Mac.apk, ATL, los24, VM) | done |
| M1 | AOSP code runs on a stock JVM (message loop proven) | done |
| M2 | Real app Activity constructs against a JVM framework | done (java_mac era) |
| M3 | **Robolectric pipeline end-to-end on VM** (android-all 8.1.0 + shadows + dex2jvm app + Wayland presenter) | done — real PackageParser via AGP `test_config.properties`; MainActivity resumes with its AppCompat theme |
| M4 | uibench renders frames to the Wayland window (full UI) | **done (render)** — NATIVE graphics (real HWUI/Skia), ~43 fps to Wayland; x86_64 JVM under Rosetta + shared fb -> native arm64 `apklayer-presentd` |
| M5 | Input (touch/keyboard), resize, lifecycle; second APK | pending |
| M6 | Binder-lite + ContentProvider subset; system-service fakes | pending |

## Definition of done (v1)

`uibench.apk` launches under apklayer on the Wayland desktop: real View tree
drawn, visible and interactive.
