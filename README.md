# apklayer

Run Android APKs natively on Linux. No emulator, no VM, no Linux-inside-Linux.

apklayer boots the **genuine AOSP stack** — ART, `framework.jar`, `libandroid_runtime`,
`libhwui` — directly on a normal Linux distro, and bridges only the host boundary:
windowing/input (Wayland/X11), GPU (Vulkan/GL via Mesa), audio (PipeWire).

The design deliberately mirrors [Mac.apk](https://github.com/kksimp/Mac.apk), which does the
same on macOS: the Android framework stays **as-is in Java** (patches are thin overlays, never
forks), and everything host-specific lives in one small native bridge.

## Status

`M0` recon → `M1` app_process on bare Linux (see `goal.md`).

## Layout

```
bridge/     thin native host bridge (C++) — windowing, input, GL, audio
cli/        `apklayer` launcher CLI (C++ or Rust, TBD)
runtime/    runtime packaging: assemble prebuilt AOSP artifacts + thin patches/
  patches/  thin overlays over AOSP sources (Mac.apk java_base_patch style) — kept minimal
tests/      APK test apps + on-VM test harness
docs/       architecture + decision notes
```

AOSP itself is **not vendored and never built here** (user rule): `runtime/` assembles
**prebuilt** AOSP artifacts; the los24 tree is a read-only reference for copying code.
We write glue, not frameworks.

## Build

Meson builds apklayer's own glue only:

```sh
meson setup build && meson compile -C build
```

## License

TBD (upstream AOSP is Apache-2.0; Mac.apk is AGPL-3.0 — decide before shipping).
