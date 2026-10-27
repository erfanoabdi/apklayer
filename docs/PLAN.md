# apklayer — Plan of Record

Status at writing (2026-10-25, revised the same day: D8, D9, D17, P7): **M5 done**. uibench runs with full UI and mouse/keyboard
input in a Wayland window (docs/ARCHITECTURE.md). This document is the single plan from
here to a full Android APK opener on Linux. Every decision below is final unless new
evidence breaks it. If that happens, change this file in the same commit as the code.

---

## 1. Decisions

| # | Topic | Decision | Reason |
|---|---|---|---|
| D1 | Framework | **Robolectric `android-all-instrumented` SDK 36** (Android 16, `16-robolectric-13921718-i7`). Drop SDK 27. Run every app on SDK 36 regardless of its `targetSdk`, as real devices do. | Modern apps and AndroidX need it. Matches the los24 source reference (Android 16). From SDK 35 on, the native graphics runtime ships inside the jar. One SDK means one thing to make solid. |
| D2 | Robolectric | **Pin to the newest Robolectric release that supports SDK 36 and contains the `simulator` module.** Coordinates and sha256 recorded in `deps/deps.lock`. | A single pinned version. The Simulator replaces our JUnit harness. |
| D3 | App host | **Use Robolectric's Simulator** (`org.robolectric:simulator:4.17`, MIT) from the published jar, unmodified (it is on Maven, so no vendored copy). Add apklayer classes beside it: a Wayland `FrameListener`, an input source, and the activity stack. **No JUnit anywhere in the product.** | Copy, don't write. The Simulator is exactly "run an app outside tests". |
| D4 | DEX conversion | **Keep dex2jvm** (upstream kksimp/dex2jvm, Apache-2.0), unmodified, plus our own `HostDefaults.java`. Always run it with the target android-all on its classpath. Cache the output by `sha256(apk) + android-all id + dex2jvm commit`. | It works on real apps today. Replacing it gains nothing for users. |
| D5 | License | **apklayer is AGPL-3.0-or-later for everyone, plus a paid commercial license for companies that do not want the AGPL terms** (decided 2026-10-28; `LICENSE`, `COMMERCIAL.md`). | Distributable through distro channels (deb/rpm/AUR/Flatpak/AppImage) under the AGPL; commercial and product use is licensed. Compatible with Apache-2.0 (AOSP, dex2jvm) and MIT (Robolectric). Needs sole copyright of our own code (CLA for contributions). |
| D6 | Process model | **One JVM per app.** All windows go to a single per-session native **`apklayer-shell`** (done in P1.6). App JVMs contain **no apklayer native code** until D9. | Crash isolation. App JVM arch can differ from the host's. One component owns Wayland. |
| D7 | Frame transport | Per window: a pixel file in `/dev/shm` (double-buffered, damage rect in header). Control, input and wakeups go over a **Unix domain socket** (pure-Java `SocketChannel` on the app side, no polling). | Removes busy polling and tearing. Pure Java in the app process. |
| D8 | Rendering | **Now:** CPU HWUI/Skia from the Robolectric prebuilt; present only damaged regions. **Later (P7):** apklayer's **own Meson build of AOSP's native runtime** (HWUI, Skia, minikin, androidfw, the `libandroid_runtime` JNI subset) from los24 sources, with HWUI's GPU pipelines enabled (`EglManager` + `SkiaOpenGLPipeline` on Mesa EGL/Wayland; `SkiaVulkanPipeline` on Mesa Vulkan). Its source/flag lists come from **one Soong host build in OrbStack** (see P7). | The prebuilt is AOSP's *host* HWUI variant, which compiles only `platform/host` stubs and excludes `SkiaOpenGLPipeline`, `SkiaVulkanPipeline`, `EglManager` and `VulkanManager` (frameworks/base/libs/hwui/Android.bp). GPU HWUI on Linux therefore means compiling HWUI's device sources for Linux ourselves. The same build gives native linux-arm64 (D17). User rule (2026-10-25): AOSP builds are OK **only when absolutely necessary**, run locally on the OrbStack VM. |
| D9 | Native `.so` in APKs | **ATL's `bionic_translation`**, not libhybris. It loads bionic-linked app `.so` files into a glibc process without an Android system image, builds with Meson, supports x86_64 and aarch64, and is used daily by ATL on real apps. libhybris solves the reverse problem: glibc userlands using Android *vendor HAL/GPU blobs* on Android-kernel phones (Halium: Sailfish, Ubuntu Touch, Droidian). It expects the device's Android `/system`+`/vendor` libraries and focuses on HAL wrappers (gralloc, hwcomposer, camera, sensors). We have no system image, and app NDK libs only need the stable NDK ABI. Alongside it go apklayer **NDK shims** in `bridge/ndk/`: `liblog`, `libandroid` (AAsset*, ANativeWindow*, AConfiguration*), `libEGL`/`libGLESv*` → Mesa, `libOpenSLES`/`libaaudio` → PipeWire. Load the APK's ABI that matches the **app JVM's arch**. No ISA translation. | The proven approach, already built with Meson. JNI is the same standard ABI under HotSpot. APKs almost always ship both arm64-v8a and x86_64. |
| D10 | GL/Vulkan in apps | `GLSurfaceView`, `SurfaceView` and `TextureView` get a real **EGL surface on a `wl_subsurface`** placed at the view's screen rect, rendered by **Mesa** directly. Vulkan goes the same way via the Mesa Vulkan WSI. | Games and video need real GPU output. Mesa speaks the same GLES/EGL/Vulkan as Android drivers. |
| D11 | WebView | **WPE WebKit** (`wpe-webkit-2.0` + `wpebackend-fdo` SHM export), rendered into the `WebView`'s canvas by an apklayer shadow of `android.webkit.WebView`. | WPE is built for embedding into a custom renderer, which matches our bitmap composition. WebKitGTK (ATL's choice) is a GTK widget, which doesn't. |
| D12 | Media / audio | **GStreamer** for everything: `MediaPlayer`, `MediaCodec`/`MediaExtractor` (codec bins), `AudioTrack`/`SoundPool` (appsrc → `pipewiresink`). `ExoPlayer` works through MediaCodec. | One dependency, already on the VM, covers codecs and PipeWire. |
| D13 | Host services | **xdg-desktop-portal** for everything sandbox-relevant: file chooser (SAF `ACTION_OPEN_DOCUMENT`), `ACTION_VIEW` links (OpenURI), camera, notifications, location. Clipboard via `wl_data_device` in the shell. | Works inside Flatpak (D16) and on every desktop. |
| D14 | Google services | **microG** (GmsCore + GsfProxy) as a preinstalled, bundled APK running under apklayer itself. | The only redistributable option. Apps bind to it like on a degoogled phone. |
| D15 | Cross-app | A tiny **`apklayerd`** session daemon (Java, same Robolectric stack) holds the installed-package registry. It routes implicit intents, `bindService` and `ContentResolver` calls between app JVMs over Unix sockets, carrying real **`Parcel` bytes**. No kernel binder. | User-approved "userspace binder-lite". Parcel bytes keep AIDL stubs unmodified. |
| D16 | Distribution | **Flatpak** (`org.freedesktop.Platform` 24.08, openjdk21 extension; android-all and Robolectric jars inside). A `.deb` later. | Portals and sandboxing for free, and the runtime is the same on every distro. |
| D17 | Host platforms | **Both x86_64 and arm64 Linux are first-class, fully native targets.** x86_64 works natively from P1, using Robolectric's prebuilt native runtime. Native arm64 arrives in **P7** with apklayer's own native-runtime build (D8). Rosetta/FEX are **not** a product path: the x86_64-JVM-under-Rosetta setup on the Parallels VM is only a development stopgap until P7, and is removed after. | Robolectric publishes no linux-arm64 native runtime, so arm64 needs our own build of the same AOSP native libs. That is one piece of work shared with GPU HWUI (D8). |
| D18 | Build | **Meson for everything.** C targets (shell, NDK shims) plus Java via Meson `jar()` targets (host, dex2jvm, stubs). Dependencies come from `deps/deps.lock`, fetched once by `scripts/fetch-deps.sh` into `~/.cache/apklayer/deps`. Nothing large goes in git. | User preference (Meson). One build. Reproducible, offline after the first fetch. |
| D19 | CLI | `apklayer install <apk\|apks\|xapk>`, `apklayer run <pkg\|apk>`, `apklayer list`, `apklayer uninstall <pkg>`. Install converts and caches the DEX, extracts the icon by rendering the launcher icon drawable with the framework, and writes a `.desktop` entry plus an `application/vnd.android.package-archive` MIME handler. | It behaves like a native app installer. |
| D20 | App data | Per app `~/.local/share/apklayer/apps/<pkg>/` holding `data/` (Context data, cache and files dirs, databases, shared_prefs), `apk/` and `classes.jar`. External storage is `~/.local/share/apklayer/storage/emulated/0` (Android/data scoped). | Survives restarts. Matches Android's layout. |
| D21 | Split APKs | Accept `.apks`/`.xapk`/`.apkm`. Use the base APK for resources, merge DEX from all splits, and pick ABI/density/locale config splits that match the host. | Most store downloads are splits today. |
| D22 | Fixes to framework behaviour | Only as **apklayer shadows** (`runtime/host/shadows`, Robolectric's own extension mechanism). Never edit android-all, Robolectric or dex2jvm. Every shadow is listed in docs/PATCHES.md. | This keeps the "no hand patches" principle. Shadows are how Robolectric itself adapts AOSP. |
| D24 | AOSP builds | **On the OrbStack `ubuntu22-x86` VM, in the user's LineageOS 24 build tree `~/los24` (`breakfast gsi_arm64 userdebug`, its existing `out/`; user, 2026-10-26: "you can also build libs and targets if you need")**. Each build is listed with its justification in docs/AOSP-BUILDS.md before it runs; build only what is needed. Planned: P7.1 (plus P7.3 if JNI signatures mismatch). | User rule. Soong is the only authoritative resolver of AOSP's native module graph (Skia, HWUI, minikin, ICU, HarfBuzz…); re-deriving it by hand would be thousands of lines of guesswork. |
| D23 | Verification | Every feature lands with a **scripted headless check**: the existing `-Dapklayer.script`/snapshot mechanism, run by `scripts/check.sh` on the Mac (fast) and on the VM (real Wayland). The corpus (P3) gates releases. | The user tests by hand; we must catch regressions first. |

---

## 2. Target architecture

```
                 ┌──────────────────────── apklayer-shell (native, host arch) ─────────────────────────┐
 Wayland ◄──────►│ one xdg_toplevel per app window · wl_subsurfaces for GL/video (D10) · wl_seat input │
 compositor      │ clipboard · decorations · per-window shm buffers · Unix socket server              │
                 └───────▲──────────────────────────────────────────────▲───────────────────────────┘
                         │ shm frames + socket (D7)                      │
 ┌───────────────────────┴───── app JVM (one per app, D6) ─────┐  ┌──────┴──────── apklayerd (D15) ─────┐
 │ android-all SDK 36 (unmodified) + Robolectric shadows        │  │ package registry · intent routing   │
 │ + apklayer shadows (WebView/WPE, media/GStreamer, portals)   │◄►│ bindService / ContentResolver relay │
 │ Simulator host: lifecycle, activity stack, windows, input    │  │ (Parcel bytes over Unix sockets)    │
 │ app classes (dex2jvm, cached) · APK .so via bionic_translation│  └─────────────────────────────────────┘
 └──────────────────────────────────────────────────────────────┘
```

Target repo layout:

```
bridge/shell/        apklayer-shell (C: main, client, window, decor, pointer, keyboard, registry)
bridge/ndk/          NDK shim libraries (C)                         (P4)
runtime/dex2jvm/     unchanged
runtime/host/        Java: simulator/ (vendored, MIT), apklayer/host/ (launcher, Wayland FrameListener,
                     input, activity stack, data dirs), apklayer/shadows/, apklayer/cli/, stubs/
runtime/apklayerd/   Java session daemon                            (P6)
deps/deps.lock       pinned Maven coordinates + sha256
scripts/             fetch-deps.sh, check.sh, vm-run.sh
tests/corpus/        corpus.lock (APK names, versions, sha256), expected snapshots
packaging/flatpak/   manifest                                       (P7)
```

---

## 3. Phases

Each phase ends with its **exit test**. Do not start the next phase until it passes. Commit
at every step. Update memory.md and goal.md when a phase completes.

### P1-P7 summary (full text with every item and status: `docs/history/PLAN-phases-P1-P7.md`)
| Phase | State | One line |
|---|---|---|
| P1 | done | CLI, install cache, Meson, SDK 36, Simulator host, shell protocol |
| P2 | done | resize, HiDPI plumbing, wheel/touch/keyboard, clipboard, lifecycle (2x output and a real IME not exercised) |
| P3 | continuous | F-Droid corpus, 7/17 T1 pass at last count; fix loop ongoing |
| P4 | in progress | native `.so` via bionic_translation, NDK shims (log, android, EGL/GLES) |
| P5 | partial | WebView, media, portals, connectivity, keystore first slices; microG pending |
| P6 | first slice | intent filters + cross-app routing; superseded in practice by the instance hub (item 8c) |
| P7 | in progress | arm64 device-library route, own SurfaceFlinger/HALs, GPU HWUI; live list is "P7.1 GPU route: next steps" below |

### P8 — Ship (M12)

1. A Flatpak manifest (D16) bundling the shell, NDK shims, bionic_translation, WPE WebKit (from
   the GNOME runtime or built), the JDK extension, deps from the lock and microG.
2. App icons and the launcher list appear in the desktop's app grid through exported `.desktop`
   files; double-clicking an `.apk` installs it.
3. Performance budget, as release criteria: cold start ≤ 4 s for a T1 app on x86_64
   (enable a JVM AppCDS archive of android-all + host), idle CPU < 2% (P1.6 damage-driven
   frames), memory ≤ 600 MB per T1 app.
4. A `.deb`, a release notes page, and docs/USER.md.

**Exit test P8:** fresh Ubuntu 24.04 machines, **x86_64 and arm64**: `flatpak install apklayer.flatpak` →
double-click `Markor.apk` → it opens and is usable on both.

---

## 4. Working rules for executing this plan

- Order is strict: P1 → P2 → P3 (then continuous) → P4 → P5 → P6 → P7 → P8.
- One logical step = one commit, with the verification output in the commit message body.
- Never edit upstream code (android-all, Robolectric, simulator, dex2jvm, bionic_translation).
  Adapt through shadows, configuration and post-processing, and log it in docs/PATCHES.md.
- AOSP builds only for what we need, in the user's ~/los24 tree and logged in docs/AOSP-BUILDS.md; local artifacts first, one-time fetches only through `deps/deps.lock`.
- Every phase: update goal.md (milestone status), memory.md (facts learned) and this file
  (if a decision changed, with the reason).
- Report progress to the user at every exit test, with a snapshot.

## 4b. User concerns tracker (never drop these)

| Concern (user) | State |
|---|---|
| Keep working toward the goal without stopping; report progress | ongoing: report at every milestone/exit test |
| Keep the md files (PLAN, memory, goal, ARCHITECTURE, README) current | done at each step; check before every commit |
| File architecture: `shell.c` / `AppHost.java` too big | ✅ 2026-10-26: shell split into 7 modules, host into AppHost/HostInput/HostWindows/ScriptRunner. Watch next: `ShellClient`, `HostWindows`; `Translator.java` (4.6k, vendored from Mac.apk) is deliberately left alone |
| Window decoration (resize, close, minimize, back button) | ✅ |
| Resize by holding the edges, resize cursor | ✅ (cursor appearance depends on the system theme; the VM theme is old) |
| D-pad menu selection and mouse-wheel scrolling (incl. Dialog List) | ✅ fixed (window focus + touch mode; dialog-aware routing) |
| Test efficiency: only run tests for what changed | ✅ group arguments on both test scripts |
| The user does not test by hand: verify on the VM before reporting | ongoing |
| See what happens in the VM (visible in the Parallels window) | ongoing: tests run in the real session |
| No sleeps / no waiting scripts; 3-second rule; no background launches; close everything started | rules in `CLAUDE.md` (NEVER section) |
| Reuse local sources first; small edits; mind maintenance and architecture | rules in `CLAUDE.md`; vendored/own code logged in THIRD_PARTY/PATCHES |
| Do not ask the user to test by hand | ✅ rule in `CLAUDE.md`; features that only a person can judge are listed here as "needs a human check" |
| Revisit: NewPipe video/audio lag | open, see P5 "Revisit list" (measurements recorded) |
| Revisit: desktop notifications annoying | policy ✅ (`tests/vm/notify.sh`); mute/cancel/actions still open |
| Run multiple apps in the sandbox | P6 slice ✅ (cross-app intents + results, `tests/vm/share.sh`); per-app bwrap sandbox blocked by Ubuntu's userns restriction |

## 5. Immediate next tasks (start here)

1. ✅ Add `LICENSE` (AGPL-3.0-or-later) (D5).
2. ✅ Restructure directories (P1.1). Make the existing script work with the new paths and verify
   uibench.
3. ✅ Write `deps/deps.lock` for the current `~/rob` contents (P1.2).
4. ✅ Fetch SDK 36 android-all + the pinned Robolectric once, then switch uibench to SDK 36 (P1.4).
   Result: Robolectric 4.17 already supports SDK 36 and publishes `simulator`, so no version bump was needed.
   The `Build` stub was dropped; `android-stub.jar` = SDK android-36.1; `--add-opens` flags are in `runtime/host/jvm.args`.
5. ✅ Simulator-based host `apklayer.host.Main` (P1.5). The published `simulator-4.17.jar` is used
   unmodified (no vendored source needed); JUnit removed from the product. Port of RunUiBench's stack, window and
   input logic (P1.5).
6. ✅ Shell protocol v1 over a Unix socket with damage-driven frames (P1.6): `apklayer-shell`
   (multi-app, zero-copy shm pools, wl_buffer.release flow), pure-Java `ShellClient`; the JNI
   presenter (libapklayer-wl, WlWindow) and presentd were removed.
7. ✅ Top-level Meson build (P1.3): `meson setup b && ninja -C b` builds apklayer-shell,
   dex2jvm.jar and apklayer-host.jar (jars via `scripts/javac-jar.sh`; the shell is optional where
   Wayland is missing). The Mac quick check (`scripts/check.sh`) uses plain javac on the same
   sources, because the Mac has no Meson.
8. ✅ Data dirs, CLI, install cache, desktop entries (P1.7–P1.9): `bin/apklayer`; data persists
   via links pre-created before the lazy Application; `Main --describe` writes info + icon;
   `.desktop` + MIME handler; config in `~/.config/apklayer/env`. Verified on the VM, including a
   menu-style launch with `gtk-launch`.
9. ◐ P1 exit test: uibench + **Vibrations Test + Open Camera pass headless on the Mac** (local
   APKs from ~/TestApps, no download). On the VM, Vibrations Test runs via `apklayer run`;
   Open Camera on the VM is still to do.
   Five more apps fail with concrete, listed causes (tests/corpus/results.md) that are P3 fix-loop
   items: manifest ContentProviders before Application.onCreate (Firebase), a `getprop` stand-in,
   `Display.getHdrCapabilities`, the MediaPlayer data source, the NewPipe startup NPE.

### P8 — Yardstick: the SystemUI APK (user, 2026-10-26: not a goal, "just something cool"; covering it means almost any app works)
Not scheduled, and it never drives a design decision. A late compatibility stress test: SystemUI uses nearly the whole platform
API, privileged window types and hidden interfaces, so running it shows the shadow layer and the stack are broad enough for
ordinary apps. **System services remain Robolectric shadows (user: "we don't need them to be real, even for SystemUI"); no
`system_server`.** If attempted: extend the shadows for `StatusBarManager`/`IStatusBarService`, status/navigation/keyguard/shade
window types, insets and cutouts, the notification-listener feed, keyguard, wallpaper, input monitors; feed them from the host.
The useful by-product is the list of platform gaps it exposes, each of which also helps ordinary apps.

### P9 — Vision: several phone instances at once (user, 2026-10-26)
Each instance is a full virtual phone made of ordinary host processes: **its own binder domain** (a separate binderfs device
from `binder-control`, later a separate userspace-binder context), its own servicemanager, SurfaceFlinger and HALs, its own
system-property area, its own `/data` (the path layer, step 0.3, maps the Android paths per instance), its own runtime directory
for the shell and vsync sockets, and one process group (cgroup/systemd scope) that can be stopped as a unit. Windows from an
instance are Wayland windows tagged with the instance (title/app-id). The same app can run in two instances with separate
accounts and storage. Isolation between instances and from the host is the P6 sandbox (bwrap/namespaces) applied per instance.
Design rule from now on: nothing in P7 may assume a single instance (no fixed `/dev/binder`, fixed socket names or global
property store; take an instance id/root as a parameter).

### P10 — Compatibility proof: the relevant CTS tests (user, 2026-10-26: "at the end of the goals add running relative CTS tests to make sure our apklayer is really compatible")
**Strictly the last thing we do (user, 2026-10-26: "i wouldn't waste time on CTS this early"): no CTS work before everything else is done.** The measure of "compatible". The tree has the sources (`~/los24/cts/tests/tests/<module>`: `view`, `widget`, `graphics`,
`text`, `content`, `os`, ... about 170 modules). Approach: (1) choose the **app-facing** modules (what ordinary apps depend on:
view, widget, graphics, text, content/resources, os basics, media, input, drawables) and leave out device-system ones (telephony,
SELinux, permissions policy, hardware certification); (2) build each module's test APK incrementally in ~/los24 (logged in
docs/AOSP-BUILDS.md); (3) run it with our own instrumentation runner (AndroidJUnitRunner inside an instance, results in a stable
format) on the shadow-based system; (4) keep a table per module: passed / failed / skipped, with each failure classified as a **gap
to fix** or **asserts real-device behaviour** (reason listed, expected-failure file under `tests/cts/`); (5) track the pass rate
per module over time, run the chosen subset in CI-style scripts on the VM. Exit test: the chosen modules reach an agreed pass
rate with every remaining failure explained. This also feeds P3: every gap found is a gap real apps can hit.

### P7.1 GPU route: next steps (written 2026-10-27, in priority order)
State: UiBench runs on the device-library route through `bin/apklayer run` (services on demand, one SurfaceFlinger virtual display per app, GPU
rendering end to end, real input through the shell's input proxy, dialogs stacked on top). Test: `tests/vm/gpu.sh` (needs the VM and the libs built from `~/los24`).

**2026-10-27 investigation:** fixed stale stack readiness before relaunch; initialized the real RenderEngine cache files and readiness property (avoids its five-second Android-init wait); display windows now map a blank shm frame before GPU output so Wayland can deliver focus/vsync. Cold launches and real clicks into Dialog List verified. Sink buffer release and stale-size frame rejection built and installed; one complete resize/Back/new-size-frame run passed, but repeated runs expose an intermittent bionic_translation mutex assertion and input focus sensitivity. A1 remains open. The dump mutex assertion was traced to missing translated pthread_mutex_timedlock and fixed; static-init/contention/reuse tests and a full dump pass. A separate EGL initialization lifetime bug remains under resize; fix in progress.

1. **✅ Fixed: crash/stall resizing with a dialog open** (verified 2026-10-27). Sink buffers return on compositor release; stale-size frames are dropped. Missing translated timed mutex locking and Android EGL initialization reference counting are fixed. Real Wayland callbacks follow the active mapped surface instead of an occluded primary. `tests/vm/gpu.sh` passes real menu clicks, compositor resize, layer survival, Back dismissal and final-size GPU frames, with before/after screenshots.
2. **✅ Fixed: dialog right margin / bounded frames** (verified 2026-10-27). A window's frame fits the display, while its buffer includes 32px shadow insets on each side. WMS placement now lives in a parent container and subtracts the insets, as `WindowState.transformFrameToSurfacePosition` does; BLAST owns the child’s destination frame and cannot overwrite the parent transform. Oversized requested frames are clamped so ViewRootImpl remeasures them. UiBench screenshots measure symmetric **16px / 16px** margins before and after resize; `gpu-check-image.py` checks them. Containers are removed when windows are removed.
3. **✅ Window mapping and focus** (verified 2026-10-27). Display windows map an initial blank frame using the shell's existing shm mechanism, then GPU dmabufs replace it. AppHost's initial-inactive exception is removed; normal pause/resume handling applies from the start. Cold launch, real input, resize, Back and margin checks pass without the workaround.
4. **✅ Headless primary** (verified 2026-10-27). The composer opens a host timing connection without WINDOW_CREATE, so only app virtual displays create Wayland windows. Refresh period is taken from wl_output's actual mode, not smoothed intervals between on-demand callbacks. Vsync uses real callbacks from the active mapped app surface. The shell reads initial output mode/scale before accepting clients. Smoke tools get their own stack-0 virtual display. Full GPU regression and 12-frame GL resize smoke pass; shell logs confirm no primary toplevel.
5. **✅ Several apps at once in the current Android instance** (verified 2026-10-27). Concurrent launchers share a flock-protected service startup and event-driven ready/death wait. Each launch has a PID-unique layer stack, an instance/launch-tagged title and its own sink/log; long package names cannot collide in the title prefix. Pointer/title hover references are cleared on window teardown. `tests/vm/multi-app.sh` launches two GPU JVMs together, locates their real windows in captures, switches focus by uinput, closes one with real Back, and maximizes/resizes the survivor. It verifies distinct layers/titles, one service stack, continued frames, lifecycle routing and cleanup. Full phone/binder/data isolation remains P9; these apps intentionally share one Android instance.
6. **✅ Text input and clipboard** (verified 2026-10-27). Proxy ids route field enable and IME events to the focused JVM. Clipboard pipes run asynchronously in the shell event loop with per-client echo suppression and immutable outgoing selections. Fixed ShellClient’s empty clipboard payload and the upstream key-character shadow’s handling of Ctrl/Alt/Meta. `tests/vm/input.sh` verifies real typing, host paste with Ctrl+V, Android copy read by wl-paste, actual IBus preedit/Chinese commit and a 20 KB transfer; restores the input source and closes all children.
7. **✅ Runtime/startup cleanup** (verified 2026-10-27). `build-runtime-overlay.sh` propagates compilation failures and atomically publishes a complete jar; forced failure preserves its hash. `build-tree-host.sh` builds all host sources in a fresh directory, adapting the actual ShadowProvider API and reusing the two pinned simulator callback classes absent from the older tree. Full fresh host/overlay compilation passes GPU dialog/resize/Back/margin checks. All ro.* properties are write-once again: system hyphen data is staged once and shared by JVMs. Property isolation/mutability tests and two-app launches pass. Tree install uses its actual framework for conversion/cache identity and its native JVM/environment for metadata/icon rendering (verified hello.input install). Trace flags honor false/0, relocation diagnostics use the existing linker trace facility, service-only startup no longer starts tail. Windowless vsync and the architecture docs are complete. A fully provisioned clean-machine installer remains release work; these checks rebuild host/overlays from scratch against the staged device artifacts.
8. **HALs before anything else after that (user, 2026-10-27): media and camera first, then the other hardware.** Host media backend started: native GStreamer retains YUV samples and exposes real decoder availability/dmabuf metadata. Real VP8 encode/decode at 64x48 and 80x60 passes pixels, PTS, EOS and sample lifetime tests. Initial Codec2 interface and native work/gralloc path now pass tests/vm/codec2.sh (three real VP8 frames, PTS/frame indices/EOS, YUV values). Fixed upstream non-DMA-heap linear mapping for the real BLOB allocator. AIDL discovery and work/buffer transfer now pass through the real Android Codec2Client in another process, including a hidden reference frame; process exit and cleanup pass. Opt-in real MediaCodec JNI now passes APK startup discovery, three decoded VP8 frames via getOutputImage (YUV/PTS), EOS and stop/release, plus HWUI frame commit and physical Back (tests/vm/native-media.sh). Actual AOSP media.player/resource_manager services run alongside Codec2. Native SurfaceView VP8 presentation also passes: real framework holder/BLAST child layers, rendered callbacks, a red-pixel screenshot check and physical Back. The YUV fallback now allocates separate R/RG plane resources; packed R8 imported but sampled incorrectly on virgl. Mapper CPU lock stages contiguous YUV, while SurfaceFlinger imports the plane dma-bufs directly. VP9 profile 0 now shares the adapter and passes native host decode at two sizes, AIDL visible/hidden references and hidden+visible superframes, app MediaCodec YUV/PTS/EOS and SurfaceView presentation. Multiple displayed pictures per input work now use SimpleC2Component.cloneAndSend and FLAG_INCOMPLETE, with EOS only on the final picture; AIDL and native app buffer/Surface tests verify duplicate timestamps and both pictures. Higher-bit-depth VP9 is not advertised. Host H.264 B-frame decoding now passes 12 coded-order inputs into 12 presentation-order YUV outputs with exact timestamps/EOS and asynchronous availability notifications. H.264 Codec2/app integration now also passes: callbacks queue the existing component handler, pending work completes by output PTS, EOS follows all 12 B-frame pictures, Java buffer output survives flush/replay, and SurfaceView renders the final frame with a red-pixel check. Native MediaExtractor callback-source MP4 parsing now passes through a separate real extractor process and original AOSP plugins, including track/CSD, keyframe seek, 12 extracted H.264 samples decoded to YUV, PTS/EOS and release. File-descriptor sources now pass nonzero-offset reads, track/sample/seek, caller ownership and immediate caller close after setDataSource; native MediaPlayer/audio remain next; other codecs/drain cases, hardware decoder dmabuf import and corpus playback remain unfinished; service is not enabled by default. Media: decoders/encoders and `MediaCodec`/`MediaPlayer`
   on the real stack (video buffers through our gralloc, YUV zero-copy into SurfaceFlinger layers, the codec2 HAL over the host's decoders), audio. Camera: the camera
   provider HAL over PipeWire/V4L2 into BufferQueue surfaces. Then gnss, sensors, vibrator and the rest of `aosp/hals/README.md`. Each follows the domain layout
   (`aosp/hals/<domain>` Android side, `bridge/hals/<domain>` host side).
   Native audio host backend verified (2026-10-27): `bridge/hals/audio` queues mapped PipeWire buffers on the owning worker and derives presentation from queued/resampler/device timing. Early process notification and full available buffer capacity avoid quantum gaps from small Android writes. S16 stereo and float mono pass actual isolated monitor PCM frequency/frame checks, delayed position, drain, pause, flush/resume, and teardown (`tests/vm/audio-host.sh`). Requires PipeWire >=1.0.4 for its monotonic clock API. Android AIDL HAL/AudioFlinger/AudioPolicy and MediaPlayer integration remain next; this does not yet replace app audio shadows.

   Native AIDL audio output verified (2026-10-27): original AOSP Module/Config/Stream/FMQ worker with own PipeWire driver, real binder port/patch/stream setup and monitor-captured stereo PCM. Presentation/drain, standby/close and pause/flush/replay (lifetime epoch 50880) pass tests/vm/audio-aidl.sh. Hardware master controls and unsupported modes return errors. Unprivileged transfer worker uses normal priority while PipeWire schedules audio. BT translates genuine mutex priority-inheritance attributes. Opt-in only; actual AudioFlinger/AudioPolicy and app native audio/MediaPlayer remain next.

   Original AudioFlinger/AudioPolicy startup verified (2026-10-27): reuse original service archives, AIDL adapter, default policy engine, effect factory with no staged effect plugins, and original volume XML. Native binder route probe returns 48k/960 frames/45ms. Standalone native activity/privacy clients return actual service absence instead of waiting for system_server. Checked fread ABI fixed for real XML parsing. AudioTrack sample playback and app JNI remain next.

   Native AudioTrack playback verified (2026-10-27): real PID/UID/Binder attribution, original AudioFlinger mixed track, stream volume setup, blocking PCM writes, marker callback, presentation timestamp and stop/flush/release. Isolated PipeWire monitor verifies both channel frequencies and all 48k tone frames (47760 nonzero including zero crossings). Actual silence frames retain the graph through presentation; completion uses the callback, no delay. tests/vm/audio-track.sh passes. Java AudioTrack/MediaPlayer JNI and attribution Parcel integration remain next.

   Scoped native attribution Parcel verified (2026-10-27): original AIDL serializer and real native Parcel, all fields/Unicode/nulls/chains, stable/distinct local Binder identity, scoped and cyclic-error release, Java Parcel reuse. tests/vm/native-audio.sh passes UI frame/physical Back, and native-media regression remains green. Native JNI gets a real pointer; general Binder/Parcel and remote proxies remain for the hub. Java AudioTrack/MediaPlayer integration is next.

   Java AudioTrack JNI verified (2026-10-27): original framework/native PCM, actual scoped attribution and PID/UID, early Application capability query, real policy volume/global session IDs, delayed head/marker/timestamp, stop/flush/release. Isolated monitor captures exact stereo440/880Hz47760 nonzero toneframes; UI commit and physical Back pass. BT monotonic condition deadline regression reproduces three old failures and passes all four API/default-clock combinations after patch12. AudioSystem original initializer runs immediately after initial capability JNI registration. Opt-in only; Java AudioService focus/player bookkeeping and MediaPlayer/general native Parcel packets remain next.

   Native media follow-up (2026-10-27): selective native payload Parcel foundation passes original JNI serialization, coexistence and ownership tests; MediaPlayer is still pending. Java Binder/FD packets and native Java interface enforcement are explicitly unsupported until Binder cache/dispatch integration. Callback timeout root cause identified in tree DeliQueue versus API36 Robolectric legacy scheduler; select AOSP's provided legacy queue mode at static initialization. Fixture confirms actual main-thread marker delivery with deli:false, PCM capture and cleanup.

   Original MediaPlayer video verified (2026-10-27): actual Java/JNI/NuPlayer/Codec2 -> SurfaceView, real FD source caller-close, async prepare, video track/duration/dimensions, pause/resume and playback pacing, native time-discontinuity Parcel timestamps, seek/replay and two completion callbacks. Native callback factory uses original payload data with normal Java recycle/finalization ownership. tests/vm/native-player.sh checks131674 red pixels, real SF layer, release and physical Back. Compressed audio/A-V integration and more source/error cases remain next; exact short-clip getCurrentPosition endpoint semantics still need investigation.

   AAC host backend verified (2026-10-27): original GStreamer native AAC decoding returns actual interleaved PCM through a separate audio API, sharing queue/notify/EOS/ownership with video and preserving video ABI. Mono/stereo49packets ->50176 decodedframes with real440Hz, PTS/EOS and retained-buffer checks pass; video and MediaPlayer regressions pass. Codec2 audio component and A/V app capture are next; no audio component is advertised yet.



   **Bluetooth** after camera: Robolectric-style shadows of `android.bluetooth.*` over a host daemon speaking D-Bus to BlueZ (not Android's own stack: it needs
   system_server's services and an exclusive controller), scoped by what the corpus apps actually call (BLE scan, GATT client/server and advertising first; then RFCOMM/L2CAP
   sockets; audio profiles through PipeWire), with a compatibility table of every API and its status; unimplemented calls return real errors. Developed against
   BlueZ's `btvirt`/vhci virtual controllers (the VM has no Bluetooth hardware).
   **Decided with the user (2026-10-27):** display backlight and thermal are the host's business (not ours); accounts are the Android way (the account apps' own
   authenticators through `AccountManager`, no host account bridge). Connectivity reports the host's real state (NetworkManager for Wi-Fi/Ethernet, ModemManager for
   cellular if the host has a modem, otherwise "no SIM"; a clearly labelled virtual-SIM test mode is the only exception); also battery/power (UPower, logind), sensors
   (IIO), game controllers and haptics (evdev), USB (udev), biometrics (fprintd), NFC (PC/SC), file/screen-capture portals.
8b. **System apps and providers on every instance by default** (user, 2026-10-27): the tree's own ContactsProvider, CalendarProvider, DownloadProvider, SettingsProvider,
   TelephonyProvider, UserDictionaryProvider, DocumentsUI (+ContactsPicker), MediaProvider and friends (APKs are in the build's `system/priv-app`), each with its data in the
   instance's storage. They are apps like any other (converted and run by apklayer), but their consumers are other apps, so this is the cross-process case: apps reach
   `ContentProvider`s through `IContentProvider` binder calls. Plan: an instance "system JVM" hosts the providers; app JVMs reach them over real Binder (the kernel binder
   already runs on the instance; the Java `Binder`/`BinderProxy` JNI has to be registered and un-shadowed for this one path, per PLAN P6's "real binder where a
   process boundary exists"); the rest of the system services stay shadows. Start with Settings and Contacts/Calendar, then Media/Documents.
8c. **Apps talk to each other like on Android** (user, 2026-10-27: "intents, broadcasts, binder AIDLs... should work like native Android"). Decision: **an instance
   hub next to the Robolectric shadows** (option 1 of three: hub; our own thin system_server with the real framework client code (ActivityThread/ContextImpl instead of
   Robolectric's ActivityController); the tree's real system_server). Hub = one process per instance owning the package registry (what `apklayer install` records), receiver
   registrations (manifest and dynamic), the directory of running services and providers, app process lifecycle, and the pid-to-package table that gives
   `Binder.getCallingUid` and permission checks their answers (all JVMs run as one host user). Each app JVM keeps its shadows; whatever targets another package is
   forwarded to the hub. `IBinder`s cross JVMs over the real kernel binder: the real `android.os.Binder`/`BinderProxy`/`Parcel` JNI is registered and Robolectric's
   shadows for them are removed, so AIDL stubs, `linkToDeath` and Parcelables behave as on a device. The hub speaks the real AIDL interfaces where they exist
   (IContentProvider, IIntentSender, IServiceConnection...), so a later move to our own system_server (option 2) does not change the apps.
   **Current status — E1 verified:** real Binder/BinderProxy/Parcel JNI and the headless singleton hub are working. Ordinary Context.bindService uses SDK-generated AIDL across app JVMs and original LoadedApk/IServiceConnection dispatch. Tests pass normal permission/exported checks, null binding, 20 immediate cancellations, Unicode/Bundle payloads, native caller identities, abrupt server death, automatic restart/reconnect, unbind/onDestroy and process cleanup. Service-only APKs launch without a display; the hub starts automatically using real publication notifications. Real-Binder media regressions and the GPU dialog/resize/input regression also pass.

   **Remaining:** E2 broadcasts; E3 providers/observers; E4 activities/results, started services and pending intents; E5 notifications. Extend service handling for multiple bindings per connection, filter-specific rebind and broader signature/runtime permission grants. E is not complete. Tests: `tests/vm/binder.sh hub`, `tests/vm/hub-services.sh bound`, `tests/vm/hub-services.sh auto`.

   Order of work, each step with an automated cross-app test on the VM: (1) two tiny test apps, one with a bound AIDL service, one binding to it across two JVMs, with a death
   notification; (2) broadcasts (manifest and dynamic receivers, ordered, sticky, permissions); (3) content providers (Settings first, then Contacts/Calendar) with
   observers; (4) cross-app `startActivity`/`startActivityForResult`, `startService`, pending intents; (5) notifications and the rest. Behaviour is checked against Android's
   documented semantics and, later, CTS's app-framework modules.
8d. **Revisit all of the user's test apps** (`/Users/erfanabdi/TestApps`, user 2026-10-27: some fail) on the GPU route once media is done: install each, launch through
   `bin/apklayer run`, record what fails (converter, classpath conflicts such as NewPipe's R8-merged `kotlin.math.MathKt` vs the platform jar's Kotlin, missing services/natives),
   fix by cause not by app, and keep a table of app, status, cause in `docs/APPS.md`. Corpus tooling: `tests/vm/corpus.sh`.
9. Vulkan (P7.5) after the HALs.
10. **CTS (P10) stays last**: not before media, camera and the other HALs work.

### P7.5 — Vulkan (user goal, 2026-10-26)

Reuse Android's `libvulkan.so` loader and WSI with our Vulkan HAL frontend over the host's Vulkan loader (Mesa, NVIDIA,
any vendor; no driver dependency). The intended presentation path provides what Android's WSI expects: `VK_KHR_android_surface` (`vkCreateAndroidSurfaceKHR` over an
`ANativeWindow`) and a swapchain whose images are buffers dequeued from that window: each gralloc dmabuf is imported as a `VkImage`
(`VK_EXT_external_memory_dma_buf`, `VK_EXT_image_drm_format_modifier`), acquire is `dequeueBuffer`, present is `queueBuffer` with the
render-complete semaphore exported as a sync fd (`VK_KHR_external_semaphore_fd`). Zero copy (better than the GL window path, which
needs one GPU blit because a GBM window surface cannot render into foreign buffers); resize is the app's `OUT_OF_DATE` handling.
Also AHardwareBuffer import for Vulkan, and HWUI's Vulkan backend (`debug.hwui.renderer=skiavk`) as the target once the layer
works. First step: probe the VM's driver for the needed extensions (virtio-gpu may lack them; Venus would provide them), then the
layer, then a `vktest` tool beside `gltest`.

**Probe (2026-10-26, Parallels VM):** no hardware Vulkan: the only device is llvmpipe (CPU; `VK_EXT_external_memory_dma_buf` and
`external_memory_fd` yes, but no `image_drm_format_modifier` and no `external_semaphore_fd`), and the Venus ICD (`virtio_icd.json`) finds no GPU
(Parallels' virtio-gpu offers no Venus). The layer can be written against llvmpipe for structure (dmabuf import, dequeue/queue) but
synchronisation and the zero-copy claim need a machine with a real GPU driver (a Mesa or NVIDIA desktop); mark such steps "needs real
GPU" and verify there.


## Vulkan G1 checkpoint (2026-10-28): native execution works, presentation unfinished
- Original Android libvulkan now runs over a `vulkan.apklayer` frontend to the host libvulkan.so.1. Reuse AOSP's API/WSI implementation rather than duplicating its swapchain machinery. Host dispatchable handles retain their original vtables; Android private data lives in a guarded side table, retired on buffer/pool/device/instance destruction (`scripts/patch-vulkan.sh`).
- `scripts/native-env.sh` preserves system XDG data paths so the host loader finds actual ICD manifests. The optional renamed loader (`libapklayer_vulkan.so`) and HAL activate the host dispatch mode. `scripts/orb-build-vulkan.py` incrementally rebuilds the original loader and two small tools with existing generated arm64 commands; `scripts/install-vulkan.sh` stages and SHA-verifies them. No clean or product change.
- `tests/vm/vulkan.sh` runs a real APK through JNI: six Vulkan instance/device lifecycles, actual image clear/copy and 256 pixel checks per cycle, raw Android symbol and host PFN dispatch, alternating explicit command-buffer free/pool destruction, enumeration VK_INCOMPLETE handling, rejection of unsupported swapchain enablement, frame-commit screenshot, physical Back and process cleanup. Driver truthfully reports llvmpipe (CPU), Vulkan 1.4.318, dmabuf available, DRM modifiers absent, sync-fd semaphore features zero. The visible test UI uses HWUI OpenGL; it is not a Vulkan presentation demo.
- **G2/G3 remain required:** implement VK_ANDROID_native_buffer image import from our gralloc dmabufs (format/modifier/memory-type validation and FD ownership); vkAcquireImageANDROID/vkQueueSignalReleaseImageANDROID synchronization; Android AHardwareBuffer Vulkan memory import/export; verify original WSI acquire/present/resize/abandon and multiple swapchains; then enable and test HWUI skiavk. The HAL and loader deliberately withhold Android swapchain/presentation-dependent extensions and report no surface support until native-buffer support exists. No zero-copy, Vulkan gaming or hardware presentation claim.
- End-to-end G2/G3 verification **needs a real Vulkan GPU/driver** with compatible dmabuf/modifier import and sync-fd semaphore support. The current Parallels virgl VM cannot supply those capabilities. Do not mark P7.5/Milestone G complete. TextureView canyon pixels/animation/Back and Chromium WebView JavaScript regressions pass with the new loader installed.

### P7 fallback and x86 counterpart (user, 2026-10-26)
**Fallback (done):** Robolectric 4.17's own native runtime for arm64 (third party, `scripts/fetch-robolectric-arm64.sh`, THIRD_PARTY.md) as a selectable flavor
of `bin/apklayer` (`APKLAYER_NATIVE` directory holding `robolectric-nativeruntime-linux-aarch64.jar`, Maven Robolectric and its android-all as in the
default flavor): uibench runs on the arm64 JVM with no Rosetta, 19 frames, same output size as the x86 baseline. It is CPU rendering (no GPU zero-copy)
and stays as the fallback while the device-library route is finished. **x86_64 counterpart (deferred: the very last thing, after the tool has been published; user, 2026-10-26):** the tree's own `libandroid_runtime` for the
Linux x86_64 host (the tree-consistent Robolectric native runtime for x86 hosts), built in ~/los24 (docs/AOSP-BUILDS.md), to replace the
Rosetta-era x86 library where the tree's framework jar is used.

- [ ] NewPipe: the loading spinner (ProgressBar in the middle while content loads) is not shown; revisit (user, 2026-10-27).
- [ ] NewPipe: one cold start stayed black (window never drew after the mapper loaded; second start drew), and every start logs "CursorWindow: Failed to read row <garbage> from a window with 0 rows" (native CursorWindow read through a shadowed Java one). Look at both (2026-10-27).
- [ ] NewPipe: HWUI logs a "Davey!" with absurd durations for every frame; check the Choreographer frame timeline / vsync ids.
- [ ] Media zero-copy: today GStreamer decodes into system memory (appsink I420) and copyGraphic writes it into the gralloc block (one CPU copy, now whole rows). Target: (a) hardware decoders (VA-API/V4L2) export dmabuf, our gralloc imports it as the output block, no CPU touch; (b) software decode: appsink gets an allocation pool over the mapped gralloc block (GST_QUERY_ALLOCATION + GstVideoMeta) so avdec writes straight into it. The VM has no hardware decoder, so (a) needs a real GPU host to verify (2026-10-27).
- NewPipe playback investigation (2026-10-27), findings so far: video decode and the copy into the output block are not the limit; the output block wait is (the app releases frames at ~8 fps, 1/3 of 24 fps); the app clock (SystemClock) matches wall time after the AppHost fix; PipeWire `apklayer.playback` shows many xruns; SurfaceFlinger's `RegSampIdle` thread spins at 100% CPU (timer wait under bionic_translation); NewPipe then crashed (VM thread, pc 0x1400). Parked by the user until media and audio are finished; revisit all TestApps afterwards (8d).

- **Media B, audio codecs and containers (2026-10-27): done and verified.** MP3, Vorbis, Opus, FLAC (extractor-decoded) and WAV through the real MediaExtractor and the tree's own software codecs (`c2.android.mp3/vorbis/opus/raw.decoder`), asserted on PCM rms, 440 Hz pitch, sample count, PTS and EOS (`tests/vm/native-media.sh`, AudioFormatsCheck). AMR, G.711, GSM, HEVC, MPEG-2/4, H.263, AV1 decoders and all encoders are listed and installed but not yet tested. Still open for B: audio clock/xruns (NewPipe symptom), HEVC and encoders tests, AudioRecord, MediaRecorder.
- **Audio clock and volume (2026-10-27): fixed and gated.** Two causes of NewPipe's "no audio / slow video": (1) the PipeWire HAL queued every write as its own few-hundred-frame buffer, so the graph consumed one small buffer per cycle: audio ran at 1/3 real time with thousands of xruns, and ExoPlayer, which follows the audio clock, played video at 8 fps. The HAL now keeps a ring and the process callback hands PipeWire a full requested buffer per cycle. (2) Every stream's volume index started at 0 (Robolectric's manager), i.e. silence; the policy now starts at AudioService's defaults (music 5/15). Gates in `tests/vm/native-audio.sh audio`: 8 s of PCM play in 8.00 s, the clock advances at 48000 frames/s, no underruns, default music volume > 0 and not muted. NewPipe needs a retest by the user (they asked to revisit apps after media/audio).
- **Media B, codecs (2026-10-27): verified end to end** (`tests/vm/native-media.sh`): decoders HEVC, MPEG-4, AV1 (dav1d) video and G.711 mu/A-law audio through the real extractors (solid red luma 81, 440 Hz); encoders AAC, Opus, FLAC and H.264 through MediaCodec with a decode round trip (440 Hz at the expected level; 12 H.264 frames back). The tree's software codecs are served by `SoftCodecs.cpp` beside the host decoders; `package_native` was added to the permission service (MediaCodec waits for it when creating an encoder). Fixed on the way: H.264 codec-config buffers shared PTS 0 with the first frame (the first output had no PTS). Not covered yet: AMR (no encoder for fixtures here), H.263, MPEG-2 decode tests; AudioRecord/MediaRecorder need an audio *input* HAL (PipeWire capture), the next audio item; camera follows. MediaPlayer on audio-only files (MP3 etc.) is covered indirectly by the extractor+decoder tests and the AAC player test.
- **Audio input (2026-10-27): AudioRecord works end to end** (`tests/vm/native-audio.sh audio`, RecordCheck): the real AudioRecord, AudioFlinger's RecordThread and a PipeWire capture stream in the audio HAL (`bridge/hals/audio/capture.c`, `PipeWireInput` in `aosp/hals/audio/Stream.cpp`, a "Host Microphone" device and "primary input" mix port in the policy). The test plays 440 Hz left / 880 Hz right into a null sink and records its monitor (`APKLAYER_AUDIO_TARGET_IN`; empty means the host's default microphone): 2.5 s read in 2.56 s, both tones recovered. Needed on the way: services `appops`, `scheduling_policy` and `package_native` in the permission service (AudioFlinger's input path waited for them forever: audioserver never became ready), `register_android_media_AudioRecord` plus a native AudioRecord shadow, and the stack's ready marker now also waits for the audio policy service. MediaRecorder (StagefrightRecorder over the same input and the encoders) is the next step; camera after.
- Decoders AMR-NB/WB, H.263 and MPEG-2 are tested (zeros-amrnb.amr checks the sample count only: no AMR-NB encoder here to make a real fixture).
- **MediaRecorder (2026-10-27): verified** (`tests/vm/native-audio.sh audio`, RecorderCheck): the original MediaRecorder (MIC, MPEG-4, AAC, 48 kHz stereo, 128 kbit/s) over the real recorder service (StagefrightRecorder, MPEG4Writer, c2.android.aac.encoder) and AudioRecord/the HAL capture: 3.12 s recorded into a 50 KB .m4a that MediaExtractor reads (audio/mp4a-latm, duration 2-4 s) and the AAC decoder turns back into 440 Hz left / 880 Hz right. Needs the Codec2 service in the stack (the test turns it on). **Media (milestone B) is closed**: decoders, encoders, extractors, MediaPlayer, audio output with a real-time clock, audio input, MediaRecorder (audio). Not part of it: video recording with a camera or Surface source (camera milestone C), hardware zero-copy decode (needs a host with a hardware decoder).

- **Milestone E work list (2026-10-27)** is in `docs/MILESTONE-E.md` (hub, services, broadcasts, providers, activities, pending intents, notifications, then the real com.android.webview provider). **E1 done (step 1a)**: real Binder/Parcel JNI in the app JVM (`-Dapklayer.realbinder=true`), `RealBinder.publish/lookup`, `tests/vm/binder.sh` (two JVMs: published binder, call, callback, caller pid identities, death notification on `kill -9`); media, player, audio and gpu regressions pass with the flag on. Next: the hub process (step 1b).
- **E2 broadcasts (2026-10-27), first part verified**: `tests/vm/broadcasts.sh` (manifest receiver of a not-running package, dynamic/manifest delivery across apps, exported rules, permissions, ordered with priorities/result/abort, sticky). Details and the open items in `docs/MILESTONE-E.md`.

(UiBench GL TextureView and Modern Combat 3 notes: `docs/history/PLAN-gl-textureview-mc3.md`.)

- Native-game coverage gap (OTR2 1.3.2, 2026-10-28): provide and verify OpenSL ES slCreateEngine/native audio path. Current VM has legacy libOpenSLES.so.1 mapping and no staged original Android OpenSLES library; game fails before graphics. Test evidence in tests/corpus/results.md.

## Licensing (decided 2026-10-28)
Decision: AGPL-3.0-or-later for everyone plus a paid commercial license for companies that do not want the AGPL terms (audit table in `docs/THIRD_PARTY.md`). Done 2026-10-28: `runtime/dex2jvm` is upstream kksimp/dex2jvm (Apache-2.0, package `io.github.kksimp.dex2jvm`, commit in `runtime/dex2jvm/.dex2jvm-commit`) plus our own `HostDefaults.java`; Mac.apk code and names removed (`MacApkUnsafe` replaced by the generated `apklayer.loader.ApklayerUnsafe`); labels in THIRD_PARTY/NOTICE/README/D4/D5 fixed; `COMMERCIAL.md` added. Still to do (human decisions, not blocking): (1) fill in the contact and terms in `COMMERCIAL.md`; (2) CLA and ownership of the agent-written commits; (3) collect NOTICE files of everything shipped; (4) the Android SDK `android.jar` terms if it is ever bundled.
