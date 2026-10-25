# apklayer — Architecture (Robolectric method)

## One sentence
Run the **unmodified AOSP framework compiled for the JVM** (Robolectric's `android-all`), load
the APK's DEX as JVM classes (dex2jvm), drive the real Activity lifecycle, and connect the
result to the Linux desktop through a thin host layer (Wayland today).

## Pipeline

```
 app.apk ─┬─ classes*.dex ──dex2jvm──> app classes (.jar) ──DenyInstrument──> @DoNotInstrument
          │                 (hierarchy from android-all)
          └─ manifest + resources.arsc ──AOSP PackageParser / AssetManager (APK passed as the manifest)

 App JVM, one per app (x86_64 under Rosetta on the arm64 VM until PLAN P7; native on x86_64)
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ Robolectric sandbox: android-all-instrumented 16 (SDK 36) + shadows 4.17     │
 │ NATIVE graphics: libandroid_runtime (in android-all) = real HWUI/Skia        │
 │                                                                              │
 │ apklayer.host (Simulator method, no JUnit): Main → SandboxBuilder → AppHost  │
 │   activity back stack ← startActivity()/finish() (recorded by Robolectric)   │
 │   event loop: Looper + paused Choreographer; real draw traversals            │
 │   on draw: ScreenUpdateHandler → takeScreenshot (all windows) → int[]        │
 │   shell INPUT → MotionEvent/KeyEvent → UiAutomation.injectInputEvent         │
 │   ShellClient (pure Java): Unix socket + 2-buffer shm file per window        │
 └───────────────┬──────────────────────────────────────────────▲───────────────┘
   FRAME(buf, damage) │ $XDG_RUNTIME_DIR/apklayer/shell.sock     │ INPUT / RELEASE / CLOSE
 ┌───────────────▼──────────────────────────────────────────────┴───────────────┐
 │ apklayer-shell (native, host arch, one per session, many apps):              │
 │   xdg_toplevel per window; the app's shm file is the wl_shm pool (zero copy) │
 │   wl_seat pointer/keyboard → focused window's app                            │
 └──────────────────────────────────────────────────────────────────────────────┘
                                   Wayland compositor
```

## Components

| Path | Role |
|---|---|
| `runtime/dex2jvm/` | DEX → JVM bytecode (from Mac.apk, AGPL). Reads class hierarchy from its own classpath, so it must be run with the target `android-all` on it. |
| `runtime/host/java/apklayer/tools/DenyInstrument.java` | Stamps a runtime-visible `@DoNotInstrument` on the app classes. dex2jvm's `<init>` lowering conflicts with Robolectric's constructor renaming. |
| `runtime/host/java/apklayer/host/Main.java` | Entry point: builds the sandbox with Robolectric's simulator `SandboxBuilder` (no JUnit) and runs `AppHost` in it. |
| `runtime/host/java/apklayer/host/AppHost.java` | App setup as in the simulator's `AppLoader` (manifest = the APK), the simulator's event loop, launcher resolution, back stack (`Robolectric.setupActivity`-style launches; previous activity stopped and hidden unless the new one is translucent, as `ActivityThread` does), resize/DPI (`hostResize`). |
| `runtime/host/java/apklayer/host/HostInput.java` | What the shell sends (pointer, xkb keys, wheel, configure) → Android events. Keys go through the topmost window's `ViewRootImpl` (focus navigation, touch mode); Backspace = Back outside text fields. |
| `runtime/host/java/apklayer/host/HostWindows.java` | Which window/view gets input: topmost window by type (dialogs above activity windows), hit-testing, wheel routing, debug dumps. |
| `runtime/host/java/apklayer/host/ScriptRunner.java` | Verification steps (`-Dapklayer.script`): tap, key, keydown/keyup, wheel, configure, back. |
| `runtime/host/java/apklayer/host/ShellFrameListener.java` | `FrameListener` for Robolectric's `ScreenUpdateHandler`: receives the composed screen (`UiAutomation.takeScreenshot`) only when something drew, and presents it. |
| `runtime/host/java/apklayer/host/ShellClient.java` | Pure-Java client of apklayer-shell: Unix socket, the window's two-buffer shm file, damage rect vs. the shown frame, buffer release tracking (latest frame wins). |
| `runtime/host/jvm.args` | JDK `--add-opens` flags Robolectric needs on modern SDKs (copied from Robolectric's own test configuration). |
| `bridge/shell/` | `apklayer-shell`: the session's Wayland side for all apps. `shell.h` (protocol doc, shared state), `main.c` (event loop), `client.c` (app sockets), `window.c` (xdg toplevels, zero-copy buffers, in-place rebuffer), `decor.c` (client-side title bar: Back/Minimize/Maximize/Close), `pointer.c` (title bar clicks, edge resize, wheel, cursors), `keyboard.c` (xkb → Android keys, repeat), `registry.c` (globals, seat, output scale). |
| `bin/apklayer` | CLI (PLAN D19). `install`: dex2jvm (cached by APK sha256 + android-all + dex2jvm commit) → DenyInstrument → `Main --describe` (package, label, launcher, version, launcher icon drawn by the framework) → `~/.local/share/apklayer/apps/<pkg>/` + `.desktop` entry (StartupWMClass = app_id) + `.apk` MIME handler. `run`: starts the shell if needed, runs the host with persistent data. Config: `~/.config/apklayer/env`. |
| `scripts/vm-run.sh` | Syncs to the VM, builds with Meson, restarts the shell and runs `bin/apklayer run <apk>`. |
| `scripts/check.sh` | Headless scripted run with snapshots (fast check on the Mac). |

## Shell protocol v1 (Unix socket, little-endian `{u32 type, u32 len, payload}`)

| Dir | Type | Payload |
|---|---|---|
| app → shell | 1 HELLO | `i32 pid`, utf8 package |
| app → shell | 2 WINDOW_CREATE | `i32 id, w, h`, utf8 `"<shm path>\0<title>"` (file = 2 × w·h ARGB buffers) |
| app → shell | 3 FRAME | `i32 id, buf, x, y, w, h` (damage rectangle) |
| app → shell | 4 WINDOW_DESTROY | `i32 id` |
| shell → app | 101 INPUT | `i32 id, type, a, b`: type 0/1/2 = touch DOWN/UP/MOVE at (a, b); 100 = key (evdev a, pressed b) |
| shell → app | 103 CLOSE | `i32 id` (window closed by the user) |
| shell → app | 104 RELEASE | `i32 id, buf` (compositor released that buffer) |

## App data (PLAN D20)

Robolectric creates `<pkg>-dataDir`, `userDataDir` and `deviceDataDir` in its per-run temp
directory when the Application is created, and deletes them at exit. The host runs with
`LazyApplication` ON and pre-creates those three entries as links into
`apps/<pkg>/data/{app,user,device}` (`-Dapklayer.data`, `-Dapklayer.package`) before the
Application exists. Robolectric keeps an existing entry and its exit cleanup does not follow
links, so SharedPreferences, databases, files and caches persist across launches.

## Why these choices
- **Unmodified framework.** Android version bumps mean switching the `android-all` jar, not
  re-applying patches. There are no hand patches to AOSP or Robolectric (see docs/PATCHES.md).
- **NATIVE graphics.** LEGACY mode is a recording fake: it draws no text and doesn't compose
  `translate`. NATIVE mode runs AOSP's own HWUI/Skia.
- **Separate shell process.** Crash isolation, one component owning Wayland for every app,
  and no native code inside app JVMs, so an app JVM's architecture can differ from the host's.
- **Rosetta (development stopgap until PLAN P7).** Robolectric publishes its native runtime
  for linux-x86_64, mac and windows only. The arm64 VM therefore runs the app JVM as x86_64
  under Rosetta (C1 JIT + Serial GC, because C2 and G1 crash there; Robolectric's temp-dir
  cleanup at exit occasionally segfaults too). P7 builds the runtime for native arm64.

## Known limits
See docs/PLAN.md: the plan of record, with the target architecture (apklayer-shell + one JVM
per app + apklayerd) and phases P1–P7.
