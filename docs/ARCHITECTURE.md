# apklayer — Architecture (Robolectric method)

## One sentence
Run the **unmodified AOSP framework compiled for the JVM** (Robolectric's `android-all`), load
the APK's DEX as JVM classes (dex2jvm), drive the real Activity lifecycle, and connect the
result to the Linux desktop through a thin host layer (Wayland today).

## Pipeline

```
 app.apk ─┬─ classes*.dex ──dex2jvm──> app classes (.jar) ──DenyInstrument──> @DoNotInstrument
          │                 (hierarchy from android-all)
          └─ manifest + resources.arsc ──AOSP PackageParser / AssetManager (via test_config.properties)

 JVM process (x86_64 under Rosetta on the arm64 VM; native on x86_64 hosts)
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ Robolectric sandbox: android-all-instrumented 16 (SDK 36) + shadows 4.17     │
 │ NATIVE graphics: librobolectric-nativeruntime = real HWUI/Skia/minikin       │
 │                                                                              │
 │ runtime/host driver (RunUiBench)                                                │
 │   activity back stack ← startActivity()/finish() (recorded by Robolectric)   │
 │   every 16 ms: idle main Looper → draw all windows (WindowManagerGlobal)     │
 │                → Bitmap → int[]                                              │
 │   input ring → MotionEvent/KeyEvent → topmost window                         │
 └───────────────┬──────────────────────────────────────────────▲───────────────┘
                 │ /dev/shm/apklayer-fb (pixels + input ring)    │
 ┌───────────────▼──────────────────────────────────────────────┴───────────────┐
 │ apklayer-presentd (native, host arch): wl_shm xdg_toplevel, wl_seat input    │
 └──────────────────────────────────────────────────────────────────────────────┘
                                   Wayland compositor
```

## Components

| Path | Role |
|---|---|
| `runtime/dex2jvm/` | DEX → JVM bytecode (from Mac.apk, AGPL). Reads class hierarchy from its own classpath, so it must be run with the target `android-all` on it. |
| `runtime/host/java/apklayer/tools/DenyInstrument.java` | Stamps a runtime-visible `@DoNotInstrument` on the app classes. dex2jvm's `<init>` lowering conflicts with Robolectric's constructor renaming. |
| `runtime/host/java/apklayer/rob/RunUiBench.java` | Driver. It runs as a JUnit test under `RobolectricTestRunner` and handles the lifecycle, back stack, window composition and input dispatch. |
| `runtime/host/java/apklayer/rob/HostWindow.java` | Host presenter access: a shared framebuffer (pure Java), or in-process JNI through `WlWindow`. |
| `runtime/host/jvm.args` | JDK `--add-opens` flags Robolectric needs on modern SDKs (copied from Robolectric's own test configuration). |
| `bridge/shell/present.c` | Wayland client: xdg_toplevel, wl_shm buffer, wl_seat pointer/keyboard queue, plus JNI entry points. |
| `bridge/shell/presentd.c` | Out-of-process presenter that maps the shared framebuffer. Lets the app JVM's arch differ from the host's. |
| `scripts/vm-run.sh` | Syncs to the VM, rebuilds, converts and runs. |

## Shared framebuffer layout (`/dev/shm/apklayer-fb`, little-endian int32)

| Index | Meaning |
|---|---|
| 0 | magic `APKL` (0 = producer gone) |
| 1, 2 | width, height |
| 3 | frame sequence (bumped after the pixels are written) |
| 4 | close requested (set by presentd) |
| 5 | input events written (monotonic, set by presentd) |
| 8 .. 8+4·64 | input ring: `{0 down / 1 up / 2 move, x, y, 0}` or `{100, evdev key, pressed, 0}` |
| 264 .. | ARGB pixels, width × height |

## Why these choices
- **Unmodified framework.** Android version bumps mean switching the `android-all` jar, not
  re-applying patches. There are no hand patches to AOSP or Robolectric (see docs/PATCHES.md).
- **NATIVE graphics.** LEGACY mode is a recording fake: it draws no text and doesn't compose
  `translate`. NATIVE mode runs AOSP's own HWUI/Skia.
- **Rosetta + split presenter.** Robolectric publishes its native runtime for linux-x86_64,
  mac and windows only, never linux-arm64. Building it means building AOSP (forbidden). The
  arm64 VM therefore runs an x86_64 JDK under Rosetta (C1 JIT + Serial GC, because C2 and G1
  crash there). Frames cross a shared file to a native presenter, so no x86_64 Wayland
  library is needed.

## Known limits
See docs/PLAN.md: the plan of record, with the target architecture (apklayer-shell + one JVM
per app + apklayerd) and phases P1–P7.
