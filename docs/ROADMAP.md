# apklayer — Roadmap to a full Android APK opener on Linux

Today (M5): one known app (uibench, pure Java, SDK 27 framework) runs with full UI and
mouse/keyboard input in a Wayland window. Phases below are ordered by dependency. Each
phase lists the approach that fits the user's rules: copy Robolectric/AOSP, avoid hand
patches, no AOSP builds without explicit approval.

## Phase 1 — Any pure-Java APK, launched like an app (next)
1. **Drop the JUnit harness.** Adopt Robolectric's own **Simulator** module (MIT, in
   `~/GitHub/robolectric/simulator`). It provides `SandboxBuilder` (sandbox without JUnit),
   `AppLoader`, a `FrameListener` hook (plug the Wayland presenter in) and
   `MouseHandler`/`KeyboardHandler`. It replaces most of `RunUiBench`.
2. **Generic launch.** Read the launcher activity from the APK (`PackageParser` already runs).
   Remove the uibench hard-codes. CLI: `apklayer run app.apk`.
3. **Install cache.** `~/.local/share/apklayer/<pkg>/`: converted jar keyed by APK hash +
   android-all id, so launches skip dex2jvm.
4. **Persistent app data.** Point Robolectric's data/cache/files dirs at the per-app dir, so
   SharedPreferences, SQLite and files survive restarts (today they're temp dirs).
5. **Window behaviour.** Resize (xdg configure → `RuntimeEnvironment.setQualifiers` /
   display metrics), HiDPI (wl_output scale → density), app label as the title, rotation.
6. **Input completeness.** Mouse wheel → `ACTION_SCROLL`, wl_touch multi-touch, xkbcommon
   keymap → full `KeyEvent`s, text input through `InputConnection` (IME), clipboard.
7. **Frame pacing.** Redraw only when the view tree is dirty, paced by `wl_surface.frame`
   callbacks instead of a fixed 16 ms loop. This cuts CPU use sharply.
8. **Compatibility corpus.** A batch of F-Droid APKs with an automated launch → screenshot →
   "no crash" smoke run (the `-Dapklayer.script`/snapshot hooks already exist).

## Phase 2 — Modern framework
- **Move to SDK 35/36 `android-all`.** Modern apps target 34+, and current AndroidX needs a
  newer framework. From SDK 35 on, the native graphics runtime ships *inside* android-all.
  This is a jar swap plus re-running dex2jvm against the new hierarchy, not a patch effort.
- **Multi-SDK.** Pick the android-all closest to the app's `targetSdk`.

## Phase 3 — Native code in APKs (largest technical gap)
Many apps ship `lib/arm64-v8a/*.so` (games, Flutter, React Native, media). These are bionic
binaries, and our JVM runs on glibc.
- **Option A:** a bionic-on-glibc loader shim (the libhybris / Android Translation Layer
  approach) so `System.loadLibrary` loads APK `.so` files into the JVM. JNI then works
  unchanged.
- **Option B:** run on ART instead of HotSpot. That's a much larger change.
- On x86_64 hosts, arm64 libraries additionally need translation (berberis / box64 /
  Mac.apk's translator).

## Phase 4 — Platform services
Robolectric's shadows are test fakes. A daily-driver opener needs real host backends:

| Android | Linux backend |
|---|---|
| WebView (shadow = stub) | WebKitGTK or CEF bridged into a View |
| MediaPlayer / ExoPlayer / AudioTrack | GStreamer or FFmpeg + PipeWire |
| Camera | PipeWire / V4L2 (xdg-desktop-portal) |
| Notifications | freedesktop notifications |
| Intents to outside (`VIEW http…`, `SEND`) | `xdg-open`, portals |
| File picker / SAF | xdg-desktop-portal FileChooser |
| Location, sensors | GeoClue, iio-sensor-proxy |
| Google Play Services | microG |
| Cross-app (ContentProvider, bound services) | binder-lite between app processes |

## Phase 5 — Performance and GPU
- **GPU HWUI.** Today it's Skia raster → Bitmap → copy → wl_shm. Move to HWUI on GL/Vulkan
  through Mesa EGL and present by dmabuf. Real EGL/GLES for `GLSurfaceView`/`TextureView`
  apps (games): Mesa speaks the same GLES ABI on Linux.
- **ARM64 hosts without Rosetta.** This needs `librobolectric-nativeruntime` /
  `libandroid_runtime` for linux-arm64. Robolectric doesn't publish it, and building it means
  compiling AOSP native host libraries. **User decision required** (current rule: no AOSP
  builds). On x86_64 Linux hosts the published prebuilt runs natively.
- **Startup.** Converted-jar cache (Phase 1), a JVM CDS archive, and preinstrumented
  android-all jars (Robolectric ships these).

## Phase 6 — Product
- Desktop integration: `.desktop` entry + MIME handler ("Open with apklayer"), app icons,
  install/uninstall, a launcher list.
- One process per app, sandboxed with bubblewrap/Flatpak portals.
- Packaging: Flatpak / .deb with the JDK + android-all bundled.
- Licensing: dex2jvm is AGPL-3.0. Either accept AGPL for apklayer, or replace it with an
  Apache-licensed converter (e.g. dex2jar's approach) or our own.
