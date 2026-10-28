# apklayer: goal

**Run Android APKs natively on Linux**: real Android underneath, no emulator, no VM, no Linux-inside-Linux.

## Vision
1. **Real Android, apklayer hardware layer.** Android's own userspace (framework, native libraries, SurfaceFlinger, binder) runs on the desktop; HALs (graphics, audio, media,
   later camera, GNSS, sensors, vibrator...) are backed by the host's standard interfaces and are never tied to one GPU vendor. Rendering is dmabuf zero-copy at the real refresh rate;
   Vulkan is a goal too.
2. **Whole phones as instances** (deferred): own servicemanager, SurfaceFlinger, properties, storage; several side by side. A single app is the small case.
3. **SystemUI as a yardstick**, not a goal: if it runs, almost any app does. System services stay Robolectric shadows.
4. **CTS as the proof of compatibility**, strictly last: the app-facing modules, failures triaged into gaps (fixed) and real-device assertions (listed).

## Method
Unmodified AOSP compiled to JVM (`android-all`) + Robolectric shadows; dex2jvm for app DEX; Android's device libraries under bionic_translation; apklayer's own HALs and Wayland shell.
No hand-patched framework copies. Thin patches only, listed in `docs/PATCHES.md`.

## Definition of done
v1 (met): UiBench opens on the Wayland desktop and is interactive. v2: an arbitrary APK opens with `apklayer run <apk>`, measured by the compatibility corpus (see `STATUS.md`, next task).
Rules for contributors and agents: `CLAUDE.md`.
