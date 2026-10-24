# apklayer — Goal

**Mission:** Run Android APKs natively on Linux — no emulator, no VM, no container — by keeping
AOSP itself as intact as possible and bridging only the host boundary.

## Principles (non-negotiable)
1. **AOSP stays AOSP.** Real ART, real `framework.jar` (Java), real `libandroid_runtime`,
   `libhwui` — patched as little as possible, via thin overlays (Mac.apk `java_base_patch`
   style), never forks.
2. **Bridge, don't rewrite.** Everything Linux-specific lives in one thin native bridge:
   windowing/input (Wayland/X11), GPU (EGL/Vulkan via Mesa), audio (PipeWire). No ATL-style
   C++ re-implementations of framework classes.
3. **Never build AOSP** (user rule — no time/resources spent on Soong builds). Runtime
   artifacts (ART, boot jars, framework.jar, native libs) must come from **prebuilt sources**;
   the los24 tree is read-only reference for copying code, and its `out/` is off-limits.
   → Pending user decision: ATL release prebuilts (x86_64) vs. another source for arm64.
4. **Write minimal code, copy maximum AOSP.** Stack for our own glue: C++/Rust/Java + Meson.
5. **ARM64 first.** The test VM is aarch64, matching Android's primary ABI (arm64-v8a) —
   native execution of app native libs, no binary translation.
6. **Every milestone is verified on the Parallels VM** (Ubuntu 24.04 ARM64,
   `parallels@10.211.55.4`) with a real APK, not just unit tests.

## Milestones
- **M0 — Recon (read-only).** Map exactly how Mac.apk boots: unpacker → HotSpot/ART +
  loader jar (`com.macapk.loader.Boot.main`) → app_process group → window/GL bridge. Extract
  the minimum artifact set an APK needs at runtime. Decide artifact source **with the user**
  (no AOSP builds).
- **M1 — app_process on bare Linux.** A prebuilt ART + boot classpath running inside the
  Ubuntu VM (outside any Android system image), executing a trivial test: load a DEX, use
  `android.util.Log` and a Handler/Looper. Proof that AOSP userspace runs on a vanilla Linux
  kernel with minimal glue.
- **M2 — First window.** Render one app: bridge HWUI/SurfaceFlinger output to a real
  Wayland/X11 surface (start with shared-memory/BLAST-style buffers → `wl_surface`; GL via
  EGL on Mesa). A stock test APK shows a window, receives touches, buttons work.
- **M3 — APK pipeline.** `apklayer install app.apk` → unpack, manifest parse, data dirs,
  launch via our app_process, lifecycle, back gesture, clipboard, IME, audio output.
- **M4 — Compatibility & polish.** Multi-window/instances, notifications (libnotify),
  permissions UX, small launcher (GTK/Qt), CI running the test app on the VM.

## Definition of done (v1)
`apklayer run some.apk` on a normal ARM64 Linux desktop → the app opens as a normal Linux
window, renders through the host GPU, takes input, plays audio, persists data — running the
genuine AOSP framework on genuine ART.

## Working agreements
- References: `/Users/erfanabdi/GitHub/Mac.apk` (how), `/Users/erfanabdi/GitHub/android_translation_layer`
  (what NOT to do + possible prebuilts), los24 tree via OrbStack (read-only source reference).
- Keep `memory.md` updated with facts; keep this file updated with milestone progress.
